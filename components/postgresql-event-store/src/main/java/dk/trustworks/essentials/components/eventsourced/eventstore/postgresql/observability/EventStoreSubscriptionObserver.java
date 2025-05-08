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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.observability;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamPersistenceStrategy;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreUnitOfWork;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.trustworks.essentials.components.foundation.fencedlock.FencedLock;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.components.foundation.types.*;
import dk.trustworks.essentials.types.LongRange;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * {@link EventStore} and {@link EventStoreSubscriptionManager}
 * observer used for collecting {@link EventStoreSubscription} and {@link EventStore} event polling
 */
public interface EventStoreSubscriptionObserver {
    /**
     * Called prior to {@link EventStoreSubscription#start()} is called
     *
     * @param eventStoreSubscription the event store subscription being started
     */
    void startingSubscriber(EventStoreSubscription eventStoreSubscription);

    /**
     * Called after {@link EventStoreSubscription#start()} is called
     *
     * @param eventStoreSubscription the event store subscription that was started
     * @param startDuration          how long did it take to start the event store subscription
     */
    void startedSubscriber(EventStoreSubscription eventStoreSubscription, Duration startDuration);

    /**
     * Called prior to {@link EventStoreSubscription#stop()} is called
     *
     * @param eventStoreSubscription the event store subscription being stopped
     */
    void stoppingSubscriber(EventStoreSubscription eventStoreSubscription);

    /**
     * Called after {@link EventStoreSubscription#stop()} is called
     *
     * @param eventStoreSubscription the event store subscription that was stopped
     * @param stopDuration           how long did it take to stop the event store subscription
     */
    void stoppedSubscriber(EventStoreSubscription eventStoreSubscription, Duration stopDuration);

    /**
     * How large is the batch size resolved for the next event store database query/poll
     *
     * @param subscriberId                         the id of the subscriber that was polling/querying the event store
     * @param aggregateType                        the type of aggregate we're polling/querying for
     * @param defaultBatchFetchSize                What is the default configured batch size for this subscriber
     * @param remainingDemandForEvents             How many remaining events is the event subscriber requesting
     * @param lastBatchSizeForEventStorePoll       What was the most recent resolve batch size
     * @param consecutiveNoPersistedEventsReturned How many times in a row did a poll/query to the event store database return 0 (zero) events
     * @param nextFromInclusiveGlobalOrder         What is the first {@link GlobalEventOrder} to use a starting point polling the event store database
     * @param batchSizeForThisEventStorePoll       What was the batch size resolved for the upcoming poll/query of the event store database
     * @param resolveBatchSizeDuration             How long did it take to resolve the batch size
     */
    void resolvedBatchSizeForEventStorePoll(SubscriberId subscriberId,
                                            AggregateType aggregateType,
                                            long defaultBatchFetchSize,
                                            long remainingDemandForEvents,
                                            long lastBatchSizeForEventStorePoll,
                                            int consecutiveNoPersistedEventsReturned,
                                            long nextFromInclusiveGlobalOrder,
                                            long batchSizeForThisEventStorePoll,
                                            Duration resolveBatchSizeDuration);

    /**
     * If {@link #resolvedBatchSizeForEventStorePoll(SubscriberId, AggregateType, long, long, long, int, long, long, Duration)}
     * returned 0 (zero) as batch size, then we will NOT poll the underlying event store database.<br>
     * Hence this method is called instead of {@link #eventStorePolled(SubscriberId, AggregateType, LongRange, List, Optional, List, Duration)}
     *
     * @param subscriberId                         the id of the subscriber that was polling/querying the event store
     * @param aggregateType                        the type of aggregate we're polling/querying for
     * @param defaultBatchFetchSize                What is the default configured batch size for this subscriber
     * @param remainingDemandForEvents             How many remaining events is the event subscriber requesting
     * @param lastBatchSizeForEventStorePoll       What was the most recent resolve batch size
     * @param consecutiveNoPersistedEventsReturned How many times in a row did a poll/query to the event store database return 0 (zero) events
     * @param nextFromInclusiveGlobalOrder         What is the first {@link GlobalEventOrder} to use a starting point polling the event store database
     * @param batchSizeForThisEventStorePoll       What was the batch size resolved for the upcoming poll/query of the event store database
     */
    void skippingPollingDueToNoNewEventsPersisted(SubscriberId subscriberId,
                                                  AggregateType aggregateType,
                                                  long defaultBatchFetchSize,
                                                  long remainingDemandForEvents,
                                                  long lastBatchSizeForEventStorePoll,
                                                  int consecutiveNoPersistedEventsReturned,
                                                  long nextFromInclusiveGlobalOrder,
                                                  long batchSizeForThisEventStorePoll);

