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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSON;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.reactive.Handler;
import dk.cloudcreate.essentials.reactive.command.*;
import org.slf4j.*;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Event Modeling style Event Sourced Event Processor and Command Handler, which is capable of both containing {@link CmdHandler} as well as {@link MessageHandler}
 * annotated event handling methods.<br>
 * The {@link EventProcessor} simplifies building event projections or process automations.<br>
 * <br>
 * <i><b>Note:</b> If the associated {@link DurableLocalCommandBus} uses the {@link UnitOfWorkControllingCommandBusInterceptor} then all
 * logic inside {@link CmdHandler} annotated methods will be performed within a {@link UnitOfWork} (this is the default when configured using the <code>spring-boot-starter-postgresql-event-store</code> module)</i>
 * <br>
 * <br>
 * <i><b>Note:</b> If the associated {@link Inboxes} used is associated with a {@link UnitOfWorkFactory}, which {@link Inboxes.DurableQueueBasedInboxes} supports, then
 * all logic inside {@link MessageHandler} annotated methods will be performed within a {@link UnitOfWork} (this is the default when configured using the <code>spring-boot-starter-postgresql-event-store</code> module)</i>
 * <br>
 * <br>
 * An {@link EventProcessor} can subscribe to multiple {@link EventStore} Event/Aggregate-Event Streams (e.g. a stream of Order events or a stream of Product events).<br>
 * To ensure efficient processing and prevent conflicts, only a single instance of a concrete {@link EventProcessor} in a cluster can have an active Event Stream subscription at a time (internally an {@link FencedLockManager} is used per subscription).
 * <br>
 * To enhance throughput, you can control the number of parallel threads utilized for handling messages. Consequently, events associated with different aggregate instances within an EventStream can be concurrently processed.
 * <br>
 * The {@link EventProcessor} also ensures ordered handling of events, partitioned by aggregate id. I.e. events related to a specific aggregate id will always be processed in the exact order they were originally added to the {@link EventStore}.<br>
 * This guarantees the preservation of the chronological sequence of events for each individual aggregate, maintaining data integrity and consistency, even during event redelivery/poison-message handling.<br>
 * <br>
 * Details:<br>
 * Instead of manually subscribing to the underlying {@link EventStore} using the {@link EventStoreSubscriptionManager}, which requires you to provide your own error and retry handling,
 * you can use the {@link EventProcessor} to subscribe to one or more {@link EventStore} event streams, while providing you with error and retry handling using the common {@link RedeliveryPolicy} concept which can be overridden using
 * the {@link #getInboxRedeliveryPolicy()} method:
 * <pre>{@code
 * @Override
 * protected RedeliveryPolicy getInboxRedeliveryPolicy() {
 *     return RedeliveryPolicy.exponentialBackoff()
 *                            .setInitialRedeliveryDelay(Duration.ofMillis(200))
 *                            .setFollowupRedeliveryDelay(Duration.ofMillis(200))
 *                            .setFollowupRedeliveryDelayMultiplier(1.1d)
 *                            .setMaximumFollowupRedeliveryDelayThreshold(Duration.ofSeconds(3))
 *                            .setMaximumNumberOfRedeliveries(20)
 *                            .setDeliveryErrorHandler(
 *                                    MessageDeliveryErrorHandler.stopRedeliveryOn(
 *                                            ConstraintViolationException.class,
 *                                            HttpClientErrorException.BadRequest.class))
 *                            .build();
 * }
 * }</pre>
 * <p>
 * You must override {@link #reactsToEventsRelatedToAggregateTypes()} to specify which EventSourced {@link AggregateType} event-streams the {@link EventProcessor} should subscribe to.<br>
 * The {@link EventProcessor} will set up an exclusive asynchronous {@link EventStoreSubscription} for each {@link AggregateType} and will forward any
 * {@link PersistedEvent}'s as {@link OrderedMessage}'s IF and ONLY IF the concrete {@link EventProcessor} subclass contains a corresponding {@link MessageHandler}
 * annotated method matching the {@link PersistedEvent#event()}'s {@link EventJSON#getEventType()}'s {@link EventType#toJavaClass()} matches that first argument type.
 * <p>
 * Example:
 * <pre>{@code
 * @Service
 * @Slf4j
 * public class TransferMoneyProcessor extends EventProcessor {
 *     private final Accounts                accounts;
 *     private final IntraBankMoneyTransfers intraBankMoneyTransfers;
 *
 *     public TransferMoneyProcessor(Accounts accounts,
 *                                   IntraBankMoneyTransfers intraBankMoneyTransfers,
 *                                   EventProcessorDependencies eventProcessorDependencies) {
 *         super(eventProcessorDependencies);
 *         this.accounts = requireNonNull(accounts, "No Accounts provided);
 *         this.intraBankMoneyTransfers = requireNonNull(intraBankMoneyTransfers, "No IntraBankMoneyTransfers provided");
 *     }
 *
 *     @Override
 *     public String getProcessorName() {
 *         return "TransferMoneyProcessor";
 *     }
 *
 *     @Override
 *     protected List<AggregateType> reactsToEventsRelatedToAggregateTypes() {
 *         return List.of(Accounts.AGGREGATE_TYPE,
 *                        IntraBankMoneyTransfers.AGGREGATE_TYPE);
 *     }
 *
 *     @CmdHandler
 *     public void handle(RequestIntraBankMoneyTransfer cmd) {
 *         if (accounts.isAccountMissing(cmd.fromAccount)) {
 *             throw new TransactionException(msg("Couldn't find fromAccount with id '{}'", cmd.fromAccount));
 *         }
 *         if (accounts.isAccountMissing(cmd.toAccount)) {
 *             throw new TransactionException(msg("Couldn't find toAccount with id '{}'", cmd.toAccount));
 *         }
 *
 *         var existingTransfer = intraBankMoneyTransfers.findTransfer(cmd.transactionId);
 *         if (existingTransfer.isEmpty()) {
 *             log.debug("===> Requesting New Transfer '{}'", cmd.transactionId);
 *             intraBankMoneyTransfers.requestNewTransfer(new IntraBankMoneyTransfer(cmd));
 *         }
 *     }
 *
 *     @MessageHandler
 *     void handle(IntraBankMoneyTransferRequested e, OrderedMessage eventMessage) {
 *         var transfer = intraBankMoneyTransfers.getTransfer(e.transactionId);
 *         accounts.getAccount(transfer.getFromAccount())
 *                 .withdrawToday(transfer.getAmount(),
 *                                transfer.aggregateId(),
 *                                AllowOverdrawingBalance.NO);
 *     }
 *
 *     @MessageHandler
 *     void handle(IntraBankMoneyTransferStatusChanged e) {
 *         var transfer = intraBankMoneyTransfers.getTransfer(e.transactionId);
 *         if (transfer.getStatus() == TransferLifeCycleStatus.FROM_ACCOUNT_WITHDRAWN) {
 *             accounts.getAccount(transfer.getToAccount())
 *                     .depositToday(transfer.getAmount(),
 *                                   transfer.aggregateId());
 *         }
 *     }
 *
 *     @MessageHandler
 *     void handle(AccountWithdrawn e) {
 *         var matchingTransfer = intraBankMoneyTransfers.findTransfer(e.transactionId);
 *
 *         matchingTransfer.ifPresent(transfer -> {
 *             transfer.markFromAccountAsWithdrawn();
 *         });
 *     }
 *
 *     @MessageHandler
 *     void handle(AccountDeposited e, OrderedMessage eventMessage) {
 *         var matchingTransfer = intraBankMoneyTransfers.findTransfer(e.transactionId);
 *         matchingTransfer.ifPresent(transfer -> {
 *             transfer.markToAccountAsDeposited();
 *         });
 *     }
 *
 *     @Override
 *     protected RedeliveryPolicy getInboxRedeliveryPolicy() {
 *         return RedeliveryPolicy.exponentialBackoff()
 *                                .setInitialRedeliveryDelay(Duration.ofMillis(200))
 *                                .setFollowupRedeliveryDelay(Duration.ofMillis(200))
 *                                .setFollowupRedeliveryDelayMultiplier(1.1d)
 *                                .setMaximumFollowupRedeliveryDelayThreshold(Duration.ofSeconds(3))
 *                                .setMaximumNumberOfRedeliveries(20)
 *                                .setDeliveryErrorHandler(
 *                                        MessageDeliveryErrorHandler.stopRedeliveryOn(
 *                                                ConstraintViolationException.class,
 *                                                HttpClientErrorException.BadRequest.class))
 *                                .build();
 *     }
 * }
 * }</pre>
 */
public abstract class EventProcessor extends AbstractEventProcessor {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private final Inboxes                       inboxes;
    private       Consumer<Message>             inboxMessageHandlerDelegate;
    private       Inbox                         inbox;
    private       PatternMatchingMessageHandler patternMatchingInboxMessageHandlerDelegate;

    /**
     * Create a new {@link EventProcessor} instance
     *
     * @param eventProcessorDependencies The {@link EventProcessorDependencies} that encapsulates all
     *                                   the dependencies required by an instance of an {@link EventProcessor}
     * @see EventProcessor#EventProcessor(EventStoreSubscriptionManager, Inboxes, DurableLocalCommandBus, List)
     */
    protected EventProcessor(EventProcessorDependencies eventProcessorDependencies) {
        this(requireNonNull(eventProcessorDependencies, "No eventProcessorDependencies provided").eventStoreSubscriptionManager,
             eventProcessorDependencies.inboxes,
             eventProcessorDependencies.commandBus,
             eventProcessorDependencies.messageHandlerInterceptors);
    }

    /**
     * Create a new {@link EventProcessor} instance
     *
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions<br>
     *                                      The  {@link EventStore} instance associated with the {@link EventStoreSubscriptionManager} is used to only query references to
     *                                      the {@link PersistedEvent}. Before an event reference message is forwarded to the corresponding {@link MessageHandler} we load the {@link PersistedEvent}'s
     *                                      payload and forward it to the {@link MessageHandler} annotated method. This avoids double storing event payloads
     * @param inboxes                       the {@link Inboxes} instance used to create an {@link Inbox}, with the name returned from {@link #getProcessorName()}.
     *                                      This {@link Inbox} is used for forwarding {@link PersistedEvent}'s received via {@link EventStoreSubscription}'s, because {@link EventStoreSubscription}'s
     *                                      doesn't handle message retry, etc.
     * @param commandBus                    The {@link CommandBus} where any {@link Handler} or {@link CmdHandler} annotated methods in the subclass of the {@link EventProcessor} will be registered
     * @param messageHandlerInterceptors    The {@link MessageHandlerInterceptor}'s that will intercept calls to the {@link MessageHandler} annotated methods.<br>
     *                                      Unless you override {@link #getMessageHandlerInterceptors()} then these are the {@link MessageHandlerInterceptor}'s that will be used.
     */
    protected EventProcessor(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                             Inboxes inboxes,
                             DurableLocalCommandBus commandBus,
                             List<MessageHandlerInterceptor> messageHandlerInterceptors) {
        super(eventStoreSubscriptionManager, commandBus, messageHandlerInterceptors);
        this.inboxes = requireNonNull(inboxes, "No inboxes instance provided");
    }

    /**
     * Create a new {@link EventProcessor} instance without any {@link MessageHandlerInterceptor}'s (you can override {@link #getMessageHandlerInterceptors()}
     * to provide custom interceptors, or you can use the {@link EventProcessor#EventProcessor(EventStoreSubscriptionManager, Inboxes, DurableLocalCommandBus, List)} constructor)
     *
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions<br>
     *                                      The  {@link EventStore} instance associated with the {@link EventStoreSubscriptionManager} is used to only query references to
     *                                      the {@link PersistedEvent}. Before an event reference message is forwarded to the corresponding {@link MessageHandler} we load the {@link PersistedEvent}'s
     *                                      payload and forward it to the {@link MessageHandler} annotated method. This avoids double storing event payloads
     * @param inboxes                       the {@link Inboxes} instance used to create an {@link Inbox}, with the name returned from {@link #getProcessorName()}.
     *                                      This {@link Inbox} is used for forwarding {@link PersistedEvent}'s received via {@link EventStoreSubscription}'s, because {@link EventStoreSubscription}'s
     *                                      doesn't handle message retry, etc.
     * @param commandBus                    The {@link CommandBus} where any {@link Handler} or {@link CmdHandler} annotated methods in the subclass of the {@link EventProcessor} will be registered
     */
    protected EventProcessor(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                             Inboxes inboxes,
                             DurableLocalCommandBus commandBus) {
        this(eventStoreSubscriptionManager,
             inboxes,
             commandBus,
             List.of());
    }

    private void setupMessageHandlerDelegate() {
        if (patternMatchingInboxMessageHandlerDelegate != null) {
            return;
        }
        patternMatchingInboxMessageHandlerDelegate = new PatternMatchingMessageHandler(this, getMessageHandlerInterceptors());
        patternMatchingInboxMessageHandlerDelegate.allowUnmatchedMessages();
        inboxMessageHandlerDelegate = handleQueuedMessageConsumer(patternMatchingInboxMessageHandlerDelegate);
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
            var processorName              = requireNonNull(getProcessorName(), "getProcessorName() returned null");
            var subscribeToEventsRelatedTo = requireNonNull(reactsToEventsRelatedToAggregateTypes(), "reactsToEventsRelatedToAggregateTypes() returned null");
            log.info("⚙️ [{}] Starting EventProcessor - will subscribe to events related to these AggregatesType's: {}",
                     processorName,
                     subscribeToEventsRelatedTo);

            setupMessageHandlerDelegate();
            inbox = inboxes.getOrCreateInbox(InboxConfig.builder()
                                                        .inboxName(InboxName.of(processorName))
                                                        .messageConsumptionMode(getInboxMessageConsumptionMode())
                                                        .numberOfParallelMessageConsumers(getNumberOfParallelInboxMessageConsumers())
                                                        .redeliveryPolicy(getInboxRedeliveryPolicy())
                                                        .build(),
                                             inboxMessageHandlerDelegate);
            durableQueueName = inbox.name().asQueueName();

            eventStoreSubscriptions = subscribeToEventsRelatedTo.stream()
                                                                .map(aggregateType -> {
                                                                    var subscriberId = resolveSubscriberId(aggregateType, processorName);
                                                                    var subscription = eventStoreSubscriptionManager.exclusivelySubscribeToAggregateEventsAsynchronously(
                                                                            subscriberId,
                                                                            aggregateType,
                                                                            resolveStartSubscriptionFromGlobalEventOrder(),
                                                                            Optional.empty(),
                                                                            new FencedLockAwareSubscriber() {
                                                                                @Override
                                                                                public void onLockAcquired(FencedLock fencedLock, SubscriptionResumePoint resumeFromAndIncluding) {
                                                                                    log.info("⚙️ [{}] Subscriber '{}' acquired lock. Will resumeFromAndIncluding: {}",
                                                                                             processorName,
                                                                                             subscriberId,
                                                                                             resumeFromAndIncluding);
                                                                                }

                                                                                @Override
                                                                                public void onLockReleased(FencedLock fencedLock) {
                                                                                    log.info("⚙️ [{}] Subscriber '{}''s lock was released",
                                                                                             processorName,
                                                                                             subscriberId);

                                                                                }
                                                                            },
                                                                            event -> forwardEventToInbox(event, inbox));
                                                                    log.info("⚙️ [{}] Created exclusive '{}' subscription: {}",
                                                                             processorName,
                                                                             aggregateType,
                                                                             subscription);
                                                                    return subscription;
                                                                })
                                                                .toList();

            log.info("⚙️ [{}] Started. # of undelivered Inbox messages: {}",
                     processorName,
                     inbox.getNumberOfUndeliveredMessages());
        }
    }

    @Override
    public void stop() {
        if (started) {
            started = false;
            log.info("⚙️ [{}] Stopping EventProcessor",
                     getProcessorName());
            eventStoreSubscriptions.forEach(Lifecycle::stop);
            inbox.stopConsuming();
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    public void resetAllSubscriptions() {
        resetAllSubscriptions(queueName -> inbox.deleteAllMessages());
    }

    public void resetSubscriptions(Map<AggregateType, GlobalEventOrder> resetAggregateSubscriptionsFromAndIncluding,
                                   boolean resetInbox) {
        resetSubscriptions(resetAggregateSubscriptionsFromAndIncluding,
                           resetInbox,
                           queueName -> inbox.deleteAllMessages());

    }

    public void doResetSubscription(EventStoreSubscription eventStoreSubscription,
                                    GlobalEventOrder resubscribeFromAndIncluding,
                                    boolean resetInbox) {

        doResetSubscription(eventStoreSubscription, resubscribeFromAndIncluding, resetInbox, queueName -> inbox.deleteAllMessages());
    }

    /**
     * Forward the event to the <code>forwardToInbox</code> as an {@link OrderedMessage}<br>
     * The default implementation checks if the concrete {@link EventProcessor} class has an {@literal @MessageHandler} annotated method that
     * handles the event. Only events with a corresponding {@link MessageHandler} annotated method will be forwarded to the {@link Inbox} and thereby delegates to the
     * {@link MessageHandler} annotated method
     *
     * @param event          the event to forward
     * @param forwardToInbox the {@link Inbox} the event should be forwarded to
     */
    protected void forwardEventToInbox(PersistedEvent event, Inbox forwardToInbox) {
        if (event.event().getEventType().isPresent() &&
                patternMatchingInboxMessageHandlerDelegate.handlesMessageWithPayload(event.event().getEventTypeAsJavaClass().get())) {
            var aggregateType         = event.aggregateType();
            var aggregateIdSerializer = resolveAggregateIdSerializer(aggregateType);
            log.debug("[{}:{}] Forwarding-To-Inbox Event of type '{}'", aggregateType, event.aggregateId(), event.event().getEventTypeOrNamePersistenceValue());
            forwardToInbox.addMessageReceived(new AbstractEventProcessor.EventReferenceOrderedMessage(aggregateType,
                                                                                                      aggregateIdSerializer.serialize(event.aggregateId()),
                                                                                                      event.eventOrder(),
                                                                                                      new MessageMetaData(event.metaData().deserialize())));
        }
    }

    /**
     * Get the {@link MessageConsumptionMode} used by the consume event messages forwarded to the underlying {@link Inbox}<br>
     * Default is {@link MessageConsumptionMode#SingleGlobalConsumer}
     *
     * @return the {@link MessageConsumptionMode} used by the consume event messages forwarded to the underlying {@link Inbox}
     */
    protected MessageConsumptionMode getInboxMessageConsumptionMode() {
        return MessageConsumptionMode.SingleGlobalConsumer;
    }

    /**
     * Get the number of message consumption threads that will consume messages from the underlying {@link Inbox}<br>
     * Default is: 1
     *
     * @return the number of message consumption threads that will consume messages from the underlying {@link Inbox}
     */
    protected int getNumberOfParallelInboxMessageConsumers() {
        return 1;
    }

    /**
     * Get the {@link RedeliveryPolicy} used when consuming messages from the underlying {@link Inbox}
     *
     * @return the {@link RedeliveryPolicy} used when consuming messages from the underlying {@link Inbox}
     */
    protected RedeliveryPolicy getInboxRedeliveryPolicy() {
        return RedeliveryPolicy.exponentialBackoff()
                               .setInitialRedeliveryDelay(Duration.ofMillis(200))
                               .setFollowupRedeliveryDelay(Duration.ofMillis(200))
                               .setFollowupRedeliveryDelayMultiplier(1.1d)
                               .setMaximumFollowupRedeliveryDelayThreshold(Duration.ofSeconds(3))
                               .setMaximumNumberOfRedeliveries(20)
                               .build();
    }

    /**
     * For backwards compatibility: Get the number of message consumption threads that will consume queued messages from the underlying {@link Inbox}.
     * This method delegates to {@link #getNumberOfParallelInboxMessageConsumers()}.
     *
     * @return the number of message consumption threads that will consume queued messages from the underlying {@link Inbox}.
     */
    @Override
    protected final int getNumberOfParallelQueuedMessageConsumers() {
        return getNumberOfParallelInboxMessageConsumers();
    }

    /**
     * For backwards compatibility: Retrieves the {@link RedeliveryPolicy} used for durable queues managed by the event processor.
     * This method delegates the retrieval to {@link #getInboxRedeliveryPolicy()}.
     *
     * @return the {@link RedeliveryPolicy} configured for durable queues.
     */
    @Override
    protected final RedeliveryPolicy getDurableQueueRedeliveryPolicy() {
        return getInboxRedeliveryPolicy();
    }

    @Override
    public String toString() {
        return "⚙️ " + this.getClass().getSimpleName() + " { " +
                "processorName='" + getProcessorName() + "'" +
                ", reactsToEventsRelatedToAggregateTypes=" + reactsToEventsRelatedToAggregateTypes() +
                ", started=" + started +
                " }";
    }

    /**
     * Retrieves the underlying {@link Inbox} associated with the {@link EventProcessor}.
     *
     * @return the {@link Inbox} instance used for managing messages within the {@link EventProcessor}.
     */
    protected final Inbox getInbox() {
        return inbox;
    }

    /**
     * Backwards compatible variant of {@link AbstractEventProcessor.EventReferenceOrderedMessage}
     */
    public static class EventReferenceOrderedMessage extends AbstractEventProcessor.EventReferenceOrderedMessage {
        public EventReferenceOrderedMessage(AggregateType aggregateType, Object aggregateId, EventOrder eventOrder) {
            super(aggregateType, aggregateId, eventOrder);
        }

        public EventReferenceOrderedMessage(AggregateType aggregateType, Object aggregateId, EventOrder eventOrder, MessageMetaData metaData) {
            super(aggregateType, aggregateId, eventOrder, metaData);
        }
    }
}
