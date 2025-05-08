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

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.EventStoreSubscription;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.components.foundation.types.SubscriberId;

import java.util.Optional;

/**
 * {@link PersistedEvent} Event handler interface for use with the {@link EventStoreSubscriptionManager}'s:
 * <ul>
 *     <li>{@link EventStoreSubscriptionManager#exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, FencedLockAwareSubscriber, PersistedEventHandler)} </li>
 *     <li>{@link EventStoreSubscriptionManager#subscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, PersistedEventHandler)}</li>
 * </ul>
 *
 * @see PatternMatchingPersistedEventHandler
 */
public interface PersistedEventHandler {
    /**
     * This method will be called if {@link EventStoreSubscription#resetFrom(GlobalEventOrder)} is called
     *
     * @param eventStoreSubscription           the {@link EventStoreSubscription} where {@link EventStoreSubscription#resetFrom(GlobalEventOrder)} was called (useful if a {@link PersistedEventHandler} listens to multiple streams)
     * @param resetFromAndIncludingGlobalOrder the value provided to {@link EventStoreSubscription#resetFrom(GlobalEventOrder)}. This {@link GlobalEventOrder} will become the new starting point in the
     *                                         EventStream associated with the {@link EventStoreSubscription#aggregateType()}
     */
    default void onResetFrom(EventStoreSubscription eventStoreSubscription, GlobalEventOrder resetFromAndIncludingGlobalOrder) {
    }

    default int handleWithBackPressure(PersistedEvent event) {
        handle(event);
        return 1;
    }

    /**
     * This method will be called in a {@link UnitOfWork} when ever a {@link PersistedEvent} is published
     *
     * @param event the event published
     */
    void handle(PersistedEvent event);
}