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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.types.LongType;

/**
 * Each event has its own unique position within the stream, also known as the event-order,
 * which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
 * <br>
 * The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
 * This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
 * related to a <b>specific</b> aggregate instance (as opposed to the {@link PersistedEvent#globalEventOrder()} which contains
 * the order of ALL events related to a specific {@link AggregateType})
 */
public class EventOrder extends LongType<EventOrder> {
    /**
     * Special value that signifies the no previous events have been persisted in relation to a given aggregate
     */
    public static final EventOrder NO_EVENTS_PREVIOUSLY_PERSISTED = EventOrder.of(-1);
    /**
     * Special value that contains the {@link EventOrder} of the FIRST Event persisted in context of a given aggregate id
     */
    public static final EventOrder FIRST_EVENT_ORDER              = EventOrder.of(0);
    /**
     * Special value that contains the maximum allowed {@link EventOrder} value
     */
    public static final EventOrder MAX_EVENT_ORDER     = EventOrder.of(Long.MAX_VALUE);

    public EventOrder(Long value) {
        super(value);
    }

    public static EventOrder of(long value) {
        return new EventOrder(value);
    }

    public EventOrder increment() {
        return new EventOrder(value + 1);
    }

    public EventOrder decrement() {
        return new EventOrder(value - 1);
    }
}