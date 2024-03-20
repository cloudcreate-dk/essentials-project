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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.types.NumberType;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Builder for the {@link AppendToStream}
 *
 * @param <ID> the id type for the aggregate
 */
public final class AppendToStreamBuilder<ID> {
    private AggregateType  aggregateType;
    private ID             aggregateId;
    private Optional<Long> appendEventsAfterEventOrder = Optional.empty();
    private List<?>        eventsToAppend;

    /**
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @return this builder instance
     */
    public AppendToStreamBuilder<ID> setAggregateType(AggregateType aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    /**
     * @param aggregateId the identifier of the aggregate we want to persist events related to
     * @return this builder instance
     */
    public AppendToStreamBuilder<ID> setAggregateId(ID aggregateId) {
        this.aggregateId = aggregateId;
        return this;
    }

    /**
     * @param appendEventsAfterEventOrder append the {@link #setEventsToAppend(List)}  after this event order, i.e. the first event in the {@link #setEventsToAppend(List)}  list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is {@link Optional#empty()} then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @return this builder instance
     */
    public AppendToStreamBuilder<ID> setAppendEventsAfterEventOrder(Optional<Long> appendEventsAfterEventOrder) {
        this.appendEventsAfterEventOrder = requireNonNull(appendEventsAfterEventOrder, "No appendEventsAfterEventOrder option provided");
        return this;
    }

    /**
     * @param appendEventsAfterEventOrder append the {@link #setEventsToAppend(List)}  after this event order, i.e. the first event in the {@link #setEventsToAppend(List)}  list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is <code>null</code> then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @return this builder instance
     */
    public AppendToStreamBuilder<ID> setAppendEventsAfterEventOrder(Long appendEventsAfterEventOrder) {
        this.appendEventsAfterEventOrder = Optional.ofNullable(appendEventsAfterEventOrder);
        return this;
    }

    /**
     * @param appendEventsAfterEventOrder append the {@link #setEventsToAppend(List)}  after this event order, i.e. the first event in the {@link #setEventsToAppend(List)}  list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is <code>null</code> then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @return this builder instance
     */
    public AppendToStreamBuilder<ID> setAppendEventsAfterEventOrder(EventOrder appendEventsAfterEventOrder) {
        this.appendEventsAfterEventOrder = Optional.ofNullable(appendEventsAfterEventOrder).map(NumberType::longValue);
        return this;
    }

    /**
     * @param appendEventsAfterEventOrder append the {@link #setEventsToAppend(List)}  after this event order, i.e. the first event in the {@link #setEventsToAppend(List)} list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is <code>null</code> then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @return this builder instance
     */
    public AppendToStreamBuilder<ID> setAppendEventsAfterEventOrder(GlobalEventOrder appendEventsAfterEventOrder) {
        this.appendEventsAfterEventOrder = Optional.ofNullable(appendEventsAfterEventOrder).map(NumberType::longValue);
        return this;
    }

    /**
     * @param eventsToAppend the events to persist/append
     * @return this builder instance
     */
    public AppendToStreamBuilder<ID> setEventsToAppend(List<?> eventsToAppend) {
        this.eventsToAppend = eventsToAppend;
        return this;
    }

    /**
     * @param eventsToAppend the events to persist/append
     * @return this builder instance
     */
    public AppendToStreamBuilder<ID> setEventsToAppend(Object... eventsToAppend) {
        this.eventsToAppend = List.of(eventsToAppend);
        return this;
    }

    /**
     * Builder an {@link AppendToStream} instance from the builder properties
     *
     * @return the {@link AppendToStream} instance
     */
    public AppendToStream<ID> build() {
        return new AppendToStream<>(aggregateType, aggregateId, appendEventsAfterEventOrder, eventsToAppend);
    }
}