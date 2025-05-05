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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreSubscription;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import org.slf4j.*;

import java.util.*;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Experimental: The {@code ViewEventProcessor} class is an abstraction for processing events that are projected into views (e.g. in a relational database).
 * It integrates with a distributed locking mechanism to ensure exclusive access during processing.<br>
 * {@link PersistedEvent}'s are processed directly and only in case of an error handling the event will this event (and later events associated with the same aggregate id)
 * by queued onto the underlying durable queue associated with this processor.
 */
public abstract class ViewEventProcessor extends AbstractEventProcessor {
    private final Logger                        logger = LoggerFactory.getLogger(this.getClass());
    private final PatternMatchingMessageHandler patternMatchingMessageHandlerDelegate;
    private final FencedLockManager             fencedLockManager;
    private final DurableQueues                 durableQueues;
    private final Consumer<Message>             queuedMessageConsumer;
    private       DurableQueueConsumer          durableQueueConsumer;
    private       LockName                      lockName;

    /**
     * Constructs a {@code ViewEventProcessor} using the provided dependencies.
     *
     * @param eventProcessorDependencies the dependencies required for initializing the {@code ViewEventProcessor}.
     *                                   Must include an {@code EventStoreSubscriptionManager}, a {@code FencedLockManager},
     *                                   {@code DurableQueues}, a {@code DurableLocalCommandBus}, and a list of
     *                                   {@code MessageHandlerInterceptor}s.
     * @throws IllegalArgumentException if {@code eventProcessorDependencies} is null.
     */
    protected ViewEventProcessor(ViewEventProcessorDependencies eventProcessorDependencies) {
        this(requireNonNull(eventProcessorDependencies, "eventProcessorDependencies is null").eventStoreSubscriptionManager(),
             eventProcessorDependencies.fencedLockManager(),
             eventProcessorDependencies.durableQueues(),
             eventProcessorDependencies.commandBus(),
             eventProcessorDependencies.messageHandlerInterceptors());
    }

    /**
     * Constructs a {@code ViewEventProcessor} using the provided dependencies.
     *
     * @param subscriptionManager the {@code EventStoreSubscriptionManager} instance used for managing event store subscriptions.
     * @param fencedLockManager   the {@code FencedLockManager} instance used for obtaining distributed locks.
     * @param durableQueues       the {@code DurableQueues} instance used for managing durable message queues.
     * @param commandBus          the {@code DurableLocalCommandBus} instance used for dispatching commands locally.
     * @param interceptors        a list of {@code MessageHandlerInterceptor} instances used to intercept and process messages.
     * @throws IllegalArgumentException if any of the required parameters are null.
     */
    protected ViewEventProcessor(
            EventStoreSubscriptionManager subscriptionManager,
            FencedLockManager fencedLockManager,
            DurableQueues durableQueues,
            DurableLocalCommandBus commandBus,
            List<MessageHandlerInterceptor> interceptors) {
        super(subscriptionManager, commandBus, interceptors);
        this.fencedLockManager = requireNonNull(fencedLockManager, "fencedLockManager is null");
        this.durableQueues = requireNonNull(durableQueues, "durableQueues is null");
        patternMatchingMessageHandlerDelegate = new PatternMatchingMessageHandler(this, getMessageHandlerInterceptors());
        patternMatchingMessageHandlerDelegate.allowUnmatchedMessages();
        queuedMessageConsumer = handleQueuedMessageConsumer(patternMatchingMessageHandlerDelegate);
    }

