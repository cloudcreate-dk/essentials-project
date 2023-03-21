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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSON;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.Inbox;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.OrderedMessage;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.shared.FailFast;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;
import org.slf4j.*;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

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
    EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                               AggregateType forAggregateType,
                                                                               GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
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
     * Create an inline event subscription, that will receive {@link PersistedEvent}'s right after they're appended to the {@link EventStore} but before the associated
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
     * Create an inline event subscription, that will receive {@link PersistedEvent}'s right after they're appended to the {@link EventStore} but before the associated
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
     * Create an inline event subscription, that will receive {@link PersistedEvent}'s right after they're appended to the {@link EventStore} but before the associated
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
        return builder().setEventStore(eventStore).setEventStorePollingBatchSize(eventStorePollingBatchSize).setEventStorePollingInterval(eventStorePollingInterval).setFencedLockManager(fencedLockManager).setSnapshotResumePointsEvery(snapshotResumePointsEvery).setDurableSubscriptionRepository(durableSubscriptionRepository).build();
    }

    void unsubscribe(EventStoreSubscription eventStoreSubscription);

    default boolean hasSubscription(EventStoreSubscription subscription) {
        requireNonNull(subscription, "No subscription provided");
        return hasSubscription(subscription.subscriberId(), subscription.aggregateType());
    }

    boolean hasSubscription(SubscriberId subscriberId, AggregateType aggregateType);

    /**
     * Default implementation of the {@link EventStoreSubscriptionManager} interface
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    class DefaultEventStoreSubscriptionManager implements EventStoreSubscriptionManager {
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

        public DefaultEventStoreSubscriptionManager(EventStore eventStore,
                                                    int eventStorePollingBatchSize,
                                                    Duration eventStorePollingInterval,
                                                    FencedLockManager fencedLockManager,
                                                    Duration snapshotResumePointsEvery,
                                                    DurableSubscriptionRepository durableSubscriptionRepository) {
            FailFast.requireTrue(eventStorePollingBatchSize >= 1, "eventStorePollingBatchSize must be >= 1");
            this.eventStore = requireNonNull(eventStore, "No eventStore provided");
            this.eventStorePollingBatchSize = eventStorePollingBatchSize;
            this.eventStorePollingInterval = requireNonNull(eventStorePollingInterval, "No eventStorePollingInterval provided");
            this.fencedLockManager = requireNonNull(fencedLockManager, "No fencedLockManager provided");
            this.durableSubscriptionRepository = requireNonNull(durableSubscriptionRepository, "No durableSubscriptionRepository provided");
            this.snapshotResumePointsEvery = requireNonNull(snapshotResumePointsEvery, "No snapshotResumePointsEvery provided");

            log.info("[{}] Using {} using {} with snapshotResumePointsEvery: {}, eventStorePollingBatchSize: {}, eventStorePollingInterval: {}",
                     fencedLockManager.getLockManagerInstanceId(),
                     fencedLockManager,
                     durableSubscriptionRepository.getClass().getSimpleName(),
                     snapshotResumePointsEvery,
                     eventStorePollingBatchSize,
                     eventStorePollingInterval
                    );
        }

        @Override
        public void start() {
            if (!started) {
                log.info("[{}] Starting EventStore Subscription Manager", fencedLockManager.getLockManagerInstanceId());

                if (!fencedLockManager.isStarted()) {
                    fencedLockManager.start();
                }

                saveResumePointsFuture = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder.builder()
                                                                                                        .nameFormat("EventStoreSubscriptionManager-" + fencedLockManager.getLockManagerInstanceId() + "-%d")
                                                                                                        .daemon(true)
                                                                                                        .build())
                                                  .scheduleAtFixedRate(this::saveResumePointsForAllSubscribers,
                                                                       snapshotResumePointsEvery.toMillis(),
                                                                       snapshotResumePointsEvery.toMillis(),
                                                                       TimeUnit.MILLISECONDS);
                started = true;
                // Start any subscribers added prior to us starting
                subscribers.values().forEach(Lifecycle::start);
            } else {
                log.debug("[{}] EventStore Subscription Manager was already started", fencedLockManager.getLockManagerInstanceId());
            }
        }

        @Override
        public void stop() {
            if (started) {
                log.info("[{}] Stopping EventStore Subscription Manager", fencedLockManager.getLockManagerInstanceId());
                saveResumePointsFuture.cancel(true);
                subscribers.forEach((subscriberIdAggregateTypePair, eventStoreSubscription) -> eventStoreSubscription.stop());
                if (fencedLockManager.isStarted()) {
                    fencedLockManager.stop();
                }
                started = false;
                log.info("[{}] Stopped EventStore Subscription Manager", fencedLockManager.getLockManagerInstanceId());
            } else {
                log.debug("[{}] EventStore Subscription Manager was already stopped", fencedLockManager.getLockManagerInstanceId());
            }
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        private void saveResumePointsForAllSubscribers() {
            // TODO: Filter out active subscribers and decide if we can increment the global event order like when the subscriber stops.
            //   Current approach is safe with regards to reset of resume-points, but it will result in one overlapping event during resubscription
            //   related to a failed node or after a subscription manager failure (i.e. it doesn't run stop() at all or run to completion)
            durableSubscriptionRepository.saveResumePoints(subscribers.values()
                                                                      .stream()
                                                                      .filter(eventStoreSubscription -> eventStoreSubscription.currentResumePoint().isPresent())
                                                                      .filter(EventStoreSubscription::isActive)
                                                                      .map(eventStoreSubscription -> eventStoreSubscription.currentResumePoint().get())
                                                                      .collect(Collectors.toList()));
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
                    eventStoreSubscription.start();
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
        public EventStoreSubscription exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId subscriberId,
                                                                                          AggregateType forAggregateType,
                                                                                          GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
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
            }
        }

        @Override
        public boolean hasSubscription(SubscriberId subscriberId, AggregateType aggregateType) {
            return subscribers.containsKey(Pair.of(subscriberId, aggregateType));
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
                if (persistedEvents.commitStage == CommitStage.BeforeCommit) {
                    persistedEvents.events.stream()
                                          .filter(event -> event.aggregateType().equals(aggregateType))
                                          .forEach(event -> {
                                              log.trace("[{}-{}] (#{}) Received {} event with eventId '{}', aggregateId: '{}', eventOrder: {}",
                                                        subscriberId,
                                                        aggregateType,
                                                        event.globalEventOrder(),
                                                        event.event().getEventTypeOrName().toString(),
                                                        event.eventId(),
                                                        event.aggregateId(),
                                                        event.eventOrder()
                                                       );
                                              try {
                                                  eventHandler.handle(event, persistedEvents.unitOfWork);
                                              } catch (Exception cause) {
                                                  onErrorHandlingEvent(event, cause);
                                              }
                                          });
                }
            }

            protected void onErrorHandlingEvent(PersistedEvent e, Exception cause) {
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
                stop();
                DefaultEventStoreSubscriptionManager.this.unsubscribe(this);
            }

            @Override
            public void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
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
        }

        private class NonExclusiveAsynchronousSubscription implements EventStoreSubscription {
            private final EventStore                    eventStore;
            private final DurableSubscriptionRepository durableSubscriptionRepository;
            private final AggregateType                 aggregateType;
            private final SubscriberId                  subscriberId;
            private final GlobalEventOrder              onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder;
            private final Optional<Tenant>              onlyIncludeEventsForTenant;
            private final PersistedEventHandler         eventHandler;

            private SubscriptionResumePoint resumePoint;
            private Disposable              subscription;

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
                    resumePoint = durableSubscriptionRepository.getOrCreateResumePoint(subscriberId,
                                                                                       aggregateType,
                                                                                       onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);
                    log.info("[{}-{}] Starting subscription from globalEventOrder: {}",
                             subscriberId,
                             aggregateType,
                             resumePoint.getResumeFromAndIncluding());

                    subscription = eventStore.pollEvents(aggregateType,
                                                         resumePoint.getResumeFromAndIncluding(),
                                                         Optional.of(eventStorePollingBatchSize),
                                                         Optional.of(eventStorePollingInterval),
                                                         onlyIncludeEventsForTenant,
                                                         Optional.of(subscriberId))
                                             .subscribe(e -> {
                                                 log.trace("[{}-{}] (#{}) Received {} event with eventId '{}', aggregateId: '{}', eventOrder: {}",
                                                           subscriberId,
                                                           aggregateType,
                                                           e.globalEventOrder(),
                                                           e.event().getEventTypeOrName().toString(),
                                                           e.eventId(),
                                                           e.aggregateId(),
                                                           e.eventOrder()
                                                          );
                                                 try {
                                                     eventStore.getUnitOfWorkFactory()
                                                               .usingUnitOfWork(unitOfWork -> eventHandler.handle(e));
                                                 } catch (Exception cause) {
                                                     onErrorHandlingEvent(e, cause);
                                                 } finally {
                                                     resumePoint.setResumeFromAndIncluding(e.globalEventOrder().increment());
                                                 }
                                             });
                } else {
                    log.debug("[{}-{}] Subscription was already started",
                              subscriberId,
                              aggregateType);
                }
            }

            protected void onErrorHandlingEvent(PersistedEvent e, Exception cause) {
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
                        subscription.dispose();
                    } catch (Exception e) {
                        log.error(msg("[{}-{}] Failed to dispose subscription flux",
                                      subscriberId,
                                      aggregateType), e);
                    }
                    // Save resume point to be the next global order event
                    log.debug("[{}-{}] Storing ResumePoint with resumeFromAndIncluding {}",
                              subscriberId,
                              aggregateType,
                              resumePoint.getResumeFromAndIncluding());
                    resumePoint.setResumeFromAndIncluding(resumePoint.getResumeFromAndIncluding());
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
                stop();
                DefaultEventStoreSubscriptionManager.this.unsubscribe(this);
            }

            @Override
            public void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
                if (started) {
                    log.info("[{}-{}] Resetting resume point and re-starts the subscriber from and including globalOrder {}",
                             subscriberId,
                             aggregateType,
                             subscribeFromAndIncludingGlobalOrder);
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

                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                    started = false;

                    start();
                } else {
                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                }
            }

            private void overrideResumePoint(GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
                requireNonNull(subscribeFromAndIncludingGlobalOrder, "No subscribeFromAndIncludingGlobalOrder value provided");
                // Override resume point
                log.info("[{}-{}] Overriding resume point to start from and include globalOrder {}",
                         subscriberId,
                         aggregateType,
                         subscribeFromAndIncludingGlobalOrder);
                resumePoint.setResumeFromAndIncluding(subscribeFromAndIncludingGlobalOrder);
                durableSubscriptionRepository.saveResumePoint(resumePoint);
            }

            @Override
            public Optional<SubscriptionResumePoint> currentResumePoint() {
                return Optional.of(resumePoint);
            }

            @Override
            public Optional<Tenant> onlyIncludeEventsForTenant() {
                return onlyIncludeEventsForTenant;
            }

            @Override
            public boolean isActive() {
                return started;
            }
        }

        private class ExclusiveAsynchronousSubscription implements EventStoreSubscription {
            private final EventStore                    eventStore;
            private final FencedLockManager             fencedLockManager;
            private final DurableSubscriptionRepository durableSubscriptionRepository;
            private final AggregateType                 aggregateType;
            private final SubscriberId                  subscriberId;
            private final GlobalEventOrder              onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder;
            private final Optional<Tenant>              onlyIncludeEventsForTenant;
            private final FencedLockAwareSubscriber     fencedLockAwareSubscriber;
            private final PersistedEventHandler         eventHandler;
            private final LockName                      lockName;

            private SubscriptionResumePoint resumePoint;
            private Disposable              subscription;

            private volatile boolean started;
            private volatile boolean active;

            public ExclusiveAsynchronousSubscription(EventStore eventStore,
                                                     FencedLockManager fencedLockManager,
                                                     DurableSubscriptionRepository durableSubscriptionRepository,
                                                     AggregateType aggregateType,
                                                     SubscriberId subscriberId,
                                                     GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
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
                            resumePoint = durableSubscriptionRepository.getOrCreateResumePoint(subscriberId,
                                                                                               aggregateType,
                                                                                               onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);
                            log.info("[{}-{}] Starting subscription from globalEventOrder: {}",
                                     subscriberId,
                                     aggregateType,
                                     resumePoint.getResumeFromAndIncluding());

                            try {
                                fencedLockAwareSubscriber.onLockAcquired(lock, resumePoint);
                            } catch (Exception e) {
                                log.error(msg("FencedLockAwareSubscriber#onLockAcquired failed for lock {} and resumePoint {}", lock.getName(), resumePoint), e);
                            }

                            subscription = eventStore.pollEvents(aggregateType,
                                                                 resumePoint.getResumeFromAndIncluding(),
                                                                 Optional.of(eventStorePollingBatchSize),
                                                                 Optional.of(eventStorePollingInterval),
                                                                 onlyIncludeEventsForTenant,
                                                                 Optional.of(subscriberId))
                                                     .subscribe(e -> {
                                                         log.trace("[{}-{}] (#{}) Received {} event with eventId '{}', aggregateId: '{}', eventOrder: {}",
                                                                   subscriberId,
                                                                   aggregateType,
                                                                   e.globalEventOrder(),
                                                                   e.event().getEventTypeOrName().toString(),
                                                                   e.eventId(),
                                                                   e.aggregateId(),
                                                                   e.eventOrder()
                                                                  );
                                                         try {
                                                             eventStore.getUnitOfWorkFactory()
                                                                       .usingUnitOfWork(unitOfWork -> eventHandler.handle(e));
                                                         } catch (Exception cause) {
                                                             onErrorHandlingEvent(e, cause);
                                                         } finally {
                                                             resumePoint.setResumeFromAndIncluding(e.globalEventOrder().increment());
                                                         }
                                                     });
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
                                if (subscription != null) {
                                    log.debug("[{}-{}] Stopping subscription flux",
                                              subscriberId,
                                              aggregateType);
                                    subscription.dispose();
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

                            // Save resume point to be the next global order event AFTER the one we know we just handled
                            log.debug("[{}-{}] Storing ResumePoint with resumeFromAndIncluding {}",
                                      subscriberId,
                                      aggregateType,
                                      resumePoint.getResumeFromAndIncluding());
                            resumePoint.setResumeFromAndIncluding(resumePoint.getResumeFromAndIncluding());
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

            protected void onErrorHandlingEvent(PersistedEvent e, Exception cause) {
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
                    fencedLockManager.cancelAsyncLockAcquiring(lockName);
                    started = false;
                }
            }

            @Override
            public void unsubscribe() {
                log.info("[{}-{}] Initiating unsubscription",
                         subscriberId,
                         aggregateType);
                stop();
                DefaultEventStoreSubscriptionManager.this.unsubscribe(this);
            }

            @Override
            public void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
                if (started) {
                    log.info("[{}-{}] Resetting resume point and re-starts the subscriber from and including globalOrder {}",
                             subscriberId,
                             aggregateType,
                             subscribeFromAndIncludingGlobalOrder);
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

                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                    started = false;

                    start();
                } else {
                    overrideResumePoint(subscribeFromAndIncludingGlobalOrder);
                }
            }

            private void overrideResumePoint(GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
                requireNonNull(subscribeFromAndIncludingGlobalOrder, "No subscribeFromAndIncludingGlobalOrder value provided");
                // Override resume point
                log.info("[{}-{}] Overriding resume point to start from and include globalOrder {}",
                         subscriberId,
                         aggregateType,
                         subscribeFromAndIncludingGlobalOrder);
                resumePoint.setResumeFromAndIncluding(subscribeFromAndIncludingGlobalOrder);
                durableSubscriptionRepository.saveResumePoint(resumePoint);
            }

            @Override
            public Optional<SubscriptionResumePoint> currentResumePoint() {
                return Optional.of(resumePoint);
            }

            @Override
            public Optional<Tenant> onlyIncludeEventsForTenant() {
                return onlyIncludeEventsForTenant;
            }

            @Override
            public boolean isActive() {
                return active;
            }
        }
    }
}