    /**
     * How long did it take for the {@link EventStore}'s polling mechanism to query the Event store database for more {@link PersistedEvent}'s
     *
     * @param subscriberId                        the id of the subscriber that was polling/querying the event store
     * @param aggregateType                       the type of aggregate we're polling/querying for
     * @param globalOrderRange                    the {@link GlobalEventOrder} range that was used when querying the event store database
     * @param transientGapsToInclude              the transient {@link GlobalEventOrder} gaps that were included in the event store database query
     * @param onlyIncludeEventIfItBelongsToTenant what tenant, if any, was the query/polling restricted to
     * @param persistedEventsReturnedFromPoll     the events returned (CAN be empty)
     * @param pollDuration                        how long did the call to {@link AggregateEventStreamPersistenceStrategy#loadEventsByGlobalOrder(EventStoreUnitOfWork, AggregateType, LongRange, List, Optional)} take
     */
    void eventStorePolled(SubscriberId subscriberId,
                          AggregateType aggregateType,
                          LongRange globalOrderRange,
                          List<GlobalEventOrder> transientGapsToInclude,
                          Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                          List<PersistedEvent> persistedEventsReturnedFromPoll,
                          Duration pollDuration);

    /**
     * How long did it take for the {@link EventStore}'s polling mechanism to reconcile gaps post polling the Event store database
     *
     * @param subscriberId           the id of the subscriber that is reconciling the gaps
     * @param aggregateType          the type aggregate the event subscriber is subscribing to
     * @param globalOrderRange       the {@link GlobalEventOrder} range that was used when querying the event store database
     * @param transientGapsToInclude the transient {@link GlobalEventOrder} gaps that were included in the event store database query
     * @param persistedEvents        the {@link PersistedEvent}'s returned from the event store database query (non-empty)
     * @param reconcileGapsDuration  how long did the reconciliation of gaps take
     */
    void reconciledGaps(SubscriberId subscriberId,
                        AggregateType aggregateType,
                        LongRange globalOrderRange,
                        List<GlobalEventOrder> transientGapsToInclude,
                        List<PersistedEvent> persistedEvents,
                        Duration reconcileGapsDuration);

    /**
     * How long did it take for the {@link EventStore}'s poll event to publish an event to the underlying {@link Flux}'s
     * sink
     *
     * @param subscriberId         the id of the subscriber that the event is published for
     * @param aggregateType        the type aggregate the event is related to
     * @param persistedEvent       the persisted event being published
     * @param publishEventDuration how long did it take to publish the event to the underlying {@link Flux}'s sink
     */
    void publishEvent(SubscriberId subscriberId,
                      AggregateType aggregateType,
                      PersistedEvent persistedEvent,
                      Duration publishEventDuration);

    /**
     * Notifying that an Asynchronous {@link EventStoreSubscription} is requesting
     * <code>numberOfEventsRequested</code> from the underlying {@link EventStore}
     * subscription {@link Flux}
     *
     * @param numberOfEventsRequested the number of events requested
     * @param eventStoreSubscription  The subscription requesting events
     */
    void requestingEvents(long numberOfEventsRequested, EventStoreSubscription eventStoreSubscription);

    /**
     * An exclusive {@link EventStoreSubscription} acquired its {@link FencedLock}
     *
     * @param lock                   the lock acquired
     * @param eventStoreSubscription The subscription that acquired the lock
     */
    void lockAcquired(FencedLock lock, EventStoreSubscription eventStoreSubscription);

