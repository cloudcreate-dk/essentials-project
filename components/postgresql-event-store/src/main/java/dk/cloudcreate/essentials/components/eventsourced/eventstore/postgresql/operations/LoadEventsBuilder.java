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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.foundation.types.EventId;

import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.*;

/**
 * Builder for {@link LoadEvents}
 */
public final class LoadEventsBuilder {
    private AggregateType aggregateType;
    private List<EventId> eventIds;

    /**
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream},
     *                      for which we want to load the events specified by the {@link #setEventIds(EventId...)} property
     * @return this builder instance
     */
    public LoadEventsBuilder setAggregateType(AggregateType aggregateType) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        return this;
    }

    /**
     * @param eventIds the identifiers of the {@link PersistedEvent}'s to load
     * @return this builder instance
     */
    public LoadEventsBuilder setEventIds(EventId... eventIds) {
        return setEventIds(List.of(eventIds));
    }

    /**
     * @param eventIds the identifiers of the {@link PersistedEvent}'s to load
     * @return this builder instance
     */
    public LoadEventsBuilder setEventIds(List<EventId> eventIds) {
        this.eventIds = requireNonNull(eventIds, "No eventIds list provided");
        requireNonEmpty(eventIds, "No eventIds provided");
        return this;
    }

    /**
     * Builder an {@link LoadEvents} instance from the builder properties
     *
     * @return the {@link LoadEvents} instance
     */
    public LoadEvents build() {
        return new LoadEvents(aggregateType, eventIds);
    }
}