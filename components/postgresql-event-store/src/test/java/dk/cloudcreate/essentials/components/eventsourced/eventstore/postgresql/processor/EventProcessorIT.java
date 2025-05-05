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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap.PostgresqlEventStreamGapHandler;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.observability.EventStoreSubscriptionObserver;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.reactive.command.*;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.reactive.command.CmdHandler;
import dk.cloudcreate.essentials.shared.collections.Lists;
import dk.cloudcreate.essentials.types.CharSequenceType;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration;
import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.EventProcessorIT.TestOrderEventProcessor.TEST_ORDERS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the EventProcessor using the following scenarios:
 * <ol>
 *   <li>A PlaceOrderCommand causes an OrderPlacedEvent to be persisted.</li>
 *   <li>The OrderPlacedEvent is handled by a @MessageHandler which synchronously sends a ConfirmOrderCommand via getCommandBus().send(...),
 *       whose handler then persists an OrderConfirmedEvent.</li>
 *   <li>A FailingCommand sent via getInbox().sendAndDontWait(...) always fails.</li>
 * </ol>
 */
@Testcontainers
public class EventProcessorIT {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");

    private Jdbi                                                                    jdbi;
    private EventStoreUnitOfWorkFactory<EventStoreUnitOfWork>                       unitOfWorkFactory;
    private TestPersistableEventMapper                                              eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");

    private TestOrderEventProcessor          testProcessor;
    private PostgresqlDurableQueues          durableQueues;
    private EventStoreSubscriptionManager    eventStoreSubscriptionManager;
    private PostgresqlFencedLockManager      fencedLockManager;
    private Inboxes.DurableQueueBasedInboxes inboxes;
    private DurableLocalCommandBus           commandBus;
    private DurableSubscriptionRepository    durableSubscriptionRepository;

    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        eventMapper = new TestPersistableEventMapper();
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

        inboxes = new Inboxes.DurableQueueBasedInboxes(durableQueues, fencedLockManager);

