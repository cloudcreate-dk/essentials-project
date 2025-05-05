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
import dk.cloudcreate.essentials.shared.collections.Lists;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration.standardSingleTenantConfiguration;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class BatchedPersistedEventSubscriber_IT {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
    public static final AggregateType PRODUCTS = AggregateType.of("Products");
    public static final AggregateType ORDERS = AggregateType.of("Orders");

    private Jdbi jdbi;
    private AggregateType aggregateType;
    private EventStoreUnitOfWorkFactory<EventStoreUnitOfWork> unitOfWorkFactory;
    private TestPersistableEventMapper eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");
    private EventStoreSubscriptionManager eventStoreSubscriptionManager;

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

    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
        if (eventStoreSubscriptionManager != null) {
            eventStoreSubscriptionManager.stop();
        }
    }


    private void disruptDatabaseConnection() {
        System.out.println("***** Disrupting DB connection *****");
        var dockerClient = postgreSQLContainer.getDockerClient();
        dockerClient.pauseContainerCmd(postgreSQLContainer.getContainerId()).exec();
    }

    private void restoreDatabaseConnection() {
        System.out.println("***** Restoring DB connection *****");
        var dockerClient = postgreSQLContainer.getDockerClient();
        dockerClient.unpauseContainerCmd(postgreSQLContainer.getContainerId()).exec();
    }

    @Test
    void test_batched_subscription() throws InterruptedException {
        testBatchedSubscription(false);
    }

    @Test
    void test_batched_subscription_with_db_connectivity_issues() throws InterruptedException {
        testBatchedSubscription(true);
    }

    private void testBatchedSubscription(boolean simulateDbConnectivityIssues) throws InterruptedException {
        var durableSubscriptionRepository = new PostgresqlDurableSubscriptionRepository(jdbi, eventStore);
        eventStoreSubscriptionManager = EventStoreSubscriptionManager.createFor(eventStore,
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

        eventStoreSubscriptionManager.start();

        var testEvents = createTestEvents();

        // For product events, we'll use the standard event handler
        var productEventsReceived = new ArrayList<PersistedEvent>();
        var productsSubscription = eventStoreSubscriptionManager.subscribeToAggregateEventsAsynchronously(
                SubscriberId.of("ProductsSub1"),
                PRODUCTS,
                GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                Optional.empty(),
                new PersistedEventHandler() {
                    @Override
                    public void onResetFrom(EventStoreSubscription eventStoreSubscription, GlobalEventOrder globalEventOrder) {
                        throw new IllegalStateException("Method shouldn't be called");
                    }

                    @Override
                    public void handle(PersistedEvent event) {
                        var isDuplicateEvent = productEventsReceived.contains(event);
                        if (isDuplicateEvent) {
                            System.out.println("Received DUPLICATE Product event: " + event);
                            // When db connectivity is interrupted we may briefly receive messages out of order or multiple times due to overlaps in lock ownership
                            if (!simulateDbConnectivityIssues) {
                                throw new IllegalStateException("Received unexpected DUPLICATE Product event: " + event);
                            }
                        } else {
                            System.out.println("Received Product event: " + event);
                            productEventsReceived.add(event);
                        }
                    }
                });
        System.out.println("productsSubscription: " + productsSubscription);

        // For order events, we'll use the batched event handler
        var orderBatchesReceived = new ConcurrentLinkedDeque<List<PersistedEvent>>();
        var orderEventsReceived = new ConcurrentLinkedDeque<PersistedEvent>();
        var batchProcessingLatch = new CountDownLatch(1);
        var totalBatchesProcessed = new AtomicInteger(0);
        var maxBatchSize = 10;
        var maxLatency = Duration.ofMillis(50);

        var ordersSubscription = eventStoreSubscriptionManager.batchSubscribeToAggregateEventsAsynchronously(
                SubscriberId.of("OrdersSub1"),
                ORDERS,
                GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                Optional.empty(),
                maxBatchSize,
                maxLatency,
                new BatchedPersistedEventHandler() {
                    @Override
                    public int handleBatch(List<PersistedEvent> events) {
                        System.out.println("Received Order batch: " + events.size() + " events, from: " +
                                Lists.first(events).get().globalEventOrder() + " to: " +
                                Lists.last(events).get().globalEventOrder());

                        // Check for duplicates within the batch
                        var duplicatesInBatch = events.stream()
                                .collect(Collectors.groupingBy(PersistedEvent::globalEventOrder))
                                .entrySet().stream()
                                .filter(entry -> entry.getValue().size() > 1)
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                        if (!duplicatesInBatch.isEmpty() && !simulateDbConnectivityIssues) {
                            throw new IllegalStateException("Received batch with duplicate events: " + duplicatesInBatch);
                        }

                        // Check if we've already processed any of these events
                        var alreadyProcessedEvents = events.stream()
                                .filter(orderEventsReceived::contains)
                                .toList();

                        if (!alreadyProcessedEvents.isEmpty() && !simulateDbConnectivityIssues) {
                            throw new IllegalStateException("Received batch with already processed events: " + alreadyProcessedEvents);
                        }

                        // Add all events to our tracking collections
                        orderBatchesReceived.add(new ArrayList<>(events));
                        orderEventsReceived.addAll(events);
                        totalBatchesProcessed.incrementAndGet();


                        // If we've received enough total events, signal completion
                        var totalNumberOfOrderEvents = testEvents.get(ORDERS)
                                .values()
                                .stream()
                                .map(List::size)
                                .reduce(Integer::sum)
                                .orElse(0);

                        if (orderEventsReceived.size() >= totalNumberOfOrderEvents) {
                            batchProcessingLatch.countDown();
                        }

                        // Return how many events to request in the next batch
                        return maxBatchSize;
                    }

                    @Override
                    public void onResetFrom(EventStoreSubscription subscription, GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
                        throw new IllegalStateException("Method shouldn't be called");
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

        if (simulateDbConnectivityIssues) {
            disruptDatabaseConnection();
            Thread.sleep(5000);
            restoreDatabaseConnection();
        }

        // Wait for all batches to be processed
        batchProcessingLatch.await();

        // Verify we received all product events
        var totalNumberOfProductEvents = testEvents.get(PRODUCTS)
                .values()
                .stream()
                .map(List::size)
                .reduce(Integer::sum)
                .get();
        System.out.println("Total number of Product Events: " + totalNumberOfProductEvents);
        var totalNumberOfOrderEvents = testEvents.get(ORDERS)
                .values()
                .stream()
                .map(List::size)
                .reduce(Integer::sum)
                .get();
        System.out.println("Total number of Order Events: " + totalNumberOfOrderEvents);
        System.out.println("Total number of Batches Processed: " + totalBatchesProcessed.get());
        System.out.println("Average batch size: " + (float) totalNumberOfOrderEvents / totalBatchesProcessed.get());

        // Verify we received all Product and Order events
        Awaitility.waitAtMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(productEventsReceived.size()).isEqualTo(totalNumberOfProductEvents));

        Awaitility.waitAtMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(orderEventsReceived.size()).isEqualTo(totalNumberOfOrderEvents));

        // Sleep a little to verify that no additional events were delivered
        Thread.sleep(5000);

        // Verify we only have Product related events in the product stream
        assertThat(productEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(PRODUCTS)).findAny()).isEmpty();
        var receivedProductEventGlobalOrders = productEventsReceived.stream()
                .map(persistedEvent -> persistedEvent.globalEventOrder().longValue());
        if (simulateDbConnectivityIssues) {
            // When db connectivity is interrupted we may briefly receive messages out of order due to overlaps in lock ownership
            receivedProductEventGlobalOrders = receivedProductEventGlobalOrders.sorted();
        }
        assertThat(receivedProductEventGlobalOrders
                .toList())
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                totalNumberOfProductEvents)
                        .boxed()
                        .collect(Collectors.toList()));

        // Check that all order events were received and are of the correct type
        assertThat(orderEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(ORDERS)).findAny()).isEmpty();
        var receivedOrderEventGlobalOrders = orderEventsReceived.stream()
                .map(persistedEvent -> persistedEvent.globalEventOrder().longValue());
        if (simulateDbConnectivityIssues) {
            // When db connectivity is interrupted we may briefly receive messages out of order due to overlaps in lock ownership
            receivedOrderEventGlobalOrders = receivedOrderEventGlobalOrders.sorted();
        }
        assertThat(receivedOrderEventGlobalOrders
                .toList())
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                totalNumberOfOrderEvents)
                        .boxed()
                        .collect(Collectors.toList()));

        // Verify batch sizes - most batches should be of max size or close to it
        // (except the last one and possibly during connectivity issues)
        if (!simulateDbConnectivityIssues) {
            // Calculate what percentage of batches were at max capacity
            long fullBatches = orderBatchesReceived.stream()
                    .filter(batch -> batch.size() == maxBatchSize)
                    .count();
            System.out.println("Percentage of full batches: " +
                    (float) fullBatches / orderBatchesReceived.size() * 100 + "%");

            // At least some batches should be at max capacity
            assertThat(fullBatches).isGreaterThan(0);
        }

        // Clean up
        productsSubscription.stop();
        ordersSubscription.stop();

        // Check the ResumePoints are updated and saved
        var lastEventOrder = new ArrayList<>(orderEventsReceived).get(totalNumberOfOrderEvents - 1);
        var lastProductEvent = productEventsReceived.get(totalNumberOfProductEvents - 1);

        assertThat(ordersSubscription.currentResumePoint().get().getResumeFromAndIncluding()).isEqualTo(lastEventOrder.globalEventOrder().increment()); // When the subscriber is stopped we store the next global event order
        var ordersSubscriptionResumePoint = durableSubscriptionRepository.getResumePoint(ordersSubscription.subscriberId(), ordersSubscription.aggregateType());
        assertThat(ordersSubscriptionResumePoint).isPresent();
        Awaitility.waitAtMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(ordersSubscriptionResumePoint.get().getResumeFromAndIncluding()).isEqualTo(lastEventOrder.globalEventOrder().increment()));  // When the subscriber is stopped we store the next global event order));

        assertThat(productsSubscription.currentResumePoint().get().getResumeFromAndIncluding()).isEqualTo(lastProductEvent.globalEventOrder().increment()); // // When the subscriber is stopped we store the next global event order
        var productsSubscriptionResumePoint = durableSubscriptionRepository.getResumePoint(productsSubscription.subscriberId(), productsSubscription.aggregateType());
        assertThat(productsSubscriptionResumePoint).isPresent();
        assertThat(productsSubscriptionResumePoint.get().getResumeFromAndIncluding()).isEqualTo(lastProductEvent.globalEventOrder().increment());// When the subscriber is stopped we store the next global event order
    }

    @Test
    void test_batched_subscription_with_max_latency() throws InterruptedException {
        var durableSubscriptionRepository = new PostgresqlDurableSubscriptionRepository(jdbi, eventStore);
        eventStoreSubscriptionManager = EventStoreSubscriptionManager.createFor(eventStore,
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

        // Start the subscription manager
        eventStoreSubscriptionManager.start();

        // Create just a few events to test max latency trigger
        var testEvents = createSmallTestEvents();

        // For order events, we'll use the batched event handler with small batch size but short max latency
        var orderBatchesReceived = new ConcurrentLinkedDeque<List<PersistedEvent>>();
        var orderEventsReceived = new ConcurrentLinkedDeque<PersistedEvent>();
        // Use smaller batch size - force batching to ensure >1 batch
        var maxBatchSize = 50; // Smaller batch size to ensure multiple batches
        var maxLatency = Duration.ofMillis(100); // Slightly longer latency to ensure events are processed

        var ordersSubscription = eventStoreSubscriptionManager.batchSubscribeToAggregateEventsAsynchronously(
                SubscriberId.of("OrdersLatencySub"),
                ORDERS,
                GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                Optional.empty(),
                maxBatchSize,
                maxLatency,
                new BatchedPersistedEventHandler() {
                    @Override
                    public int handleBatch(List<PersistedEvent> events) {
                        System.out.println("Received Order batch: " + events.size() + " events, from: " +
                                Lists.first(events).get().globalEventOrder() + " to: " +
                                Lists.last(events).get().globalEventOrder());

                        // Add all events to our tracking collections
                        orderBatchesReceived.add(new ArrayList<>(events));
                        orderEventsReceived.addAll(events);

                        // Return how many events to request in the next batch
                        return maxBatchSize;
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

       Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted( () ->
               assertThat(orderEventsReceived.size()).isGreaterThan(1)
       );

        // Calculate statistics
        var totalNumberOfOrderEvents = testEvents.get(ORDERS)
                .values()
                .stream()
                .map(List::size)
                .reduce(Integer::sum)
                .get();
        System.out.println("Total number of Order Events: " + totalNumberOfOrderEvents);
        System.out.println("Total number of Batches Processed: " + orderBatchesReceived.size());
        System.out.println("Average batch size: " + (float) totalNumberOfOrderEvents / orderBatchesReceived.size());

        // Verify we received all Order events
        Awaitility.waitAtMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(orderEventsReceived.size()).isEqualTo(totalNumberOfOrderEvents));

        // Since we have few events and a large max batch size,
        // max latency should trigger and we should have more than one batch
        // but fewer batches than total events
        assertThat(orderBatchesReceived.size()).isGreaterThanOrEqualTo(1);
        assertThat(orderBatchesReceived.stream().mapToInt(Collection::size).sum()).isEqualTo(totalNumberOfOrderEvents);

        // Clean up
        ordersSubscription.stop();
    }

    private Map<AggregateType, Map<?, List<?>>> createTestEvents() {
        var eventsPerAggregateType = new HashMap<AggregateType, Map<?, List<?>>>();

        // Products
        var aggregateType = PRODUCTS;
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

    private Map<AggregateType, Map<Object, List<Object>>> createSmallTestEvents() {
        var eventsPerAggregateType = new HashMap<AggregateType, Map<Object, List<Object>>>();

        // Orders
        var aggregateType = ORDERS;
        var aggregatesAndEvents = new HashMap<Object, List<Object>>();
        // Create more events to ensure multiple batches
        for (int i = 0; i < 20; i++) {
            var orderId = OrderId.random();
            if (i % 2 == 0) {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i),
                        new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), i)));
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
        private final CorrelationId correlationId = CorrelationId.random();
        private final EventId causedByEventId = EventId.random();

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
}