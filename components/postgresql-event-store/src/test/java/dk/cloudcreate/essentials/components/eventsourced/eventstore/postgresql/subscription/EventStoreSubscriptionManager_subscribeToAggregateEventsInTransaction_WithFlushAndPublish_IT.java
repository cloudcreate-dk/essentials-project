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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.reactive.EventHandler;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.*;
import java.util.*;
import java.util.stream.*;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration.standardSingleTenantConfiguration;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EventStoreSubscriptionManager_subscribeToAggregateEventsInTransaction_WithFlushAndPublish_IT {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
    public static final AggregateType PRODUCTS  = AggregateType.of("Products");
    public static final AggregateType ORDERS    = AggregateType.of("Orders");

    private Jdbi                                                                    jdbi;
    private AggregateType                                                           aggregateType;
    private EventStoreUnitOfWorkFactory<EventStoreUnitOfWork>                       unitOfWorkFactory;
    private TestPersistableEventMapper                                              eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?>         postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest").withDatabaseName("event-store")
                                                                                                                   .withUsername("test-user")
                                                                                                                   .withPassword("secret-password");
    private       EventStoreSubscriptionManager  eventStoreSubscriptionManagerNode1;
    private       RecordingLocalEventBusConsumer recordingLocalEventBusConsumer;


    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        aggregateType = ORDERS;
        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        eventMapper = new TestPersistableEventMapper();
        var jsonSerializer = new JacksonJSONEventSerializer(createObjectMapper());
        var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                       unitOfWorkFactory,
                                                                                       eventMapper,
                                                                                       SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration(jsonSerializer,
                                                                                                                                                                                      IdentifierColumnType.UUID,
                                                                                                                                                                                      JSONColumnType.JSONB));

        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                persistenceStrategy);
        eventStore.addAggregateEventStreamConfiguration(aggregateType,
                                                        OrderId.class);
        eventStore.addAggregateEventStreamConfiguration(standardSingleTenantConfiguration(PRODUCTS,
                                                                                          jsonSerializer,
                                                                                          AggregateIdSerializer.serializerFor(ProductId.class),
                                                                                          IdentifierColumnType.TEXT,
                                                                                          JSONColumnType.JSON));
        recordingLocalEventBusConsumer = new RecordingLocalEventBusConsumer();
        eventStore.localEventBus()
                  .addSyncSubscriber(recordingLocalEventBusConsumer);
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
        if (eventStoreSubscriptionManagerNode1 != null) {
            eventStoreSubscriptionManagerNode1.stop();
        }
        recordingLocalEventBusConsumer.clear();
    }


    @Test
    void subscribe_using_FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStream() {
        eventStore.addEventStoreInterceptor(new FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStream());
        var durableSubscriptionRepository = new PostgresqlDurableSubscriptionRepository(jdbi, eventStore);
        eventStoreSubscriptionManagerNode1 = EventStoreSubscriptionManager.createFor(eventStore,
                                                                                     50,
                                                                                     Duration.ofMillis(100),
                                                                                     new PostgresqlFencedLockManager(jdbi,
                                                                                                                     unitOfWorkFactory,
                                                                                                                     Optional.of("Node1"),
                                                                                                                     Duration.ofSeconds(3),
                                                                                                                     Duration.ofSeconds(1),
                                                                                                                     false),
                                                                                     Duration.ofSeconds(1),
                                                                                     durableSubscriptionRepository);
        eventStoreSubscriptionManagerNode1.start();

        var testEvents = createTestEvents();

        var productEventsReceived = new ArrayList<PersistedEvent>();
        var productsSubscription = eventStoreSubscriptionManagerNode1.subscribeToAggregateEventsInTransaction(
                SubscriberId.of("ProductsSub1"),
                PRODUCTS,
                Optional.empty(),
                new TransactionalPersistedEventHandler() {
                    @Override
                    public void handle(PersistedEvent event, UnitOfWork unitOfWork) {
                        System.out.println("Received Product event: " + event);
                        productEventsReceived.add(event);
                    }
                });
        System.out.println("productsSubscription: " + productsSubscription);

        var orderEventsReceived = new ArrayList<PersistedEvent>();
        var ordersSubscription = eventStoreSubscriptionManagerNode1.subscribeToAggregateEventsInTransaction(
                SubscriberId.of("OrdersSub1"),
                ORDERS,
                Optional.empty(),
                new TransactionalPersistedEventHandler() {
                    @Override
                    public void handle(PersistedEvent event, UnitOfWork unitOfWork) {
                        System.out.println("Received Order event: " + event);
                        orderEventsReceived.add(event);
                    }
                });

        System.out.println("ordersSubscription: " + ordersSubscription);

        // Persist all test events
        testEvents.forEach((aggregateType, aggregatesAndEvents) -> {
            aggregatesAndEvents.forEach((aggregateId, events) -> {
                var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
                System.out.println(msg("Persisting {} {} events related to aggregate id {}",
                                       events.size(),
                                       aggregateType,
                                       aggregateId));
                var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                     aggregateId,
                                                                     events);
                assertThat(aggregateEventStream.aggregateId()).isEqualTo(aggregateId);
                assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
                assertThat(aggregateEventStream.eventList().size()).isEqualTo(events.size());
                unitOfWork.commit();
            });
        });

        // Verify we received all product events
        var totalNumberOfProductEvents = testEvents.get(PRODUCTS)
                                                   .values()
                                                   .stream()
                                                   .map(List::size)
                                                   .reduce(Integer::sum)
                                                   .get();
        System.out.println("Total number of Product Events: " + totalNumberOfProductEvents);
        Awaitility.waitAtMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> assertThat(productEventsReceived.size()).isEqualTo(totalNumberOfProductEvents));
        // Verify we only have Product related events
        assertThat(productEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(PRODUCTS)).findAny()).isEmpty();
        assertThat(productEventsReceived.stream()
                                        .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                        .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfProductEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));

        // Verify we received all Order events
        var totalNumberOfOrderEvents = testEvents.get(ORDERS)
                                                 .values()
                                                 .stream()
                                                 .map(List::size)
                                                 .reduce(Integer::sum)
                                                 .get();
        System.out.println("Total number of Order Events: " + totalNumberOfOrderEvents);
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(orderEventsReceived.size()).isEqualTo(totalNumberOfOrderEvents));
        assertThat(orderEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(ORDERS)).findAny()).isEmpty();
        assertThat(orderEventsReceived.stream()
                                      .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                      .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfOrderEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));

        // Verify event bus publishing
        assertThat(recordingLocalEventBusConsumer.flushPersistedEvents).hasSize(totalNumberOfOrderEvents + totalNumberOfProductEvents);
        assertThat(recordingLocalEventBusConsumer.flushPersistedEvents.stream()
                                                                             .filter(persistedEvent -> persistedEvent.aggregateType().equals(ORDERS))
                                                                             .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                                                             .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfOrderEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));
        assertThat(recordingLocalEventBusConsumer.flushPersistedEvents.stream()
                                                                             .filter(persistedEvent -> persistedEvent.aggregateType().equals(PRODUCTS))
                                                                             .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                                                             .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfProductEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));


        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents).isEmpty();
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents).isEmpty();
        assertThat(recordingLocalEventBusConsumer.afterRollbackPersistedEvents).isEmpty();

        productsSubscription.stop();
        ordersSubscription.stop();
    }


    private Map<AggregateType, Map<?, List<?>>> createTestEvents() {
        var eventsPerAggregateType = new HashMap<AggregateType, Map<?, List<?>>>();

        // Products
        var aggregateType       = PRODUCTS;
        var aggregatesAndEvents = new HashMap<Object, List<?>>();
        for (int i = 0; i < 10; i++) {
            var productId = ProductId.random();
            if (i % 2 == 0) {
                aggregatesAndEvents.put(productId, List.of(new ProductEvent.ProductAdded(productId),
                                                           new ProductEvent.ProductDiscontinued(productId)));
            } else {
                aggregatesAndEvents.put(productId, List.of(new ProductEvent.ProductAdded(productId)));
            }
        }
        eventsPerAggregateType.put(aggregateType, aggregatesAndEvents);

        // Orders
        aggregateType = ORDERS;
        aggregatesAndEvents = new HashMap<>();
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
        eventsPerAggregateType.put(aggregateType, aggregatesAndEvents);
        return eventsPerAggregateType;
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
        public PersistableEvent map(Object aggregateId, AggregateEventStreamConfiguration aggregateEventStreamConfiguration, Object event, EventOrder eventOrder) {
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

    private static class RecordingLocalEventBusConsumer implements EventHandler {
        private final List<PersistedEvent> beforeCommitPersistedEvents  = new ArrayList<>();
        private final List<PersistedEvent> afterCommitPersistedEvents   = new ArrayList<>();
        private final List<PersistedEvent> afterRollbackPersistedEvents = new ArrayList<>();
        private final List<PersistedEvent> flushPersistedEvents         = new ArrayList<>();

        @Override
        public void handle(Object event) {
            var persistedEvents = (PersistedEvents) event;
            if (persistedEvents.commitStage == CommitStage.Flush) {
                flushPersistedEvents.addAll(persistedEvents.events);
            } else if (persistedEvents.commitStage == CommitStage.BeforeCommit) {
                beforeCommitPersistedEvents.addAll(persistedEvents.events);
            } else if (persistedEvents.commitStage == CommitStage.AfterCommit) {
                afterCommitPersistedEvents.addAll(persistedEvents.events);
            } else {
                afterRollbackPersistedEvents.addAll(persistedEvents.events);
            }
        }

        private void clear() {
            beforeCommitPersistedEvents.clear();
            afterCommitPersistedEvents.clear();
            afterRollbackPersistedEvents.clear();
            flushPersistedEvents.clear();
        }
    }
}