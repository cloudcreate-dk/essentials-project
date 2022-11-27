/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.List;
import java.util.stream.Stream;

/**
 * Handle event stream gaps handling for a specific {@link SubscriberId}
 */
public interface SubscriptionGapHandler {
    /**
     * The id of the subscriber that we're handling gaps on behalf of
     *
     * @return the id of the subscriber that we're handling gaps on behalf of
     */
    SubscriberId subscriberId();

    /**
     * Based on the <code>globalOrderQueryRange</code> resolve which transient gaps that should be included in the {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}
     *
     * @param aggregateType         the aggregate type we want to resolve gaps for
     * @param globalOrderQueryRange the global order query range being used when querying for new events
     * @return a list of {@link GlobalEventOrder} gaps (can be null or empty if no transient gaps exists for this subscriber)
     */
    List<GlobalEventOrder> findTransientGapsToIncludeInQuery(AggregateType aggregateType,
                                                             LongRange globalOrderQueryRange);

    /**
     * Reconcile the <code>persistedEvents</code> returned from {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}
     * against the <code>globalOrderRange</code> and <code>transientGapsIncludedInQuery</code> (returned by {@link #findTransientGapsToIncludeInQuery(AggregateType, LongRange)})
     * that were used in the {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)} query
     * <br>
     * This method is responsible for resolving transient transientGapsIncludedInQuery and promoting transientGapsIncludedInQuery to be permanent transientGapsIncludedInQuery in case the permanent gap criteria is met
     *
     * @param aggregateType                the aggregate type we want to reconcile transientGapsIncludedInQuery for
     * @param globalOrderQueryRange        the globalOrderRange used in the call to {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}
     * @param persistedEvents              the returned from {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}
     * @param transientGapsIncludedInQuery the gap, returned by {@link #findTransientGapsToIncludeInQuery(AggregateType, LongRange)}) using the same <code>globalOrderRange</code> input parameter, that was used in the
     *                                     call to {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}
     */
    void reconcileGaps(AggregateType aggregateType, LongRange globalOrderQueryRange, List<PersistedEvent> persistedEvents, List<GlobalEventOrder> transientGapsIncludedInQuery);

    /**
     * Reset all transient gaps registered by this subscription handler for the given aggregate type
     *
     * @param aggregateType the aggregate type we want to reset transient gaps for
     * @return list of transient gap {@link GlobalEventOrder}'s the were removed
     */
    List<GlobalEventOrder> resetTransientGapsFor(AggregateType aggregateType);

    /**
     * Get all transient gaps registered by this subscription handler for the given aggregate type
     *
     * @param aggregateType the aggregate type we want to get all transient gaps for
     * @return list of transient gap {@link GlobalEventOrder}'s registered by this subscription handler for the given aggregate type
     */
    List<GlobalEventOrder> getTransientGapsFor(AggregateType aggregateType);

    /**
     * Get all permanent {@link AggregateType} specific event-stream gaps across all subscribers (shorthand for calling {@link EventStreamGapHandler#getPermanentGapsFor(AggregateType)})
     *
     * @param aggregateType the aggregate type we want all permanent gaps for
     * @return stream of permanent gap {@link GlobalEventOrder}'s registered for the given aggregate type
     */
    Stream<GlobalEventOrder> getPermanentGapsFor(AggregateType aggregateType);
}
