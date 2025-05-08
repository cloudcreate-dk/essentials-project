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

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.trustworks.essentials.components.foundation.json.JSONDeserializationException;
import dk.trustworks.essentials.components.foundation.messaging.*;
import dk.trustworks.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.trustworks.essentials.components.foundation.types.SubscriberId;
import dk.trustworks.essentials.reactive.Handler;
import dk.trustworks.essentials.reactive.command.*;
import dk.trustworks.essentials.shared.Lifecycle;
import dk.trustworks.essentials.types.LongRange;
import org.slf4j.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;
import static dk.trustworks.essentials.shared.MessageFormatter.msg;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * The AbstractEventProcessor provides base functionality for event processors that react to events
 * associated with one or more aggregate event streams stored in the {@link EventStore}.
 * It manages event subscriptions and message handling mechanisms.
 * <br>
 * It includes integration with durable queues and supports handling of message handler interceptors,
 * command bus handling, and resettable subscriptions.
 * <br>
 * Subclasses can customize behavior by overriding specific methods or providing their own
 * configurations such as message handler interceptors, subscription policies, and processing logic.
 * This class requires concrete subclasses to define the set of {@link AggregateType}s they are reacting to
 * as well as the processor's name.
 */
public abstract class AbstractEventProcessor implements Lifecycle {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final EventStoreSubscriptionManager eventStoreSubscriptionManager;
    protected final DurableLocalCommandBus        commandBus;
    protected final EventStore                    eventStore;

    protected volatile boolean                         started;
    protected          List<EventStoreSubscription>    eventStoreSubscriptions;
    protected          AnnotatedCommandHandler         commandBusHandlerDelegate;
    protected          List<MessageHandlerInterceptor> messageHandlerInterceptors;

    protected QueueName durableQueueName;

    /**
     * Resolves a unique {@link SubscriberId} by combining the processor name and aggregate type.
     *
     * @param aggregateType the type of aggregate associated with the subscriber
     * @param processorName the name of the processor for which the {@link SubscriberId} is being resolved
     * @return a unique {@link SubscriberId} for the given processor name and aggregate type
     */
    public static SubscriberId resolveSubscriberId(AggregateType aggregateType, String processorName) {
        requireNonNull(aggregateType, "aggregateType is null");
        requireNonNull(processorName, "processorName is null");
        return SubscriberId.of(processorName + ":" + aggregateType);
    }