        commandBus = DurableLocalCommandBus.builder()
                                           .setInterceptors(new UnitOfWorkControllingCommandBusInterceptor(unitOfWorkFactory))
                                           .setDurableQueues(durableQueues)
                                           .setCommandQueueRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 3))
                                           .build();
        commandBus.start();

        testProcessor = new TestOrderEventProcessor(new EventProcessorDependencies(eventStoreSubscriptionManager,
                                                                                   inboxes,
                                                                                   commandBus,
                                                                                   List.of()),
                                                    eventStore);
        testProcessor.start();
    }

    @AfterEach
    void teardown() {
        if (testProcessor != null) {
            testProcessor.stop();
        }
        if (eventStoreSubscriptionManager != null) {
            eventStoreSubscriptionManager.stop();
        }
        if (fencedLockManager != null) {
            fencedLockManager.stop();
        }
        if (commandBus != null) {
            commandBus.stop();
        }
        if (durableQueues != null) {
            durableQueues.stop();
        }
    }


    // -------------------------------------------------------------------------------------------------------------------

    /**
     * Scenario 1 & 2:
     * Sending a PlaceOrderCommand should cause an OrderPlacedEvent to be persisted.
     * Its MessageHandler then synchronously sends a ConfirmOrderCommand which persists an OrderConfirmedEvent.
     */
    @Test
    void testPlaceOrderAndConfirmationFlow() throws Exception {
        var orderId      = OrderId.random();
        var orderDetails = "Test order details";
        var placeCmd     = new PlaceOrderCommand(orderId, orderDetails);

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
        assertThat(event0.event().getEventTypeAsJavaClass()).isPresent().isEqualTo(Optional.of(OrderPlacedEvent.class));
        assertThat(event1.event().getEventTypeAsJavaClass()).isPresent().isEqualTo(Optional.of(OrderConfirmedEvent.class));
    }

    /**
     * Scenario 3A:
     * Send a FailingCommandSentUsingAsyncAndDontWaitViaCommandBus via the CommandBus. Its handler always throws an exception.
     * This test also verifies that such a command does not prevent the integration test from shutting down.
     */
    @Test
    void testFailingCommandSendViaCommandBus_sendAndDontWait_EventuallyEndsUpInAsADeadLetterMessage() throws Exception {
        var orderId    = OrderId.random();
        var failingCmd = new FailingCommandSentUsingAsyncAndDontWaitViaCommandBus(orderId, "Intentional failure for testing");

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
        assertThat(deadLetterMessages.get(0).getPayload()).isInstanceOf(FailingCommandSentUsingAsyncAndDontWaitViaCommandBus.class);
    }

    /**
     * Scenario 3B:
     * Send a FailingCommandSentViaInbox via the event processors Inbox. Its handler always throws an exception.
     */
    @Test
    void testFailingCommandSentViaInboxEventuallyEndsUpInAsADeadLetterMessage() throws Exception {
        var orderId    = OrderId.random();
        var failingCmd = new FailingCommandSentViaInbox(orderId, "Intentional failure for testing");

        // Verify we don't have any dead-letter messages
        var queueName = testProcessor.getInboxForTesting().name().asQueueName();
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);

        // Send the failing command using the processor's inbox (asynchronous, fire-and-forget)
        testProcessor.getInboxForTesting().addMessageReceived(failingCmd);

        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> {
                      assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(1);
                  });

        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 10);
        assertThat(deadLetterMessages).hasSize(1);
        assertThat(deadLetterMessages.get(0).getPayload()).isInstanceOf(FailingCommandSentViaInbox.class);
    }

    @Test
    public void testResetAllSubscriptions() {
        IntStream.range(0, 5).forEach(i -> {
            var orderId      = OrderId.random();
            var orderDetails = "Test order details";
            var placeCmd     = new PlaceOrderCommand(orderId, orderDetails);
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

        var queueName = testProcessor.getInboxForTesting().name().asQueueName();
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        // Send the failing command using the processor's inbox (asynchronous, fire-and-forget)
        var failingCmd = new FailingCommandSentViaInbox(OrderId.random(), "Intentional failure for testing");
        testProcessor.getInboxForTesting().addMessageReceived(failingCmd);

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

    // --------------------------
    // Supporting classes for the test
    // --------------------------

    /**
     * OrderId
     */
    public static class OrderId extends CharSequenceType<OrderId> {
        protected OrderId(CharSequence value) {
            super(value);
        }

        public static OrderId random() {
            return new OrderId(RandomIdGenerator.generate());
        }

        public static OrderId of(CharSequence id) {
            return new OrderId(id);
        }
    }

    // --- Commands ---

    public static class PlaceOrderCommand {
        public final OrderId orderId;
        public final String  orderDetails;

        public PlaceOrderCommand(OrderId orderId, String orderDetails) {
            this.orderId = orderId;
            this.orderDetails = orderDetails;
        }
    }

    public static class ConfirmOrderCommand {
        public final OrderId orderId;

        public ConfirmOrderCommand(OrderId orderId) {
            this.orderId = orderId;
        }
    }

    public static class FailingCommandSentViaInbox {
        public final OrderId orderId;
        public final String  reason;

        public FailingCommandSentViaInbox(OrderId orderId, String reason) {
            this.orderId = orderId;
            this.reason = reason;
        }
    }

    public static class FailingCommandSentUsingAsyncAndDontWaitViaCommandBus {
        public final OrderId orderId;
        public final String  reason;

        public FailingCommandSentUsingAsyncAndDontWaitViaCommandBus(OrderId orderId, String reason) {
            this.orderId = orderId;
            this.reason = reason;
        }
    }

    // --- Events ---

    @Revision(1)
    public static class OrderPlacedEvent {
        public final OrderId orderId;
        public final String  orderDetails;

        public OrderPlacedEvent(OrderId orderId, String orderDetails) {
            this.orderId = orderId;
            this.orderDetails = orderDetails;
        }
    }

    public static class OrderConfirmedEvent {
        public final OrderId orderId;

        public OrderConfirmedEvent(OrderId orderId) {
            this.orderId = orderId;
        }
    }

    // --------------------------
    // Test EventProcessor
    // --------------------------

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
    public static class TestOrderEventProcessor extends EventProcessor {
        public static final AggregateType TEST_ORDERS = AggregateType.of("TestOrders");

        private final PostgresqlEventStore<?> eventStore;

        private final ConcurrentMap<AggregateType, GlobalEventOrder> resetPoints = new ConcurrentHashMap<>();

        private Consumer<ConcurrentMap<AggregateType, GlobalEventOrder>> resetCallback;

        public TestOrderEventProcessor(EventProcessorDependencies eventProcessorDependencies,
                                       PostgresqlEventStore<?> eventStore) {
            this(eventProcessorDependencies, eventStore,
                 resetPoints -> {
                     throw new IllegalStateException("No reset callback configured");
                 });
        }

        public TestOrderEventProcessor(EventProcessorDependencies eventProcessorDependencies,
                                       PostgresqlEventStore<?> eventStore,
                                       Consumer<ConcurrentMap<AggregateType, GlobalEventOrder>> resetCallback) {
            super(eventProcessorDependencies);
            this.eventStore = eventStore;
            this.resetCallback = resetCallback;
            eventStore.addAggregateEventStreamConfiguration(TEST_ORDERS,
                                                            AggregateIdSerializer.serializerFor(OrderId.class));
        }

        public void setResetCallback(Consumer<ConcurrentMap<AggregateType, GlobalEventOrder>> resetCallback) {
            this.resetCallback = resetCallback;
        }

        @Override
        public String getProcessorName() {
            return "TestOrderEventProcessor";
        }

        @Override
        protected List<AggregateType> reactsToEventsRelatedToAggregateTypes() {
            return List.of(TEST_ORDERS);
        }


        public Inbox getInboxForTesting() {
            return super.getInbox();
        }

        @Override
        protected RedeliveryPolicy getInboxRedeliveryPolicy() {
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
        public void handle(PlaceOrderCommand cmd) {
            if (!eventStore.hasEventStream(TEST_ORDERS, cmd.orderId)) {
                // Start a new event stream for this order
                var event = new OrderPlacedEvent(cmd.orderId, cmd.orderDetails);
                eventStore.startStream(TEST_ORDERS, cmd.orderId, List.of(event));
            }
        }

        @CmdHandler
        public void handle(ConfirmOrderCommand cmd) {
            var eventStream = eventStore.fetchStream(TEST_ORDERS, cmd.orderId);
            if (eventStream.isPresent() && !Lists.last(eventStream.get().eventList()).get().event().getEventTypeAsJavaClass().get().equals(OrderConfirmedEvent.class)) {
                // Append the confirmation event to the existing stream
                var event = new OrderConfirmedEvent(cmd.orderId);
                eventStore.appendToStream(TEST_ORDERS, cmd.orderId, event);
            }
        }


        @CmdHandler
        public void handle(FailingCommandSentUsingAsyncAndDontWaitViaCommandBus cmd) {
            System.out.println("*** Handling FailingCommand: " + cmd.reason);
            throw new RuntimeException(cmd.reason);
        }


        // ----- Message Handler -----

        @MessageHandler
        public void handle(FailingCommandSentViaInbox cmd) {
            System.out.println("*** Handling FailingCommand: " + cmd.reason);
            throw new RuntimeException(cmd.reason);
        }

        /**
         * When an OrderPlacedEvent is processed, send a ConfirmOrderCommand synchronously.
         */
        @MessageHandler
        public void onOrderPlaced(OrderPlacedEvent event) {
            getCommandBus().send(new ConfirmOrderCommand(event.orderId));
        }
    }


    // -------------------------------------------------------------------------------------------------------------------

    public static ObjectMapper createObjectMapper() {
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

    public static class TestPersistableEventMapper implements PersistableEventMapper {
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
                                         null, // Leave reading the EventRevision to the PersistableEvent's from method
                                         META_DATA,
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         null);
        }
    }
}