    @Override
    public void start() {
        if (started) return;
        started = true;
        var processorName              = requireNonNull(getProcessorName(), "getProcessorName() returned null");
        var subscribeToEventsRelatedTo = requireNonNull(reactsToEventsRelatedToAggregateTypes(), "reactsToEventsRelatedToAggregateTypes() returned null");
        logger.info("🎑⚙️  [{}] Starting ViewEventProcessor - will subscribe to events related to these AggregatesType's: {}",
                    processorName,
                    subscribeToEventsRelatedTo);
        this.durableQueueName = QueueName.of(processorName + ":queue");
        this.lockName = LockName.of(processorName + ":lock");
        fencedLockManager.acquireLockAsync(lockName,
                                           LockCallback.builder()
                                                       .onLockAcquired(lock -> {
                                                           logger.info("FencedLock '{}' for ViewProcessor '{}' with DurableQueue '{}' was ACQUIRED - will start Exclusive DurableQueueConsumer", lockName, processorName, durableQueueName);
                                                           startSubscribersAndDurableConsumer(processorName, subscribeToEventsRelatedTo, lock);
                                                           logger.info("Exclusive DurableQueueConsumer for Queue '{}': {}", durableQueueName, durableQueueConsumer);
                                                       })
                                                       .onLockReleased(lock -> {
                                                           if (durableQueueConsumer != null) {
                                                               logger.info("FencedLock '{}' for ViewProcessor '{}' was RELEASED - will stop {} subscriber(s)", lockName, processorName, eventStoreSubscriptions.size());
                                                               eventStoreSubscriptions.forEach(Lifecycle::stop);
                                                               logger.info("FencedLock '{}' for ViewProcessor '{}' and DurableQueue '{}' was RELEASED - will stop Exclusive DurableQueueConsumer: {}", lockName, processorName, durableQueueName, durableQueueConsumer);
                                                               durableQueueConsumer.cancel();
                                                               logger.info("Stopped Exclusive DurableQueueConsumer for Queue '{}': {}", durableQueueName, durableQueueConsumer);
                                                           } else {
                                                               logger.warn("FencedLock '{}' for ViewProcessor '{}' was RELEASED - didn't find an Exclusive DurableQueueConsumer for Queue '{}'!", lockName, processorName, durableQueueName);
                                                           }
                                                       })
                                                       .build());
    }

    private void startSubscribersAndDurableConsumer(String processorName, List<AggregateType> subscribeToEventsRelatedTo, FencedLock lock) {
        durableQueueConsumer = durableQueues.consumeFromQueue(durableQueueName,
                                                              getDurableQueueRedeliveryPolicy(),
                                                              getNumberOfParallelQueuedMessageConsumers(),
                                                              queuedMessage -> {
                                                                  queuedMessage.getMetaData().put(MessageMetaData.FENCED_LOCK_TOKEN,
                                                                                                  lock.getCurrentToken().toString());
                                                                  eventStore.getUnitOfWorkFactory().usingUnitOfWork(uow -> {
                                                                      handleQueuedMessage(queuedMessage);
                                                                  });
                                                              }
                                                             );
        eventStoreSubscriptions = subscribeToEventsRelatedTo.stream()
                                                            .map(aggregateType -> {
                                                                var subscriberId = AbstractEventProcessor.resolveSubscriberId(aggregateType, processorName);
                                                                var subscription = eventStoreSubscriptionManager.subscribeToAggregateEventsAsynchronously(
                                                                        subscriberId,
                                                                        aggregateType,
                                                                        resolveStartSubscriptionFromGlobalEventOrder(),
                                                                        this::handlePersistedEvent);
                                                                logger.info("🎑⚙️ [{}] Created non-exclusive async '{}' subscription: {}",
                                                                            processorName,
                                                                            aggregateType,
                                                                            subscription);
                                                                return subscription;
                                                            })
                                                            .toList();

        logger.info("🎑⚙️  [{}] Started. There are #{} undelivered queue messages",
                    processorName,
                    durableQueues.getTotalMessagesQueuedFor(durableQueueName));
    }

