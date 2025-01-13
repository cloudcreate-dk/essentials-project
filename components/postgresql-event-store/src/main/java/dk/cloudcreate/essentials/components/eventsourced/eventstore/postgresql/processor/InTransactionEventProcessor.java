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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSON;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventType;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.json.JSONDeserializationException;
import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.Inboxes.DurableQueueBasedInboxes;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.reactive.Handler;
import dk.cloudcreate.essentials.reactive.command.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Event Modeling style Event Sourced Event Processor and Command Handler, which is capable of both containing {@link CmdHandler} as well as {@link MessageHandler}
 * annotated event handling methods.<br>
 * The {@link InTransactionEventProcessor} simplifies building event projections or process automations.<br>
 * <br>
 * <i><b>Note:</b> If the associated {@link DurableLocalCommandBus} uses the {@link UnitOfWorkControllingCommandBusInterceptor} then all
 * logic inside {@link CmdHandler} methods will be performed within a {@link UnitOfWork}</i>
 * <br>
 * <br>
 * <i><b>Note:</b> If the associated {@link Inboxes} uses is associated with a {@link UnitOfWorkFactory}, which {@link DurableQueueBasedInboxes} supports, then
 * all logic inside {@link MessageHandler} methods will be performed within a {@link UnitOfWork}</i>
 * <br>
 * <br>
 * An {@link InTransactionEventProcessor} can subscribe in transaction to multiple {@link EventStore} Event Streams (e.g. a stream of Order events or a stream of Product events).<br>
 * If useExclusively flag is true only a single instance of a concrete {@link InTransactionEventProcessor} in a cluster can have an active Event Stream subscription at a time (using the {@link FencedLockManager}).
 * <br>
 * To enhance throughput, you can control the number of parallel threads utilized for handling messages. Consequently, events associated with different aggregate instances within an EventStream can be concurrently processed.
 * <br>
 * If the useExclusively flag is true, the {@link InTransactionEventProcessor} also ensures ordered handling of events<br>
 * This guarantees the preservation of the chronological sequence of events for each individual aggregate, maintaining data integrity and consistency.<br>
 * <br>
 * Details:<br>
 * Instead of manually subscribing to the underlying {@link EventStore} using the {@link EventStoreSubscriptionManager}, which requires you to provide your own error and retry handling,
 * you can use the {@link InTransactionEventProcessor} to subscribe to one or more {@link EventStore} event streams, while providing you with error and retry handling using the common {@link RedeliveryPolicy} concept
 * <p>
 * You must override {@link #reactsToEventsRelatedToAggregateTypes()} to specify which EventSourced {@link AggregateType} event-streams the {@link EventProcessor} should subscribe to.<br>
 * The {@link InTransactionEventProcessor} will set up an (optionally exclusive) synchronous in-transaction {@link EventStoreSubscription} for each {@link AggregateType} and will forward any
 * {@link PersistedEvent}'s as {@link OrderedMessage}'s IF and ONLY IF the concrete {@link EventProcessor} subclass contains a corresponding {@link MessageHandler}
 * annotated method matching the {@link PersistedEvent#event()}'s {@link EventJSON#getEventType()}'s {@link EventType#toJavaClass()} matches that first argument type.
 * <b>Note: In transaction subscriptions does not support replay.</b>
 * <p>
 * Example:
 * <pre>{@code
 * @Service
 * @Slf4j
 * public class TransferMoneyProcessor extends InTransactionEventProcessor {
 *     private final Accounts                accounts;
 *     private final IntraBankMoneyTransfers intraBankMoneyTransfers;
 *
 *     public TransferMoneyProcessor(@NonNull Accounts accounts,
 *                                   @NonNull IntraBankMoneyTransfers intraBankMoneyTransfers,
 *                                   @NonNull EventProcessorDependencies eventProcessorDependencies) {
 *         super(eventProcessorDependencies, true);
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
public abstract class InTransactionEventProcessor implements Lifecycle {
    protected final Logger                        log = LoggerFactory.getLogger(this.getClass());
    private final   EventStoreSubscriptionManager eventStoreSubscriptionManager;
    protected final DurableLocalCommandBus        commandBus;
    private final   EventStore                    eventStore;

    private boolean                         started;
    private List<EventStoreSubscription>    eventStoreSubscriptions;
    private AnnotatedCommandHandler         commandBusHandlerDelegate;
    private PatternMatchingMessageHandler   patternMatchingHandlerDelegate;
    private List<MessageHandlerInterceptor> messageHandlerInterceptors;
    private boolean                         useExclusively;

    /**
     * Create a new {@link InTransactionEventProcessor} instance
     *
     * @param eventProcessorDependencies The {@link EventProcessorDependencies} that encapsulates all
     *                                   the dependencies required by an instance of an {@link EventProcessor}
     * @param useExclusively             If true then exclusive subscriptions are used (using the {@link FencedLockManager}).
     * @see InTransactionEventProcessor#InTransactionEventProcessor(EventStoreSubscriptionManager, DurableLocalCommandBus, List, boolean)
     */
    protected InTransactionEventProcessor(EventProcessorDependencies eventProcessorDependencies,
                                          boolean useExclusively) {
        this(requireNonNull(eventProcessorDependencies, "No eventProcessorDependencies provided").eventStoreSubscriptionManager,
             eventProcessorDependencies.commandBus,
             eventProcessorDependencies.messageHandlerInterceptors,
             useExclusively);
    }

    /**
     * Create a new {@link InTransactionEventProcessor} instance - using non-exclusive subscriptions
     *
     * @param eventProcessorDependencies The {@link EventProcessorDependencies} that encapsulates all
     *                                   the dependencies required by an instance of an {@link EventProcessor}
     * @see InTransactionEventProcessor#InTransactionEventProcessor(EventStoreSubscriptionManager, DurableLocalCommandBus, List, boolean)
     */
    protected InTransactionEventProcessor(EventProcessorDependencies eventProcessorDependencies) {
        this(requireNonNull(eventProcessorDependencies, "No eventProcessorDependencies provided").eventStoreSubscriptionManager,
             eventProcessorDependencies.commandBus,
             eventProcessorDependencies.messageHandlerInterceptors,
             false);
    }

    /**
     * Create a new {@link InTransactionEventProcessor} instance
     *
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions<br>
     * @param commandBus                    The {@link CommandBus} where any {@link Handler} or {@link CmdHandler} annotated methods in the subclass of the {@link EventProcessor} will be registered
     * @param messageHandlerInterceptors    The {@link MessageHandlerInterceptor}'s that will intercept calls to the {@link MessageHandler} annotated methods.<br>
     *                                      Unless you override {@link #getMessageHandlerInterceptors()} then these are the {@link MessageHandlerInterceptor}'s that will be used.
     * @param useExclusively                If true exclusive subscription (using the {@link FencedLockManager}).
     */
    protected InTransactionEventProcessor(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                          DurableLocalCommandBus commandBus,
                                          List<MessageHandlerInterceptor> messageHandlerInterceptors,
                                          boolean useExclusively) {
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "No eventStoreSubscriptionManager provided");
        this.commandBus = requireNonNull(commandBus, "No commandBus provided");
        this.eventStore = requireNonNull(eventStoreSubscriptionManager.getEventStore(), "No eventStore is associated with the eventStoreSubscriptionManager provided");
        this.messageHandlerInterceptors = new CopyOnWriteArrayList<>(requireNonNull(messageHandlerInterceptors, "No messageHandlerInterceptors list provided"));
        this.useExclusively = useExclusively;
        setupCommandHandler();
    }

    private void setupCommandHandler() {
        commandBusHandlerDelegate = new AnnotatedCommandHandler(this);
        commandBus.addCommandHandler(commandBusHandlerDelegate);
    }

    private void setupMessageHandlerDelegate() {
        if (patternMatchingHandlerDelegate != null) {
            return;
        }
        patternMatchingHandlerDelegate = new PatternMatchingMessageHandler(this, getMessageHandlerInterceptors());
        patternMatchingHandlerDelegate.allowUnmatchedMessages();
    }

    /**
     * Default: The {@link MessageHandlerInterceptor}'s provided in the {@link InTransactionEventProcessor#InTransactionEventProcessor(EventStoreSubscriptionManager, DurableLocalCommandBus, List, boolean)} constructor<br>
     * You can also override this method to provide your own {@link MessageHandlerInterceptor}'s that should be used when calling {@link MessageHandler} annotated methods<br>
     * This method will be called during {@link InTransactionEventProcessor#start()} time
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
            log.info("⚙️ [{}] Starting InTransactionEventProcessor - will subscribe '{}' to events related to these AggregatesType's: {}",
                     processorName,
                     useExclusively ? "exclusively" : "non-exclusively",
                     subscribeToEventsRelatedTo);

            setupMessageHandlerDelegate();
            subscribeInTransaction(subscribeToEventsRelatedTo);
        }
    }

    @Override
    public void stop() {
        if (started) {
            started = false;
            log.info("⚙️ [{}] Stopping InTransactionEventProcessor",
                     getProcessorName());

            eventStoreSubscriptions.forEach(Lifecycle::stop);
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    private void subscribeInTransaction(List<AggregateType> subscribeToEventsRelatedTo) {
        eventStoreSubscriptions = subscribeToEventsRelatedTo.stream()
                                                            .map(this::createEventStoreSubscription).toList();
    }


    private EventStoreSubscription createEventStoreSubscription(AggregateType aggregateType) {
        var subscriberId = SubscriberId.of(getProcessorName() + ":" + aggregateType + ":sync");

        EventStoreSubscription subscription = createEventStoreSubscription(aggregateType, subscriberId);

        log.info("⚙️ [{}] InTransactionEventProcessor created subscription '{}'",
                 getProcessorName(),
                 subscription);

        return subscription;
    }

    private EventStoreSubscription createEventStoreSubscription(AggregateType aggregateType, SubscriberId subscriberId) {
        if (useExclusively) {
            return eventStoreSubscriptionManager.exclusivelySubscribeToAggregateEventsInTransaction(
                    subscriberId,
                    aggregateType,
                    Optional.empty(),
                    (event, unitOfWork) -> invokeHandler(event, unitOfWork, aggregateType, subscriberId));
        } else {
            return eventStoreSubscriptionManager.subscribeToAggregateEventsInTransaction(
                    subscriberId,
                    aggregateType,
                    (event, unitOfWork) -> invokeHandler(event, unitOfWork, aggregateType, subscriberId));
        }
    }

    private void invokeHandler(PersistedEvent event, UnitOfWork unitOfWork, AggregateType aggregateType, SubscriberId subscriberId) {
        try {
            log.info("[{}-{}] Processing event: {} using unit of work '{}'", subscriberId, aggregateType, event, unitOfWork.info());
            patternMatchingHandlerDelegate.accept(OrderedMessage.of(event.event().deserialize(),
                                                                    resolveAggregateIdSerializer(event.aggregateType()).serialize(event.aggregateId()),
                                                                    event.eventOrder().value(),
                                                                    new MessageMetaData(event.metaData().deserialize()))
                                                 );
        } catch (JSONDeserializationException e) {
            log.error("Failed to deserialize PersistedEvent '{}'", event.event().getEventTypeOrNamePersistenceValue(), e);
            throw e;
        }
    }

    /**
     * For non-exclusive subscriptions this method returns true if {@link #isStarted()} is true.<br>
     * For exclusive this method returns true if one or more of the underlying {@link FencedLock} are acquired.<br>
     * Otherwise it returns false
     *
     * @return see description above
     */
    public boolean isActive() {
        return eventStoreSubscriptions.stream()
                                      .anyMatch(EventStoreSubscription::isActive);
    }

    /**
     * Are the event store subscriptions exclusivity (i.e. using a {@link FencedLock})?
     *
     * @return if the event store subscriptions use exclusivity (i.e. using a {@link FencedLock})?
     */
    public boolean isUseExclusively() {
        return useExclusively;
    }

    private AggregateIdSerializer resolveAggregateIdSerializer(AggregateType aggregateType) {
        return ((ConfigurableEventStore<?>) eventStore).getAggregateEventStreamConfiguration(aggregateType).aggregateIdSerializer;
    }

    /**
     * Get the name of the event processor.<br>
     * This name is used as value for the underlying {@link EventStoreSubscription#subscriberId()}'s
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


    @Override
    public String toString() {
        return "⚙️ " + this.getClass().getSimpleName() + " { " +
                "processorName='" + getProcessorName() + "'" +
                ", reactsToEventsRelatedToAggregateTypes=" + reactsToEventsRelatedToAggregateTypes() +
                ", started=" + started +
                " }";
    }

    protected final EventStore getEventStore() {
        return eventStore;
    }

    protected final DurableLocalCommandBus getCommandBus() {
        return commandBus;
    }

}
