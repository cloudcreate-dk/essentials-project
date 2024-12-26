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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSON;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.json.JSONDeserializationException;
import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.reactive.Handler;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.types.LongRange;
import org.slf4j.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Event Modeling style Event Sourced Event Processor and Command Handler, which is capable of both containing {@link CmdHandler} as well as {@link MessageHandler}
 * annotated event handling methods.<br>
 * The {@link EventProcessor} simplifies building event projections or process automations.<br>
 * <br>
 * <i><b>Note:</b> If the associated {@link DurableLocalCommandBus} uses the {@link UnitOfWorkControllingCommandBusInterceptor} then all
 * logic inside {@link CmdHandler} methods will be performed within a {@link UnitOfWork}</i>
 * <br>
 * <br>
 * <i><b>Note:</b> If the associated {@link Inboxes} uses is associated with a {@link UnitOfWorkFactory}, which {@link Inboxes.DurableQueueBasedInboxes} supports, then
 * all logic inside {@link MessageHandler} methods will be performed within a {@link UnitOfWork}</i>
 * <br>
 * <br>
 * An {@link EventProcessor} can subscribe to multiple {@link EventStore} Event Streams (e.g. a stream of Order events or a stream of Product events).<br>
 * To ensure efficient processing and prevent conflicts, only a single instance of a concrete {@link EventProcessor} in a cluster can have an active Event Stream subscription at a time (using the {@link FencedLockManager}).
 * <br>
 * To enhance throughput, you can control the number of parallel threads utilized for handling messages. Consequently, events associated with different aggregate instances within an EventStream can be concurrently processed.
 * <br>
 * The {@link EventProcessor} also ensures ordered handling of events, partitioned by aggregate id. I.e. events related to a specific aggregate id will always be processed in the exact order they were originally added to the {@link EventStore}.<br>
 * This guarantees the preservation of the chronological sequence of events for each individual aggregate, maintaining data integrity and consistency, even during event redelivery/poison-message handling.<br>
 * <br>
 * Details:<br>
 * Instead of manually subscribing to the underlying {@link EventStore} using the {@link EventStoreSubscriptionManager}, which requires you to provide your own error and retry handling,
 * you can use the {@link EventProcessor} to subscribe to one or more {@link EventStore} event streams, while providing you with error and retry handling using the common {@link RedeliveryPolicy} concept
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
 *     public TransferMoneyProcessor(@NonNull Accounts accounts,
 *                                   @NonNull IntraBankMoneyTransfers intraBankMoneyTransfers,
 *                                   @NonNull EventStoreSubscriptionManager eventStoreSubscriptionManager,
 *                                   @NonNull Inboxes inboxes,
 *                                   @NonNull DurableLocalCommandBus commandBus) {
 *         super(eventStoreSubscriptionManager,
 *               inboxes,
 *               commandBus);
 *         this.accounts = accounts;
 *         this.intraBankMoneyTransfers = intraBankMoneyTransfers;
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
 *     @Handler
 *     public void handle(@NonNull RequestIntraBankMoneyTransfer cmd) {
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
 * }
 * }</pre>
 */
public abstract class EventProcessor implements Lifecycle {
    protected final Logger                        log = LoggerFactory.getLogger(this.getClass());
    private final   EventStoreSubscriptionManager eventStoreSubscriptionManager;
    private final   Inboxes                       inboxes;
    protected final DurableLocalCommandBus        commandBus;
    private final   EventStore                    eventStore;