    /**
     * An exclusive {@link EventStoreSubscription} had its {@link FencedLock} released
     *
     * @param lock                   the lock released
     * @param eventStoreSubscription The subscription that had a lock released
     */
    void lockReleased(FencedLock lock, EventStoreSubscription eventStoreSubscription);


    /**
     * A {@link TransactionalPersistedEventHandler} associated with the {@link EventStoreSubscription} handled the <code>event</code>
     *
     * @param event                  the event that was handled
     * @param eventHandler           the event handler that handled the event
     * @param eventStoreSubscription the {@link EventStoreSubscription} that subscribed to the event
     * @param handleEventDuration    how long did the {@link TransactionalPersistedEventHandler#handle(PersistedEvent, UnitOfWork)} call take
     */
    void handleEvent(PersistedEvent event,
                     TransactionalPersistedEventHandler eventHandler,
                     EventStoreSubscription eventStoreSubscription,
                     Duration handleEventDuration);

    /**
     * A {@link PersistedEventHandler} associated with the {@link EventStoreSubscription} handled the <code>event</code>
     *
     * @param event                  the event that was handled
     * @param eventHandler           the event handler that handled the event
     * @param eventStoreSubscription the {@link EventStoreSubscription} that subscribed to the event
     * @param handleEventDuration    how long did the {@link PersistedEventHandler#handle(PersistedEvent)} or {@link PersistedEventHandler#handleWithBackPressure(PersistedEvent)}
     *                               call take
     */
    void handleEvent(PersistedEvent event,
                     PersistedEventHandler eventHandler,
                     EventStoreSubscription eventStoreSubscription,
                     Duration handleEventDuration);

    /**
     * The handling of an event failed
     *
     * @param event                  the event that the <code>eventHandler</code> failed to handle
     * @param eventHandler           the {@link TransactionalPersistedEventHandler} that failed to handle the event
     * @param cause                  the exception thrown by {@link TransactionalPersistedEventHandler#handle(PersistedEvent, UnitOfWork)}
     * @param eventStoreSubscription the {@link EventStoreSubscription} that subscribed to the event
     */
    void handleEventFailed(PersistedEvent event,
                           TransactionalPersistedEventHandler eventHandler,
                           Throwable cause,
                           EventStoreSubscription eventStoreSubscription
                          );

    /**
     * The handling of an event failed
     *
     * @param event                  the event that the <code>eventHandler</code> failed to handle
     * @param eventHandler           the {@link PersistedEventHandler} that failed to handle the event
     * @param cause                  the exception thrown by {@link PersistedEventHandler#handleWithBackPressure(PersistedEvent)}
     * @param eventStoreSubscription the {@link EventStoreSubscription} that subscribed to the event
     */
    void handleEventFailed(PersistedEvent event,
                           PersistedEventHandler eventHandler,
                           Throwable cause,
                           EventStoreSubscription eventStoreSubscription
                          );


    /**
     * How long did it take for an asynchronous {@link EventStoreSubscription} to resolve the resume point while starting
     * the subscription
     *
     * @param resumePoint                                             The resolved resume point
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder the specified initial resume point (if no resume point exists)
     * @param eventStoreSubscription                                  the {@link EventStoreSubscription} that is subscribing
     * @param resolveResumePointDuration                              How long did it take to resolve the resume point
     */
    void resolveResumePoint(SubscriptionResumePoint resumePoint,
                            GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                            EventStoreSubscription eventStoreSubscription,
                            Duration resolveResumePointDuration);

    /**
     * When {@link EventStoreSubscription#unsubscribe()} is called
     *
     * @param eventStoreSubscription the event store subscription that unsubscribes from the {@link EventStoreSubscriptionManager}
     */
    void unsubscribing(EventStoreSubscription eventStoreSubscription);

