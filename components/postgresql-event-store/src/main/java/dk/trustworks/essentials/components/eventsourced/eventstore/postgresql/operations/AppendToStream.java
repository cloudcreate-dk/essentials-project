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

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.interceptor.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.trustworks.essentials.types.NumberType;

import java.util.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Operation matching the {@link EventStore#appendToStream(AggregateType, Object, Optional, List)} method call<br>
 * Operation also matches {@link EventStoreInterceptor#intercept(AppendToStream, EventStoreInterceptorChain)}
 *
 * @param <ID> the id type for the aggregate
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class AppendToStream<ID> {
    /**
     * the aggregate type that the underlying {@link AggregateEventStream} is associated with
     */
    public final AggregateType  aggregateType;
    /**
     * the identifier of the aggregate we want to persist events related to
     */
    public final ID             aggregateId;
    private      Optional<Long> appendEventsAfterEventOrder;
    private      List<?>        eventsToAppend;

    /**
     * Create a new builder that produces a new {@link AppendToStream} instance
     *
     * @param <ID> the id type for the aggregate
     * @return a new {@link AppendToStreamBuilder} instance
     */
    public static <ID> AppendToStreamBuilder<ID> builder() {
        return new AppendToStreamBuilder<ID>();
    }

    /**
     * Append the <code>events</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The the {@link EventStore} will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     * to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.<br>
     * IF you know the last persisted aggregate event order then please use {@link #AppendToStream(AggregateType, Object, Optional, List)} constructor
     *
     * @param aggregateType  the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId    the identifier of the aggregate we want to persist events related to
     * @param eventsToAppend the events to persist/append
     */
    public AppendToStream(AggregateType aggregateType, ID aggregateId, List<?> eventsToAppend) {
        this(aggregateType,
             aggregateId,
             Optional.empty(),
             eventsToAppend);
    }


    /**
     * Append the <code>events</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The the {@link EventStore} will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     * to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.<br>
     * IF you know the last persisted aggregate event order then please use {@link #AppendToStream(AggregateType, Object, Optional, List)} constructor
     *
     * @param aggregateType  the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId    the identifier of the aggregate we want to persist events related to
     * @param eventsToAppend the events to persist/append
     */
    public AppendToStream(AggregateType aggregateType, ID aggregateId, Object... eventsToAppend) {
        this(aggregateType,
             aggregateId,
             Optional.empty(),
             List.of(eventsToAppend));
    }

    /**
     * Append the <code>events</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 the identifier of the aggregate we want to persist events related to
     * @param appendEventsAfterEventOrder append the <code>events</code> after this event order, i.e. the first event in the <code>events</code> list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is {@link Optional#empty()} then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @param eventsToAppend              the events to persist/append
     */
    public AppendToStream(AggregateType aggregateType, ID aggregateId, Optional<Long> appendEventsAfterEventOrder, List<?> eventsToAppend) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.aggregateId = requireNonNull(aggregateId, "No aggregateId provided");
        this.appendEventsAfterEventOrder = requireNonNull(appendEventsAfterEventOrder, "No appendEventsAfterEventOrder provided");
        this.eventsToAppend = requireNonNull(eventsToAppend, "No eventsToAppend provided");
    }

    /**
     * @param appendEventsAfterEventOrder append the <code>events</code> after this event order, i.e. the first event in the <code>events</code> list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is {@link Optional#empty()} then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     */
    public AppendToStream setAppendEventsAfterEventOrder(Optional<Long> appendEventsAfterEventOrder) {
        this.appendEventsAfterEventOrder = requireNonNull(appendEventsAfterEventOrder, "No appendEventsAfterEventOrder option provided");
        return this;
    }

    /**
     * @param appendEventsAfterEventOrder append the <code>events</code> after this event order, i.e. the first event in the <code>events</code> list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is <code>null</code> then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     */
    public AppendToStream setAppendEventsAfterEventOrder(Long appendEventsAfterEventOrder) {
        this.appendEventsAfterEventOrder = Optional.ofNullable(appendEventsAfterEventOrder);
        return this;
    }

    /**
     * @param appendEventsAfterEventOrder append the <code>events</code> after this event order, i.e. the first event in the <code>events</code> list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is <code>null</code> then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     */
    public AppendToStream setAppendEventsAfterEventOrder(EventOrder appendEventsAfterEventOrder) {
        this.appendEventsAfterEventOrder = Optional.ofNullable(appendEventsAfterEventOrder).map(NumberType::value);
        return this;
    }

    /**
     * @param appendEventsAfterEventOrder append the <code>events</code> after this event order, i.e. the first event in the <code>events</code> list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     *                                    If <code>appendEventsAfterEventOrder</code> is <code>null</code> then the {@link EventStore}
     *                                    will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     */
    public AppendToStream setAppendEventsAfterEventOrder(GlobalEventOrder appendEventsAfterEventOrder) {
        this.appendEventsAfterEventOrder = Optional.ofNullable(appendEventsAfterEventOrder).map(NumberType::longValue);
        return this;
    }

    /**
     * @param eventsToAppend the events to persist/append
     */
    public AppendToStream setEventsToAppend(List<?> eventsToAppend) {
        this.eventsToAppend = requireNonNull(eventsToAppend, "No eventsToAppend provided");
        return this;
    }

    /**
     * @param eventsToAppend the events to persist/append
     */
    public AppendToStream setEventsToAppend(Object... eventsToAppend) {
        return setEventsToAppend(List.of(eventsToAppend));
    }

    /**
     * @return the aggregate type that the underlying {@link AggregateEventStream} is associated with
     */
    public AggregateType getAggregateType() {
        return aggregateType;
    }

    /**
     * @return the identifier of the aggregate we want to persist events related to
     */
    public ID getAggregateId() {
        return aggregateId;
    }

    /**
     * @return The {@link GlobalEventOrder} after which (i.e. not including) where the {@link #eventsToAppend} will be appended.<br>
     * The first event in the {@link #eventsToAppend} list
     * will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     * If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     * If <code>appendEventsAfterEventOrder</code> is {@link Optional#empty()} then the {@link EventStore}
     * will call {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     * to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     */
    public Optional<Long> getAppendEventsAfterEventOrder() {
        return appendEventsAfterEventOrder;
    }

    /**
     * @return The events to persist/append
     */
    public List<?> getEventsToAppend() {
        return eventsToAppend;
    }

    @Override
    public String toString() {
        return "AppendToStream{" +
                "aggregateType=" + aggregateType +
                ", aggregateId=" + aggregateId +
                ", appendEventsAfterEventOrder=" + appendEventsAfterEventOrder +
                ", eventsToAppend=" + eventsToAppend +
                '}';
    }
}
