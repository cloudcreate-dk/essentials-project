/*
 * Copyright 2021-2023 the original author or authors.
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
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.reactive.Handler;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.shared.reflection.Classes;
import dk.cloudcreate.essentials.types.*;
import org.slf4j.*;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Event Modeling style Event Sourced Event Processor and Command Handler, which is capable of both containing Command {@link Handler} as well as {@link MessageHandler}
 * annotated methods.<br>
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
 *     void handle(IntraBankMoneyTransferRequested e) {
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
 *     void handle(AccountDeposited e) {
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

    private boolean                       started;
    private List<EventStoreSubscription>  eventStoreSubscriptions;
    private Consumer<Message>             inboxMessageHandlerDelegate;
    private AnnotatedCommandHandler       commandBusHandlerDelegate;
    private Inbox                         inbox;
    private PatternMatchingMessageHandler patternMatchingInboxMessageHandlerDelegate;

    /**
     * Create a new {@link EventProcessor} instance
     *
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions
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
             null);
    }

    /**
     * Create a new {@link EventProcessor} instance
     *
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions
     * @param inboxes                       the {@link Inboxes} instance used to create an {@link Inbox}, with the name returned from {@link #getProcessorName()}.
     *                                      This {@link Inbox} is used for forwarding {@link PersistedEvent}'s received via {@link EventStoreSubscription}'s, because {@link EventStoreSubscription}'s
     *                                      doesn't handle message retry, etc.
     * @param commandBus                    The {@link CommandBus} where any {@link Handler} or {@link CmdHandler} annotated methods in the subclass of the {@link EventProcessor} will be registered
     * @param eventStore                    Optional argument. If the <code>eventStore</code> is NULL (default if you're using {@link EventProcessor#EventProcessor(EventStoreSubscriptionManager, Inboxes, DurableLocalCommandBus)})
     *                                      then we queue the full {@link PersistedEvent}'s payload in the {@link Inbox}. If  <code>eventStore</code> is NON-NULL then we only queue a reference to
     *                                      the {@link PersistedEvent} and before the message is forwarded to the corresponding {@link MessageHandler} then we load the {@link PersistedEvent}'s
     *                                      payload and forward it to the {@link MessageHandler} annotated method
     */
    protected EventProcessor(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                             Inboxes inboxes,
                             DurableLocalCommandBus commandBus,
                             EventStore eventStore) {
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "No eventStoreSubscriptionManager provided");
        this.inboxes = requireNonNull(inboxes, "No inboxes instance provided");
        this.commandBus = requireNonNull(commandBus, "No commandBus provided");
        this.eventStore = eventStore;
        setupEventAndMessageHandlers();
    }

    private void setupEventAndMessageHandlers() {
        commandBusHandlerDelegate = new AnnotatedCommandHandler(this);
        commandBus.addCommandHandler(commandBusHandlerDelegate);

        patternMatchingInboxMessageHandlerDelegate = new PatternMatchingMessageHandler(this);
        patternMatchingInboxMessageHandlerDelegate.allowUnmatchedMessages();
        inboxMessageHandlerDelegate = (msg) -> {
            if (EventReferenceOrderedMessage.isOrderedEventReference(msg)) {
                var orderedMessage    = (OrderedMessage) msg;
                var aggregateType     = (AggregateType) orderedMessage.getPayload();
                var stringAggregateId = orderedMessage.getKey();
                // TODO: If necessary we can introduce a generic factory to convert other values than SingleValueType's
                var aggregateIdSingleValueType = EventReferenceOrderedMessage.getSingleValueKeyType(orderedMessage);
                var aggregateId = aggregateIdSingleValueType != null ?
                                  SingleValueType.fromObject(stringAggregateId,
                                                             aggregateIdSingleValueType) :
                                  stringAggregateId;

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
                patternMatchingInboxMessageHandlerDelegate.accept(OrderedMessage.of(events.get(0).event().deserialize(),
                                                                                    aggregateId.toString(),
                                                                                    eventOrder));
            } else {
                patternMatchingInboxMessageHandlerDelegate.accept(msg);
            }
        };
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
     * Forward the event to the <code>forwardToInbox</code> as an {@link OrderedMessage}<br>
     * The default implementation checks if the concrete {@link EventProcessor} class has an {@literal @MessageHandler} annotated method that
     * handles the event. Only events with a corresponding {@link MessageHandler} annotated method will be forwarded to the {@link Inbox} and thereby delegates to the
     * {@link MessageHandler} annotated method
     *
     * @param event          the event to forward
     * @param forwardToInbox the {@link Inbox} the event should be forwarded to
     */
    protected void forwardEventToInbox(PersistedEvent event, Inbox forwardToInbox) {
        if (patternMatchingInboxMessageHandlerDelegate.handlesMessageWithPayload(event.event().getEventType().get().toJavaClass())) {
            if (eventStore != null) {
                forwardToInbox.addMessageReceived(new EventReferenceOrderedMessage(event.aggregateType(),
                                                                                   event.aggregateId(),
                                                                                   event.eventOrder()));
            } else {
                forwardToInbox.addMessageReceived(OrderedMessage.of(event.event().getJsonDeserialized().get(),
                                                                    event.aggregateId().toString(),
                                                                    event.eventOrder().longValue()));
            }
        }
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
        /**
         * {@link MessageMetaData} key, that's stored the Fully Qualified Class Name
         * of the {@link OrderedMessage#getKey()} such that it can be converted back
         * to a {@link dk.cloudcreate.essentials.types.SingleValueType}
         */
        public static final String SINGLE_VALUE_KEY_TYPE_KEY    = "SINGLE_VALUE_KEY_TYPE";

        public EventReferenceOrderedMessage(AggregateType aggregateType, Object aggregateId, EventOrder eventOrder) {
            this(aggregateType, aggregateId, eventOrder, MessageMetaData.of());
        }

        public EventReferenceOrderedMessage(AggregateType aggregateType, Object aggregateId, EventOrder eventOrder, MessageMetaData metaData) {
            super(aggregateType, aggregateId.toString(), eventOrder.longValue(), metaData);
            metaData.put(EVENT_REFERENCE_METADATA_KEY, "true");
            if (aggregateId instanceof SingleValueType<?, ?>) {
                metaData.put(SINGLE_VALUE_KEY_TYPE_KEY, aggregateId.getClass().getName());
            }
        }

        public static boolean isOrderedEventReference(Message msg) {
            requireNonNull(msg, "No msg provided");
            return msg instanceof OrderedMessage && "true".equals(msg.getMetaData().get(EVENT_REFERENCE_METADATA_KEY));
        }

        public static Class<? extends SingleValueType<?, ?>> getSingleValueKeyType(OrderedMessage msg) {
            requireNonNull(msg, "No msg provided");
            var singleValueType = msg.getMetaData().get(SINGLE_VALUE_KEY_TYPE_KEY);
            if (singleValueType != null) {
                return (Class<? extends SingleValueType<?, ?>>) Classes.forName(singleValueType);
            }
            return null;
        }
    }
}