    private boolean                         started;
    private List<EventStoreSubscription>    eventStoreSubscriptions;
    private Consumer<Message>               inboxMessageHandlerDelegate;
    private AnnotatedCommandHandler         commandBusHandlerDelegate;
    private Inbox                           inbox;
    private PatternMatchingMessageHandler   patternMatchingInboxMessageHandlerDelegate;
    private List<MessageHandlerInterceptor> messageHandlerInterceptors;

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
     *                                      The  {@link EventStore} instance associated with the {@link EventStoreSubscriptionManager} is used to only queue a reference to
     *                                      the {@link PersistedEvent} and before the message is forwarded to the corresponding {@link MessageHandler} then we load the {@link PersistedEvent}'s
     *                                      payload and forward it to the {@link MessageHandler} annotated method
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
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "No eventStoreSubscriptionManager provided");
        this.inboxes = requireNonNull(inboxes, "No inboxes instance provided");
        this.commandBus = requireNonNull(commandBus, "No commandBus provided");
        this.eventStore = requireNonNull(eventStoreSubscriptionManager.getEventStore(), "No eventStore is associated with the eventStoreSubscriptionManager provided");
        this.messageHandlerInterceptors = new CopyOnWriteArrayList<>(requireNonNull(messageHandlerInterceptors, "No messageHandlerInterceptors list provided"));
        setupCommandHandler();
    }

    /**
     * Create a new {@link EventProcessor} instance without any {@link MessageHandlerInterceptor}'s (you can override {@link #getMessageHandlerInterceptors()}
     * to provide custom interceptors, or you can use the {@link EventProcessor#EventProcessor(EventStoreSubscriptionManager, Inboxes, DurableLocalCommandBus, List)} constructor)
     *
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions<br>
     *                                      The  {@link EventStore} instance associated with the {@link EventStoreSubscriptionManager} is used to only queue a reference to
     *                                      the {@link PersistedEvent} and before the message is forwarded to the corresponding {@link MessageHandler} then we load the {@link PersistedEvent}'s
     *                                      payload and forward it to the {@link MessageHandler} annotated method
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

    private void setupCommandHandler() {
        commandBusHandlerDelegate = new AnnotatedCommandHandler(this);
        commandBus.addCommandHandler(commandBusHandlerDelegate);
    }

    private void setupMessageHandlerDelegate() {
        if (patternMatchingInboxMessageHandlerDelegate != null) {
            return;
        }
        patternMatchingInboxMessageHandlerDelegate = new PatternMatchingMessageHandler(this, getMessageHandlerInterceptors());
        patternMatchingInboxMessageHandlerDelegate.allowUnmatchedMessages();
        inboxMessageHandlerDelegate = (msg) -> {
            if (msg instanceof OrderedMessage orderedMessage && EventReferenceOrderedMessage.isEventReference(orderedMessage)) {
                var aggregateType         = (AggregateType) orderedMessage.getPayload();
                var aggregateIdSerializer = resolveAggregateIdSerializer(aggregateType);

                var stringAggregateId = orderedMessage.getKey();
                var aggregateId       = aggregateIdSerializer.deserialize(stringAggregateId);

                var eventOrder = orderedMessage.order;
                log.trace("Looking up event for aggregate '{}' with id '{}' and event-order {}",
                          aggregateType,
                          aggregateId,
                          eventOrder);
                var events = eventStore.fetchStream(aggregateType,
                                                    aggregateId,
                                                    LongRange.only(eventOrder))
                                       .orElseThrow(() -> new IllegalArgumentException(msg("Couldn't find a matching event for aggregate '{}' with id '{}' and event-order {}",
                                                                                           aggregateType,
                                                                                           aggregateId,
                                                                                           eventOrder)))
                                       .eventList();
                if (events.size() != 1) {
                    throw new IllegalArgumentException(msg("Couldn't find a matching event for aggregate '{}' with id '{}' and event-order {}",
                                                           aggregateType,
                                                           aggregateId,
                                                           eventOrder));
                }
                var persistedEvent = events.get(0);
                try {
                    patternMatchingInboxMessageHandlerDelegate.accept(OrderedMessage.of(persistedEvent.event().deserialize(),
                                                                                        stringAggregateId,
                                                                                        eventOrder,
                                                                                        msg.getMetaData()));
                } catch (JSONDeserializationException e) {
                    log.error("Failed to deserialize PersistedEvent '{}'", persistedEvent.event().getEventTypeOrNamePersistenceValue(), e);
                    throw e;
                }
            } else {
                patternMatchingInboxMessageHandlerDelegate.accept(msg);
            }
        };
    }

    /**
     * Default: The {@link MessageHandlerInterceptor}'s provided in the {@link EventProcessor#EventProcessor(EventStoreSubscriptionManager, Inboxes, DurableLocalCommandBus, List)} constructor<br>
     * You can also override this method to provide your own {@link MessageHandlerInterceptor}'s that should be used when calling {@link MessageHandler} annotated methods<br>
     * This method will be called during {@link EventProcessor#start()} time
     *
     * @return the {@link MessageHandlerInterceptor}'s that should be used when calling {@link MessageHandler} annotated methods
     */
    protected List<MessageHandlerInterceptor> getMessageHandlerInterceptors() {
        return messageHandlerInterceptors;
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

            eventStoreSubscriptions = subscribeToEventsRelatedTo.stream()
                                                                .map(aggregateType -> {
                                                                    var subscriberId = SubscriberId.of(processorName + ":" + aggregateType);
                                                                    var subscription = eventStoreSubscriptionManager.exclusivelySubscribeToAggregateEventsAsynchronously(
                                                                            subscriberId,
                                                                            aggregateType,
                                                                            GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
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
                                                                .collect(Collectors.toList());

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


    /**
     * This method returns true if one or more of the underlying {@link FencedLock}'s have been acquired.<br>
     * Otherwise it returns false
     *
     * @return see description above
     */
    public boolean isActive() {
        return eventStoreSubscriptions.stream()
                                      .anyMatch(EventStoreSubscription::isActive);
    }

    /**
     * Resets all {@link AggregateType} subscriptions fromAndIncluding {@link GlobalEventOrder#FIRST_GLOBAL_EVENT_ORDER}<br>
     * {@link #onSubscriptionsReset(AggregateType, GlobalEventOrder)} will be called for each {@link AggregateType}<br>
     * <br>
     * This method also calls {@link Inbox#deleteAllMessages()}
     *
     * @see #resetSubscriptions(Map)
     */
    public void resetAllSubscriptions() {
        Map<AggregateType, GlobalEventOrder> resetParameters = eventStoreSubscriptions.stream()
                                                                                      .map(EventStoreSubscription::aggregateType)
                                                                                      .collect(toMap(identity(), aggregateType -> GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER));
        var resetInbox = true;
        resetSubscriptions(resetParameters, resetInbox);
    }

    /**
     * Reset the specific {@link AggregateType} fromAndIncluding the specified {@link GlobalEventOrder}<br>
     * {@link #onSubscriptionsReset(AggregateType, GlobalEventOrder)} will be called for each {@link AggregateType}<br>
     * <br>
     * Note: This method does NOT call {@link Inbox#deleteAllMessages()}
     *
     * @param resetAggregateSubscriptionsFromAndIncluding a map of {@link AggregateType} and reset-fromAndIncluding {@link GlobalEventOrder}
     */
    public void resetSubscriptions(Map<AggregateType, GlobalEventOrder> resetAggregateSubscriptionsFromAndIncluding) {
        resetSubscriptions(resetAggregateSubscriptionsFromAndIncluding, false);
    }

    private void resetSubscriptions(Map<AggregateType, GlobalEventOrder> resetAggregateSubscriptionsFromAndIncluding,
                                    boolean resetInbox) {
        requireNonNull(resetAggregateSubscriptionsFromAndIncluding, "resetAggregateSubscriptionsFromAndIncluding is null");
        AtomicBoolean isResetInbox = new AtomicBoolean(resetInbox);
        resetAggregateSubscriptionsFromAndIncluding.forEach((aggregateType, resubscribeFromAndIncluding) -> {
            findSubscription(aggregateType).ifPresentOrElse(eventStoreSubscription -> {
                                                                doResetSubscription(eventStoreSubscription, resubscribeFromAndIncluding, isResetInbox.get());
                                                                isResetInbox.set(false);
                                                            },
                                                            () -> {
                                                                throw new IllegalArgumentException(msg("[{}] Cannot reset subscription for {} '{}' since the {} doesn't subscribe to events for this aggregate type",
                                                                                                       getProcessorName(),
                                                                                                       AggregateType.class.getSimpleName(),
                                                                                                       aggregateType,
                                                                                                       EventProcessor.class.getSimpleName()));
                                                            });
        });
    }

    private void doResetSubscription(EventStoreSubscription eventStoreSubscription,
                                     GlobalEventOrder resubscribeFromAndIncluding,
                                     boolean resetInbox) {

        log.info("[{}] Resetting subscription for '{}' restartingFromAndIncluding globalEventOrder '{}'",
                 getProcessorName(),
                 eventStoreSubscription.subscriberId(),
                 resubscribeFromAndIncluding);

        eventStoreSubscription.resetFrom(resubscribeFromAndIncluding, r -> {
            if (resetInbox) {
                log.info("[{}] Deleting all messages for inbox '{}'", getProcessorName(), inbox.name());
                inbox.deleteAllMessages();
            }
            onSubscriptionsReset(eventStoreSubscription.aggregateType(), r);
        });
    }

    private Optional<EventStoreSubscription> findSubscription(AggregateType aggregateType) {
        return eventStoreSubscriptions.stream()
                                      .filter(eventStoreSubscription -> eventStoreSubscription.aggregateType().equals(aggregateType))
                                      .findFirst();
    }

    /**
     * Will be called when {@link #resetAllSubscriptions()} or {@link #resetSubscriptions(Map)} - override this method
     * perform any reset related side effects that may be required (e.g. resetting a view)
     *
     * @param aggregateType               the {@link AggregateType} for which the subscription is being reset
     * @param resubscribeFromAndIncluding the {@link GlobalEventOrder} that the subscription will restart from and including
     */
    protected void onSubscriptionsReset(AggregateType aggregateType, GlobalEventOrder resubscribeFromAndIncluding) {
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

            forwardToInbox.addMessageReceived(new EventReferenceOrderedMessage(aggregateType,
                                                                               aggregateIdSerializer.serialize(event.aggregateId()),
                                                                               event.eventOrder(),
                                                                               new MessageMetaData(event.metaData().deserialize())));
        }
    }

    private AggregateIdSerializer resolveAggregateIdSerializer(AggregateType aggregateType) {
        return ((ConfigurableEventStore<?>) eventStore).getAggregateEventStreamConfiguration(aggregateType).aggregateIdSerializer;
    }

    /**
     * Get the name of the event processor.<br>
     * This name is used as value for the underlying {@link EventStoreSubscription#subscriberId()}'s
     * and for the {@link InboxName}
     *
     * @return the name of the event processor
     */
    public abstract String getProcessorName();


    /**
     * Get the {@link AggregateType}'s who's events this {@link EventProcessor} is reacting to
     *
     * @return the {@link AggregateType}'s who's events this {@link EventProcessor} is reacting to
     */
    protected abstract List<AggregateType> reactsToEventsRelatedToAggregateTypes();

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
     * Default is: is the number of available CPU processors
     *
     * @return the number of message consumption threads that will consume messages from the underlying {@link Inbox}
     */
    protected int getNumberOfParallelInboxMessageConsumers() {
        return Runtime.getRuntime().availableProcessors();
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


    @Override
    public String toString() {
        return "⚙️ " + this.getClass().getSimpleName() + " { " +
                "processorName='" + getProcessorName() + "'" +
                ", reactsToEventsRelatedToAggregateTypes=" + reactsToEventsRelatedToAggregateTypes() +
                ", started=" + started +
                " }";
    }

    protected final Inbox getInbox() {
        return inbox;
    }

    protected final EventStore getEventStore() {
        return eventStore;
    }

    protected final DurableLocalCommandBus getCommandBus() {
        return commandBus;
    }

    /**
     * Variant of {@link OrderedMessage} that stores the {@link AggregateType} associated with the Message as the payload,
     * the {@link PersistedEvent#aggregateId()} as the {@link OrderedMessage#getKey()} and
     * the {@link PersistedEvent#eventOrder()} as the {@link OrderedMessage#getOrder()}
     */
    public static class EventReferenceOrderedMessage extends OrderedMessage {
        /**
         * {@link MessageMetaData} key, that's used as a flag to indicate
         * that an {@link OrderedMessage} is an {@link EventReferenceOrderedMessage}
         */
        public static final String EVENT_REFERENCE_METADATA_KEY = "EVENT_REFERENCE";

        public EventReferenceOrderedMessage(AggregateType aggregateType, Object aggregateId, EventOrder eventOrder) {
            this(aggregateType, aggregateId, eventOrder, MessageMetaData.of());
        }

        public EventReferenceOrderedMessage(AggregateType aggregateType, Object aggregateId, EventOrder eventOrder, MessageMetaData metaData) {
            super(aggregateType, aggregateId.toString(), eventOrder.longValue(), metaData);
            metaData.put(EVENT_REFERENCE_METADATA_KEY, "true");
        }

        public static boolean isEventReference(OrderedMessage msg) {
            requireNonNull(msg, "No msg provided");
            return "true".equals(msg.getMetaData().get(EVENT_REFERENCE_METADATA_KEY));
        }
    }
}
