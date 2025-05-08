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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.gap;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamConfiguration;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.trustworks.essentials.components.foundation.types.*;
import dk.trustworks.essentials.types.LongRange;

import java.util.*;
import java.util.stream.Stream;

/**
 * The {@link PostgresqlEventStore} can be configured with an {@link EventStreamGapHandler}, which keeps track of
 * transient and permanent {@link AggregateType} event-stream gaps, related to a specific {@link SubscriberId}.
 * The {@link EventStore#pollEvents(AggregateType, long, Optional, Optional, Optional, Optional)} will keep track of event stream gaps if you specify
 * a {@link SubscriberId}<br>
 * <br>
 * A transient event stream gap is defined as a gap in an event stream, where a {@link PersistedEvent#globalEventOrder()}
 * is missing in the list of {@link PersistedEvent}'s returned from {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}<br>
 * A gap will remain transient until the {@link EventStreamGapHandler} determines that the gap has met the permanent gap criteria (e.g. if the gap has existed for more than
 * the current database transaction timeout) after which the gap is promoted to permanent gap status<br>
 * After this the gap will be marked as permanent and won't be included in future calls to {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}<br>
 * Permanent gaps are maintained across all Subscriber's at the {@link AggregateType} level and can be reset using {@link #resetPermanentGapsFor(AggregateType)}/{@link #resetPermanentGapsFor(AggregateType, LongRange)}
 * and {@link #resetPermanentGapsFor(AggregateType, List)}
 *
 * @param <CONFIG> the {@link AggregateType} configuration type
 * @see NoEventStreamGapHandler
 * @see PostgresqlEventStreamGapHandler
 */
public interface EventStreamGapHandler<CONFIG extends AggregateEventStreamConfiguration> {
    /**
     * Get a {@link SubscriptionGapHandler} for this specific Subscriber
     *
     * @param subscriberId the id of the subscriber that we're handling gaps for
     * @return the subscription gap handler
     */
    SubscriptionGapHandler gapHandlerFor(SubscriberId subscriberId);

    /**
     * Reset all permanent {@link AggregateType} specific event-stream gaps across all subscribers
     *
     * @param aggregateType the aggregate type we want to reset all permanent gaps for
     * @return list of all the {@link GlobalEventOrder} for all the permanent gaps that were removed
     */
    List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType);

    /**
     * Reset all permanent {@link AggregateType} specific event-stream gaps across all subscribers within the specified <code>resetForThisSpecificGlobalEventOrdersRange</code>
     *
     * @param aggregateType                              the aggregate type we want to reset all permanent gaps for
     * @param resetForThisSpecificGlobalEventOrdersRange remove all permanent gaps that exist within the specified range
     * @return list of all the {@link GlobalEventOrder} for all the permanent gaps that were removed
     */
    List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType, LongRange resetForThisSpecificGlobalEventOrdersRange);

    /**
     * Reset all permanent {@link AggregateType} specific event-stream gaps across all subscribers within the specified <code>resetForThisSpecificGlobalEventOrdersRange</code>
     *
     * @param aggregateType                          the aggregate type we want to reset all permanent gaps for
     * @param resetForTheseSpecificGlobalEventOrders remove all permanent gaps that exist within the specified list
     * @return list of all the {@link GlobalEventOrder} for all the permanent gaps that were removed
     */
    List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType, List<GlobalEventOrder> resetForTheseSpecificGlobalEventOrders);

    /**
     * Get all permanent {@link AggregateType} specific event-stream gaps across all subscribers
     *
     * @param aggregateType the aggregate type we want all permanent gaps for
     * @return stream of permanent gap {@link GlobalEventOrder}'s registered for the given aggregate type
     */
    Stream<GlobalEventOrder> getPermanentGapsFor(AggregateType aggregateType);
}
