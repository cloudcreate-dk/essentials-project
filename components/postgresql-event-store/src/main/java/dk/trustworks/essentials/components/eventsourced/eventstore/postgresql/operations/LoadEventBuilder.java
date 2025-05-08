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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.foundation.types.EventId;

/**
 * Builder for {@link LoadEvent}
 */
public final class LoadEventBuilder {
    private AggregateType aggregateType;
    private EventId eventId;

    /**
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream}, which should contain the {@link PersistedEvent} with the given <code>eventId</code>, is associated with
     * @return this builder instance
     */
    public LoadEventBuilder setAggregateType(AggregateType aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    /**
     *
     * @param eventId the identifier of the {@link PersistedEvent}
     * @return this builder instance
     */
    public LoadEventBuilder setEventId(EventId eventId) {
        this.eventId = eventId;
        return this;
    }

    /**
     * Builder an {@link LoadEvent} instance from the builder properties
     * @return the {@link LoadEvent} instance
     */
    public LoadEvent build() {
        return new LoadEvent(aggregateType, eventId);
    }
}