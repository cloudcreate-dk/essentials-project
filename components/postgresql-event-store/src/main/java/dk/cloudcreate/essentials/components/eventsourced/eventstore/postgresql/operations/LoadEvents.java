/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.*;
import dk.cloudcreate.essentials.components.foundation.types.EventId;

import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.*;

/**
 * Operation matching the {@link EventStore#loadEvents(AggregateType, EventId...)} <br>
 * Operation also matches {@link EventStoreInterceptor#intercept(LoadEvents, EventStoreInterceptorChain)}
 */
public final class LoadEvents {
    /**
     * the aggregate type that the underlying {@link AggregateEventStream}, for which we want to load the events specified by {@link #eventIds}
     */
    public final AggregateType aggregateType;
    /**
     * the identifiers of the {@link PersistedEvent}'s we want to load
     */
    public final List<EventId> eventIds;

    /**
     * Create a new builder that produces a new {@link LoadEvents} instance
     *
     * @return a new {@link LoadEventsBuilder} instance
     */
    public static LoadEventsBuilder builder() {
        return new LoadEventsBuilder();
    }

    /**
     * Load the events belonging to <code>aggregateType</code> and having the specified <code>eventId</code>'s
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream}, for which we want to load the events specified by the <code>eventIds</code> parameter
     * @param eventIds       the list of identifiers of the {@link PersistedEvent}'s we want to load
     */
    public LoadEvents(AggregateType aggregateType, List<EventId> eventIds) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.eventIds = requireNonNull(eventIds, "No eventIds list provided");
        requireNonEmpty(eventIds, "No eventIds provided");
    }

    /**
     * @return the aggregate type that the underlying {@link AggregateEventStream}, which should contain the {@link PersistedEvent} with the given <code>eventId</code>, is associated with
     */
    public AggregateType getAggregateType() {
        return aggregateType;
    }

    /**
     * @return the identifiers of the {@link PersistedEvent} to load
     */
    public List<EventId> getEventIds() {
        return eventIds;
    }

    @Override
    public String toString() {
        return "LoadEvents{" +
                "aggregateType=" + aggregateType +
                ", eventIds=" + eventIds +
                '}';
    }
}
