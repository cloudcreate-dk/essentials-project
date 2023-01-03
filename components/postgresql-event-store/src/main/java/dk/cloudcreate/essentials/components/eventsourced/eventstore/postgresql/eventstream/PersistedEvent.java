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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.types.*;

import java.time.OffsetDateTime;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Common interface for an Event that has been persisted (as opposed to a {@link PersistableEvent} which is an Event that
 * haven't yet been persisted by the {@link EventStore})<br>
 * You can create a new instance using {@link #from(EventId, AggregateType, Object, EventJSON, EventOrder, EventRevision, GlobalEventOrder, EventMetaDataJSON, OffsetDateTime, Optional, Optional, Optional)}
 * or {@link DefaultPersistedEvent#DefaultPersistedEvent(EventId, AggregateType, Object, EventJSON, EventOrder, EventRevision, GlobalEventOrder, EventMetaDataJSON, OffsetDateTime, Optional, Optional, Optional)}
 * <br>
 * Two {@link PersistedEvent}'s are <b>equal</b> if the have the same {@link #eventId()} value<br>
 * If you want to compare the contents, please use {@link #valueEquals(PersistedEvent)}
 *
 * @see PersistableEvent
 */
public interface PersistedEvent {
    /**
     * @param eventId          Unique id for this Event
     * @param aggregateType    Contains the Type of Aggregate the Event-Stream the events belongs to
     * @param aggregateId      Contains the aggregate identifier that an event is related to.<br>
     *                         This is also known as the Stream-Id
     * @param event            The serialized Event that was persisted (aka the payload)
     * @param eventOrder       Contains the order of an event relative to the aggregate instance (the {@link #aggregateId})<br>
     *                         Each event has its own unique position within the stream, also known as the event-order,
     *                         which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
     *                         <br>
     *                         The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
     *                         This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
     *                         related to a <b>specific</b> aggregate instance (as opposed to the {@link #globalEventOrder()} which contains
     *                         the order of ALL events related to a specific {@link AggregateType})
     * @param eventRevision    The revision of the {@link #event()} - first revision has value 1
     * @param globalEventOrder Contains the global order an event<br>
     *                         The Global Order is a sequential ever-growing number, which that tracks the order in which events have been stored in the {@link EventStore} table
     *                         across all {@link AggregateEventStream}'s with the same {@link AggregateType}.<br>
     *                         The first global-event-order has value 1, since this is the initial value for a Postgresql BIGINT IDENTITY column.
     * @param metaData         Additional user controlled event metadata
     * @param timestamp        The timestamp for this Event (stored as UTC in the database)
     * @param causedByEventId  Unique id of the Event that caused this Event to exist (aka the causal Event).<br>
     *                         This is useful for tracing the causality of Events on top of the {@link #correlationId()}
     * @param correlationId    The correlation id for this event (used for tracking how events are related)<br>
     *                         Having this value is optional, but highly recommended
     * @param tenant           The tenant that the event belongs to<br>
     *                         Having a {@link Tenant} value associated with an Event is optional.
     * @return new {@link PersistedEvent} instance
     */
    static PersistedEvent from(EventId eventId,
                               AggregateType aggregateType,
                               Object aggregateId,
                               EventJSON event,
                               EventOrder eventOrder,
                               EventRevision eventRevision,
                               GlobalEventOrder globalEventOrder,
                               EventMetaDataJSON metaData,
                               OffsetDateTime timestamp,
                               Optional<EventId> causedByEventId,
                               Optional<CorrelationId> correlationId,
                               Optional<Tenant> tenant) {
        return new PersistedEvent.DefaultPersistedEvent(eventId,
                                                        aggregateType,
                                                        aggregateId,
                                                        event,
                                                        eventOrder,
                                                        eventRevision,
                                                        globalEventOrder,
                                                        metaData,
                                                        timestamp,
                                                        causedByEventId,
                                                        correlationId,
                                                        tenant);
    }

    /**
     * @param persistableEvent        The persistable event to convert to a {@link PersistedEvent}
     * @param aggregateType           Contains the Type of Aggregate the Event-Stream the events belongs to
     * @param globalEventOrder        Contains the global order an event<br>
     *                                The Global Order is a sequential ever-growing number, which that tracks the order in which events have been stored in the {@link EventStore} table
     *                                across all {@link AggregateEventStream}'s with the same {@link AggregateType}.<br>
     *                                The first global-event-order has value 1, since this is the initial value for a Postgresql BIGINT IDENTITY column.
     * @param serializedEvent         The serialized Event that was persisted (aka the payload)
     * @param serializedEventMetaData The serialized {@link EventMetaData}
     * @param eventTimestamp          The timestamp for this Event (stored as UTC in the database)
     * @return new {@link PersistedEvent} instance
     */
    static PersistedEvent from(PersistableEvent persistableEvent,
                               AggregateType aggregateType,
                               GlobalEventOrder globalEventOrder,
                               EventJSON serializedEvent,
                               EventMetaDataJSON serializedEventMetaData,
                               OffsetDateTime eventTimestamp) {
        requireNonNull(persistableEvent, "No persistableEvent provided");
        return from(persistableEvent.eventId(),
                    aggregateType,
                    persistableEvent.aggregateId(),
                    serializedEvent,
                    persistableEvent.eventOrder(),
                    persistableEvent.eventRevision(),
                    globalEventOrder,
                    serializedEventMetaData,
                    eventTimestamp,
                    persistableEvent.causedByEventId(),
                    persistableEvent.correlationId(),
                    persistableEvent.tenant()
                   );
    }

    /**
     * Unique id for this Event
     */
    EventId eventId();

    /**
     * Contains the type of Aggregate the Event-Stream the events belongs to
     */
    AggregateType aggregateType();

    /**
     * Contains the aggregate identifier that an event is related to.<br>
     * This is also known as the Stream-Id
     */
    Object aggregateId();

    /**
     * Contains the order of an event relative to the aggregate instance (the {@link #aggregateId})<br>
     * Each event has its own unique position within the stream, also known as the event-order,
     * which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
     * <br>
     * The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
     * This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
     * related to a <b>specific</b> aggregate instance (as opposed to the {@link #globalEventOrder()} which contains
     * the order of ALL events related to a specific {@link AggregateType})
     */
    EventOrder eventOrder();

    /**
     * Contains the global order an event<br>
     * The Global Order is a sequential ever-growing number, which that tracks the order in which events have been stored in the {@link EventStore} table
     * across all {@link AggregateEventStream}'s with the same {@link AggregateType}.<br>
     * The first global-event-order has value 1, since this is the initial value for a Postgresql BIGINT IDENTITY column.
     */
    GlobalEventOrder globalEventOrder();

    /**
     * Additional user controlled event metadata
     */
    EventMetaDataJSON metaData();

    /**
     * The serialized Event that was persisted (aka the payload)
     */
    EventJSON event();

    /**
     * The timestamp for this Event (stored as UTC in the database)
     */
    OffsetDateTime timestamp();

    /**
     * Unique id of the Event that caused this Event to exist (aka the causal Event).<br>
     * This is useful for tracing the causality of Events on top of the {@link #correlationId()}
     */
    Optional<EventId> causedByEventId();

    /**
     * The correlation id for this event (used for tracking how events are related)<br>
     * Having this value is optional, but highly recommended
     */
    Optional<CorrelationId> correlationId();

    /**
     * The revision of the {@link #event()} - first revision has value 1
     */
    EventRevision eventRevision();

    /**
     * The tenant that the event belongs to<br>
     * Having a {@link Tenant} value associated with an Event is optional.
     */
    Optional<Tenant> tenant();

    /**
     * Compare each individual property in <code>this</code> {@link PersistedEvent} and compare them individually
     * with the provided <code>that</code> {@link PersistedEvent}
     *
     * @param that the {@link PersistedEvent} to compare this instance against
     * @return true if ALL properties in <code>this</code> instance match those in the <code>that</code> instance
     */
    boolean valueEquals(PersistedEvent that);

    class DefaultPersistedEvent implements PersistedEvent {
        private final EventId                 eventId;
        private final Object                  aggregateId;
        private final AggregateType           streamName;
        private final EventJSON               event;
        private final EventOrder              eventOrder;
        private final GlobalEventOrder        globalEventOrder;
        private final EventRevision           eventRevision;
        private final EventMetaDataJSON       metaData;
        private final OffsetDateTime          timestamp;
        private final Optional<EventId>       causedByEventId;
        private final Optional<CorrelationId> correlationId;
        private final Optional<Tenant>        tenant;

        public DefaultPersistedEvent(EventId eventId,
                                     AggregateType streamName,
                                     Object aggregateId,
                                     EventJSON event,
                                     EventOrder eventOrder,
                                     EventRevision eventRevision,
                                     GlobalEventOrder globalEventOrder,
                                     EventMetaDataJSON metaData,
                                     OffsetDateTime timestamp,
                                     Optional<EventId> causedByEventId,
                                     Optional<CorrelationId> correlationId,
                                     Optional<Tenant> tenant) {
            this.eventId = requireNonNull(eventId, "You must supply a eventId");
            this.streamName = requireNonNull(streamName, "You must supply a streamName");
            this.aggregateId = requireNonNull(aggregateId, "You must supply a aggregateId");
            this.event = requireNonNull(event, "You must supply a event instance");
            this.eventOrder = requireNonNull(eventOrder, "You must supply an eventOrder instance");
            this.eventRevision = requireNonNull(eventRevision, "You must supply an eventRevision instance");
            this.globalEventOrder = requireNonNull(globalEventOrder, "You must supply a globalEventOrder instance");
            this.timestamp = requireNonNull(timestamp, "You must supply a timestamp");
            this.metaData = requireNonNull(metaData, "You must supply a metaData instance");
            this.causedByEventId = requireNonNull(causedByEventId, "You must supply an Optional<EventId> causedByEventId instance");
            this.correlationId = requireNonNull(correlationId, "You must supply an Optional<CorrelationId> correlationId instance");
            this.tenant = requireNonNull(tenant, "You must supply an Optional<Tenant> tenant instance");
        }

        @Override
        public Object aggregateId() {
            return aggregateId;
        }

        @Override
        public EventOrder eventOrder() {
            return eventOrder;
        }

        @Override
        public GlobalEventOrder globalEventOrder() {
            return globalEventOrder;
        }

        @Override
        public OffsetDateTime timestamp() {
            return timestamp;
        }

        @Override
        public EventId eventId() {
            return eventId;
        }

        @Override
        public AggregateType aggregateType() {
            return streamName;
        }

        @Override
        public Optional<EventId> causedByEventId() {
            return causedByEventId;
        }

        @Override
        public Optional<CorrelationId> correlationId() {
            return correlationId;
        }

        @Override
        public EventRevision eventRevision() {
            return eventRevision;
        }

        @Override
        public EventMetaDataJSON metaData() {
            return metaData;
        }

        @Override
        public Optional<Tenant> tenant() {
            return tenant;
        }

        @Override
        public boolean valueEquals(PersistedEvent that) {
            if (this == that) return true;
            if (that == null) return false;
            return eventId.equals(that.eventId()) &&
                    aggregateId.equals(that.aggregateId()) &&
                    streamName.equals(that.aggregateType()) &&
                    event.equals(that.event()) &&
                    eventOrder.equals(that.eventOrder()) &&
                    globalEventOrder.equals(that.globalEventOrder()) &&
                    eventRevision.equals(that.eventRevision()) &&
                    metaData.equals(that.metaData()) &&
                    timestamp.toInstant().toEpochMilli() == that.timestamp().toInstant().toEpochMilli() &&
                    causedByEventId.equals(that.causedByEventId()) &&
                    correlationId.equals(that.correlationId()) &&
                    tenant.equals(that.tenant());
        }

        @Override
        public EventJSON event() {
            return event;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PersistedEvent)) return false;
            var that = (PersistedEvent) o;
            return eventId.equals(that.eventId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId);
        }

        @Override
        public String toString() {
            return "PersistedEvent{" +
                    "eventId=" + eventId +
                    ", aggregateId=" + aggregateId +
                    ", streamName=" + streamName +
                    ", eventOrder=" + eventOrder +
                    ", eventRevision=" + eventRevision +
                    ", globalEventOrder=" + globalEventOrder +
                    ", event=" + event +
                    ", metaData=" + metaData +
                    ", timestamp=" + timestamp +
                    ", causedByEventId=" + causedByEventId +
                    ", correlationId=" + correlationId +
                    ", tenant=" + tenant +
                    '}';
        }
    }
}
