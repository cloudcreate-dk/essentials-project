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

package dk.cloudcreate.essentials.components.eventsourced.aggregates;


import dk.cloudcreate.essentials.components.eventsourced.aggregates.flex.FlexAggregate;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.modern.AggregateRoot;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateEventStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

import java.util.*;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Wrapper object that captures the results of any command handling (e.g. static constructor method or instance command methods in {@link FlexAggregate}/{@link AggregateRoot} sub classes).<br>
 * The purpose of this wrapper is to wrap information about the aggregate-id, event-order of last applied historic event (if any) and any events that were a side effect of the command method invocation
 *
 * @param <ID>         the aggregate id type
 * @param <EVENT_TYPE> the type of event
 */
public class EventsToPersist<ID, EVENT_TYPE> {
    /**
     * The id of the aggregate this relates to
     */
    public final ID               aggregateId;
    /**
     * (Zero based event order) contains the eventOrder for the last (previous/historic) event applied during {@link AggregateRoot#rehydrate(AggregateEventStream)}/{@link FlexAggregate#rehydrate(AggregateEventStream)}.
     * See {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     */
    public final EventOrder       eventOrderOfLastRehydratedEvent;
    public final List<EVENT_TYPE> events;
    private      boolean          committed;

    /**
     * @param aggregateId                     the aggregate id this relates to
     * @param eventOrderOfLastRehydratedEvent (Zero based event order) contains the eventOrder for the last (previous/historic) event applied during {@link AggregateRoot#rehydrate(AggregateEventStream)}/{@link FlexAggregate#rehydrate(AggregateEventStream)}.
     *                                        See {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     * @param events                          the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     */
    public EventsToPersist(ID aggregateId,
                           EventOrder eventOrderOfLastRehydratedEvent,
                           List<EVENT_TYPE> events) {
        this.aggregateId = requireNonNull(aggregateId, "You must supply an aggregate id");
        this.eventOrderOfLastRehydratedEvent = eventOrderOfLastRehydratedEvent;
        this.events = requireNonNull(events, "No events list provided");
    }

    /**
     * @param aggregateId                     the aggregate id this relates to
     * @param eventOrderOfLastRehydratedEvent (Zero based event order) contains the eventOrder for the last (previous/historic) event applied during {@link AggregateRoot#rehydrate(AggregateEventStream)}/{@link FlexAggregate#rehydrate(AggregateEventStream)}.
     *                                        See {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     * @param events                          the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     */
    public EventsToPersist(ID aggregateId,
                           EventOrder eventOrderOfLastRehydratedEvent,
                           EVENT_TYPE... events) {
        this.aggregateId = requireNonNull(aggregateId, "You must supply an aggregate id");
        this.eventOrderOfLastRehydratedEvent = eventOrderOfLastRehydratedEvent;
        this.events = List.of(events);
    }

    /**
     * Wrap the events that should be persisted for this new aggregate
     *
     * @param aggregateId     the aggregate id this relates to
     * @param eventsToPersist the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     *                        May be empty if the command method invocation didn't result in any events (e.g. due to idempotency checks)
     * @param <ID>            the aggregate id type
     */
    public static <ID, EVENT_TYPE> EventsToPersist<ID, EVENT_TYPE> initialAggregateEvents(ID aggregateId,
                                                                                          EVENT_TYPE... eventsToPersist) {
        requireNonNull(aggregateId, "You must supply an aggregateId");
        return new EventsToPersist<>(aggregateId,
                                     EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED,
                                     List.of(eventsToPersist));
    }

    /**
     * Wrap the events that should be persisted for this existing aggregate
     *
     * @param aggregateId                     the aggregate id this relates to
     * @param eventOrderOfLastRehydratedEvent (Zero based event order) contains the eventOrder for the last (previous/historic) event applied during {@link AggregateRoot#rehydrate(AggregateEventStream)}/{@link FlexAggregate#rehydrate(AggregateEventStream)}.
     *                                        See {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}<br>
     * @param eventsToPersist                 the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     *                                        May be empty if the command method invocation didn't result in any events (e.g. due to idempotency checks)
     * @param <ID>                            the aggregate id type
     */
    public static <ID, EVENT_TYPE> EventsToPersist<ID, EVENT_TYPE> events(ID aggregateId,
                                                                          EventOrder eventOrderOfLastRehydratedEvent,
                                                                          EVENT_TYPE... eventsToPersist) {
        return new EventsToPersist<>(aggregateId,
                                     eventOrderOfLastRehydratedEvent,
                                     eventsToPersist);
    }

    /**
     * Wrap the events that should be persisted relates to the given aggregate's command handling
     *
     * @param aggregate        the easy aggregate instance that the <code>eventsToPersist</code>
     * @param eventsToPersist  the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     *                         May be empty if the command method invocation didn't result in any events (e.g. due to idempotency checks)
     * @param <ID>             the aggregate id type
     * @param <AGGREGATE_TYPE> the aggregate type
     */
    public static <ID, EVENT_TYPE, AGGREGATE_TYPE extends FlexAggregate<ID, AGGREGATE_TYPE>> EventsToPersist<ID, EVENT_TYPE> events(FlexAggregate<ID, AGGREGATE_TYPE> aggregate,
                                                                                                                                    EVENT_TYPE... eventsToPersist) {
        return new EventsToPersist<>(requireNonNull(aggregate.aggregateId(), "Aggregate doesn't have an aggregateId, please use initialAggregateEvents(id, events)"),
                                     aggregate.eventOrderOfLastRehydratedEvent(),
                                     eventsToPersist);
    }