    @Override
    public void stop() {
        if (!started) return;
        started = false;
        logger.info("🎑⚙️  [{}] Stopping ViewEventProcessor",
                    getProcessorName());
        fencedLockManager.cancelAsyncLockAcquiring(lockName);
        logger.info("🎑⚙️  [{}] ViewEventProcessor Stopped ", getProcessorName());
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    /**
     * Retrieves the durable queues associated with this event processor.
     *
     * @return the {@link DurableQueues} instance associated with this event processor.
     */
    public DurableQueues getDurableQueues() {
        return durableQueues;
    }

    private void handlePersistedEvent(PersistedEvent event) {
        var aggregateType = event.aggregateType();
        var serializer    = resolveAggregateIdSerializer(aggregateType);
        var key           = serializer.serialize(event.aggregateId());
        var order         = event.eventOrder().longValue();
        var payload       = event.event().deserialize();
        var meta          = new MessageMetaData(event.metaData().deserialize());
        var msg           = OrderedMessage.of(payload, key, order, meta);

        try {
            if (durableQueues.hasOrderedMessageQueuedForKey(durableQueueName, key)) {
                logger.debug("[{}:{}] The queue already has ordered message queued for key '{}'. Queueing message with event-order '{}'", aggregateType, key, key, order);
                durableQueues.queueMessage(durableQueueName,
                                           new EventReferenceOrderedMessage(aggregateType, key, event.eventOrder(), meta));
            } else {
                patternMatchingMessageHandlerDelegate.accept(msg);
            }
        } catch (Exception e) {
            logger.debug("[{}:{}] Direct handling failed for event '{}', enqueuing for retry.",
                         aggregateType, key, event.event().getEventTypeOrNamePersistenceValue(), e);
            durableQueues.queueMessage(durableQueueName, new EventReferenceOrderedMessage(
                    aggregateType, key, event.eventOrder(), meta));
        }
    }

    private void handleQueuedMessage(QueuedMessage queuedMessage) {
        if (queuedMessage instanceof EventReferenceOrderedMessage orderedMessage) {
            logger.debug("[{}] Handling queued message '{}' for Aggregate '{}' with key '{}' and event-order '{}'", durableQueueName, queuedMessage.getId(), orderedMessage.getPayload(), orderedMessage.key, orderedMessage.order);
        } else {
            logger.debug("[{}] Handling queued message '{}'", durableQueueName, queuedMessage.getId());
        }
        var msg = queuedMessage.getMessage();
        queuedMessageConsumer.accept(msg);
    }

    /**
     * Reset all event store subscriptions and purge the durable queue.
     *
     * @see AbstractEventProcessor#resetAllSubscriptions(Consumer)
     */
    public void resetAllSubscriptions() {
        resetAllSubscriptions(durableQueues::purgeQueue);
    }

    /**
     * Resets the subscriptions for specified aggregate types starting from the given global event order
     * and optionally purges the durable queue associated with them.
     *
     * @param resetAggregateSubscriptionsFromAndIncluding a map where the key represents the {@link AggregateType} for which the subscription
     *                                                    will be reset, and the value specifies the {@link GlobalEventOrder} from where the
     *                                                    subscription should start processing events.
     * @param resetDurableQueue                           a flag indicating whether the durable queue associated with the subscriptions
     *                                                    should be purged during the reset.
     */
    public void resetSubscriptions(Map<AggregateType, GlobalEventOrder> resetAggregateSubscriptionsFromAndIncluding,
                                   boolean resetDurableQueue) {
        resetSubscriptions(resetAggregateSubscriptionsFromAndIncluding,
                           resetDurableQueue,
                           durableQueues::purgeQueue);

    }

    /**
     * Resets the given event store subscription to start processing events from the specified global order
     * and optionally purges the associated durable queue.
     *
     * @param eventStoreSubscription      the event store subscription that will be reset
     * @param resubscribeFromAndIncluding the global order from which the subscription will start processing events
     * @param resetDurableQueue           flag indicating whether the associated durable queue should be purged
     */
    public void doResetSubscription(EventStoreSubscription eventStoreSubscription,
                                    GlobalEventOrder resubscribeFromAndIncluding,
                                    boolean resetDurableQueue) {
        doResetSubscription(eventStoreSubscription, resubscribeFromAndIncluding, resetDurableQueue, durableQueues::purgeQueue);
    }

    @Override
    public String toString() {
        return "🎑⚙️ " + this.getClass().getSimpleName() + " { " +
                "processorName='" + getProcessorName() + "'" +
                ", reactsToEventsRelatedToAggregateTypes=" + reactsToEventsRelatedToAggregateTypes() +
                ", started=" + started +
                " }";
    }

}
