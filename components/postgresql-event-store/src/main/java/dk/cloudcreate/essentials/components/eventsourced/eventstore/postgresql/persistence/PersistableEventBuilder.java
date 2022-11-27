/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.types.*;

import java.time.OffsetDateTime;

public class PersistableEventBuilder {
    private EventId         eventId;
    private AggregateType   aggregateType;
    private Object          aggregateId;
    private EventTypeOrName eventTypeOrName;
    private Object          event;
    private EventOrder      eventOrder;
    private EventRevision   eventRevision;
    private EventMetaData   metaData;
    private OffsetDateTime  timestamp;
    private EventId         causedByEventId;
    private CorrelationId   correlationId;
    private Tenant          tenant;

    /**
     * @param eventId Unique id for this Event. If left out or null an {@link EventId#random()} value will be used
     * @return this builder instance
     */
    public PersistableEventBuilder setEventId(EventId eventId) {
        this.eventId = eventId;
        return this;
    }

    /**
     * @param aggregateType Contains the Type of Aggregate the Event-Stream the events belongs to
     * @return this builder instance
     */
    public PersistableEventBuilder setAggregateType(AggregateType aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    /**
     * @param aggregateId Contains the aggregate identifier that an event is related to.<br>
     *                    This is also known as the Stream-Id
     * @return this builder instance
     */
    public PersistableEventBuilder setAggregateId(Object aggregateId) {
        this.aggregateId = aggregateId;
        return this;
    }

    /**
     * @param eventTypeOrName The {@link EventType} or {@link EventName} wrapped in the {@link EventTypeOrName}
     * @return this builder instance
     */
    public PersistableEventBuilder setEventTypeOrName(EventTypeOrName eventTypeOrName) {
        this.eventTypeOrName = eventTypeOrName;
        return this;
    }

    /**
     * @param event The raw non-serialized Event
     * @return this builder instance
     */
    public PersistableEventBuilder setEvent(Object event) {
        this.event = event;
        return this;
    }

    /**
     * @param eventOrder Contains the order of an event relative to the aggregate instance (the {@link #aggregateId})<br>
     *                   Each event has its own unique position within the stream, also known as the event-order,
     *                   which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
     *                   <br>
     *                   The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
     *                   This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
     *                   related to a <b>specific</b> aggregate instance (as opposed to the {@link PersistedEvent#globalEventOrder()} which contains
     *                   the order of ALL events related to a specific {@link AggregateType})
     * @return this builder instance
     */
    public PersistableEventBuilder setEventOrder(EventOrder eventOrder) {
        this.eventOrder = eventOrder;
        return this;
    }

    /**
     * @param eventRevision The revision of the {@link #setEvent(Object)} - first revision has value 1<br>
     *                      If left out/<code>null</code> the default implementation will first try and lookup if
     *                      the raw {@link #setEvent(Object)} is annotated with the {@link Revision} annotation and
     *                      read the value from there. Otherwise, it will set the value to {@link EventRevision#FIRST}<br>
     *                      See {@link PersistableEvent#resolveEventRevision(Object)}
     * @return this builder instance
     */
    public PersistableEventBuilder setEventRevision(EventRevision eventRevision) {
        this.eventRevision = eventRevision;
        return this;
    }

    /**
     * @param metaData Additional user controlled event metadata
     * @return this builder instance
     */
    public PersistableEventBuilder setMetaData(EventMetaData metaData) {
        this.metaData = metaData;
        return this;
    }

    /**
     * @param timestamp The timestamp for this Event (stored as UTC in the database)
     * @return this builder instance
     */
    public PersistableEventBuilder setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * @param causedByEventId Unique id of the Event that caused this Event to exist (aka the causal Event).<br>
     *                        This is useful for tracing the causality of Events on top of the {@link #setCorrelationId(CorrelationId)}
     * @return this builder instance
     */
    public PersistableEventBuilder setCausedByEventId(EventId causedByEventId) {
        this.causedByEventId = causedByEventId;
        return this;
    }

    /**
     * The correlation id for this event (used for tracking how events are related)<br>
     * Having this value is optional, but highly recommended
     *
     * @return this builder instance
     */
    public PersistableEventBuilder setCorrelationId(CorrelationId correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    /**
     * The tenant that the event belongs to<br>
     * Having a {@link Tenant} value associated with an Event is optional.
     *
     * @return this builder instance this builder instance
     */
    public PersistableEventBuilder setTenant(Tenant tenant) {
        this.tenant = tenant;
        return this;
    }

    public PersistableEvent build() {
        return PersistableEvent.from(eventId,
                                     aggregateType,
                                     aggregateId,
                                     eventTypeOrName,
                                     event,
                                     eventOrder,
                                     eventRevision,
                                     metaData,
                                     timestamp,
                                     causedByEventId,
                                     correlationId,
                                     tenant);
    }
}