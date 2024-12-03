/*
 * Copyright 2021-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful;

import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.Order;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreManagedUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.messaging.MessageHandler;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.Inboxes;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.reactive.command.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues;
import dk.cloudcreate.essentials.reactive.command.CmdHandler;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.slf4j.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory;
import static dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateRepositoryIT.createObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class StatefulAggregateRepositoryCombinedWithInTransactionEventProcessorIT {
    private static final Logger        log                   = LoggerFactory.getLogger(StatefulAggregateRepositoryCombinedWithInTransactionEventProcessorIT.class);
    public static final  AggregateType ORDERS                = AggregateType.of("Orders");
    public static final  ProductId     AUTO_ADDED_PRODUCT_ID = ProductId.random();

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");

    private Jdbi                               jdbi;
    private AggregateType                      aggregateType;
    private EventStoreManagedUnitOfWorkFactory unitOfWorkFactory;

    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;
    private StatefulAggregateRepository<OrderId, OrderEvent, Order>                 ordersRepository;
    private EventStoreSubscriptionManager.DefaultEventStoreSubscriptionManager      eventStoreSubscriptionManager;
    private PostgresqlDurableQueues                                                 durableQueues;
    private Inboxes                                                                 inboxes;
    private DurableLocalCommandBus                                                  commandBus;
    private EventProcessorDependencies                                              eventProcessorDependencies;
    private MyEventProcessor                                                        eventProcessor;
    private CopyOnWriteArrayList<PersistedEvents>                                   collectedEvents;
    private PostgresqlFencedLockManager                                             lockManager;

    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        aggregateType = ORDERS;
        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        var aggregateEventStreamConfigurationFactory = SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration(new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                                      IdentifierColumnType.UUID,
                                                                                                                                                      JSONColumnType.JSONB);
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                                     unitOfWorkFactory,
                                                                                                     new StatefulAggregateRepositoryIT.TestPersistableEventMapper(),
                                                                                                     aggregateEventStreamConfigurationFactory));

        lockManager = PostgresqlFencedLockManager.builder()
                                                 .setJdbi(jdbi)
                                                 .setUnitOfWorkFactory(unitOfWorkFactory)
                                                 .setLockTimeOut(Duration.ofSeconds(3))
                                                 .setLockConfirmationInterval(Duration.ofSeconds(1))
                                                 .buildAndStart();

        eventStoreSubscriptionManager = EventStoreSubscriptionManager.builder()
                                                                     .setEventStore(eventStore)
                                                                     .setFencedLockManager(lockManager)
                                                                     .setDurableSubscriptionRepository(new PostgresqlDurableSubscriptionRepository(jdbi, eventStore))
                                                                     .build();


        ordersRepository = StatefulAggregateRepository.from(eventStore,
                                                            ORDERS,
                                                            reflectionBasedAggregateRootFactory(),
                                                            Order.class);

        durableQueues = PostgresqlDurableQueues.builder()
                                               .setUnitOfWorkFactory(unitOfWorkFactory)
                                               .build();

        inboxes = Inboxes.durableQueueBasedInboxes(durableQueues, lockManager);

        commandBus = DurableLocalCommandBus.builder()
                                           .setDurableQueues(durableQueues)
                                           .setInterceptors(new UnitOfWorkControllingCommandBusInterceptor(unitOfWorkFactory))
                                           .build();

        eventProcessorDependencies = new EventProcessorDependencies(
                eventStoreSubscriptionManager,
                inboxes,
                commandBus,
                List.of()
        );

        collectedEvents = new CopyOnWriteArrayList<PersistedEvents>();
        eventStore.localEventBus().addSyncSubscriber(event -> {
            if (event instanceof PersistedEvents persistedEvents) {
                if (persistedEvents.commitStage == CommitStage.BeforeCommit && !persistedEvents.events.isEmpty()) {
                    log.info("Collected persisted events : {}", persistedEvents.events);
                    collectedEvents.add(persistedEvents);
                }
            }
        });

        durableQueues.start();
        eventStoreSubscriptionManager.start();
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
        if (eventProcessor != null) {
            eventProcessor.stop();
        }
        if (durableQueues != null) {
            durableQueues.stop();
        }
        if (eventStoreSubscriptionManager != null) {
            eventStoreSubscriptionManager.stop();
        }
        if (lockManager != null) {
            lockManager.stop();
        }
    }

    @Test
    void non_exclusive_verify_changes_to_an_aggregate_across_cmdhandler_and_message_handler_in_the_same_unitofwork_is_captured() {
        // Given
        eventProcessor = new MyEventProcessor(
                eventProcessorDependencies,
                false,
                ordersRepository);
        eventProcessor.start();

        // When
        var orderId = OrderId.random();
        commandBus.send(new CreateOrder(orderId, CustomerId.random(), 1234));

        // Then
        assertThat(collectedEvents).hasSize(2);
        assertThat(collectedEvents.get(0).events).hasSize(1);
        assertThat(collectedEvents.get(1).events).hasSize(1);

        var orderCreated = collectedEvents.get(0).events.get(0).event().deserialize();
        assertThat(orderCreated).isExactlyInstanceOf(OrderEvent.OrderAdded.class);

        var productAddedToOrder = collectedEvents.get(1).events.get(0).event().deserialize();
        assertThat(productAddedToOrder).isExactlyInstanceOf(OrderEvent.ProductAddedToOrder.class);

        var order = unitOfWorkFactory.withUnitOfWork(() -> ordersRepository.load(orderId));
        assertThat(order.productAndQuantity).hasSize(1);
    }

    @Test
    void exclusive_verify_changes_to_an_aggregate_across_cmdhandler_and_message_handler_in_the_same_unitofwork_is_captured() {
        // Given
        eventProcessor = new MyEventProcessor(
                eventProcessorDependencies,
                true,
                ordersRepository);
        eventProcessor.start();

        // When
        var orderId = OrderId.random();
        commandBus.send(new CreateOrder(orderId, CustomerId.random(), 1234));

        // Then
        assertThat(collectedEvents).hasSize(2);
        assertThat(collectedEvents.get(0).events).hasSize(1);
        assertThat(collectedEvents.get(1).events).hasSize(1);

        var orderCreated = collectedEvents.get(0).events.get(0).event().deserialize();
        assertThat(orderCreated).isExactlyInstanceOf(OrderEvent.OrderAdded.class);

        var productAddedToOrder = collectedEvents.get(1).events.get(0).event().deserialize();
        assertThat(productAddedToOrder).isExactlyInstanceOf(OrderEvent.ProductAddedToOrder.class);

        var order = unitOfWorkFactory.withUnitOfWork(() -> ordersRepository.load(orderId));
        assertThat(order.productAndQuantity).hasSize(1);
    }

    private record CreateOrder(OrderId orderId, CustomerId customerId, int orderNumber) {
    }

    private static class MyEventProcessor extends InTransactionEventProcessor {
        private final StatefulAggregateRepository<OrderId, OrderEvent, Order> ordersRepository;

        protected MyEventProcessor(EventProcessorDependencies eventProcessorDependencies,
                                   boolean useExclusively,
                                   StatefulAggregateRepository<OrderId, OrderEvent, Order> ordersRepository) {
            super(eventProcessorDependencies, useExclusively);
            this.ordersRepository = ordersRepository;
        }

        @Override
        public String getProcessorName() {
            return "MyEventProcessor";
        }

        @Override
        protected List<AggregateType> reactsToEventsRelatedToAggregateTypes() {
            return List.of(ORDERS);
        }

        @CmdHandler
        void handle(CreateOrder cmd) {
            ordersRepository.save(new Order(cmd.orderId, cmd.customerId, cmd.orderNumber));
        }

        @MessageHandler
        void handle(OrderEvent.OrderAdded orderAdded) {
            var order = ordersRepository.load(orderAdded.orderId);
            order.addProduct(AUTO_ADDED_PRODUCT_ID, 1);
        }
    }
}
