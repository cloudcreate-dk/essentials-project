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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.trustworks.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.observability.EventStoreSubscriptionObserver;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSON;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.trustworks.essentials.components.foundation.*;
import dk.trustworks.essentials.components.foundation.fencedlock.*;
import dk.trustworks.essentials.components.foundation.messaging.eip.store_and_forward.Inbox;
import dk.trustworks.essentials.components.foundation.messaging.queue.OrderedMessage;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.components.foundation.types.*;
import dk.trustworks.essentials.shared.FailFast;
import dk.trustworks.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.trustworks.essentials.shared.functional.CheckedRunnable;
import dk.trustworks.essentials.shared.functional.tuple.Pair;
import dk.trustworks.essentials.shared.time.StopWatch;
import org.reactivestreams.Subscription;
import org.slf4j.*;
import reactor.core.publisher.*;
import reactor.util.retry.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Collectors;

import static dk.trustworks.essentials.shared.Exceptions.rethrowIfCriticalError;
import static dk.trustworks.essentials.shared.FailFast.*;
import static dk.trustworks.essentials.shared.MessageFormatter.msg;

/**
 * Provides support for durable {@link EventStore} subscriptions.<br>
 * The {@link EventStoreSubscriptionManager} will keep track of where in the Event Stream, for a given {@link AggregateType},
 * the individual subscribers are.<br>
 * If a subscription is added, the {@link EventStoreSubscriptionManager} will ensure that the subscriber resumes from where
 * it left of the last time, or ensure that they start from the specified <code>onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder</code>
 * in case it's the very first time the subscriber is subscribing
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface EventStoreSubscriptionManager extends Lifecycle {

    /**
     * The {@link EventStore} associated with the {@link EventStoreSubscriptionManager}
     *
     * @return the {@link EventStore} associated with the {@link EventStoreSubscriptionManager
     */
    EventStore getEventStore();

    /**
     * Create a builder for the {@link EventStoreSubscriptionManager}
     *
     * @return a builder for the {@link EventStoreSubscriptionManager}
     */
    static EventStoreSubscriptionManagerBuilder builder() {
        return new EventStoreSubscriptionManagerBuilder();
    }

    /**
     * Create an asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param onlyIncludeEventsForTenant                              if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param eventHandler                                            the event handler that will receive the published {@link PersistedEvent}'s<br>
     *                                                                Exceptions thrown from the eventHandler will cause the event to be skipped. If you need a retry capability
     *                                                                please use {@link #subscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, Inbox)}
     * @return the subscription handle
     */
    EventStoreSubscription subscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                    AggregateType forAggregateType,
                                                                    GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                    Optional<Tenant> onlyIncludeEventsForTenant,
                                                                    PersistedEventHandler eventHandler);

    /**
     * Create an asynchronous batched subscription that will receive {@link PersistedEvent}s in batches after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem<br>
     * This subscription method is a batching alternative that processes events in batches
     * to improve throughput, at the cost of higher latency, and reduce database load. It should be used when high throughput is needed and the event handlers can
     * efficiently process batches of events.
     * <p>
     * Key features:
     * <ul>
     *   <li>Processes events in batches for improved throughput</li>
     *   <li>Tracks batch progress and updates resume points only after entire batch completes</li>
     *   <li>Only requests more events after batch processing completes</li>
     *   <li>Supports max latency for processing partial batches</li>
     * </ul>
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param onlyIncludeEventsForTenant                              if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param maxBatchSize                                            the maximum batch size before processing
     * @param maxLatency                                              the maximum time to wait before processing a partial batch
     * @param eventHandler                                            the event handler that will receive the published {@link PersistedEvent}'s in batches<br>
     *                                                                Exceptions thrown from the eventHandler will cause the events to be skipped.
     * @return the subscription handle
     */
    EventStoreSubscription batchSubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                         AggregateType forAggregateType,
                                                                         GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                         Optional<Tenant> onlyIncludeEventsForTenant,
                                                                         int maxBatchSize,
                                                                         Duration maxLatency,
                                                                         BatchedPersistedEventHandler eventHandler);

    /**
     * Create an asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param onlyIncludeEventsForTenant                              if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param forwardToInbox                                          The Inbox where the {@link PersistedEvent}'s will be forwarded to as an {@link OrderedMessage} containing
     *                                                                the {@link PersistedEvent#event()}'s {@link EventJSON#getJsonDeserialized()} as payload and the {@link PersistedEvent#aggregateId()} as {@link OrderedMessage#getKey()}
     *                                                                and the {@link PersistedEvent#eventOrder()} as {@link OrderedMessage#getOrder()}.<br>
     *                                                                This reuses the {@link Inbox} ability to retry event deliveries
     * @return the subscription handle
     */
    default EventStoreSubscription subscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                            AggregateType forAggregateType,
                                                                            GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                            Optional<Tenant> onlyIncludeEventsForTenant,
                                                                            Inbox forwardToInbox) {
        requireNonNull(forwardToInbox, "No forwardToInbox instance provided");
        return subscribeToAggregateEventsAsynchronously(subscriberId,
                                                        forAggregateType,
                                                        onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                        onlyIncludeEventsForTenant,
                                                        event -> forwardToInbox.addMessageReceived(OrderedMessage.of(event.event().getJsonDeserialized().get(),
                                                                                                                     event.aggregateId().toString(),
                                                                                                                     event.eventOrder().longValue())));
    }

    /**
     * Create an asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param forwardToInbox                                          The Inbox where the {@link PersistedEvent}'s will be forwarded to as an {@link OrderedMessage} containing
     *                                                                the {@link PersistedEvent#event()}'s {@link EventJSON#getJsonDeserialized()} as payload and the {@link PersistedEvent#aggregateId()} as {@link OrderedMessage#getKey()}
     *                                                                and the {@link PersistedEvent#eventOrder()} as {@link OrderedMessage#getOrder()}.<br>
     *                                                                This reuses the {@link Inbox} ability to retry event deliveries
     * @return the subscription handle
     */
    default EventStoreSubscription subscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                            AggregateType forAggregateType,
                                                                            GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                            Inbox forwardToInbox) {

        return subscribeToAggregateEventsAsynchronously(subscriberId,
                                                        forAggregateType,
                                                        onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                        Optional.empty(),
                                                        forwardToInbox);
    }


    /**
     * Create an asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param eventHandler                                            the event handler that will receive the published {@link PersistedEvent}'s<br>
     *                                                                Exceptions thrown from the eventHandler will cause the event to be skipped. If you need a retry capability
     *                                                                please use {@link #subscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Inbox)}
     * @return the subscription handle
     */
    default EventStoreSubscription subscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                            AggregateType forAggregateType,
                                                                            GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                            PersistedEventHandler eventHandler) {
        return subscribeToAggregateEventsAsynchronously(subscriberId,
                                                        forAggregateType,
                                                        onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                        Optional.empty(),
                                                        eventHandler);
    }

    /**
     * Create an asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem
     *
     * @param subscriberId                                            The unique identifier for the subscriber.
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder A function that determines the global event order
     *                                                                to start subscribing from on the first subscription.
     * @param eventHandler                                            the event handler that will receive the published {@link PersistedEvent}'s<br>
     *                                                                Exceptions thrown from the eventHandler will cause the event to be skipped. If you need a retry capability
     *                                                                please use {@link #subscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Inbox)}
     * @return A subscription object that manages the lifecycle of the subscription.
     */
    default EventStoreSubscription subscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                            AggregateType forAggregateType,
                                                                            Function<AggregateType, GlobalEventOrder> onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                            PersistedEventHandler eventHandler) {
        return subscribeToAggregateEventsAsynchronously(subscriberId,
                                                        forAggregateType,
                                                        onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder.apply(forAggregateType),
                                                        Optional.empty(),
                                                        eventHandler);
    }

    /**
     * Create an exclusive asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem<br>
     * An exclusive subscription means that the {@link EventStoreSubscriptionManager} will acquire a distributed {@link FencedLock} to ensure that only one active subscriber in a cluster,
     * out of all subscribers that share the same <code>subscriberId</code>, is allowed to have an active subscribe at a time
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param onlyIncludeEventsForTenant                              if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param fencedLockAwareSubscriber                               Callback interface that will be called when the exclusive/fenced lock is acquired or released
     * @param eventHandler                                            the event handler that will receive the published {@link PersistedEvent}'s<br>
     *                                                                Exceptions thrown from the eventHandler will cause the event to be skipped. If you need a retry capability
     *                                                                please use {@link #exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, FencedLockAwareSubscriber, Inbox)}
     * @return the subscription handle
     */
    default EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                       AggregateType forAggregateType,
                                                                                       GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                       Optional<Tenant> onlyIncludeEventsForTenant,
                                                                                       FencedLockAwareSubscriber fencedLockAwareSubscriber,
                                                                                       PersistedEventHandler eventHandler) {
        return exclusivelySubscribeToAggregateEventsAsynchronously(
                subscriberId,
                forAggregateType,
                a -> onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                onlyIncludeEventsForTenant,
                fencedLockAwareSubscriber,
                eventHandler
                                                                  );
    }

    /**
     * Create an exclusive asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem<br>
     * An exclusive subscription means that the {@link EventStoreSubscriptionManager} will acquire a distributed {@link FencedLock} to ensure that only one active subscriber in a cluster,
     * out of all subscribers that share the same <code>subscriberId</code>, is allowed to have an active subscribe at a time
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using the returned {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param onlyIncludeEventsForTenant                              if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param fencedLockAwareSubscriber                               Callback interface that will be called when the exclusive/fenced lock is acquired or released
     * @param eventHandler                                            the event handler that will receive the published {@link PersistedEvent}'s<br>
     *                                                                Exceptions thrown from the eventHandler will cause the event to be skipped. If you need a retry capability
     *                                                                please use {@link #exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, FencedLockAwareSubscriber, Inbox)}
     * @return the subscription handle
     */
    EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                               AggregateType forAggregateType,
                                                                               Function<AggregateType, GlobalEventOrder> onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                               Optional<Tenant> onlyIncludeEventsForTenant,
                                                                               FencedLockAwareSubscriber fencedLockAwareSubscriber,
                                                                               PersistedEventHandler eventHandler);

    /**
     * Create an exclusive asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem<br>
     * An exclusive subscription means that the {@link EventStoreSubscriptionManager} will acquire a distributed {@link FencedLock} to ensure that only one active subscriber in a cluster,
     * out of all subscribers that share the same <code>subscriberId</code>, is allowed to have an active subscribe at a time
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param fencedLockAwareSubscriber                               Callback interface that will be called when the exclusive/fenced lock is acquired or released
     * @param eventHandler                                            the event handler that will receive the published {@link PersistedEvent}'s<br>
     *                                                                Exceptions thrown from the eventHandler will cause the event to be skipped. If you need a retry capability
     *                                                                please use {@link #exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, FencedLockAwareSubscriber, Inbox)}
     * @return the subscription handle
     */
    default EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                       AggregateType forAggregateType,
                                                                                       GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                       FencedLockAwareSubscriber fencedLockAwareSubscriber,
                                                                                       PersistedEventHandler eventHandler) {
        return exclusivelySubscribeToAggregateEventsAsynchronously(subscriberId,
                                                                   forAggregateType,
                                                                   onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                   Optional.empty(),
                                                                   fencedLockAwareSubscriber,
                                                                   eventHandler);
    }

    /**
     * Create an exclusive asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem<br>
     * An exclusive subscription means that the {@link EventStoreSubscriptionManager} will acquire a distributed {@link FencedLock} to ensure that only one active subscriber in a cluster,
     * out of all subscribers that share the same <code>subscriberId</code>, is allowed to have an active subscribe at a time
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param onlyIncludeEventsForTenant                              if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param fencedLockAwareSubscriber                               Callback interface that will be called when the exclusive/fenced lock is acquired or released
     * @param forwardToInbox                                          The Inbox where the {@link PersistedEvent}'s will be forwarded to as an {@link OrderedMessage} containing
     *                                                                the {@link PersistedEvent#event()}'s {@link EventJSON#getJsonDeserialized()} as payload and the {@link PersistedEvent#aggregateId()} as {@link OrderedMessage#getKey()}
     *                                                                and the {@link PersistedEvent#eventOrder()} as {@link OrderedMessage#getOrder()}.<br>
     *                                                                This reuses the {@link Inbox} ability to retry event deliveries
     * @return the subscription handle
     */
    default EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                       AggregateType forAggregateType,
                                                                                       GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                       Optional<Tenant> onlyIncludeEventsForTenant,
                                                                                       FencedLockAwareSubscriber fencedLockAwareSubscriber,
                                                                                       Inbox forwardToInbox) {
        requireNonNull(forwardToInbox, "No forwardToInbox instance provided");
        return exclusivelySubscribeToAggregateEventsAsynchronously(subscriberId,
                                                                   forAggregateType,
                                                                   onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                   onlyIncludeEventsForTenant,
                                                                   fencedLockAwareSubscriber,
                                                                   event -> forwardToInbox.addMessageReceived(OrderedMessage.of(event.event().getJsonDeserialized().get(),
                                                                                                                                event.aggregateId().toString(),
                                                                                                                                event.eventOrder().longValue())));
    }

    /**
     * Create an exclusive asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem<br>
     * An exclusive subscription means that the {@link EventStoreSubscriptionManager} will acquire a distributed {@link FencedLock} to ensure that only one active subscriber in a cluster,
     * out of all subscribers that share the same <code>subscriberId</code>, is allowed to have an active subscribe at a time
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param fencedLockAwareSubscriber                               Callback interface that will be called when the exclusive/fenced lock is acquired or released
     * @param forwardToInbox                                          The Inbox where the {@link PersistedEvent}'s will be forwarded to as an {@link OrderedMessage} containing
     *                                                                the {@link PersistedEvent#event()}'s {@link EventJSON#getJsonDeserialized()} as payload and the {@link PersistedEvent#aggregateId()} as {@link OrderedMessage#getKey()}
     *                                                                and the {@link PersistedEvent#eventOrder()} as {@link OrderedMessage#getOrder()}.<br>
     *                                                                This reuses the {@link Inbox} ability to retry event deliveries
     * @return the subscription handle
     */
    default EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                       AggregateType forAggregateType,
                                                                                       GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                       FencedLockAwareSubscriber fencedLockAwareSubscriber,
                                                                                       Inbox forwardToInbox) {
        requireNonNull(forwardToInbox, "No forwardToInbox instance provided");
        return exclusivelySubscribeToAggregateEventsAsynchronously(subscriberId,
                                                                   forAggregateType,
                                                                   onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                   Optional.empty(),
                                                                   fencedLockAwareSubscriber,
                                                                   forwardToInbox);
    }

    /**
     * Create an exclusive asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem<br>
     * An exclusive subscription means that the {@link EventStoreSubscriptionManager} will acquire a distributed {@link FencedLock} to ensure that only one active subscriber in a cluster,
     * out of all subscribers that share the same <code>subscriberId</code>, is allowed to have an active subscribe at a time
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param handler                                                 the event handler that will receive the published {@link PersistedEvent}'s and the callback interface will be called when the exclusive/fenced lock is acquired or released<br>
     *                                                                Exceptions thrown from the eventHandler will cause the event to be skipped. If you need a retry capability
     *                                                                please use {@link #exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, FencedLockAwareSubscriber, Inbox)}
     * @return the subscription handle
     */
    default <HANDLER extends PersistedEventHandler & FencedLockAwareSubscriber> EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                                                                                           AggregateType forAggregateType,
                                                                                                                                                           GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                                                                                           HANDLER handler) {
        return exclusivelySubscribeToAggregateEventsAsynchronously(subscriberId,
                                                                   forAggregateType,
                                                                   onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                   Optional.empty(),
                                                                   handler);
    }

    /**
     * Create an exclusive asynchronous subscription that will receive {@link PersistedEvent} after they have been committed to the {@link EventStore}<br>
     * This ensures that the handling of events can occur in a separate transaction, than the one that persisted the events, thereby avoiding the dual write problem<br>
     * An exclusive subscription means that the {@link EventStoreSubscriptionManager} will acquire a distributed {@link FencedLock} to ensure that only one active subscriber in a cluster,
     * out of all subscribers that share the same <code>subscriberId</code>, is allowed to have an active subscribe at a time
     *
     * @param subscriberId                                            the unique id for the subscriber
     * @param forAggregateType                                        the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder If it's the first time the given <code>subscriberId</code> is subscribing then the subscription will be using this {@link GlobalEventOrder} as the starting point in the
     *                                                                EventStream associated with the <code>aggregateType</code>
     * @param onlyIncludeEventsForTenant                              if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param handler                                                 the event handler that will receive the published {@link PersistedEvent}'s and the callback interface will be called when the exclusive/fenced lock is acquired or released<br>
     *                                                                Exceptions thrown from the eventHandler will cause the event to be skipped. If you need a retry capability
     *                                                                please use {@link #exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, FencedLockAwareSubscriber, Inbox)}
     * @return the subscription handle
     */
    default <HANDLER extends PersistedEventHandler & FencedLockAwareSubscriber> EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                                                                                           AggregateType forAggregateType,
                                                                                                                                                           GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                                                                                           Optional<Tenant> onlyIncludeEventsForTenant,
                                                                                                                                                           HANDLER handler) {
        return exclusivelySubscribeToAggregateEventsAsynchronously(subscriberId,
                                                                   forAggregateType,
                                                                   onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                   onlyIncludeEventsForTenant,
                                                                   handler,
                                                                   handler);
    }

    /**
     * Create an inline event subscription, that will receive {@link PersistedEvent}'s right after they're appended to the {@link EventStore} but before the associated
     * {@link UnitOfWork} is committed. This allows you to create transactional consistent event projections.
     *
     * @param subscriberId     the unique id for the subscriber
     * @param forAggregateType the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param eventHandler     the event handler that will receive the published {@link PersistedEvent}'s. Exceptions thrown by this handler will cause the {@link UnitOfWork} to rollback
     * @return the subscription handle
     */
    default EventStoreSubscription subscribeToAggregateEventsInTransaction(SubscriberId subscriberId,
                                                                           AggregateType forAggregateType,
                                                                           TransactionalPersistedEventHandler eventHandler) {
        return subscribeToAggregateEventsInTransaction(subscriberId,
                                                       forAggregateType,
                                                       Optional.empty(),
                                                       eventHandler);
    }


    /**
     * Create an exclusive in-transaction event subscription, that will receive {@link PersistedEvent}'s right after they're appended to the {@link EventStore} but before the associated
     * {@link UnitOfWork} is committed. This allows you to create transactional consistent event projections.
     *
     * @param subscriberId               the unique id for the subscriber
     * @param forAggregateType           the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onlyIncludeEventsForTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param eventHandler               the event handler that will receive the published {@link PersistedEvent}'s. Exceptions thrown by this handler will cause the {@link UnitOfWork} to rollback
     * @return the subscription handle
     */
    EventStoreSubscription exclusivelySubscribeToAggregateEventsInTransaction(SubscriberId subscriberId,
                                                                              AggregateType forAggregateType,
                                                                              Optional<Tenant> onlyIncludeEventsForTenant,
                                                                              TransactionalPersistedEventHandler eventHandler);

    /**
     * Create an in-transaction event subscription, that will receive {@link PersistedEvent}'s right after they're appended to the {@link EventStore} but before the associated
     * {@link UnitOfWork} is committed. This allows you to create transactional consistent event projections.
     *
     * @param subscriberId               the unique id for the subscriber
     * @param forAggregateType           the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onlyIncludeEventsForTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param eventHandler               the event handler that will receive the published {@link PersistedEvent}'s. Exceptions thrown by this handler will cause the {@link UnitOfWork} to rollback
     * @return the subscription handle
     */
    EventStoreSubscription subscribeToAggregateEventsInTransaction(SubscriberId subscriberId,
                                                                   AggregateType forAggregateType,
                                                                   Optional<Tenant> onlyIncludeEventsForTenant,
                                                                   TransactionalPersistedEventHandler eventHandler);

    /**
     * Create an in-transaction event subscription, that will receive {@link PersistedEvent}'s right after they're appended to the {@link EventStore} but before the associated
     * {@link UnitOfWork} is committed. This allows you to create transactional consistent event projections.
     *
     * @param subscriberId               the unique id for the subscriber
     * @param forAggregateType           the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param onlyIncludeEventsForTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param forwardToInbox             The Inbox where the {@link PersistedEvent}'s will be forwarded to as an {@link OrderedMessage} containing
     *                                   the {@link PersistedEvent#event()}'s {@link EventJSON#getJsonDeserialized()} as payload and the {@link PersistedEvent#aggregateId()} as {@link OrderedMessage#getKey()}
     *                                   and the {@link PersistedEvent#eventOrder()} as {@link OrderedMessage#getOrder()}.<br>
     *                                   This reuses the {@link Inbox} ability to retry event deliveries
     * @return the subscription handle
     */
    default EventStoreSubscription subscribeToAggregateEventsInTransaction(SubscriberId subscriberId,
                                                                           AggregateType forAggregateType,
                                                                           Optional<Tenant> onlyIncludeEventsForTenant,
                                                                           Inbox forwardToInbox) {
        requireNonNull(forwardToInbox, "No forwardToInbox instance provided");
        return subscribeToAggregateEventsInTransaction(subscriberId,
                                                       forAggregateType,
                                                       onlyIncludeEventsForTenant,
                                                       (event, unitOfWork) -> forwardToInbox.addMessageReceived(OrderedMessage.of(event.event().getJsonDeserialized().get(),
                                                                                                                                  event.aggregateId().toString(),
                                                                                                                                  event.eventOrder().longValue())));

    }

    /**
     * Create an in-transaction event subscription, that will receive {@link PersistedEvent}'s right after they're appended to the {@link EventStore} but before the associated
     * {@link UnitOfWork} is committed. This allows you to create transactional consistent event projections.
     *
     * @param subscriberId     the unique id for the subscriber
     * @param forAggregateType the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     * @param forwardToInbox   The Inbox where the {@link PersistedEvent}'s will be forwarded to as an {@link OrderedMessage} containing
     *                         the {@link PersistedEvent#event()}'s {@link EventJSON#getJsonDeserialized()} as payload and the {@link PersistedEvent#aggregateId()} as {@link OrderedMessage#getKey()}
     *                         and the {@link PersistedEvent#eventOrder()} as {@link OrderedMessage#getOrder()}.<br>
     *                         This reuses the {@link Inbox} ability to retry event deliveries
     * @return the subscription handle
     */
    default EventStoreSubscription subscribeToAggregateEventsInTransaction(SubscriberId subscriberId,
                                                                           AggregateType forAggregateType,
                                                                           Inbox forwardToInbox) {
        requireNonNull(forwardToInbox, "No forwardToInbox instance provided");
        return subscribeToAggregateEventsInTransaction(subscriberId,
                                                       forAggregateType,
                                                       Optional.empty(),
                                                       forwardToInbox);

    }

    /**
     * Create a new {@link EventStoreSubscriptionManager} that can manage event subscriptions against the provided <code>eventStore</code>
     * Use {@link #builder()} instead
     *
     * @param eventStore                    the event store that the created {@link EventStoreSubscriptionManager} can manage event subscriptions against
     * @param eventStorePollingBatchSize    how many events should The {@link EventStore} maximum return when polling for events
     * @param eventStorePollingInterval     how often should the {@link EventStore} be polled for new events
     * @param fencedLockManager             the {@link FencedLockManager} that will be used to acquire a {@link FencedLock} for exclusive asynchronous subscriptions
     * @param snapshotResumePointsEvery     How often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
     * @param durableSubscriptionRepository The repository responsible for persisting {@link SubscriptionResumePoint}
     * @return the newly create {@link EventStoreSubscriptionManager}
     * @see PostgresqlFencedLockManager
     * @see PostgresqlDurableSubscriptionRepository
     * @see DefaultEventStoreSubscriptionManager
     */
    static EventStoreSubscriptionManager createFor(EventStore eventStore,
                                                   int eventStorePollingBatchSize,
                                                   Duration eventStorePollingInterval,
                                                   FencedLockManager fencedLockManager,
                                                   Duration snapshotResumePointsEvery,
                                                   DurableSubscriptionRepository durableSubscriptionRepository) {
        return builder().setEventStore(eventStore)
                        .setEventStorePollingBatchSize(eventStorePollingBatchSize)
                        .setEventStorePollingInterval(eventStorePollingInterval)
                        .setFencedLockManager(fencedLockManager)
                        .setSnapshotResumePointsEvery(snapshotResumePointsEvery)
                        .setDurableSubscriptionRepository(durableSubscriptionRepository)
                        .build();
    }

    void unsubscribe(EventStoreSubscription eventStoreSubscription);

    default boolean hasSubscription(EventStoreSubscription subscription) {
        requireNonNull(subscription, "No subscription provided");
        return hasSubscription(subscription.subscriberId(), subscription.aggregateType());
    }

    boolean hasSubscription(SubscriberId subscriberId, AggregateType aggregateType);

    Set<Pair<SubscriberId, AggregateType>> getActiveSubscriptions();

    /**
     * @return current event order for the given subscriber only of the subscriber has a registered resume point
     */
    Optional<GlobalEventOrder> getCurrentEventOrder(SubscriberId subscriberId, AggregateType aggregateType);

    /**
     * Default implementation of the {@link EventStoreSubscriptionManager} interface that uses the {@link EventStore#getEventStoreSubscriptionObserver()}
     * to track {@link EventStoreSubscription} statistics
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    final class DefaultEventStoreSubscriptionManager implements EventStoreSubscriptionManager {
        private static final Logger log = LoggerFactory.getLogger(EventStoreSubscriptionManager.class);

        private final EventStore                    eventStore;
        private final int                           eventStorePollingBatchSize;
        private final Duration                      eventStorePollingInterval;
        private final FencedLockManager             fencedLockManager;
        private final DurableSubscriptionRepository durableSubscriptionRepository;
        private final Duration                      snapshotResumePointsEvery;

        private final    ConcurrentMap<Pair<SubscriberId, AggregateType>, EventStoreSubscription> subscribers = new ConcurrentHashMap<>();
        private volatile boolean                                                                  started;
        private          ScheduledFuture<?>                                                       saveResumePointsFuture;
        private final    boolean                                                                  startLifeCycles;
        private          ScheduledExecutorService                                                 resumePointsScheduledExecutorService;
        private final    EventStoreSubscriptionObserver                                           eventStoreSubscriptionObserver;

        public DefaultEventStoreSubscriptionManager(EventStore eventStore,
                                                    int eventStorePollingBatchSize,
                                                    Duration eventStorePollingInterval,
                                                    FencedLockManager fencedLockManager,
                                                    Duration snapshotResumePointsEvery,
                                                    DurableSubscriptionRepository durableSubscriptionRepository,
                                                    boolean startLifeCycles) {
            FailFast.requireTrue(eventStorePollingBatchSize >= 1, "eventStorePollingBatchSize must be >= 1");
            this.eventStore = requireNonNull(eventStore, "No eventStore provided");
            this.eventStorePollingBatchSize = eventStorePollingBatchSize;
            this.eventStorePollingInterval = requireNonNull(eventStorePollingInterval, "No eventStorePollingInterval provided");
            this.fencedLockManager = requireNonNull(fencedLockManager, "No fencedLockManager provided");
            this.durableSubscriptionRepository = requireNonNull(durableSubscriptionRepository, "No durableSubscriptionRepository provided");
            this.snapshotResumePointsEvery = requireNonNull(snapshotResumePointsEvery, "No snapshotResumePointsEvery provided");
            this.eventStoreSubscriptionObserver = eventStore.getEventStoreSubscriptionObserver();
            this.startLifeCycles = startLifeCycles;

            log.info("[{}] Using {} using {} with snapshotResumePointsEvery: {}, eventStorePollingBatchSize: {}, eventStorePollingInterval: {}, " +
                             "eventStoreSubscriptionObserver: {}, startLifeCycles: {}",
                     fencedLockManager.getLockManagerInstanceId(),
                     fencedLockManager,
                     durableSubscriptionRepository.getClass().getSimpleName(),
                     snapshotResumePointsEvery,
                     eventStorePollingBatchSize,
                     eventStorePollingInterval,
                     eventStoreSubscriptionObserver,
                     startLifeCycles
                    );
        }

        @Override
        public void start() {
            if (!startLifeCycles) {
                log.debug("Start of lifecycle beans is disabled");
                return;
            }
            if (!started) {
                log.info("[{}] Starting EventStore Subscription Manager", fencedLockManager.getLockManagerInstanceId());

                if (!fencedLockManager.isStarted()) {
                    fencedLockManager.start();
                }

                resumePointsScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder.builder()
                                                                                                                      .nameFormat("EventStoreSubscriptionManager-SaveResumePoints-" + fencedLockManager.getLockManagerInstanceId() + "-%d")
                                                                                                                      .daemon(true)
                                                                                                                      .build());
                saveResumePointsFuture = resumePointsScheduledExecutorService
                        .scheduleAtFixedRate(this::saveResumePointsForAllSubscribers,
                                             snapshotResumePointsEvery.toMillis(),
                                             snapshotResumePointsEvery.toMillis(),
                                             TimeUnit.MILLISECONDS);
                started = true;
                // Start any subscribers added prior to us starting
                subscribers.values().forEach(this::startEventStoreSubscriber);
            } else {
                log.debug("[{}] EventStore Subscription Manager was already started", fencedLockManager.getLockManagerInstanceId());
            }
        }

        private void startEventStoreSubscriber(EventStoreSubscription eventStoreSubscription) {
            log.debug("[{}] Starting EventStoreSubscription '{}': '{}'", fencedLockManager.getLockManagerInstanceId(), eventStoreSubscription.subscriberId(), eventStoreSubscription);
            eventStoreSubscriptionObserver.startingSubscriber(eventStoreSubscription);
            var startDuration = StopWatch.time(CheckedRunnable.safe(eventStoreSubscription::start));
            log.info("[{}] Started EventStoreSubscription '{}' in {} ms.", fencedLockManager.getLockManagerInstanceId(), eventStoreSubscription.subscriberId(), startDuration.toMillis());
            eventStoreSubscriptionObserver.startedSubscriber(eventStoreSubscription, startDuration);
        }

        private void stopEventStoreSubscriber(EventStoreSubscription eventStoreSubscription) {
            log.debug("[{}] Stopping EventStoreSubscription '{}': '{}'", fencedLockManager.getLockManagerInstanceId(), eventStoreSubscription.subscriberId(), eventStoreSubscription);
            eventStoreSubscriptionObserver.stoppingSubscriber(eventStoreSubscription);
            var stopDuration = StopWatch.time(CheckedRunnable.safe(eventStoreSubscription::stop));
            log.info("[{}] Stopped EventStoreSubscription '{}' in {} ms.", fencedLockManager.getLockManagerInstanceId(), eventStoreSubscription.subscriberId(), stopDuration.toMillis());
            eventStoreSubscriptionObserver.stoppedSubscriber(eventStoreSubscription, stopDuration);
        }

        @Override
        public void stop() {
            if (started) {
                log.info("[{}] Stopping EventStore Subscription Manager", fencedLockManager.getLockManagerInstanceId());
                subscribers.forEach((subscriberIdAggregateTypePair, eventStoreSubscription) -> stopEventStoreSubscriber(eventStoreSubscription));
                if (saveResumePointsFuture != null) {
                    log.debug("[{}] Cancelling saveResumePointsFuture", fencedLockManager.getLockManagerInstanceId());
                    saveResumePointsFuture.cancel(true);
                    saveResumePointsFuture = null;
                    log.debug("[{}] Cancelled saveResumePointsFuture", fencedLockManager.getLockManagerInstanceId());
                }
                if (resumePointsScheduledExecutorService != null) {
                    log.debug("[{}] Shutting down resumePointsScheduledExecutorService", fencedLockManager.getLockManagerInstanceId());
                    resumePointsScheduledExecutorService.shutdownNow();
                    resumePointsScheduledExecutorService = null;
                    log.debug("[{}] Shutdown resumePointsScheduledExecutorService", fencedLockManager.getLockManagerInstanceId());
                }
                if (fencedLockManager.isStarted()) {
                    log.debug("[{}] Stopping fencedLockManager", fencedLockManager.getLockManagerInstanceId());
                    fencedLockManager.stop();
                }

                started = false;
                log.info("[{}] Stopped EventStore Subscription Manager", fencedLockManager.getLockManagerInstanceId());
            } else {
                log.info("[{}] EventStore Subscription Manager was already stopped", fencedLockManager.getLockManagerInstanceId());
            }
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public EventStore getEventStore() {
            return eventStore;
        }


        @Override
        public Set<Pair<SubscriberId, AggregateType>> getActiveSubscriptions() {
            return this.subscribers.entrySet().stream()
                                   .filter(e -> e.getValue().isActive())
                                   .map(Map.Entry::getKey)
                                   .collect(Collectors.toSet());
        }

        @Override
        public Optional<GlobalEventOrder> getCurrentEventOrder(SubscriberId subscriberId, AggregateType aggregateType) {
            return Optional.ofNullable(this.subscribers.get(Pair.of(subscriberId, aggregateType)))
                           .flatMap(EventStoreSubscription::currentResumePoint)
                           .map(SubscriptionResumePoint::getResumeFromAndIncluding);
        }

        private void saveResumePointsForAllSubscribers() {
            // TODO: Filter out active subscribers and decide if we can increment the global event order like when the subscriber stops.
            //   Current approach is safe with regards to reset of resume-points, but it will result in one overlapping event during resubscription
            //   related to a failed node or after a subscription manager failure (i.e. it doesn't run stop() at all or run to completion)
            try {
                durableSubscriptionRepository.saveResumePoints(subscribers.values()
                                                                          .stream()
                                                                          .filter(EventStoreSubscription::isActive)
                                                                          .filter(eventStoreSubscription -> eventStoreSubscription.currentResumePoint().isPresent())
                                                                          .map(eventStoreSubscription -> eventStoreSubscription.currentResumePoint().get())
                                                                          .collect(Collectors.toList()));
            } catch (Exception e) {
                if (IOExceptionUtil.isIOException(e)) {
                    log.debug(msg("Failed to store ResumePoint's for the {} subscriber(s) - Experienced a Connection issue, this can happen during JVM or application shutdown", subscribers.size()));
                } else {
                    log.error(msg("Failed to store ResumePoint's for the {} subscriber(s)", subscribers.size()), e);
                }
            }
        }

        private EventStoreSubscription addEventStoreSubscription(SubscriberId subscriberId,
                                                                 AggregateType forAggregateType,
                                                                 EventStoreSubscription eventStoreSubscription) {
            requireNonNull(subscriberId, "No subscriberId provided");
            requireNonNull(forAggregateType, "No forAggregateType provided");
            requireNonNull(eventStoreSubscription, "No eventStoreSubscription provided");

            var previousEventStoreSubscription = subscribers.putIfAbsent(
                    Pair.of(subscriberId, forAggregateType),
                    eventStoreSubscription);
            if (previousEventStoreSubscription == null) {
                log.info("[{}-{}] Added {} event store subscription",
                         subscriberId,
                         forAggregateType,
                         eventStoreSubscription.getClass().getSimpleName());
                if (started && !eventStoreSubscription.isStarted()) {
                    startEventStoreSubscriber(eventStoreSubscription);
                }
                return eventStoreSubscription;
            } else {
                log.info("[{}-{}] Event Store subscription was already added",
                         subscriberId,
                         forAggregateType);
                return previousEventStoreSubscription;
            }
        }

        @Override
        public EventStoreSubscription subscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                               AggregateType forAggregateType,
                                                                               GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                               Optional<Tenant> onlyIncludeEventsForTenant,
                                                                               PersistedEventHandler eventHandler) {
            requireNonNull(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder, "No onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder provided");
            requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant option provided");
            requireNonNull(eventHandler, "No eventHandler provided");
            return addEventStoreSubscription(subscriberId,
                                             forAggregateType,
                                             new NonExclusiveAsynchronousSubscription(eventStore,
                                                                                      durableSubscriptionRepository,
                                                                                      forAggregateType,
                                                                                      subscriberId,
                                                                                      onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                      onlyIncludeEventsForTenant,
                                                                                      eventHandler));
        }

        @Override
        public EventStoreSubscription batchSubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                    AggregateType forAggregateType,
                                                                                    GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                    Optional<Tenant> onlyIncludeEventsForTenant,
                                                                                    int maxBatchSize,
                                                                                    Duration maxLatency,
                                                                                    BatchedPersistedEventHandler eventHandler) {
            requireNonNull(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder, "No onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder provided");
            requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant option provided");
            requireNonNull(eventHandler, "No eventHandler provided");
            return addEventStoreSubscription(subscriberId,
                                             forAggregateType,
                                             new NonExclusiveBatchedAsynchronousSubscription(eventStore,
                                                                                             durableSubscriptionRepository,
                                                                                             forAggregateType,
                                                                                             subscriberId,
                                                                                             onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                             onlyIncludeEventsForTenant,
                                                                                             maxBatchSize,
                                                                                             maxLatency,
                                                                                             eventHandler));
        }

        @Override
        public EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                          AggregateType forAggregateType,
                                                                                          Function<AggregateType, GlobalEventOrder> onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                          Optional<Tenant> onlyIncludeEventsForTenant,
                                                                                          FencedLockAwareSubscriber fencedLockAwareSubscriber,
                                                                                          PersistedEventHandler eventHandler) {
            requireNonNull(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder, "No onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder provided");
            requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant option provided");
            requireNonNull(eventHandler, "No eventHandler provided");
            return addEventStoreSubscription(subscriberId,
                                             forAggregateType,
                                             new ExclusiveAsynchronousSubscription(eventStore,
                                                                                   fencedLockManager,
                                                                                   durableSubscriptionRepository,
                                                                                   forAggregateType,
                                                                                   subscriberId,
                                                                                   onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                   onlyIncludeEventsForTenant,
                                                                                   fencedLockAwareSubscriber,
                                                                                   eventHandler));
        }

        @Override
        public EventStoreSubscription exclusivelySubscribeToAggregateEventsInTransaction(SubscriberId subscriberId,
                                                                                         AggregateType forAggregateType,
                                                                                         Optional<Tenant> onlyIncludeEventsForTenant,
                                                                                         TransactionalPersistedEventHandler eventHandler) {
            requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant option provided");
            requireNonNull(eventHandler, "No eventHandler provided");

            return addEventStoreSubscription(subscriberId,
                                             forAggregateType,
                                             new ExclusiveInTransactionSubscription(eventStore,
                                                                                    fencedLockManager,
                                                                                    forAggregateType,
                                                                                    subscriberId,
                                                                                    onlyIncludeEventsForTenant,
                                                                                    eventHandler
                                             ));
        }

        @Override
        public EventStoreSubscription subscribeToAggregateEventsInTransaction(SubscriberId subscriberId,
                                                                              AggregateType forAggregateType,
                                                                              Optional<Tenant> onlyIncludeEventsForTenant,
                                                                              TransactionalPersistedEventHandler eventHandler) {
            requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant option provided");
            requireNonNull(eventHandler, "No eventHandler provided");
            return addEventStoreSubscription(subscriberId,
                                             forAggregateType,
                                             new NonExclusiveInTransactionSubscription(eventStore,
                                                                                       forAggregateType,
                                                                                       subscriberId,
                                                                                       onlyIncludeEventsForTenant,
                                                                                       eventHandler));
        }

        /**
         * Called by {@link EventStoreSubscription#unsubscribe()}
         *
         * @param eventStoreSubscription the eventstore subscription that's being stopped
         */
        @Override
        public void unsubscribe(EventStoreSubscription eventStoreSubscription) {
            requireNonNull(eventStoreSubscription, "No eventStoreSubscription provided");
            var removedSubscription = subscribers.remove(Pair.of(eventStoreSubscription.subscriberId(), eventStoreSubscription.aggregateType()));
            if (removedSubscription != null) {
                log.info("[{}-{}] Unsubscribing", removedSubscription.subscriberId(), removedSubscription.aggregateType());
                stopEventStoreSubscriber(eventStoreSubscription);
            }
        }

        @Override
        public boolean hasSubscription(SubscriberId subscriberId, AggregateType aggregateType) {
            return subscribers.containsKey(Pair.of(subscriberId, aggregateType));
        }

        private class ExclusiveInTransactionSubscription implements EventStoreSubscription {
            private final EventStore                         eventStore;
            private final FencedLockManager                  fencedLockManager;
            private final AggregateType                      aggregateType;
            private final SubscriberId                       subscriberId;
            private final Optional<Tenant>                   onlyIncludeEventsForTenant;
            private final TransactionalPersistedEventHandler eventHandler;
            private final LockName                           lockName;

            private volatile boolean started;
            private volatile boolean active;

            public ExclusiveInTransactionSubscription(EventStore eventStore,
                                                      FencedLockManager fencedLockManager,
                                                      AggregateType aggregateType,
                                                      SubscriberId subscriberId,
                                                      Optional<Tenant> onlyIncludeEventsForTenant,
                                                      TransactionalPersistedEventHandler eventHandler) {
                this.eventStore = requireNonNull(eventStore, "No eventStore provided");
                this.fencedLockManager = requireNonNull(fencedLockManager, "No fencedLockManager provided");
                this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
                this.subscriberId = requireNonNull(subscriberId, "No subscriberId provided");
                this.onlyIncludeEventsForTenant = requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant provided");
                this.eventHandler = requireNonNull(eventHandler, "No eventHandler provided");
                lockName = LockName.of(msg("[{}-{}]", subscriberId, aggregateType));
            }

            @Override
            public SubscriberId subscriberId() {
                return subscriberId;
            }

            @Override
            public AggregateType aggregateType() {
                return aggregateType;
            }

            @Override
            public void start() {
                if (!started) {
                    started = true;

                    log.info("[{}-{}] Starting subscription",
                             subscriberId,
                             aggregateType);

                    fencedLockManager.acquireLockAsync(lockName, new LockCallback() {
                        @Override
                        public void lockAcquired(FencedLock lock) {
                            log.info("[{}-{}] Acquired lock.",
                                     subscriberId,
                                     aggregateType);
                            active = true;
                            eventStoreSubscriptionObserver.lockAcquired(lock, ExclusiveInTransactionSubscription.this);

                            eventStore.localEventBus()
                                      .addSyncSubscriber(ExclusiveInTransactionSubscription.this::onEvent);

                        }

                        @Override
                        public void lockReleased(FencedLock lock) {
                            if (!active) {
                                return;
                            }
                            log.info("[{}-{}] Lock Released. Stopping subscription",
                                     subscriberId,
                                     aggregateType);
                            eventStoreSubscriptionObserver.lockReleased(lock, ExclusiveInTransactionSubscription.this);
                            try {
                                eventStore.localEventBus()
                                          .removeSyncSubscriber(ExclusiveInTransactionSubscription.this::onEvent);
                            } catch (Exception e) {
                                log.error(msg("[{}-{}] Failed to dispose subscription flux",
                                              subscriberId,
                                              aggregateType), e);
                            }

                            active = false;
                            log.info("[{}-{}] Stopped subscription",
                                     subscriberId,
                                     aggregateType);
                        }

                    });

                } else {
                    log.debug("[{}-{}] Subscription was already started",
                              subscriberId,
                              aggregateType);
                }
            }

            private void onEvent(Object e) {
                if (!(e instanceof PersistedEvents)) {
                    return;
                }

                var persistedEvents = (PersistedEvents) e;
                if (persistedEvents.commitStage == CommitStage.BeforeCommit || persistedEvents.commitStage == CommitStage.Flush) {
                    persistedEvents.events.stream()
                                          .filter(event -> event.aggregateType().equals(aggregateType))
                                          .forEach(event -> {
                                              log.trace("[{}-{}] (#{}) Received {} event with eventId '{}', aggregateId: '{}', eventOrder: {} during commit-stage '{}'",
                                                        subscriberId,
                                                        aggregateType,
                                                        event.globalEventOrder(),
                                                        event.event().getEventTypeOrName().toString(),
                                                        event.eventId(),
                                                        event.aggregateId(),
                                                        event.eventOrder(),
                                                        persistedEvents.commitStage
                                                       );
                                              try {
                                                  var handleEventTiming = StopWatch.start("handleEvent (" + subscriberId + ", " + aggregateType + ")");
                                                  eventHandler.handle(event, persistedEvents.unitOfWork);
                                                  eventStoreSubscriptionObserver.handleEvent(event,
                                                                                             eventHandler,
                                                                                             ExclusiveInTransactionSubscription.this,
                                                                                             handleEventTiming.stop().getDuration());
                                                  if (persistedEvents.commitStage == CommitStage.Flush) {
                                                      persistedEvents.unitOfWork.removeFlushedEventPersisted(event);
                                                  }
                                              } catch (Throwable cause) {
                                                  rethrowIfCriticalError(cause);
                                                  eventStoreSubscriptionObserver.handleEventFailed(event,
                                                                                                   eventHandler,
                                                                                                   cause,
                                                                                                   ExclusiveInTransactionSubscription.this);
                                                  onErrorHandlingEvent(event, cause);
                                              }
                                          });

                }
            }

            protected void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
                // TODO: Add better retry mechanism, poison event handling, etc.
                log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
                              subscriberId,
                              aggregateType,
                              e.globalEventOrder(),
                              e.event().getEventTypeOrName().getValue()), cause);
            }

            @Override
            public boolean isStarted() {
                return started;
            }

            @Override
            public void stop() {
                if (started) {
                    log.info("[{}-{}] Stopping subscription",
                             subscriberId,
                             aggregateType);
                    try {
                        log.debug("[{}-{}] Stopping subscription flux",
                                  subscriberId,
                                  aggregateType);
                        eventStore.localEventBus()
                                  .removeSyncSubscriber(ExclusiveInTransactionSubscription.this::onEvent);
                    } catch (Exception e) {
                        log.error(msg("[{}-{}] Failed to dispose subscription flux",
                                      subscriberId,
                                      aggregateType), e);
                    }
                    started = false;
                    log.info("[{}-{}] Stopped subscription",
                             subscriberId,
                             aggregateType);
                }
            }

            @Override
            public void unsubscribe() {
                log.info("[{}-{}] Initiating unsubscription",
                         subscriberId,
                         aggregateType);
                eventStoreSubscriptionObserver.unsubscribing(this);
                DefaultEventStoreSubscriptionManager.this.unsubscribe(this);
            }

            @Override
            public boolean isExclusive() {
                return true;
            }

            @Override
            public boolean isInTransaction() {
                return true;
            }

            @Override
            public void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder, Consumer<GlobalEventOrder> resetProcessor) {
                throw new EventStoreException(msg("[{}-{}] Reset of ResumePoint isn't support for an In-Transaction subscription",
                                                  subscriberId,
                                                  aggregateType));
            }

            @Override
            public Optional<SubscriptionResumePoint> currentResumePoint() {
                return Optional.empty();
            }

            @Override
            public Optional<Tenant> onlyIncludeEventsForTenant() {
                return onlyIncludeEventsForTenant;
            }

            @Override
            public boolean isActive() {
                return active;
            }

            @Override
            public String toString() {
                return "ExclusiveInTransactionSubscription{" +
                        "aggregateType=" + aggregateType +
                        ", subscriberId=" + subscriberId +
                        ", onlyIncludeEventsForTenant=" + onlyIncludeEventsForTenant +
                        ", started=" + started +
                        ", active=" + active +
                        '}';
            }

            @Override
            public void request(long n) {
                // NOP
            }
        }

        private class NonExclusiveInTransactionSubscription implements EventStoreSubscription {
            private final EventStore                         eventStore;
            private final AggregateType                      aggregateType;
            private final SubscriberId                       subscriberId;
            private final Optional<Tenant>                   onlyIncludeEventsForTenant;
            private final TransactionalPersistedEventHandler eventHandler;

            private volatile boolean started;

            public NonExclusiveInTransactionSubscription(EventStore eventStore,
                                                         AggregateType aggregateType,
                                                         SubscriberId subscriberId,
                                                         Optional<Tenant> onlyIncludeEventsForTenant,
                                                         TransactionalPersistedEventHandler eventHandler) {
                this.eventStore = requireNonNull(eventStore, "No eventStore provided");
                this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
                this.subscriberId = requireNonNull(subscriberId, "No subscriberId provided");
                this.onlyIncludeEventsForTenant = requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant provided");
                this.eventHandler = requireNonNull(eventHandler, "No eventHandler provided");

            }

            @Override
            public SubscriberId subscriberId() {
                return subscriberId;
            }

            @Override
            public AggregateType aggregateType() {
                return aggregateType;
            }

            @Override
            public void start() {
                if (!started) {
                    started = true;

                    log.info("[{}-{}] Starting subscription",
                             subscriberId,
                             aggregateType);

                    eventStore.localEventBus()
                              .addSyncSubscriber(this::onEvent);

                } else {
                    log.debug("[{}-{}] Subscription was already started",
                              subscriberId,
                              aggregateType);
                }
            }

            private void onEvent(Object e) {
                if (!(e instanceof PersistedEvents)) {
                    return;
                }

                var persistedEvents = (PersistedEvents) e;
                if (persistedEvents.commitStage == CommitStage.BeforeCommit || persistedEvents.commitStage == CommitStage.Flush) {
                    persistedEvents.events.stream()
                                          .filter(event -> event.aggregateType().equals(aggregateType))
                                          .forEach(event -> {
                                              log.trace("[{}-{}] (#{}) Received {} event with eventId '{}', aggregateId: '{}', eventOrder: {} during commit-stage '{}'",
                                                        subscriberId,
                                                        aggregateType,
                                                        event.globalEventOrder(),
                                                        event.event().getEventTypeOrName().toString(),
                                                        event.eventId(),
                                                        event.aggregateId(),
                                                        event.eventOrder(),
                                                        persistedEvents.commitStage
                                                       );
                                              try {
                                                  var handleEventTiming = StopWatch.start("handleEvent (" + subscriberId + ", " + aggregateType + ")");
                                                  eventHandler.handle(event, persistedEvents.unitOfWork);
                                                  eventStoreSubscriptionObserver.handleEvent(event,
                                                                                             eventHandler,
                                                                                             NonExclusiveInTransactionSubscription.this,
                                                                                             handleEventTiming.stop().getDuration());

                                                  if (persistedEvents.commitStage == CommitStage.Flush) {
                                                      persistedEvents.unitOfWork.removeFlushedEventPersisted(event);
                                                  }
                                              } catch (Throwable cause) {
                                                  rethrowIfCriticalError(cause);
                                                  eventStoreSubscriptionObserver.handleEventFailed(event,
                                                                                                   eventHandler,
                                                                                                   cause,
                                                                                                   NonExclusiveInTransactionSubscription.this);
                                                  onErrorHandlingEvent(event, cause);
                                              }
                                          });

                }
            }

            protected void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
                // TODO: Add better retry mechanism, poison event handling, etc.
                log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
                              subscriberId,
                              aggregateType,
                              e.globalEventOrder(),
                              e.event().getEventTypeOrName().getValue()), cause);
            }

            @Override
            public boolean isStarted() {
                return started;
            }

            @Override
            public void stop() {
                if (started) {
                    log.info("[{}-{}] Stopping subscription",
                             subscriberId,
                             aggregateType);
                    try {
                        log.debug("[{}-{}] Stopping subscription flux",
                                  subscriberId,
                                  aggregateType);
                        eventStore.localEventBus()
                                  .removeSyncSubscriber(this::onEvent);
                    } catch (Exception e) {
                        log.error(msg("[{}-{}] Failed to dispose subscription flux",
                                      subscriberId,
                                      aggregateType), e);
                    }
                    started = false;
                    log.info("[{}-{}] Stopped subscription",
                             subscriberId,
                             aggregateType);
                }
            }

            @Override
            public void unsubscribe() {
                log.info("[{}-{}] Initiating unsubscription",
                         subscriberId,
                         aggregateType);
                eventStoreSubscriptionObserver.unsubscribing(this);
                DefaultEventStoreSubscriptionManager.this.unsubscribe(this);
            }

            @Override
            public boolean isExclusive() {
                return false;
            }

            @Override
            public boolean isInTransaction() {
                return true;
            }

            @Override
            public void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder, Consumer<GlobalEventOrder> resetProcessor) {
                throw new EventStoreException(msg("[{}-{}] Reset of ResumePoint isn't support for an In-Transaction subscription",
                                                  subscriberId,
                                                  aggregateType));
            }

            @Override
            public Optional<SubscriptionResumePoint> currentResumePoint() {
                return Optional.empty();
            }

            @Override
            public Optional<Tenant> onlyIncludeEventsForTenant() {
                return onlyIncludeEventsForTenant;
            }

            @Override
            public boolean isActive() {
                return started;
            }

            @Override
            public String toString() {
                return "NonExclusiveInTransactionSubscription{" +
                        "aggregateType=" + aggregateType +
                        ", subscriberId=" + subscriberId +
                        ", onlyIncludeEventsForTenant=" + onlyIncludeEventsForTenant +
                        ", started=" + started +
                        '}';
            }

            @Override
            public void request(long n) {
                // NOP
            }
        }

        private class NonExclusiveAsynchronousSubscription implements EventStoreSubscription {
            private final EventStore                     eventStore;
            private final DurableSubscriptionRepository  durableSubscriptionRepository;
            private final AggregateType                  aggregateType;
            private final SubscriberId                   subscriberId;
            private final GlobalEventOrder               onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder;
            private final Optional<Tenant>               onlyIncludeEventsForTenant;
            private final PersistedEventHandler          eventHandler;
            private       SubscriptionResumePoint        resumePoint;
            private       BaseSubscriber<PersistedEvent> subscription;

            private volatile boolean started;

            public NonExclusiveAsynchronousSubscription(EventStore eventStore,
                                                        DurableSubscriptionRepository durableSubscriptionRepository,
                                                        AggregateType aggregateType,
                                                        SubscriberId subscriberId,
                                                        GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                        Optional<Tenant> onlyIncludeEventsForTenant,
                                                        PersistedEventHandler eventHandler) {
                this.eventStore = requireNonNull(eventStore, "No eventStore provided");
                this.durableSubscriptionRepository = requireNonNull(durableSubscriptionRepository, "No durableSubscriptionRepository provided");
                this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
                this.subscriberId = requireNonNull(subscriberId, "No subscriberId provided");
                this.onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder = requireNonNull(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                              "No onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder provided");
                this.onlyIncludeEventsForTenant = requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant provided");
                this.eventHandler = requireNonNull(eventHandler, "No eventHandler provided");
            }

            @Override
            public SubscriberId subscriberId() {
                return subscriberId;
            }

            @Override
            public AggregateType aggregateType() {
                return aggregateType;
            }

            @Override
            public void start() {
                if (!started) {
                    started = true;
                    log.info("[{}-{}] Looking up subscription resumePoint",
                             subscriberId,
                             aggregateType);
                    var resolveResumePointTiming = StopWatch.start("resolveResumePoint (" + subscriberId + ", " + aggregateType + ")");
                    resumePoint = durableSubscriptionRepository.getOrCreateResumePoint(subscriberId,
                                                                                       aggregateType,
                                                                                       onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);
                    log.info("[{}-{}] Starting subscription from globalEventOrder: {}",
                             subscriberId,
                             aggregateType,
                             resumePoint.getResumeFromAndIncluding());
                    eventStoreSubscriptionObserver.resolveResumePoint(resumePoint,
                                                                      onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                      NonExclusiveAsynchronousSubscription.this,
                                                                      resolveResumePointTiming.stop().getDuration());

                    subscription = new PersistedEventSubscriber(eventHandler,
                                                                this,
                                                                this::onErrorHandlingEvent,
                                                                eventStorePollingBatchSize,
                                                                eventStore);
                    eventStore.pollEvents(aggregateType,
                                          resumePoint.getResumeFromAndIncluding(),
                                          Optional.of(eventStorePollingBatchSize),
                                          Optional.of(eventStorePollingInterval),
                                          onlyIncludeEventsForTenant,
                                          Optional.of(subscriberId))
                              .limitRate(eventStorePollingBatchSize)
                              .subscribe(subscription);
                } else {
                    log.debug("[{}-{}] Subscription was already started",
                              subscriberId,
                              aggregateType);
                }
            }

            @Override
            public void request(long n) {
                if (!started) {
                    log.warn("[{}-{}] Cannot request {} event(s) as the subscriber isn't active",
                             subscriberId,
                             aggregateType,
                             n);
                    return;
                }
                log.trace("[{}-{}] Requesting {} event(s)",
                          subscriberId,
                          aggregateType,
                          n);
                eventStoreSubscriptionObserver.requestingEvents(n, this);
                subscription.request(n);
            }

            /**
             * The error handler called for any non-retryable Exceptions (as specified by the {@link RetryBackoffSpec})<br>
             * <b>Note: Default behaviour needs to at least request one more event</b><br>
             * Similar to:
             * <pre>{@code
             * void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
             *      log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
             *                      subscriberId,
             *                      aggregateType,
             *                      e.globalEventOrder(),
             *                      e.event().getEventTypeOrName().getValue()), cause);
             *      log.trace("[{}-{}] (#{}) Requesting 1 event from the EventStore",
             *                  subscriberId(),
             *                  aggregateType(),
             *                  e.globalEventOrder()
             *                  );
             *      eventStoreSubscription.request(1);
             * }
             * }</pre>
             *
             * @param e     the event that failed
             * @param cause the cause of the failure
             */
            protected void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
                log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
                              subscriberId,
                              aggregateType,
                              e.globalEventOrder(),
                              e.event().getEventTypeOrName().getValue()), cause);
                log.trace("[{}-{}] (#{}) Requesting 1 event from the EventStore",
                          subscriberId(),
                          aggregateType(),
                          e.globalEventOrder()
                         );
                request(1);
            }

            @Override
            public boolean isStarted() {
                return started;
            }

            @Override
            public void stop() {
                if (started) {
                    log.info("[{}-{}] Stopping subscription",
                             subscriberId,
                             aggregateType);
                    try {
                        log.debug("[{}-{}] Stopping subscription flux",
                                  subscriberId,
                                  aggregateType);
                        subscription.dispose();
                    } catch (Exception e) {
                        log.error(msg("[{}-{}] Failed to dispose subscription flux",
                                      subscriberId,
                                      aggregateType), e);
                    }
                    try {
                        // Allow the reactive components to complete
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // Ignore
                        Thread.currentThread().interrupt();
                    }
                    // Save resume point to be the next global order event
                    log.debug("[{}-{}] Storing ResumePoint with resumeFromAndIncluding {}",
                              subscriberId,
                              aggregateType,
                              resumePoint.getResumeFromAndIncluding());

                    durableSubscriptionRepository.saveResumePoint(resumePoint);
                    started = false;
                    log.info("[{}-{}] Stopped subscription",
                             subscriberId,
                             aggregateType);
                }
            }

            @Override
            public void unsubscribe() {
                log.info("[{}-{}] Initiating unsubscription",
                         subscriberId,
                         aggregateType);
                eventStoreSubscriptionObserver.unsubscribing(this);
                DefaultEventStoreSubscriptionManager.this.unsubscribe(this);
            }

            @Override
            public boolean isExclusive() {
                return false;
            }

            @Override
            public boolean isInTransaction() {
                return false;
            }

            @Override
            public void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder, Consumer<GlobalEventOrder> resetProcessor) {
                requireNonNull(subscribeFromAndIncludingGlobalOrder, "subscribeFromAndIncludingGlobalOrder must not be null");
                requireNonNull(resetProcessor, "resetProcessor must not be null");

                eventStoreSubscriptionObserver.resettingFrom(subscribeFromAndIncludingGlobalOrder, this);
                if (started) {
                    log.info("[{}-{}] Resetting resume point and re-starts the subscriber from and including globalOrder {}",
                             subscriberId,
                             aggregateType,
                             subscribeFromAndIncludingGlobalOrder);
                    stop();
                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                    resetProcessor.accept(subscribeFromAndIncludingGlobalOrder);
                    start();
                } else {
                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                    resetProcessor.accept(subscribeFromAndIncludingGlobalOrder);
                }
            }

            private void overrideResumePoint(GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
                requireNonNull(subscribeFromAndIncludingGlobalOrder, "No subscribeFromAndIncludingGlobalOrder value provided");
                // Override resume point
                log.info("[{}-{}] Overriding resume point to start from-and-including-globalOrder {}",
                         subscriberId,
                         aggregateType,
                         subscribeFromAndIncludingGlobalOrder);
                resumePoint.setResumeFromAndIncluding(subscribeFromAndIncludingGlobalOrder);
                durableSubscriptionRepository.saveResumePoint(resumePoint);
                try {
                    eventHandler.onResetFrom(this, subscribeFromAndIncludingGlobalOrder);
                } catch (Exception e) {
                    log.info(msg("[{}-{}] Failed to reset eventHandler '{}' to use start from-and-including-globalOrder {}",
                                 subscriberId,
                                 aggregateType,
                                 eventHandler,
                                 subscribeFromAndIncludingGlobalOrder),
                             e);
                }
            }

            @Override
            public Optional<SubscriptionResumePoint> currentResumePoint() {
                return Optional.ofNullable(resumePoint);
            }

            @Override
            public Optional<Tenant> onlyIncludeEventsForTenant() {
                return onlyIncludeEventsForTenant;
            }

            @Override
            public boolean isActive() {
                return started;
            }

            @Override
            public String toString() {
                return "NonExclusiveAsynchronousSubscription{" +
                        "aggregateType=" + aggregateType +
                        ", subscriberId=" + subscriberId +
                        ", onlyIncludeEventsForTenant=" + onlyIncludeEventsForTenant +
                        ", resumePoint=" + resumePoint +
                        ", started=" + started +
                        '}';
            }
        }

        private class ExclusiveAsynchronousSubscription implements EventStoreSubscription {
            private final EventStore                                eventStore;
            private final FencedLockManager                         fencedLockManager;
            private final DurableSubscriptionRepository             durableSubscriptionRepository;
            private final AggregateType                             aggregateType;
            private final SubscriberId                              subscriberId;
            private final Function<AggregateType, GlobalEventOrder> onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder;
            private final Optional<Tenant>                          onlyIncludeEventsForTenant;
            private final FencedLockAwareSubscriber                 fencedLockAwareSubscriber;
            private final PersistedEventHandler                     eventHandler;
            private final LockName                                  lockName;

            private SubscriptionResumePoint        resumePoint;
            private BaseSubscriber<PersistedEvent> subscription;

            private volatile boolean started;
            private volatile boolean active;

            public ExclusiveAsynchronousSubscription(EventStore eventStore,
                                                     FencedLockManager fencedLockManager,
                                                     DurableSubscriptionRepository durableSubscriptionRepository,
                                                     AggregateType aggregateType,
                                                     SubscriberId subscriberId,
                                                     Function<AggregateType, GlobalEventOrder> onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                     Optional<Tenant> onlyIncludeEventsForTenant,
                                                     FencedLockAwareSubscriber fencedLockAwareSubscriber,
                                                     PersistedEventHandler eventHandler) {
                this.eventStore = requireNonNull(eventStore, "No eventStore provided");
                this.fencedLockManager = requireNonNull(fencedLockManager, "No fencedLockManager provided");
                this.durableSubscriptionRepository = requireNonNull(durableSubscriptionRepository, "No durableSubscriptionRepository provided");
                this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
                this.subscriberId = requireNonNull(subscriberId, "No subscriberId provided");
                this.onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder = requireNonNull(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                              "No onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder provided");
                this.onlyIncludeEventsForTenant = requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant provided");
                this.fencedLockAwareSubscriber = requireNonNull(fencedLockAwareSubscriber, "No fencedLockAwareSubscriber provided");
                this.eventHandler = requireNonNull(eventHandler, "No eventHandler provided");
                lockName = LockName.of(msg("[{}-{}]", subscriberId, aggregateType));
            }

            @Override
            public SubscriberId subscriberId() {
                return subscriberId;
            }

            @Override
            public AggregateType aggregateType() {
                return aggregateType;
            }

            @Override
            public void start() {
                if (!started) {
                    started = true;
                    log.info("[{}-{}] Started subscriber",
                             subscriberId,
                             aggregateType);

                    fencedLockManager.acquireLockAsync(lockName, new LockCallback() {
                        @Override
                        public void lockAcquired(FencedLock lock) {
                            log.info("[{}-{}] Acquired lock. Looking up subscription resumePoint",
                                     subscriberId,
                                     aggregateType);
                            active = true;
                            eventStoreSubscriptionObserver.lockAcquired(lock, ExclusiveAsynchronousSubscription.this);


                            var resolveResumePointTiming = StopWatch.start("resolveResumePoint (" + subscriberId + ", " + aggregateType + ")");
                            resumePoint = durableSubscriptionRepository.getOrCreateResumePoint(subscriberId,
                                                                                               aggregateType,
                                                                                               onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);
                            log.info("[{}-{}] Starting subscription from globalEventOrder: {}",
                                     subscriberId,
                                     aggregateType,
                                     resumePoint.getResumeFromAndIncluding());
                            eventStoreSubscriptionObserver.resolveResumePoint(resumePoint,
                                                                              onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder.apply(aggregateType),
                                                                              ExclusiveAsynchronousSubscription.this,
                                                                              resolveResumePointTiming.stop().getDuration());

                            try {
                                fencedLockAwareSubscriber.onLockAcquired(lock, resumePoint);
                            } catch (Exception e) {
                                log.error(msg("FencedLockAwareSubscriber#onLockAcquired failed for lock {} and resumePoint {}", lock.getName(), resumePoint), e);
                            }

                            subscription = new PersistedEventSubscriber(eventHandler,
                                                                        ExclusiveAsynchronousSubscription.this,
                                                                        ExclusiveAsynchronousSubscription.this::onErrorHandlingEvent,
                                                                        eventStorePollingBatchSize,
                                                                        eventStore);

                            eventStore.pollEvents(aggregateType,
                                                  resumePoint.getResumeFromAndIncluding(),
                                                  Optional.of(eventStorePollingBatchSize),
                                                  Optional.of(eventStorePollingInterval),
                                                  onlyIncludeEventsForTenant,
                                                  Optional.of(subscriberId))
                                      .limitRate(eventStorePollingBatchSize)
                                      .subscribe(subscription);
                        }

                        @Override
                        public void lockReleased(FencedLock lock) {
                            if (!active) {
                                return;
                            }
                            log.info("[{}-{}] Lock Released. Stopping subscription",
                                     subscriberId,
                                     aggregateType);
                            try {
                                eventStoreSubscriptionObserver.lockReleased(lock, ExclusiveAsynchronousSubscription.this);
                                if (subscription != null) {
                                    log.debug("[{}-{}] Stopping subscription flux",
                                              subscriberId,
                                              aggregateType);
                                    subscription.dispose();
                                    subscription = null;
                                } else {
                                    log.debug("[{}-{}] Didn't find a subscription flux to dispose",
                                              subscriberId,
                                              aggregateType);
                                }
                            } catch (Exception e) {
                                log.error(msg("[{}-{}] Failed to dispose subscription flux",
                                              subscriberId,
                                              aggregateType), e);
                            }

                            try {
                                fencedLockAwareSubscriber.onLockReleased(lock);
                            } catch (Exception e) {
                                log.error(msg("FencedLockAwareSubscriber#onLockReleased failed for lock {}", lock.getName()), e);
                            }

                            try {
                                // Allow the reactive components to complete
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                // Ignore
                                Thread.currentThread().interrupt();
                            }

                            // Save resume point to be the next global order event AFTER the one we know we just handled
                            log.info("[{}-{}] Storing ResumePoint with resumeFromAndIncluding {}",
                                     subscriberId,
                                     aggregateType,
                                     resumePoint.getResumeFromAndIncluding());

                            durableSubscriptionRepository.saveResumePoint(resumePoint);
                            active = false;
                            log.info("[{}-{}] Stopped subscription",
                                     subscriberId,
                                     aggregateType);
                        }

                    });
                } else {
                    log.debug("[{}-{}] Subscription was already started",
                              subscriberId,
                              aggregateType);
                }
            }

            @Override
            public void request(long n) {
                if (!started) {
                    log.warn("[{}-{}] Cannot request {} event(s) as the subscriber isn't active",
                             subscriberId,
                             aggregateType,
                             n);
                    return;
                }
                if (!fencedLockManager.isLockedByThisLockManagerInstance(lockName)) {
                    log.warn("[{}-{}] Cannot request {} event(s) as the subscriber hasn't acquired the lock",
                             subscriberId,
                             aggregateType,
                             n);
                    return;
                }
                if (subscription == null) {
                    log.info("[{}-{}] Cannot request {} event(s) as the subscriber is null - the exclusive subscription is shutting down",
                             subscriberId,
                             aggregateType,
                             n);
                    return;
                }

                log.trace("[{}-{}] Requesting {} event(s)",
                          subscriberId,
                          aggregateType,
                          n);
                eventStoreSubscriptionObserver.requestingEvents(n, this);
                subscription.request(n);
            }

            /**
             * The error handler called for any non-retryable Exceptions (as specified by the {@link RetryBackoffSpec})<br><br>
             * <b>Note: Default behaviour needs to at least request one more event</b><br>
             * Similar to:
             * <pre>{@code
             * void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
             *      log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
             *                      subscriberId,
             *                      aggregateType,
             *                      e.globalEventOrder(),
             *                      e.event().getEventTypeOrName().getValue()), cause);
             *      log.trace("[{}-{}] (#{}) Requesting 1 event from the EventStore",
             *                  subscriberId(),
             *                  aggregateType(),
             *                  e.globalEventOrder()
             *                  );
             *      eventStoreSubscription.request(1);
             * }
             * }</pre>
             *
             * @param e     the event that failed
             * @param cause the cause of the failure
             */
            protected void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
                log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
                              subscriberId,
                              aggregateType,
                              e.globalEventOrder(),
                              e.event().getEventTypeOrName().getValue()), cause);
                log.trace("[{}-{}] (#{}) Requesting 1 event from the EventStore",
                          subscriberId(),
                          aggregateType(),
                          e.globalEventOrder()
                         );
                request(1);
            }

            @Override
            public boolean isStarted() {
                return started;
            }

            @Override
            public void stop() {
                if (started) {
                    fencedLockManager.cancelAsyncLockAcquiring(lockName);
                    started = false;
                }
            }

            @Override
            public void unsubscribe() {
                log.info("[{}-{}] Initiating unsubscription",
                         subscriberId,
                         aggregateType);
                eventStoreSubscriptionObserver.unsubscribing(this);
                DefaultEventStoreSubscriptionManager.this.unsubscribe(this);
            }

            @Override
            public boolean isExclusive() {
                return true;
            }

            @Override
            public boolean isInTransaction() {
                return false;
            }

            @Override
            public void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder, Consumer<GlobalEventOrder> resetProcessor) {
                requireNonNull(subscribeFromAndIncludingGlobalOrder, "subscribeFromAndIncludingGlobalOrder must not be null");
                requireNonNull(resetProcessor, "resetProcessor must not be null");

                eventStoreSubscriptionObserver.resettingFrom(subscribeFromAndIncludingGlobalOrder, this);
                if (isStarted() && isActive()) {
                    log.info("[{}-{}] Resetting resume point and re-starts the subscriber from and including globalOrder {}",
                             subscriberId,
                             aggregateType,
                             subscribeFromAndIncludingGlobalOrder);
                    stop();
                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                    resetProcessor.accept(subscribeFromAndIncludingGlobalOrder);
                    start();
                } else {
                    log.info("[{}-{}] Cannot reset resume point to fromAndIncluding {} because the underlying lock hasn't been acquired. isStarted: {}, isActive (is-lock-acquired): {}",
                             subscriberId,
                             aggregateType,
                             subscribeFromAndIncludingGlobalOrder,
                             isStarted(),
                             isActive());

                }
            }

            private void overrideResumePoint(GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
                requireNonNull(subscribeFromAndIncludingGlobalOrder, "No subscribeFromAndIncludingGlobalOrder value provided");
                // Override resume point
                log.info("[{}-{}] Overriding resume point to start from-and-including-globalOrder {}",
                         subscriberId,
                         aggregateType,
                         subscribeFromAndIncludingGlobalOrder);
                resumePoint.setResumeFromAndIncluding(subscribeFromAndIncludingGlobalOrder);
                durableSubscriptionRepository.saveResumePoint(resumePoint);
                try {
                    eventHandler.onResetFrom(this, subscribeFromAndIncludingGlobalOrder);
                } catch (Exception e) {
                    log.info(msg("[{}-{}] Failed to reset eventHandler '{}' to use start from-and-including-globalOrder {}",
                                 subscriberId,
                                 aggregateType,
                                 eventHandler,
                                 subscribeFromAndIncludingGlobalOrder),
                             e);
                }
            }

            @Override
            public Optional<SubscriptionResumePoint> currentResumePoint() {
                return Optional.ofNullable(resumePoint);
            }

            @Override
            public Optional<Tenant> onlyIncludeEventsForTenant() {
                return onlyIncludeEventsForTenant;
            }

            @Override
            public boolean isActive() {
                return active;
            }

            @Override
            public String toString() {
                return "ExclusiveAsynchronousSubscription{" +
                        "aggregateType=" + aggregateType +
                        ", subscriberId=" + subscriberId +
                        ", onlyIncludeEventsForTenant=" + onlyIncludeEventsForTenant +
                        ", lockName=" + lockName +
                        ", resumePoint=" + resumePoint +
                        ", started=" + started +
                        ", active=" + active +
                        '}';
            }
        }

        private class NonExclusiveBatchedAsynchronousSubscription implements EventStoreSubscription {
            private final EventStore                     eventStore;
            private final DurableSubscriptionRepository  durableSubscriptionRepository;
            private final AggregateType                  aggregateType;
            private final SubscriberId                   subscriberId;
            private final GlobalEventOrder               onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder;
            private final Optional<Tenant>               onlyIncludeEventsForTenant;
            private final int                            maxBatchSize;
            private final Duration                       maxLatency;
            private final BatchedPersistedEventHandler   eventHandler;
            private       SubscriptionResumePoint        resumePoint;
            private       BaseSubscriber<PersistedEvent> subscription;

            private volatile boolean started;

            public NonExclusiveBatchedAsynchronousSubscription(EventStore eventStore,
                                                               DurableSubscriptionRepository durableSubscriptionRepository,
                                                               AggregateType aggregateType,
                                                               SubscriberId subscriberId,
                                                               GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                               Optional<Tenant> onlyIncludeEventsForTenant,
                                                               int maxBatchSize,
                                                               Duration maxLatency,
                                                               BatchedPersistedEventHandler eventHandler) {
                this.eventStore = requireNonNull(eventStore, "No eventStore provided");
                this.durableSubscriptionRepository = requireNonNull(durableSubscriptionRepository, "No durableSubscriptionRepository provided");
                this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
                this.subscriberId = requireNonNull(subscriberId, "No subscriberId provided");
                this.onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder = requireNonNull(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                                              "No onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder provided");
                this.onlyIncludeEventsForTenant = requireNonNull(onlyIncludeEventsForTenant, "No onlyIncludeEventsForTenant provided");

                requireTrue(maxBatchSize > 0, "maxBatchSize must be greater than 0");
                this.maxBatchSize = maxBatchSize;
                this.maxLatency = requireNonNull(maxLatency, "No maxLatency provided");
                this.eventHandler = requireNonNull(eventHandler, "No eventHandler provided");
            }

            @Override
            public SubscriberId subscriberId() {
                return subscriberId;
            }

            @Override
            public AggregateType aggregateType() {
                return aggregateType;
            }

            @Override
            public void start() {
                if (!started) {
                    started = true;
                    log.info("[{}-{}] Looking up subscription resumePoint",
                             subscriberId,
                             aggregateType);
                    var resolveResumePointTiming = StopWatch.start("resolveResumePoint (" + subscriberId + ", " + aggregateType + ")");
                    resumePoint = durableSubscriptionRepository.getOrCreateResumePoint(subscriberId,
                                                                                       aggregateType,
                                                                                       onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);
                    log.info("[{}-{}] Starting subscription from globalEventOrder: {}",
                             subscriberId,
                             aggregateType,
                             resumePoint.getResumeFromAndIncluding());
                    eventStoreSubscriptionObserver.resolveResumePoint(resumePoint,
                                                                      onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                      DefaultEventStoreSubscriptionManager.NonExclusiveBatchedAsynchronousSubscription.this,
                                                                      resolveResumePointTiming.stop().getDuration());

                    subscription = new BatchedPersistedEventSubscriber(
                            eventHandler,
                            this,
                            this::onErrorHandlingEvent,
                            eventStorePollingBatchSize,
                            eventStore,
                            maxBatchSize,
                            maxLatency);
                    eventStore.pollEvents(aggregateType,
                                          resumePoint.getResumeFromAndIncluding(),
                                          Optional.of(eventStorePollingBatchSize),
                                          Optional.of(eventStorePollingInterval),
                                          onlyIncludeEventsForTenant,
                                          Optional.of(subscriberId))
                              .limitRate(eventStorePollingBatchSize)
                              .subscribe(subscription);
                } else {
                    log.debug("[{}-{}] Subscription was already started",
                              subscriberId,
                              aggregateType);
                }
            }

            @Override
            public void request(long n) {
                if (!started) {
                    log.warn("[{}-{}] Cannot request {} event(s) as the subscriber isn't active",
                             subscriberId,
                             aggregateType,
                             n);
                    return;
                }
                log.trace("[{}-{}] Requesting {} event(s)",
                          subscriberId,
                          aggregateType,
                          n);
                eventStoreSubscriptionObserver.requestingEvents(n, this);
                subscription.request(n);
            }

            /**
             * The error handler called for any non-retryable Exceptions (as specified by the {@link RetryBackoffSpec})<br>
             * <b>Note: Default behaviour needs to at least request one more event</b><br>
             * Similar to:
             * <pre>{@code
             * void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
             *      log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
             *                      subscriberId,
             *                      aggregateType,
             *                      e.globalEventOrder(),
             *                      e.event().getEventTypeOrName().getValue()), cause);
             *      log.trace("[{}-{}] (#{}) Requesting 1 event from the EventStore",
             *                  subscriberId(),
             *                  aggregateType(),
             *                  e.globalEventOrder()
             *                  );
             *      eventStoreSubscription.request(1);
             * }
             * }</pre>
             *
             * @param e     the event that failed
             * @param cause the cause of the failure
             */
            /**
             * Error handler for batched event processing
             */
            protected void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
                log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
                              subscriberId,
                              aggregateType,
                              e.globalEventOrder(),
                              e.event().getEventTypeOrName().getValue()), cause);
                log.trace("[{}-{}] (#{}) Requesting 1 event from the EventStore",
                          subscriberId(),
                          aggregateType(),
                          e.globalEventOrder()
                         );
                request(1);
            }

            @Override
            public boolean isStarted() {
                return started;
            }

            @Override
            public void stop() {
                if (started) {
                    log.info("[{}-{}] Stopping subscription",
                             subscriberId,
                             aggregateType);
                    try {
                        log.debug("[{}-{}] Stopping subscription flux",
                                  subscriberId,
                                  aggregateType);
                        subscription.dispose();
                    } catch (Exception e) {
                        log.error(msg("[{}-{}] Failed to dispose subscription flux",
                                      subscriberId,
                                      aggregateType), e);
                    }
                    try {
                        // Allow the reactive components to complete
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // Ignore
                        Thread.currentThread().interrupt();
                    }
                    // Save resume point to be the next global order event
                    log.debug("[{}-{}] Storing ResumePoint with resumeFromAndIncluding {}",
                              subscriberId,
                              aggregateType,
                              resumePoint.getResumeFromAndIncluding());

                    durableSubscriptionRepository.saveResumePoint(resumePoint);
                    started = false;
                    log.info("[{}-{}] Stopped subscription",
                             subscriberId,
                             aggregateType);
                }
            }

            @Override
            public void unsubscribe() {
                log.info("[{}-{}] Initiating unsubscription",
                         subscriberId,
                         aggregateType);
                eventStoreSubscriptionObserver.unsubscribing(this);
                DefaultEventStoreSubscriptionManager.this.unsubscribe(this);
            }

            @Override
            public boolean isExclusive() {
                return false;
            }

            @Override
            public boolean isInTransaction() {
                return false;
            }

            @Override
            public void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder, Consumer<GlobalEventOrder> resetProcessor) {
                requireNonNull(subscribeFromAndIncludingGlobalOrder, "subscribeFromAndIncludingGlobalOrder must not be null");
                requireNonNull(resetProcessor, "resetProcessor must not be null");

                eventStoreSubscriptionObserver.resettingFrom(subscribeFromAndIncludingGlobalOrder, this);
                if (started) {
                    log.info("[{}-{}] Resetting resume point and re-starts the subscriber from and including globalOrder {}",
                             subscriberId,
                             aggregateType,
                             subscribeFromAndIncludingGlobalOrder);
                    stop();
                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                    resetProcessor.accept(subscribeFromAndIncludingGlobalOrder);
                    start();
                } else {
                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                    resetProcessor.accept(subscribeFromAndIncludingGlobalOrder);
                }
            }

            private void overrideResumePoint(GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
                requireNonNull(subscribeFromAndIncludingGlobalOrder, "No subscribeFromAndIncludingGlobalOrder value provided");
                // Override resume point
                log.info("[{}-{}] Overriding resume point to start from-and-including-globalOrder {}",
                         subscriberId,
                         aggregateType,
                         subscribeFromAndIncludingGlobalOrder);
                resumePoint.setResumeFromAndIncluding(subscribeFromAndIncludingGlobalOrder);
                durableSubscriptionRepository.saveResumePoint(resumePoint);
                try {
                    eventHandler.onResetFrom(this, subscribeFromAndIncludingGlobalOrder);
                } catch (Exception e) {
                    log.info(msg("[{}-{}] Failed to reset eventHandler '{}' to use start from-and-including-globalOrder {}",
                                 subscriberId,
                                 aggregateType,
                                 eventHandler,
                                 subscribeFromAndIncludingGlobalOrder),
                             e);
                }
            }

            @Override
            public Optional<SubscriptionResumePoint> currentResumePoint() {
                return Optional.ofNullable(resumePoint);
            }

            @Override
            public Optional<Tenant> onlyIncludeEventsForTenant() {
                return onlyIncludeEventsForTenant;
            }

            @Override
            public boolean isActive() {
                return started;
            }

            @Override
            public String toString() {
                return "NonExclusiveBatchedAsynchronousSubscription{" +
                        "aggregateType=" + aggregateType +
                        ", subscriberId=" + subscriberId +
                        ", onlyIncludeEventsForTenant=" + onlyIncludeEventsForTenant +
                        ", resumePoint=" + resumePoint +
                        ", started=" + started +
                        '}';
            }


        }

        /**
         * Generic {@link BaseSubscriber} which forwards to the provided {@link PersistedEventHandler}
         * with backpressure and handles indefinite retries in relation to {@link IOExceptionUtil#isIOException(Throwable)},
         * if using constructor without any specified {@link RetryBackoffSpec}, updates {@link SubscriptionResumePoint} after each event handled
         * and delegates any non-retryable Exceptions (as specified by the {@link RetryBackoffSpec}) to the provided <code>onErrorHandler</code>,
         * which is responsible for error handling and for calling {@link EventStoreSubscription#request(long)} to continue event processing
         */
        class PersistedEventSubscriber extends BaseSubscriber<PersistedEvent> {
            private static final Logger log = LoggerFactory.getLogger(PersistedEventSubscriber.class);

            private final PersistedEventHandler                 eventHandler;
            private final EventStoreSubscription                eventStoreSubscription;
            private final BiConsumer<PersistedEvent, Throwable> onErrorHandler;
            private final RetryBackoffSpec                      forwardToEventHandlerRetryBackoffSpec;
            private final long                                  eventStorePollingBatchSize;
            private final EventStore                            eventStore;

            /**
             * Subscribe with indefinite retries in relation to Exceptions where {@link IOExceptionUtil#isIOException(Throwable)} return true
             *
             * @param eventHandler               The event handler that {@link PersistedEvent}'s are forwarded to
             * @param eventStoreSubscription     the {@link EventStoreSubscription} (as created by {@link EventStoreSubscriptionManager})
             * @param onErrorHandler             The error handler called for any non-retryable Exceptions (as specified by the {@link RetryBackoffSpec})<br>
             *                                   <b>Note: Default behaviour needs to at least request one more event</b><br>
             *                                   Similar to:
             *                                   <pre>{@code
             *                                   void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
             *                                        log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
             *                                                        subscriberId,
             *                                                        aggregateType,
             *                                                        e.globalEventOrder(),
             *                                                        e.event().getEventTypeOrName().getValue()), cause);
             *                                        log.trace("[{}-{}] (#{}) Requesting 1 event from the EventStore",
             *                                                    subscriberId(),
             *                                                    aggregateType(),
             *                                                    e.globalEventOrder()
             *                                                    );
             *                                        eventStoreSubscription.request(1);
             *                                   }
             *                                   }</pre>
             * @param eventStorePollingBatchSize The batch size used when polling events from the {@link EventStore}
             * @param eventStore                 The {@link EventStore} to use
             */
            public PersistedEventSubscriber(PersistedEventHandler eventHandler,
                                            EventStoreSubscription eventStoreSubscription,
                                            BiConsumer<PersistedEvent, Throwable> onErrorHandler,
                                            long eventStorePollingBatchSize,
                                            EventStore eventStore) {
                this(eventHandler,
                     eventStoreSubscription,
                     onErrorHandler,
                     Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(100)) // Initial delay of 100ms
                          .maxBackoff(Duration.ofSeconds(1)) // Maximum backoff of 1 second
                          .jitter(0.5)
                          .filter(IOExceptionUtil::isIOException),
                     eventStorePollingBatchSize,
                     eventStore);
            }

            /**
             * Subscribe with custom {@link RetryBackoffSpec}
             *
             * @param eventHandler                          The event handler that {@link PersistedEvent}'s are forwarded to
             * @param eventStoreSubscription                the {@link EventStoreSubscription} (as created by {@link EventStoreSubscriptionManager})
             * @param onErrorHandler                        The error handler called for any non-retryable Exceptions (as specified by the {@link RetryBackoffSpec})<br>
             *                                              <b>Note: Default behaviour needs to at least request one more event</b><br>
             *                                              Similar to:
             *                                              <pre>{@code
             *                                              void onErrorHandlingEvent(PersistedEvent e, Throwable cause) {
             *                                                   log.error(msg("[{}-{}] (#{}) Skipping {} event because of error",
             *                                                                   subscriberId,
             *                                                                   aggregateType,
             *                                                                   e.globalEventOrder(),
             *                                                                   e.event().getEventTypeOrName().getValue()), cause);
             *                                                   log.trace("[{}-{}] (#{}) Requesting 1 event from the EventStore",
             *                                                               subscriberId(),
             *                                                               aggregateType(),
             *                                                               e.globalEventOrder()
             *                                                               );
             *                                                   eventStoreSubscription.request(1);
             *                                              }
             *                                              }</pre>
             * @param forwardToEventHandlerRetryBackoffSpec The {@link RetryBackoffSpec} used.<br>
             *                                              Example:
             *                                              <pre>{@code
             *                                              Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(100)) // Initial delay of 100ms
             *                                                   .maxBackoff(Duration.ofSeconds(1)) // Maximum backoff of 1 second
             *                                                   .jitter(0.5)
             *                                                   .filter(IOExceptionUtil::isIOException)
             *                                              }
             *                                              </pre>
             * @param eventStorePollingBatchSize            The batch size used when polling events from the {@link EventStore}
             * @param eventStore                            The {@link EventStore} to use
             */
            public PersistedEventSubscriber(PersistedEventHandler eventHandler,
                                            EventStoreSubscription eventStoreSubscription,
                                            BiConsumer<PersistedEvent, Throwable> onErrorHandler,
                                            RetryBackoffSpec forwardToEventHandlerRetryBackoffSpec,
                                            long eventStorePollingBatchSize,
                                            EventStore eventStore) {
                this.eventHandler = requireNonNull(eventHandler, "No eventHandler provided");
                this.eventStoreSubscription = requireNonNull(eventStoreSubscription, "No eventStoreSubscription provided");
                this.onErrorHandler = requireNonNull(onErrorHandler, "No errorHandler provided");
                this.forwardToEventHandlerRetryBackoffSpec = requireNonNull(forwardToEventHandlerRetryBackoffSpec, "No retryBackoffSpec provided");
                this.eventStorePollingBatchSize = eventStorePollingBatchSize;
                this.eventStore = requireNonNull(eventStore, "No eventStore provided");
                // Verify that the provided eventStoreSubscription supports resume-points
                eventStoreSubscription.currentResumePoint().orElseThrow(() -> new IllegalArgumentException(msg("The provided {} doesn't support resume-points", eventStoreSubscription.getClass().getName())));
            }

            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                log.debug("[{}-{}] On Subscribe with eventStorePollingBatchSize {}",
                          eventStoreSubscription.subscriberId(),
                          eventStoreSubscription.aggregateType(),
                          eventStorePollingBatchSize
                         );
                eventStoreSubscription.request(eventStorePollingBatchSize);
            }

            @Override
            protected void hookOnNext(PersistedEvent e) {
                Mono.fromCallable(() -> {
                        log.trace("[{}-{}] (#{}) Forwarding {} event with eventId '{}', aggregateId: '{}', eventOrder: {} to EventHandler",
                                  eventStoreSubscription.subscriberId(),
                                  eventStoreSubscription.aggregateType(),
                                  e.globalEventOrder(),
                                  e.event().getEventTypeOrName().toString(),
                                  e.eventId(),
                                  e.aggregateId(),
                                  e.eventOrder()
                                 );
                        return eventStore.getUnitOfWorkFactory()
                                         .withUnitOfWork(unitOfWork -> {
                                             var handleEventTiming = StopWatch.start("handleEvent (" + eventStoreSubscription.subscriberId() + ", " + eventStoreSubscription.aggregateType() + ")");
                                             var result            = eventHandler.handleWithBackPressure(e);
                                             eventStore.getEventStoreSubscriptionObserver().handleEvent(e,
                                                                                                        eventHandler,
                                                                                                        eventStoreSubscription,
                                                                                                        handleEventTiming.stop().getDuration()
                                                                                                       );
                                             return result;
                                         });
                    })
                    .retryWhen(forwardToEventHandlerRetryBackoffSpec
                                       .doBeforeRetry(retrySignal -> {
                                           log.trace("[{}-{}] (#{}) Ready to perform {} attempt retry of {} event with eventId '{}', aggregateId: '{}', eventOrder: {} to EventHandler",
                                                     eventStoreSubscription.subscriberId(),
                                                     eventStoreSubscription.aggregateType(),
                                                     e.globalEventOrder(),
                                                     retrySignal.totalRetries() + 1,
                                                     e.event().getEventTypeOrName().getValue(),
                                                     e.eventId(),
                                                     e.aggregateId(),
                                                     e.eventOrder()
                                                    );

                                       })
                                       .doAfterRetry(retrySignal -> {
                                           log.debug("[{}-{}] (#{}) {} {} retry of {} event with eventId '{}', aggregateId: '{}', eventOrder: {} to EventHandler",
                                                     eventStoreSubscription.subscriberId(),
                                                     eventStoreSubscription.aggregateType(),
                                                     e.globalEventOrder(),
                                                     retrySignal.failure() != null ? "Failed" : "Succeeded",
                                                     retrySignal.totalRetries(),
                                                     e.event().getEventTypeOrName().getValue(),
                                                     e.eventId(),
                                                     e.aggregateId(),
                                                     e.eventOrder(),
                                                     retrySignal.failure()
                                                    );
                                       }))
                    .doFinally(signalType -> {
                        eventStoreSubscription.currentResumePoint().get().setResumeFromAndIncluding(e.globalEventOrder().increment());
                    })
                    .subscribe(requestSize -> {
                                   if (requestSize < 0) {
                                       requestSize = 1;
                                   }
                                   log.trace("[{}-{}] (#{}) Requesting {} events from the EventStore",
                                             eventStoreSubscription.subscriberId(),
                                             eventStoreSubscription.aggregateType(),
                                             e.globalEventOrder(),
                                             requestSize
                                            );
                                   if (requestSize > 0) {
                                       eventStoreSubscription.request(requestSize);
                                   }
                               },
                               error -> {
                                   rethrowIfCriticalError(error);
                                   eventStore.getEventStoreSubscriptionObserver().handleEventFailed(e,
                                                                                                    eventHandler,
                                                                                                    error,
                                                                                                    eventStoreSubscription);
                                   onErrorHandler.accept(e, error.getCause());
                               });
            }
        }
    }
}