    /**
     * Called when {@link EventStoreSubscription#resetFrom(GlobalEventOrder, Consumer)} is called
     *
     * @param subscribeFromAndIncludingGlobalOrder the global event order that the subscription should restart from
     * @param eventStoreSubscription               the event store subscription being reset
     */
    void resettingFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder,
                       EventStoreSubscription eventStoreSubscription);

    class NoOpEventStoreSubscriptionObserver implements EventStoreSubscriptionObserver {

        @Override
        public void startingSubscriber(EventStoreSubscription eventStoreSubscription) {

        }

        @Override
        public void startedSubscriber(EventStoreSubscription eventStoreSubscription, Duration startDuration) {

        }

        @Override
        public void stoppingSubscriber(EventStoreSubscription eventStoreSubscription) {

        }

        @Override
        public void stoppedSubscriber(EventStoreSubscription eventStoreSubscription, Duration stopDuration) {

        }

        @Override
        public void resolvedBatchSizeForEventStorePoll(SubscriberId subscriberId, AggregateType aggregateType, long defaultBatchFetchSize, long remainingDemandForEvents,
                                                       long lastBatchSizeForEventStorePoll, int consecutiveNoPersistedEventsReturned, long nextFromInclusiveGlobalOrder,
                                                       long batchSizeForThisEventStorePoll, Duration resolveBatchSizeDuration) {

        }

        @Override
        public void skippingPollingDueToNoNewEventsPersisted(SubscriberId subscriberId, AggregateType aggregateType, long defaultBatchFetchSize, long remainingDemandForEvents,
                                                             long lastBatchSizeForEventStorePoll, int consecutiveNoPersistedEventsReturned, long nextFromInclusiveGlobalOrder,
                                                             long batchSizeForThisEventStorePoll) {

        }

        @Override
        public void eventStorePolled(SubscriberId subscriberId, AggregateType aggregateType, LongRange globalOrderRange, List<GlobalEventOrder> transientGapsToInclude,
                                     Optional<Tenant> onlyIncludeEventIfItBelongsToTenant, List<PersistedEvent> persistedEventsReturnedFromPoll, Duration pollDuration) {

        }

        @Override
        public void reconciledGaps(SubscriberId subscriberId, AggregateType aggregateType, LongRange globalOrderRange, List<GlobalEventOrder> transientGapsToInclude,
                                   List<PersistedEvent> persistedEvents, Duration reconcileGapsDuration) {

        }

        @Override
        public void publishEvent(SubscriberId subscriberId, AggregateType aggregateType, PersistedEvent persistedEvent, Duration publishEventDuration) {

        }

        @Override
        public void requestingEvents(long numberOfEventsRequested, EventStoreSubscription eventStoreSubscription) {

        }

        @Override
        public void lockAcquired(FencedLock lock, EventStoreSubscription eventStoreSubscription) {

        }

        @Override
        public void lockReleased(FencedLock lock, EventStoreSubscription eventStoreSubscription) {

        }

        @Override
        public void handleEvent(PersistedEvent event, TransactionalPersistedEventHandler eventHandler, EventStoreSubscription eventStoreSubscription, Duration handleEventDuration) {

        }

        @Override
        public void handleEvent(PersistedEvent event, PersistedEventHandler eventHandler, EventStoreSubscription eventStoreSubscription, Duration handleEventDuration) {

        }

        @Override
        public void handleEventFailed(PersistedEvent event, TransactionalPersistedEventHandler eventHandler, Throwable cause, EventStoreSubscription eventStoreSubscription) {

        }

        @Override
        public void handleEventFailed(PersistedEvent event, PersistedEventHandler eventHandler, Throwable cause, EventStoreSubscription eventStoreSubscription) {

        }

        @Override
        public void resolveResumePoint(SubscriptionResumePoint resumePoint, GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                       EventStoreSubscription eventStoreSubscription, Duration resolveResumePointDuration) {

        }

        @Override
        public void unsubscribing(EventStoreSubscription eventStoreSubscription) {

        }

        @Override
        public void resettingFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder, EventStoreSubscription eventStoreSubscription) {

        }
    }
}
