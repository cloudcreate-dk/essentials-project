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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.processor;

import com.zaxxer.hikari.*;
import dk.trustworks.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.gap.PostgresqlEventStreamGapHandler;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.observability.EventStoreSubscriptionObserver;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.trustworks.essentials.components.foundation.messaging.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.trustworks.essentials.components.foundation.reactive.command.*;
import dk.trustworks.essentials.components.queue.postgresql.PostgresqlDurableQueues;
import dk.trustworks.essentials.reactive.command.CmdHandler;
import dk.trustworks.essentials.shared.collections.Lists;
import org.awaitility.Awaitility;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration;
import static dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.processor.EventProcessorIT.createObjectMapper;
import static dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.processor.ViewEventProcessorIT.TestOrderViewEventProcessor.TEST_ORDERS;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ViewEventProcessorIT {

    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");

    private HikariConfig                                                            cfg;
    private HikariDataSource                                                        ds;
    private Jdbi                                                                    jdbi;
    private EventStoreUnitOfWorkFactory<EventStoreUnitOfWork>                       unitOfWorkFactory;
    private EventProcessorIT.TestPersistableEventMapper                             eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");

    private TestOrderViewEventProcessor   testProcessor;
    private PostgresqlDurableQueues       durableQueues;
    private EventStoreSubscriptionManager eventStoreSubscriptionManager;
    private PostgresqlFencedLockManager   fencedLockManager;
    private DurableLocalCommandBus        commandBus;
    private DurableSubscriptionRepository durableSubscriptionRepository;

    @BeforeEach
    void setup() {
        cfg = new HikariConfig();
        cfg.setJdbcUrl(postgreSQLContainer.getJdbcUrl());
        cfg.setUsername(postgreSQLContainer.getUsername());
        cfg.setPassword(postgreSQLContainer.getPassword());
        cfg.setMaximumPoolSize(20);

        ds = new HikariDataSource(cfg);

        jdbi = Jdbi.create(ds);
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        eventMapper = new EventProcessorIT.TestPersistableEventMapper();
        var jsonSerializer = new JacksonJSONEventSerializer(createObjectMapper());
        var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                       unitOfWorkFactory,
                                                                                       eventMapper,
                                                                                       standardSingleTenantConfiguration(
                                                                                               jsonSerializer,
                                                                                               IdentifierColumnType.UUID,
                                                                                               JSONColumnType.JSONB));
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                persistenceStrategy,
                                                Optional.empty(),
                                                eventStore -> new PostgresqlEventStreamGapHandler<>(eventStore,
                                                                                                    unitOfWorkFactory),
                                                new EventStoreSubscriptionObserver.NoOpEventStoreSubscriptionObserver());

        fencedLockManager = PostgresqlFencedLockManager.builder()
                                                       .setEventBus(eventStore.localEventBus())
                                                       .setJdbi(jdbi)
                                                       .setLockTimeOut(Duration.ofSeconds(2))
                                                       .setLockConfirmationInterval(Duration.ofSeconds(1))
                                                       .setReleaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation(true)
                                                       .setUnitOfWorkFactory(unitOfWorkFactory)
                                                       .buildAndStart();

        durableSubscriptionRepository = new PostgresqlDurableSubscriptionRepository(jdbi, eventStore);

        eventStoreSubscriptionManager = EventStoreSubscriptionManager.builder()
                                                                     .setEventStore(eventStore)
                                                                     .setFencedLockManager(fencedLockManager)
                                                                     .setDurableSubscriptionRepository(durableSubscriptionRepository)
                                                                     .setSnapshotResumePointsEvery(Duration.ofSeconds(1))
                                                                     .build();
        eventStoreSubscriptionManager.start();

        durableQueues = PostgresqlDurableQueues.builder()
                                               .setJsonSerializer(jsonSerializer)
                                               .setMessageHandlingTimeout(Duration.ofSeconds(2))
                                               .setTransactionalMode(TransactionalMode.SingleOperationTransaction)
                                               .setUnitOfWorkFactory(unitOfWorkFactory)
                                               .build();
        durableQueues.start();

        commandBus = DurableLocalCommandBus.builder()
                                           .setInterceptors(new UnitOfWorkControllingCommandBusInterceptor(unitOfWorkFactory))
                                           .setDurableQueues(durableQueues)
                                           .setCommandQueueRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 3))
                                           .build();
        commandBus.start();

        testProcessor = new TestOrderViewEventProcessor(new ViewEventProcessorDependencies(eventStoreSubscriptionManager,
                                                                                           fencedLockManager,
                                                                                           durableQueues,
                                                                                           commandBus,
                                                                                           List.of()),
                                                        eventStore);
        testProcessor.start();
    }


    @AfterEach
    public void tearDown() {
        if (testProcessor != null) {
            testProcessor.stop();
        }
        if (durableQueues != null) {
            durableQueues.stop();
        }
        if (commandBus != null) {
            commandBus.stop();
        }
        if (eventStoreSubscriptionManager != null) {
            eventStoreSubscriptionManager.stop();
        }
        if (fencedLockManager != null) {
            fencedLockManager.stop();
        }
    }

    @Test
    public void verify_view_event_processor_with_load() {
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            IntStream.range(1, 10_001).sequential().forEach(value -> {
                var orderId = EventProcessorIT.OrderId.random();
                var event   = new EventProcessorIT.OrderPlacedEvent(orderId, "Load Order Details " + value);
                eventStore.appendToStream(TEST_ORDERS, orderId, List.of(event));
            });
        });

        Awaitility.waitAtMost(Duration.ofMinutes(2)).untilAsserted(() -> {
            int orderPlacedEventCounter = testProcessor.getOrderPlacedEventCounter().get();
            assert orderPlacedEventCounter == 10_000;
        });
    }

    @Test
    public void verify_view_event_processor_flow() {
        var orderId      = EventProcessorIT.OrderId.random();
        var orderDetails = "Test order details";
        var placeCmd     = new EventProcessorIT.PlaceOrderCommand(orderId, orderDetails);

        // Send the command via the command bus
        commandBus.send(placeCmd);

        // Fetch the aggregate event stream for this order
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .until(() -> unitOfWorkFactory.withUnitOfWork(() -> {
                      var streamOpt = eventStore.fetchStream(TEST_ORDERS, orderId);
                      return streamOpt.isPresent() && streamOpt.get().eventList().size() == 2;
                  }));

        // We expect two events: first the OrderPlacedEvent then the OrderConfirmedEvent
        var events = unitOfWorkFactory.withUnitOfWork(() -> eventStore.fetchStream(TEST_ORDERS, orderId).get().eventList());
        assertThat(events).hasSize(2);
        var event0 = events.get(0);
        var event1 = events.get(1);
        assertThat(event0.event().getEventTypeAsJavaClass()).isPresent().isEqualTo(Optional.of(EventProcessorIT.OrderPlacedEvent.class));
        assertThat(event1.event().getEventTypeAsJavaClass()).isPresent().isEqualTo(Optional.of(EventProcessorIT.OrderConfirmedEvent.class));
    }

    @Test
    void testFailingCommandSendViaCommandBus_sendAndDontWait_EventuallyEndsUpInAsADeadLetterMessage() throws Exception {
        var orderId    = EventProcessorIT.OrderId.random();
        var failingCmd = new EventProcessorIT.FailingCommandSentUsingAsyncAndDontWaitViaCommandBus(orderId, "Intentional failure for testing");

        // Verify we don't have any dead-letter messages
        var queueName = commandBus.getCommandQueueName();
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);

        // Send the failing command using the command bus (asynchronous, fire-and-forget)
        commandBus.sendAndDontWait(failingCmd);

        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> {
                      assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(1);
                  });

        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 10);
        assertThat(deadLetterMessages).hasSize(1);
        assertThat(deadLetterMessages.get(0).getPayload()).isInstanceOf(EventProcessorIT.FailingCommandSentUsingAsyncAndDontWaitViaCommandBus.class);
    }

    @Test
    public void verify_failed_event_handling_gets_queued() {
        var orderId      = EventProcessorIT.OrderId.random();
        var orderDetails = "Fail order details";
        var event        = new EventProcessorIT.OrderPlacedEvent(orderId, orderDetails);
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            eventStore.appendToStream(TEST_ORDERS, orderId, List.of(event));
        });

        var queueName = testProcessor.getDurableQueueName();

        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> {
                      assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(1);
                  });

        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 10);
        assertThat(deadLetterMessages).hasSize(1);
        assertThat(deadLetterMessages.get(0).getMessage()).isInstanceOf(OrderedMessage.class);
        assertThat(deadLetterMessages.get(0).getPayload()).isInstanceOf(AggregateType.class);
        assertThat(deadLetterMessages.get(0).getPayload()).isEqualTo(TEST_ORDERS);
        assertThat(((OrderedMessage) deadLetterMessages.get(0).getMessage()).getKey()).isEqualTo(orderId.toString());
        assertThat(((OrderedMessage) deadLetterMessages.get(0).getMessage()).getOrder()).isEqualTo(0);
    }

    @Test
    public void verify_failed_event_handling_additional_event_gets_queued() {
        var orderId      = EventProcessorIT.OrderId.random();
        var orderDetails = "Fail order details";
        var event        = new EventProcessorIT.OrderPlacedEvent(orderId, orderDetails);
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            eventStore.appendToStream(TEST_ORDERS, orderId, List.of(event));
        });

        var queueName = testProcessor.getDurableQueueName();

        Awaitility.waitAtMost(Duration.ofSeconds(3))
                  .untilAsserted(() -> {
                      assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(1);
                  });
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);

        var eventConfirmed = new EventProcessorIT.OrderConfirmedEvent(orderId);
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            eventStore.appendToStream(TEST_ORDERS, orderId, List.of(eventConfirmed));
        });

        Awaitility.waitAtMost(Duration.ofSeconds(3))
                  .untilAsserted(() -> {
                      assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
                  });

        var queuedMessages = durableQueues.getQueuedMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 10);
        assertThat(queuedMessages).hasSize(1);
        assertThat(queuedMessages.get(0).getMessage()).isInstanceOf(OrderedMessage.class);
        assertThat(queuedMessages.get(0).getPayload()).isInstanceOf(AggregateType.class);
        assertThat(queuedMessages.get(0).getPayload()).isEqualTo(TEST_ORDERS);
        assertThat(((OrderedMessage) queuedMessages.get(0).getMessage()).getKey()).isEqualTo(orderId.toString());
        assertThat(((OrderedMessage) queuedMessages.get(0).getMessage()).getOrder()).isEqualTo(1);
    }

    @Test
    public void testResetAllSubscriptions() {
        IntStream.range(0, 5).forEach(i -> {
            var orderId      = EventProcessorIT.OrderId.random();
            var orderDetails = "Test order details";
            var placeCmd     = new EventProcessorIT.PlaceOrderCommand(orderId, orderDetails);
            commandBus.send(placeCmd);
        });
        var subscriberId = AbstractEventProcessor.resolveSubscriberId(TEST_ORDERS, testProcessor.getProcessorName());
        Awaitility.waitAtMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            var currentEventOrder = eventStoreSubscriptionManager.getCurrentEventOrder(subscriberId, TEST_ORDERS);
            assertThat(currentEventOrder)
                    .hasValueSatisfying(order ->
                                                assertThat(order.longValue()).isEqualTo(11L) // 11 = 5 (aggregates) * 2 (events) + 1 (for the next global event order)
                                       );
        });

        var queueName = testProcessor.getDurableQueueName();
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        // Send the failing command using the processor's inbox (asynchronous, fire-and-forget)
        var failingCmd = new EventProcessorIT.FailingCommandSentViaInbox(EventProcessorIT.OrderId.random(), "Intentional failure for testing");
        testProcessor.getDurableQueuesForTesting().queueMessage(queueName, Message.of(failingCmd));
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);

        // Set a callback to verify that we do reset resumePoints
        testProcessor.setResetCallback(resetPoints -> {
            Awaitility.waitAtMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                var currentEventOrder = eventStoreSubscriptionManager.getCurrentEventOrder(subscriberId, TEST_ORDERS);
                System.out.println("After resetting: Current event order: " + currentEventOrder.get());
                assertThat(currentEventOrder)
                        .hasValueSatisfying(order ->
                                                    assertThat(order.longValue()).isEqualTo(1)
                                           )
                        .describedAs("After resetting: Current event order");
                var currentResumePoint = durableSubscriptionRepository.getResumePoint(subscriberId, TEST_ORDERS);
                System.out.println("After resetting: Current resume point: " + currentEventOrder.get());
                assertThat(currentResumePoint)
                        .hasValueSatisfying(resumePoint -> assertThat(resumePoint.getResumeFromAndIncluding().longValue()).isEqualTo(1))
                        .describedAs("After resetting: Current resume point");
            });
        });
        testProcessor.resetAllSubscriptions();

        // Check subscriptions catchup again
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var currentEventOrder = eventStoreSubscriptionManager.getCurrentEventOrder(subscriberId, TEST_ORDERS);
            System.out.println("After resetting: Current event order: " + currentEventOrder.get());
            assertThat(currentEventOrder)
                    .hasValueSatisfying(order ->
                                                assertThat(order.longValue()).isEqualTo(11)
                                       )
                    .describedAs("After resetting: Current event order");
            var currentResumePoint = durableSubscriptionRepository.getResumePoint(subscriberId, TEST_ORDERS);
            System.out.println("After resetting: Current resume point: " + currentEventOrder.get());
            assertThat(currentResumePoint)
                    .hasValueSatisfying(resumePoint -> assertThat(resumePoint.getResumeFromAndIncluding().longValue()).isEqualTo(11))
                    .describedAs("After resetting: Current resume point");
        });

        // Assert Inbox is purged
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);
            assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        });
    }

    /**
     * TestOrderEventProcessor is a simple EventProcessor that:
     * <ul>
     *     <li>Reacts to events for the "TestOrders" aggregate type</li>
     *     <li>Handles PlaceOrderCommand by persisting an OrderPlacedEvent</li>
     *     <li>Handles OrderPlacedEvent (via a @MessageHandler) by sending a ConfirmOrderCommand synchronously</li>
     *     <li>Handles ConfirmOrderCommand by persisting an OrderConfirmedEvent</li>
     *     <li>Handles FailingCommand by throwing an exception</li>
     * </ul>
     */
    public static class TestOrderViewEventProcessor extends ViewEventProcessor {
        public static final AggregateType TEST_ORDERS = AggregateType.of("TestOrders");
        private final PostgresqlEventStore<?> eventStore;
        private final ConcurrentMap<AggregateType, GlobalEventOrder> resetPoints = new ConcurrentHashMap<>();
        private Consumer<ConcurrentMap<AggregateType, GlobalEventOrder>> resetCallback;
        private final AtomicInteger orderPlacedEventCounter = new AtomicInteger(0);

        public TestOrderViewEventProcessor(ViewEventProcessorDependencies eventProcessorDependencies,
                                           PostgresqlEventStore<?> eventStore) {
            super(eventProcessorDependencies);
            this.eventStore = eventStore;
            eventStore.addAggregateEventStreamConfiguration(TEST_ORDERS,
                                                            AggregateIdSerializer.serializerFor(EventProcessorIT.OrderId.class));
        }

        @Override
        public String getProcessorName() {
            return "TestOrderViewEventProcessor";
        }

        public DurableQueues getDurableQueuesForTesting() {
            return super.getDurableQueues();
        }

        public void setResetCallback(Consumer<ConcurrentMap<AggregateType, GlobalEventOrder>> resetCallback) {
            this.resetCallback = resetCallback;
        }

        @Override
        protected List<AggregateType> reactsToEventsRelatedToAggregateTypes() {
            return List.of(TEST_ORDERS);
        }

        @Override
        protected RedeliveryPolicy getDurableQueueRedeliveryPolicy() {
            return RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 3);
        }

        @Override
        protected void onSubscriptionsReset(AggregateType aggregateType, GlobalEventOrder resubscribeFromAndIncluding) {
            if (resetPoints.containsKey(aggregateType)) {
                throw new IllegalStateException("resetPoints already contains key for " + aggregateType);
            }
            resetPoints.put(aggregateType, resubscribeFromAndIncluding);
            resetCallback.accept(resetPoints);
        }

        // ----- Command Handlers -----

        @CmdHandler
        public void handle(EventProcessorIT.PlaceOrderCommand cmd) {
            if (!eventStore.hasEventStream(TEST_ORDERS, cmd.orderId)) {
                // Start a new event stream for this order
                var event = new EventProcessorIT.OrderPlacedEvent(cmd.orderId, cmd.orderDetails);
                eventStore.startStream(TEST_ORDERS, cmd.orderId, List.of(event));
            }
        }

        @CmdHandler
        public void handle(EventProcessorIT.ConfirmOrderCommand cmd) {
            var eventStream = eventStore.fetchStream(TEST_ORDERS, cmd.orderId);
            if (eventStream.isPresent() && !Lists.last(eventStream.get().eventList()).get().event().getEventTypeAsJavaClass().get().equals(EventProcessorIT.OrderConfirmedEvent.class)) {
                // Append the confirmation event to the existing stream
                var event = new EventProcessorIT.OrderConfirmedEvent(cmd.orderId);
                eventStore.appendToStream(TEST_ORDERS, cmd.orderId, event);
            }
        }

        @CmdHandler
        public void handle(EventProcessorIT.FailingCommandSentUsingAsyncAndDontWaitViaCommandBus cmd) {
            System.out.println("*** Handling FailingCommand: " + cmd.reason);
            throw new RuntimeException(cmd.reason);
        }

        // ----- Message Handler -----

        @MessageHandler
        public void handle(EventProcessorIT.FailingCommandSentViaInbox cmd) {
            System.out.println("*** Handling FailingCommand: " + cmd.reason);
            throw new RuntimeException(cmd.reason);
        }

        /**
         * When an OrderPlacedEvent is processed, send a ConfirmOrderCommand synchronously.
         */
        @MessageHandler
        public void onOrderPlaced(EventProcessorIT.OrderPlacedEvent event) {
            if (event.orderDetails.startsWith("Load")) {
                orderPlacedEventCounter.incrementAndGet();
            } else if (event.orderDetails.startsWith("Fail")) {
                throw new RuntimeException(event.orderDetails);
            } else {
                getCommandBus().send(new EventProcessorIT.ConfirmOrderCommand(event.orderId));
            }
        }

        public AtomicInteger getOrderPlacedEventCounter() {
            return orderPlacedEventCounter;
        }
    }
}
