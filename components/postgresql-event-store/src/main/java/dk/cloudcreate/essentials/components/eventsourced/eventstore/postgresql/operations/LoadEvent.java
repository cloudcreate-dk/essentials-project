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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.*;
import dk.cloudcreate.essentials.components.foundation.types.EventId;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Operation matching the {@link EventStore#loadEvent(AggregateType, EventId)}<br>
 * Operation also matches {@link EventStoreInterceptor#intercept(LoadEvent, EventStoreInterceptorChain)}
 */
public final class LoadEvent {
    /**
     * the aggregate type that the underlying {@link AggregateEventStream}, which should contain the {@link PersistedEvent} with the given <code>eventId</code>, is associated with
     */
    public final AggregateType aggregateType;
    /**
     * the identifier of the {@link PersistedEvent}
     */
    public final EventId       eventId;

    /**
     * Create a new builder that produces a new {@link LoadEvent} instance
     *
     * @return a new {@link LoadEventBuilder} instance
     */
    public static LoadEventBuilder builder() {
        return new LoadEventBuilder();
    }

    /**
     * Load the event belonging to <code>aggregateType</code> and having the specified <code>eventId</code>
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream}, which should contain the {@link PersistedEvent} with the given <code>eventId</code>, is associated with
     * @param eventId       the identifier of the {@link PersistedEvent}
     */
    public LoadEvent(AggregateType aggregateType, EventId eventId) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.eventId = requireNonNull(eventId, "No eventId provided");
    }

    /**
     * @return the aggregate type that the underlying {@link AggregateEventStream}, which should contain the {@link PersistedEvent} with the given <code>eventId</code>, is associated with
     */
    public AggregateType getAggregateType() {
        return aggregateType;
    }

    /**
     * @return the identifier of the {@link PersistedEvent}
     */
    public EventId getEventId() {
        return eventId;
    }

    @Override
    public String toString() {
        return "LoadEvent{" +
                "aggregateType=" + aggregateType +
                ", eventId=" + eventId +
                '}';
    }
}