    /**
     * Creates an empty {@link EventsToPersist} which is handy for a commanded method didn't have a side-effect (e.g. due to idempotent handling)
     *
     * @param aggregateId the aggregate id this relates to
     * @param <ID>        the aggregate id type
     */
    public static <ID, EVENT_TYPE> EventsToPersist<ID, EVENT_TYPE> noEvents(ID aggregateId) {
        return new EventsToPersist<>(aggregateId, null);
    }

    /**
     * Creates an empty {@link EventsToPersist} which is handy for a commanded method didn't have a side-effect (e.g. due to idempotent handling)
     *
     * @param aggregate        the easy aggregate instance
     * @param <ID>             the aggregate id type
     * @param <AGGREGATE_TYPE> the aggregate type
     */
    public static <ID, EVENT_TYPE, AGGREGATE_TYPE extends FlexAggregate<ID, AGGREGATE_TYPE>> EventsToPersist<ID, EVENT_TYPE> noEvents(FlexAggregate<ID, AGGREGATE_TYPE> aggregate) {
        return new EventsToPersist<>(aggregate.aggregateId(),
                                     aggregate.eventOrderOfLastRehydratedEvent());
    }

    public void markEventsAsCommitted() {
        committed = true;
    }

    public boolean isCommitted() {
        return committed;
    }

    /**
     * Immutable method that appends all events in <code>appendEventsToPersist</code>
     * to the {@link #events} in this instance. The result is returned as a NEW
     * {@link EventsToPersist} instance that contains the combined list of events
     *
     * @param appendEventsToPersist the events to append to this instance
     * @return The result is returned as a NEW
     * {@link EventsToPersist} instance that contains the combined list of events. The new {@link EventsToPersist}
     * retains the original value of the {@link EventsToPersist#eventOrderOfLastRehydratedEvent}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EventsToPersist<ID, EVENT_TYPE> append(EventsToPersist<ID, EVENT_TYPE> appendEventsToPersist) {
        requireNonNull(appendEventsToPersist, "You must supply an appendEventsToPersist instance");

        if (!this.aggregateId.equals(appendEventsToPersist.aggregateId)) {
            throw new IllegalArgumentException(msg("Cannot append appendEventsToPersist since aggregate id's are not the same. " +
                                                           "this.aggregateId='{}', appendEventsToPersist.aggregateId='{}'",
                                                   this.aggregateId,
                                                   appendEventsToPersist.aggregateId));
        }

        if (eventOrderOfLastRehydratedEvent == null && !events.isEmpty()) {
            throw new IllegalStateException("this.eventOrderOfLastRehydratedEvent is null and therefore doesn't support appending");
        }
        if (appendEventsToPersist.eventOrderOfLastRehydratedEvent == null) {
            throw new IllegalStateException("appendEventsToPersist.eventOrderOfLastRehydratedEvent is null");
        }

        long expectedEventOrderOfLastRehydratedEvent = eventOrderOfLastRehydratedEvent.longValue() + events.size();
        if (expectedEventOrderOfLastRehydratedEvent != appendEventsToPersist.eventOrderOfLastRehydratedEvent.longValue()) {
            throw new IllegalArgumentException(msg("Cannot append appendEventsToPersist as appendEventsToPersist.eventOrderOfLastRehydratedEvent was " +
                                                           "{} but it was expected to be {}",
                                                   appendEventsToPersist.eventOrderOfLastRehydratedEvent,
                                                   expectedEventOrderOfLastRehydratedEvent));
        }

        var allEventsToPersist = new ArrayList(events);
        allEventsToPersist.addAll(appendEventsToPersist.events);
        return new EventsToPersist<>(aggregateId,
                                     eventOrderOfLastRehydratedEvent,
                                     allEventsToPersist);
    }

    @Override
    public String toString() {
        return "EventsToPersist{" +
                "aggregateId=" + aggregateId +
                ", eventOrderOfLastRehydratedEvent=" + eventOrderOfLastRehydratedEvent +
                ", eventsToPersist=" + events.size() +
                ", committed=" + committed +
                '}';
    }

    /**
     * Is the {@link #events} empty
     *
     * @return Is the {@link #events} empty
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * {@link #events} {@link Stream}
     *
     * @return {@link #events} {@link Stream}
     */
    public Stream<EVENT_TYPE> stream() {
        return events.stream();
    }

    /**
     * Number of events. Shorthand for {@link #events#size()}
     *
     * @return Shorthand for {@link #events#size()}
     */
    public int size() {
        return events.size();
    }

    /**
     * Get event at index. Shorthand for {@link #events#get(int)}
     *
     * @param index the index on the {@link #events} list
     * @return Shorthand for {@link #events#get(int)}
     */
    public Object get(int index) {
        return events.get(index);
    }
}