    /**
     * Create a new {@link AbstractEventProcessor} instance
     *
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions<br>
     *                                      The  {@link EventStore} instance associated with the {@link EventStoreSubscriptionManager} is used to only query references to
     *                                      the {@link PersistedEvent}. Before an event reference message is forwarded to the corresponding {@link MessageHandler} we load the {@link PersistedEvent}'s
     *                                      payload and forward it to the {@link MessageHandler} annotated method. This avoids double storing event payloads
     * @param commandBus                    The {@link CommandBus} where any {@link Handler} or {@link CmdHandler} annotated methods in the subclass of the {@link AbstractEventProcessor} will be registered
     * @param messageHandlerInterceptors    The {@link MessageHandlerInterceptor}'s that will intercept calls to the {@link MessageHandler} annotated methods.<br>
     *                                      Unless you override {@link #getMessageHandlerInterceptors()} then these are the {@link MessageHandlerInterceptor}'s that will be used.
     */
    protected AbstractEventProcessor(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                     DurableLocalCommandBus commandBus,
                                     List<MessageHandlerInterceptor> messageHandlerInterceptors) {
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "No eventStoreSubscriptionManager provided");
        this.commandBus = requireNonNull(commandBus, "No commandBus provided");
        this.eventStore = requireNonNull(eventStoreSubscriptionManager.getEventStore(), "No eventStore is associated with the eventStoreSubscriptionManager provided");
        this.messageHandlerInterceptors = new CopyOnWriteArrayList<>(requireNonNull(messageHandlerInterceptors, "No messageHandlerInterceptors list provided"));
        setupCommandHandler();
    }

    /**
     * Setup the EventProcessor's {@link CommandHandler} capabilities - default delegates any matching commands on to
     * {@link Handler} or {@link CmdHandler} annotated methods in the subclass of the {@link AbstractEventProcessor}
     */
    protected void setupCommandHandler() {
        commandBusHandlerDelegate = new AnnotatedCommandHandler(this);
        commandBus.addCommandHandler(commandBusHandlerDelegate);
    }

    /**
     * Default: The {@link MessageHandlerInterceptor}'s provided in the {@link AbstractEventProcessor#AbstractEventProcessor(EventStoreSubscriptionManager, DurableLocalCommandBus, List)} constructor<br>
     * You can also override this method to provide your own {@link MessageHandlerInterceptor}'s that should be used when calling {@link MessageHandler} annotated methods<br>
     * This method will be called during {@link AbstractEventProcessor#start()} time
     *
     * @return the {@link MessageHandlerInterceptor}'s that should be used when calling {@link MessageHandler} annotated methods
     */
    protected List<MessageHandlerInterceptor> getMessageHandlerInterceptors() {
        return messageHandlerInterceptors;
    }

    /**
     * This method returns true if one or more underlying {@link EventStoreSubscription} are active<br>
     * Otherwise it returns false
     *
     * @return see description above
     */
    public boolean isActive() {
        return eventStoreSubscriptions.stream()
                                      .anyMatch(EventStoreSubscription::isActive);
    }

    /**
     * Creates and returns a {@link Consumer} to handle queued messages. The consumer processes the
     * given message by distinguishing between ordered messages containing event references and
     * other generic messages. For event references, it fetches the associated event from the
     * event store and delegates the handling of the deserialized event to the specified
     * {@link PatternMatchingMessageHandler}. If the message does not contain an event reference,
     * it is directly delegated to the handler.
     *
     * @param patternMatchingMessageHandlerDelegate the delegate to handle the processed message,
     *                                              after optional deserialization.
     *                                              This handler is responsible for applying custom
     *                                              processing logic to the incoming message.
     * @return a configured {@link Consumer} of {@link Message}, which processes queued messages
     * appropriately and invokes the specified delegate for handling events or direct messages.
     */
    protected Consumer<Message> handleQueuedMessageConsumer(PatternMatchingMessageHandler patternMatchingMessageHandlerDelegate) {
        return msg -> {
            if (msg instanceof OrderedMessage orderedMessage && AbstractEventProcessor.EventReferenceOrderedMessage.isEventReference(orderedMessage)) {
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
                log.debug("[{}:{}] Handling Event of type '{}'", aggregateType, aggregateId, persistedEvent.event().getEventTypeOrNamePersistenceValue());
                try {
                    patternMatchingMessageHandlerDelegate.accept(OrderedMessage.of(persistedEvent.event().deserialize(),
                                                                                   stringAggregateId,
                                                                                   eventOrder,
                                                                                   msg.getMetaData()));
                } catch (JSONDeserializationException e) {
                    log.error("Failed to deserialize PersistedEvent '{}'", persistedEvent.event().getEventTypeOrNamePersistenceValue(), e);
                    throw e;
                }
            } else {
                patternMatchingMessageHandlerDelegate.accept(msg);
            }
        };
    }

    /**
     * Resets all event store subscriptions to their initial state.
     * This method sets the global event order for each subscription to the first global event order, purges the durable queue
     * and invokes the provided callback function to reset durable queues.
     *
     * @param resetDurableQueueCallback a callback function executed if the durable queue is going to be purged - the actual queue reset logic must be implemented in this callback
     */
    protected void resetAllSubscriptions(Consumer<QueueName> resetDurableQueueCallback) {
        var resetParameters = eventStoreSubscriptions.stream()
                                                     .map(EventStoreSubscription::aggregateType)
                                                     .collect(toMap(identity(), aggregateType -> GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER));
        resetSubscriptions(resetParameters, true, resetDurableQueueCallback);
    }

    /**
     * Reset the specific {@link AggregateType} fromAndIncluding the specified {@link GlobalEventOrder}<br>
     * {@link #onSubscriptionsReset(AggregateType, GlobalEventOrder)} will be called for each {@link AggregateType}<br>
     * <br>
     * Note: This method does NOT purge the durable queue
     *
     * @param resetAggregateSubscriptionsFromAndIncluding a map of {@link AggregateType} and reset-fromAndIncluding {@link GlobalEventOrder}
     */
    protected void resetSubscriptions(Map<AggregateType, GlobalEventOrder> resetAggregateSubscriptionsFromAndIncluding) {
        resetSubscriptions(resetAggregateSubscriptionsFromAndIncluding, false, queueName -> {
        });
    }

    /**
     * Resets the subscriptions for specified {@link AggregateType}s from a given {@link GlobalEventOrder}
     * and optionally resets the durable queue once.<br>
     * {@link #onSubscriptionsReset(AggregateType, GlobalEventOrder)} will be called for each {@link AggregateType}<br>
     *
     * @param resetAggregateSubscriptionsFromAndIncluding a map of {@link AggregateType} and the {@link GlobalEventOrder}
     *                                                    from and including which the subscription should be reset
     * @param resetDurableQueue                           a flag indicating whether the durable queue should be purged
     * @param resetDurableQueueCallback                   a callback function executed if the durable queue is going to be purged - the actual queue reset logic must be implemented in this callback
     */
    protected void resetSubscriptions(Map<AggregateType, GlobalEventOrder> resetAggregateSubscriptionsFromAndIncluding,
                                      boolean resetDurableQueue,
                                      Consumer<QueueName> resetDurableQueueCallback) {
        requireNonNull(resetAggregateSubscriptionsFromAndIncluding, "resetAggregateSubscriptionsFromAndIncluding is null");
        requireNonNull(resetDurableQueueCallback, "resetDurableQueueCallback is null");
        log.info("[{}] Resetting EventProcessor subscriptions with resetDurableQueue '{}'", getProcessorName(), resetDurableQueue);
        var isResetDurableQueue = new AtomicBoolean(resetDurableQueue);
        resetAggregateSubscriptionsFromAndIncluding.forEach((aggregateType, resubscribeFromAndIncluding) -> {
            findSubscription(aggregateType).ifPresentOrElse(eventStoreSubscription -> {
                                                                doResetSubscription(eventStoreSubscription, resubscribeFromAndIncluding, isResetDurableQueue.get(), resetDurableQueueCallback);
                                                                isResetDurableQueue.set(false);
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

    /**
     * Reset subscription from and including global order and reset durable queue with callback (called by {@link #resetSubscriptions(Map, boolean, Consumer)} for each {@link AggregateType} being reset)
     *
     * @param eventStoreSubscription      event store subscription
     * @param resubscribeFromAndIncluding reset from and including global order
     * @param resetDurableQueue           should durable queue be purged
     * @param resetDurableQueueCallback   a callback function executed if the durable queue is going to be purged - the actual queue reset logic must be implemented in this callback
     */
    protected void doResetSubscription(EventStoreSubscription eventStoreSubscription,
                                       GlobalEventOrder resubscribeFromAndIncluding,
                                       boolean resetDurableQueue,
                                       Consumer<QueueName> resetDurableQueueCallback) {
        requireNonNull(eventStoreSubscription, "eventStoreSubscription is null");
        requireNonNull(resubscribeFromAndIncluding, "resubscribeFromAndIncluding is null");
        requireNonNull(resetDurableQueueCallback, "resetDurableQueueCallback is null");

        log.info("[{}] Resetting subscription for '{}' restartingFromAndIncluding globalEventOrder '{}'",
                 getProcessorName(),
                 eventStoreSubscription.subscriberId(),
                 resubscribeFromAndIncluding);

        eventStoreSubscription.resetFrom(resubscribeFromAndIncluding, r -> {
            if (resetDurableQueue) {
                log.info("[{}] Deleting all messages for durable queue'{}'", getProcessorName(), durableQueueName);
                resetDurableQueueCallback.accept(durableQueueName);
            }
            onSubscriptionsReset(eventStoreSubscription.aggregateType(), r);
        });
    }

    /**
     * Finds the {@link EventStoreSubscription} for the specified {@link AggregateType}.
     * This method searches through the collection of event store subscriptions
     * to find one that matches the given aggregate type.
     *
     * @param aggregateType the {@link AggregateType} for which the subscription is being searched
     * @return an {@link Optional} containing the found {@link EventStoreSubscription}
     * if one exists for the specified {@link AggregateType}, or an empty {@link Optional} if not
     */
    protected Optional<EventStoreSubscription> findSubscription(AggregateType aggregateType) {
        return eventStoreSubscriptions.stream()
                                      .filter(eventStoreSubscription -> eventStoreSubscription.aggregateType().equals(aggregateType))
                                      .findFirst();
    }

    /**
     * Will be called when {@link #resetSubscriptions(Map, boolean, Consumer)}, {@link #resetAllSubscriptions(Consumer)} or {@link #resetSubscriptions(Map)} - override this method
     * perform any reset related side effects that may be required (e.g. resetting a view)
     *
     * @param aggregateType               the {@link AggregateType} for which the subscription is being reset
     * @param resubscribeFromAndIncluding the {@link GlobalEventOrder} that the subscription will restart from and including
     */
    protected void onSubscriptionsReset(AggregateType aggregateType, GlobalEventOrder resubscribeFromAndIncluding) {
    }

    /**
     * Resolves the {@link AggregateIdSerializer} for the specified {@link AggregateType}.
     * Retrieves the {@link AggregateIdSerializer} associated with the given {@link AggregateType}
     * from the {@link EventStore}'s aggregate event stream configuration.
     *
     * @param aggregateType The {@link AggregateType} for which the {@link AggregateIdSerializer} is being resolved.
     * @return The {@link AggregateIdSerializer} associated with the specified {@link AggregateType}.
     */
    protected AggregateIdSerializer resolveAggregateIdSerializer(AggregateType aggregateType) {
        return ((ConfigurableEventStore<?>) eventStore).getAggregateEventStreamConfiguration(aggregateType).aggregateIdSerializer;
    }

    /**
     * Get the name of the underlying durable queue
     *
     * @return the {@link QueueName of the underlying durable queue}
     */
    public QueueName getDurableQueueName() {
        return durableQueueName;
    }

    /**
     * Get the name of the event processor.<br>
     * This name is used as value for the underlying {@link EventStoreSubscription#subscriberId()}'s
     * and for the durable queue's name (queue naming is implementation dependent)
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
     * Get the number of message consumption threads that will consume messages from the underlying Durable queue<br>
     * Default is: 1
     *
     * @return the number of message consumption threads that will consume messages from the underlying Durable queue
     */
    protected int getNumberOfParallelQueuedMessageConsumers() {
        return 1;
    }

    /**
     * Get the {@link RedeliveryPolicy} used when consuming messages from the underlying Durable queue
     *
     * @return the {@link RedeliveryPolicy} used when consuming messages from the underlying Durable queue
     */
    protected RedeliveryPolicy getDurableQueueRedeliveryPolicy() {
        return RedeliveryPolicy.exponentialBackoff()
                               .setInitialRedeliveryDelay(Duration.ofMillis(200))
                               .setFollowupRedeliveryDelay(Duration.ofMillis(200))
                               .setFollowupRedeliveryDelayMultiplier(1.1d)
                               .setMaximumFollowupRedeliveryDelayThreshold(Duration.ofSeconds(3))
                               .setMaximumNumberOfRedeliveries(20)
                               .build();
    }

    /**
     * Determines the starting {@link GlobalEventOrder} for new event subscriptions.
     * Depending on the configuration, it may start from the latest persisted global event order
     * or from the first global event order.
     * <p>
     * If {@link #isStartSubscriptionFromLatestEvent()} is true, the starting global event order
     * will be the next global event order after the highest persisted one for a given {@link AggregateType}.
     * If it's false, the subscription will start from {@link GlobalEventOrder#FIRST_GLOBAL_EVENT_ORDER}.
     *
     * @return a {@link Function} that provides the starting {@link GlobalEventOrder} for each {@link AggregateType}.
     */
    protected Function<AggregateType, GlobalEventOrder> resolveStartSubscriptionFromGlobalEventOrder() {
        if (isStartSubscriptionFromLatestEvent()) {
            return aggregateType -> eventStore.getUnitOfWorkFactory().withUnitOfWork(() -> eventStore.findHighestGlobalEventOrderPersisted(aggregateType)
                                                                                                     .map(GlobalEventOrder::increment)
                                                                                                     .orElse(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER));
        }
        return aggregateType -> GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER;
    }

    /**
     * New subscriptions should start consuming events from the latest event. Default false
     *
     * @return is new subscriptions should start consuming events from the latest event.
     */
    protected boolean isStartSubscriptionFromLatestEvent() {
        return false;
    }

    /**
     * Retrieves the instance of the EventStore associated with this processor
     *
     * @return the {@link EventStore} instance
     */
    protected final EventStore getEventStore() {
        return eventStore;
    }

    /**
     * Retrieves the instance of the {@link DurableLocalCommandBus} associated with this processor.
     *
     * @return the {@link DurableLocalCommandBus} instance
     */
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
         * that an {@link OrderedMessage} is an {@link EventProcessor.EventReferenceOrderedMessage}
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
