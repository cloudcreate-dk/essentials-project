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

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Operation matching the {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}<br>
 * Operation also matches {@link EventStoreInterceptor#intercept(LoadLastPersistedEventRelatedTo, EventStoreInterceptorChain)}
 *
 * @param <ID> the id type for the aggregate
 */
public final class LoadLastPersistedEventRelatedTo<ID> {
    /**
     * the aggregate type that the underlying {@link AggregateEventStream} is associated with
     */
    public final AggregateType aggregateType;
    /**
     * the identifier of the aggregate we want to find the last {@link PersistedEvent}
     */
    public final ID            aggregateId;

    /**
     * Create a new builder that produces a new {@link LoadLastPersistedEventRelatedTo} instance
     *
     * @param <ID> the id type for the aggregate
     * @return a new {@link LoadLastPersistedEventRelatedToBuilder} instance
     */
    public static <ID> LoadLastPersistedEventRelatedToBuilder<ID> builder() {
        return new LoadLastPersistedEventRelatedToBuilder<>();
    }


    /**
     * Load the last {@link PersistedEvent} in relation to the specified <code>aggregateType</code> and <code>aggregateId</code>
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to find the last {@link PersistedEvent}
     */
    public LoadLastPersistedEventRelatedTo(AggregateType aggregateType, ID aggregateId) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.aggregateId = requireNonNull(aggregateId, "No aggregateId provided");
    }

    /**
     * @return the aggregate type that the underlying {@link AggregateEventStream} is associated with
     */
    public AggregateType getAggregateType() {
        return aggregateType;
    }

    /**
     * @return the identifier of the aggregate we want to find the last {@link PersistedEvent}
     */
    public ID getAggregateId() {
        return aggregateId;
    }

    @Override
    public String toString() {
        return "LoadLastPersistedEventRelatedTo{" +
                "aggregateType=" + aggregateType +
                ", aggregateId=" + aggregateId +
                '}';
    }
}
