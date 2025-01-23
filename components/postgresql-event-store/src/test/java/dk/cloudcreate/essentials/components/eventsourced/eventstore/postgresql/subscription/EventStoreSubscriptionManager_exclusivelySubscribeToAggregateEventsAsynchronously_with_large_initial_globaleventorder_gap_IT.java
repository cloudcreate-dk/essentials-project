/*
 * Copyright 2021-2025 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreManagedUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.LongStream;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EventStoreSubscriptionManager_exclusivelySubscribeToAggregateEventsAsynchronously_with_large_initial_globaleventorder_gap_IT {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
    public static final AggregateType ORDERS    = AggregateType.of("Orders");

    @Container
    private final PostgreSQLContainer<?>        postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");
    private       EventStoreSubscriptionManager eventStoreSubscriptionManager;


    @BeforeEach
    void setup() {
        eventStoreSubscriptionManager = createEventStoreSubscriptionManager("node1");
    }

    @AfterEach
    void cleanup() {
        if (eventStoreSubscriptionManager != null) {
            eventStoreSubscriptionManager.getEventStore().getUnitOfWorkFactory().getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
            assertThat(eventStoreSubscriptionManager.getEventStore().getUnitOfWorkFactory().getCurrentUnitOfWork()).isEmpty();
            eventStoreSubscriptionManager.stop();
        }
    }

    private EventStoreSubscriptionManager createEventStoreSubscriptionManager(String nodeName) {
        var jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                               postgreSQLContainer.getUsername(),
                               postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        var unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        var eventMapper       = new TestPersistableEventMapper();
        var jsonSerializer    = new JacksonJSONEventSerializer(createObjectMapper());
        var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                       unitOfWorkFactory,
                                                                                       eventMapper,
                                                                                       standardSingleTenantConfiguration(jsonSerializer,
                                                                                                                         IdentifierColumnType.UUID,
                                                                                                                         JSONColumnType.JSONB));
        var eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                    persistenceStrategy);
        eventStore.addAggregateEventStreamConfiguration(ORDERS,
                                                        OrderId.class);

        var durableSubscriptionRepository = new PostgresqlDurableSubscriptionRepository(jdbi, eventStore);
        var eventStoreSubscriptionManager = EventStoreSubscriptionManager.createFor(eventStore,
                                                                                    20,
                                                                                    Duration.ofMillis(100),
                                                                                    new PostgresqlFencedLockManager(jdbi,
                                                                                                                    unitOfWorkFactory,
                                                                                                                    Optional.of(nodeName),
                                                                                                                    Duration.ofSeconds(3),
                                                                                                                    Duration.ofSeconds(1),
                                                                                                                    false),
                                                                                    Duration.ofSeconds(1),
                                                                                    durableSubscriptionRepository);
        eventStoreSubscriptionManager.start();
        return eventStoreSubscriptionManager;
    }


    @Test
    void subscribe_with_large_initial_globaleventorder_gap() {
        var unitOfWorkFactory = eventStoreSubscriptionManager.getEventStore()
                                                             .getUnitOfWorkFactory();
        // Override the global-event-order sequence value to create an initial gap
        var globalEventOrderSequenceColumnName = unitOfWorkFactory
                .withUnitOfWork(uow ->
                                        ((PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration>) eventStoreSubscriptionManager.getEventStore())
                                                .getPersistenceStrategy()
                                                .resolveGlobalEventOrderSequenceName(uow, ORDERS)
                                                .get()
                               );
        var initialGlobalEventOrder = unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createQuery("SELECT last_value FROM " + globalEventOrderSequenceColumnName)
                                                                                 .mapTo(Long.class)
                                                                                 .first());
        assertThat(initialGlobalEventOrder).isEqualTo(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue());
        var newInitialGlobalEventOrder = new GlobalEventOrder(1_000_000L);
        unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createUpdate("SELECT setval(:sequenceName, :sequenceValue, false)")
                                                   .bind("sequenceName", globalEventOrderSequenceColumnName)
                                                   .bind("sequenceValue", newInitialGlobalEventOrder)
                                                   .execute());
        var storedGlobalEventOrder = unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createQuery("SELECT last_value FROM " + globalEventOrderSequenceColumnName)
                                                                                .mapTo(Long.class)
                                                                                .first());
        assertThat(storedGlobalEventOrder).isEqualTo(storedGlobalEventOrder.longValue());

        // Given
        var testEvents = createTestEvents();
        var totalNumberOfOrderEvents = testEvents.values()
                                                 .stream()
                                                 .map(List::size)
                                                 .reduce(Integer::sum)
                                                 .get();
        System.out.println("Total number of Order Events: " + totalNumberOfOrderEvents);

        // Persist all test events
        testEvents.forEach((aggregateId, events) -> {
            var eventStore = eventStoreSubscriptionManager.getEventStore();
            unitOfWorkFactory.usingUnitOfWork(() -> {
                System.out.println(msg("Persisting {} {} events related to aggregate id {}",
                                       events.size(),
                                       ORDERS,
                                       aggregateId));
                var aggregateEventStream = eventStore.appendToStream(ORDERS,
                                                                     aggregateId,
                                                                     EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED,
                                                                     events);
                assertThat((CharSequence) aggregateEventStream.aggregateId()).isEqualTo(aggregateId);
                assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
                assertThat(aggregateEventStream.eventList().size()).isEqualTo(events.size());
            });
        });

        // Start subscriber
        var subscription = createOrderSubscription(eventStoreSubscriptionManager);
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> {
                      assertThat(subscription.subscription.currentResumePoint()).isPresent();
                  });
        // And verify that the subscriber found a new resume point, because the lowest persisted global event order is > the GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER
        // Due to waitAtMost timing the subscription may already have started and advanced and hence the getResumeFromAndIncluding can be >= newInitialGlobalEventOrder
        assertThat(subscription.subscription.currentResumePoint().get().getResumeFromAndIncluding()).isGreaterThanOrEqualTo(newInitialGlobalEventOrder);

        // Verify we received all Order events
        Awaitility.waitAtMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> {
                      assertThat(subscription.eventsReceived).hasSize(totalNumberOfOrderEvents);
                  });

        var allReceivedEvents = subscription.eventsReceived.stream().sorted(Comparator.comparing(PersistedEvent::globalEventOrder)).toList();
        assertThat(allReceivedEvents.stream()
                                    .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                    .toList())
                .isEqualTo(LongStream.range(newInitialGlobalEventOrder.longValue(),
                                            newInitialGlobalEventOrder.longValue() + totalNumberOfOrderEvents)
                                     .boxed()
                                     .toList());

        // Check the ResumePoints are updated
        var lastEventOrder = allReceivedEvents.get(totalNumberOfOrderEvents - 1);
        if (subscription.subscription.currentResumePoint().isPresent()) {
            assertThat(subscription.subscription.currentResumePoint().get().getResumeFromAndIncluding()).isEqualTo(lastEventOrder.globalEventOrder().increment()); // When the subscriber is stopped we store the next global event order
        }
    }

    private EventSubscriber createOrderSubscription(EventStoreSubscriptionManager eventStoreSubscriptionManager) {
        var orderEventsReceived = new ConcurrentLinkedDeque<PersistedEvent>();
        var eventSubscription = eventStoreSubscriptionManager.exclusivelySubscribeToAggregateEventsAsynchronously(
                SubscriberId.of("OrdersSubscriber"),
                ORDERS,
                GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                Optional.empty(),
                new FencedLockAwareSubscriber() {
                    @Override
                    public void onLockAcquired(FencedLock fencedLock, SubscriptionResumePoint resumeFromAndIncluding) {
                        System.out.println("Lock acquired: " + fencedLock);
                    }

                    @Override
                    public void onLockReleased(FencedLock fencedLock) {
                        System.out.println("Lock released: " + fencedLock);
                    }
                },
                new PersistedEventHandler() {
                    @Override
                    public void onResetFrom(EventStoreSubscription eventStoreSubscription, GlobalEventOrder globalEventOrder) {
                        throw new IllegalStateException("Method shouldn't be called");
                    }

                    @Override
                    public void handle(PersistedEvent event) {
                        System.out.println("Received Order event: " + event);
                        orderEventsReceived.add(event);
                    }
                });
        return new EventSubscriber(eventSubscription, orderEventsReceived);
    }

    private Map<OrderId, List<OrderEvent>> createTestEvents() {
        // Orders
        var aggregatesAndEvents = new HashMap<OrderId, List<OrderEvent>>();
        for (int i = 0; i < 100; i++) {
            var orderId = OrderId.random();
            if (i % 2 == 0) {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i),
                                                         new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), i)));
            } else if (i % 3 == 0) {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i),
                                                         new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), i),
                                                         new OrderEvent.ProductOrderQuantityAdjusted(orderId, ProductId.random(), i - 1)));
            } else if (i % 5 == 0) {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i),
                                                         new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), i),
                                                         new OrderEvent.ProductOrderQuantityAdjusted(orderId, ProductId.random(), i - 1),
                                                         new OrderEvent.ProductRemovedFromOrder(orderId, ProductId.random()),
                                                         new OrderEvent.OrderAccepted(orderId)));
            } else {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i)));
            }
        }
        return aggregatesAndEvents;
    }

    private ObjectMapper createObjectMapper() {
        var objectMapper = JsonMapper.builder()
                                     .disable(MapperFeature.AUTO_DETECT_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_SETTERS)
                                     .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                                     .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                     .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                     .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                     .enable(MapperFeature.AUTO_DETECT_CREATORS)
                                     .enable(MapperFeature.AUTO_DETECT_FIELDS)
                                     .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                                     .addModule(new Jdk8Module())
                                     .addModule(new JavaTimeModule())
                                     .addModule(new EssentialTypesJacksonModule())
                                     .addModule(new EssentialsImmutableJacksonModule())
                                     .build();

        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return objectMapper;
    }

    private static class TestPersistableEventMapper implements PersistableEventMapper {
        private final CorrelationId correlationId   = CorrelationId.random();
        private final EventId       causedByEventId = EventId.random();

        @Override
        public PersistableEvent map(Object aggregateId,
                                    AggregateEventStreamConfiguration aggregateEventStreamConfiguration,
                                    Object event,
                                    EventOrder eventOrder) {
            return PersistableEvent.from(EventId.random(),
                                         aggregateEventStreamConfiguration.aggregateType,
                                         aggregateId,
                                         EventTypeOrName.with(event.getClass()),
                                         event,
                                         eventOrder,
                                         EventRevision.of(1),
                                         META_DATA,
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         null);
        }
    }

    private record EventSubscriber(EventStoreSubscription subscription,
                                   ConcurrentLinkedDeque<PersistedEvent> eventsReceived) {

    }
}