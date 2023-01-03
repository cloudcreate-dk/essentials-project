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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Aggregate snapshot loaded using {@link AggregateSnapshotRepository#loadSnapshot(AggregateType, Object, Class)}
 *
 * @param <ID>                  the aggregate ID type
 * @param <AGGREGATE_IMPL_TYPE> the concrete aggregate implementation type
 */
public class AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE> {
    /**
     * the name of the aggregate's event stream
     */
    public final AggregateType              aggregateType;
    /**
     * the identifier for the aggregate instance
     */
    public final ID                         aggregateId;
    /**
     * the concrete aggregate implementation type
     */
    public final Class<AGGREGATE_IMPL_TYPE> aggregateImplType;

    /**
     * The {@link EventOrder} of the last Event that was included in the snapshot
     */
    public final EventOrder          eventOrderOfLastIncludedEvent;
    /**
     * The aggregate snapshot loaded from the repository - if this is of type {@link BrokenSnapshot} then the Aggregate snapshot couldn't be deserialized
     */
    public final AGGREGATE_IMPL_TYPE aggregateSnapshot;

    /**
     * @param aggregateType                 the name of the aggregate's event stream
     * @param aggregateId                   the identifier for the aggregate instance
     * @param aggregateImplType             the concrete aggregate implementation type
     * @param aggregateSnapshot             The aggregate snapshot loaded from the repository (optional as we can load snapshots without payload)
     * @param eventOrderOfLastIncludedEvent The {@link EventOrder} of the last Event that was included in the snapshot
     */
    public AggregateSnapshot(AggregateType aggregateType,
                             ID aggregateId,
                             Class<AGGREGATE_IMPL_TYPE> aggregateImplType,
                             AGGREGATE_IMPL_TYPE aggregateSnapshot,
                             EventOrder eventOrderOfLastIncludedEvent) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType supplied");
        this.aggregateId = requireNonNull(aggregateId, "No aggregateId supplied");
        this.aggregateImplType = requireNonNull(aggregateImplType, "No aggregateImplType supplied");
        this.aggregateSnapshot = aggregateSnapshot;
        this.eventOrderOfLastIncludedEvent = requireNonNull(eventOrderOfLastIncludedEvent, "No globalEventOrderOfLastIncludedEvent supplied");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AggregateSnapshot<?, ?> that = (AggregateSnapshot<?, ?>) o;
        return aggregateType.equals(that.aggregateType) && aggregateId.equals(that.aggregateId) &&
                aggregateImplType.equals(that.aggregateImplType) && eventOrderOfLastIncludedEvent.equals(that.eventOrderOfLastIncludedEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateType, aggregateId, aggregateImplType, eventOrderOfLastIncludedEvent);
    }

    @Override
    public String toString() {
        return "AggregateSnapshot{" +
                "aggregateType=" + aggregateType +
                ", aggregateId=" + aggregateId +
                ", eventOrderOfLastIncludedEvent=" + eventOrderOfLastIncludedEvent +
                ", aggregateImplType=" + aggregateImplType +
                '}';
    }
}
