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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;


import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.types.*;

import java.time.*;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Common interface for a Persistable Events, i.e. an Event that hasn't yet been persisted (as opposed to a {@link PersistedEvent}).<br>
 * A {@link PersistableEvent} forms a wrapper around the real business {@link #event()}.<br>
 * You can create a new instance using {@link #from(EventId, AggregateType, Object, EventTypeOrName, Object, EventOrder, EventRevision, EventMetaData, OffsetDateTime, EventId, CorrelationId, Tenant)}
 * or {@link DefaultPersistableEvent#DefaultPersistableEvent(EventId, AggregateType, Object, EventTypeOrName, Object, EventOrder, EventRevision, EventMetaData, OffsetDateTime, EventId, CorrelationId, Tenant)}
 * <br>
 * Two {@link PersistableEvent}'s are <b>equal</b> if the have the same {@link #eventId()} value<br>
 * If you want to compare the contents, please use {@link #eventId()}
 *
 * @see PersistedEvent
 */
public interface PersistableEvent {
    /**
     * Create a new builder for a {@link PersistableEvent}
     *
     * @return the new builder instance
     */
    static PersistableEventBuilder builder() {
        return new PersistableEventBuilder();
    }

    /**
     * Create a {@link PersistableEvent} - see {@link #builder()}
     *
     * @param eventId         Unique id for this Event. If left out or null an {@link EventId#random()} value will be used
     * @param aggregateType   Contains the Type of Aggregate the Event-Stream the events belongs to
     * @param aggregateId     Contains the aggregate identifier that an event is related to.<br>
     *                        This is also known as the Stream-Id
     * @param eventTypeOrName The {@link EventType} or {@link EventName} wrapped in the {@link EventTypeOrName}
     * @param event           The raw non-serialized Event
     * @param eventOrder      Contains the order of an event relative to the aggregate instance (the {@link #aggregateId})<br>
     *                        Each event has its own unique position within the stream, also known as the event-order,
     *                        which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
     *                        <br>
     *                        The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
     *                        This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
     *                        related to a <b>specific</b> aggregate instance (as opposed to the {@link PersistedEvent#globalEventOrder()} which contains
     *                        the order of ALL events related to a specific {@link AggregateType})
     * @param eventRevision   The revision of the {@link #event()} - first revision has value 1<br>
     *                        If left out/<code>null</code> the default implementation will first try and lookup if
     *                        the raw {@link #event()} is annotated with the {@link Revision} annotation and
     *                        read the value from there. Otherwise, it will set the value to {@link EventRevision#FIRST}<br>
     *                        See {@link PersistableEvent#resolveEventRevision(Object)}
     * @param metaData        Additional user controlled event metadata
     * @param timestamp       The timestamp for this Event (stored as UTC in the database). If left out/null {@link OffsetDateTime#now(Clock)} will be called
     * @param causedByEventId Unique id of the Event that caused this Event to exist (aka the causal Event).<br>
     *                        This is useful for tracing the causality of Events on top of the {@link #correlationId()}
     * @param correlationId   The correlation id for this event (used for tracking how events are related)<br>
     *                        Having this value is optional, but highly recommended
     * @param tenant          The tenant that the event belongs to<br>
     *                        Having a {@link Tenant} value associated with an Event is optional.
     */
    static PersistableEvent from(EventId eventId,
                                 AggregateType aggregateType,
                                 Object aggregateId,
                                 EventTypeOrName eventTypeOrName,
                                 Object event,
                                 EventOrder eventOrder,
                                 EventRevision eventRevision,
                                 EventMetaData metaData,
                                 OffsetDateTime timestamp,
                                 EventId causedByEventId,
                                 CorrelationId correlationId,
                                 Tenant tenant) {
        requireNonNull(event, "No event instance provided");
        return new DefaultPersistableEvent(
                eventId != null ? eventId : EventId.random(),
                aggregateType,
                aggregateId,
                eventTypeOrName,
                event,
                eventOrder,
                eventRevision != null ? eventRevision : resolveEventRevision(event),
                metaData != null ? metaData : EventMetaData.of(),
                timestamp != null ? timestamp : OffsetDateTime.now(Clock.systemUTC()),
                causedByEventId,
                correlationId,
                tenant);
    }

    /**
     * Resolve the {@link EventRevision} based on
     * the raw {@link #event()} is annotated with the {@link Revision} annotation and
     * read the value from there. Otherwise, it will set the value to {@link EventRevision#FIRST}
     *
     * @param event the raw un-serialized event
     * @return the {@link EventRevision}
     */
    static EventRevision resolveEventRevision(Object event) {
        requireNonNull(event, "No event provided");
        return resolveEventRevision(event.getClass());
    }

    /**
     * Resolve the {@link EventRevision} based on
     * the raw {@link #event()} is annotated with the {@link Revision} annotation and
     * read the value from there. Otherwise, it will set the value to {@link EventRevision#FIRST}
     *
     * @param eventType the event type
     * @return the {@link EventRevision}
     */
    static EventRevision resolveEventRevision(Class<?> eventType) {
        requireNonNull(eventType, "No event provided");
        Revision revision = eventType.getAnnotation(Revision.class);
        if (revision != null) {
            return EventRevision.of(revision.value());
        }
        return EventRevision.FIRST;
    }


    /**
     * Unique id for this Event
     */
    EventId eventId();

    /**
     * Contains the name of the Event-Stream this aggregates events belongs to
     */
    AggregateType streamName();

    /**
     * The java type for the event or alternatively the name of the Event in case the {@link #event()} is a String containing the raw JSON
     */
    EventTypeOrName eventTypeOrName();

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
     * related to a <b>specific</b> aggregate instance (as opposed to the {@link PersistedEvent#globalEventOrder()} which contains
     * the order of ALL events related to a specific {@link AggregateType})
     */
    EventOrder eventOrder();

    /**
     * The timestamp for this Event. Will always be persisted as UTC. If empty the underlying {@link EventStore}
     * will provide a value
     */
    Optional<OffsetDateTime> timestamp();

    /**
     * Unique id of the Event that caused this Event to exist (aka the causal Event).<br>
     * This is useful for tracing the the causality of Events on top of the {@link #correlationId()}
     */
    Optional<EventId> causedByEventId();

    /**
     * The correlation id for this event (used for tracking all events related).<br>
     * Having this value is optional, but highly recommended
     */
    Optional<CorrelationId> correlationId();

    /**
     * The revision of the {@link #event()} - first revision has value 1
     */
    EventRevision eventRevision();

    /**
     * Additional user controlled event metadata
     */
    EventMetaData metaData();

    /**
     * The tenant that the event belongs to<br>
     * Having a {@link Tenant} value associated with an Event is optional.
     */
    Optional<Tenant> tenant();

    /**
     * The actual Event instance that's going to be persisted<br>
     * At this point the event hasn't been serialized yet - as opposed to the {@link PersistedEvent#event()} where the business event is Serialized
     */
    Object event();

    boolean valueEquals(PersistableEvent that);

    final class DefaultPersistableEvent implements PersistableEvent {
        private final EventId                  eventId;
        private final Object                   aggregateId;
        private final AggregateType            streamName;
        private final EventTypeOrName          eventTypeOrName;
        private final Object                   event;
        private final EventOrder               eventOrder;
        private final EventRevision            eventRevision;
        private final EventMetaData            metaData;
        private final Optional<OffsetDateTime> timestamp;
        private final Optional<EventId>        causedByEventId;
        private final Optional<CorrelationId>  correlationId;
        private final Optional<Tenant>         tenant;

        public DefaultPersistableEvent(EventId eventId,
                                       AggregateType streamName,
                                       Object aggregateId,
                                       EventTypeOrName eventTypeOrName,
                                       Object event,
                                       EventOrder eventOrder,
                                       EventRevision eventRevision,
                                       EventMetaData metaData,
                                       OffsetDateTime timestamp,
                                       EventId causedByEventId,
                                       CorrelationId correlationId,
                                       Tenant tenant) {
            this.eventId = eventId != null ? eventId : EventId.random();
            this.streamName = requireNonNull(streamName, "You must supply a streamName");
            this.aggregateId = requireNonNull(aggregateId, "You must supply a aggregateId");
            this.eventTypeOrName = requireNonNull(eventTypeOrName, "You must supply an eventTypeOrName");
            this.event = requireNonNull(event, "You must supply a event instance");
            this.eventOrder = requireNonNull(eventOrder, "You must supply an eventOrder instance");
            this.eventRevision = eventRevision != null ? eventRevision : resolveEventRevision(event);
            this.tenant = Optional.ofNullable(tenant);
            this.timestamp = Optional.ofNullable(timestamp); // Time is assigned by the EventStore
            this.causedByEventId = Optional.ofNullable(causedByEventId);
            this.correlationId = Optional.ofNullable(correlationId);
            this.metaData = requireNonNull(metaData, "You must supply a metaData instance");
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
        public Optional<OffsetDateTime> timestamp() {
            return timestamp;
        }

        @Override
        public EventId eventId() {
            return eventId;
        }

        @Override
        public AggregateType streamName() {
            return streamName;
        }

        @Override
        public EventTypeOrName eventTypeOrName() {
            return eventTypeOrName;
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
        public EventMetaData metaData() {
            return metaData;
        }

        @Override
        public Optional<Tenant> tenant() {
            return tenant;
        }

        @Override
        public Object event() {
            return event;
        }

        /**
         * Compare each individual property in <code>this</code> {@link PersistableEvent} and compare them individually
         * with the provided <code>that</code> {@link PersistableEvent}
         *
         * @param that the {@link PersistableEvent} to compare this instance against
         * @return true if ALL properties in <code>this</code> instance match those in the <code>that</code> instance
         */
        @Override
        public boolean valueEquals(PersistableEvent that) {
            if (this == that)
                return true;
            if (that == null)
                return false;
            return aggregateId.equals(that.aggregateId()) &&
                    streamName.equals(that.streamName()) &&
                    eventTypeOrName.equals(that.eventTypeOrName()) &&
                    event.equals(that.event()) &&
                    eventOrder.equals(that.eventOrder()) &&
                    eventId.equals(that.eventId()) &&
                    eventRevision.equals(that.eventRevision()) &&
                    metaData.equals(that.metaData()) &&
                    timestamp.map(zonedDateTime -> zonedDateTime.toInstant().toEpochMilli()).equals(that.timestamp().map(zonedDateTime -> zonedDateTime.toInstant().toEpochMilli())) &&
                    causedByEventId.equals(that.causedByEventId()) &&
                    correlationId.equals(that.correlationId()) &&
                    tenant.equals(that.tenant());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof PersistableEvent))
                return false;
            var that = (PersistableEvent) o;
            return eventId.equals(that.eventId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId);
        }

        @Override
        public String toString() {
            return "PersistableEvent{" +
                    "eventId=" + eventId +
                    ", aggregateId=" + aggregateId +
                    ", streamName=" + streamName +
                    ", eventTypeOrName=" + eventTypeOrName +
                    ", eventOrder=" + eventOrder +
                    ", eventRevision=" + eventRevision +
                    ", event (payload type)=" + event.getClass().getName() +
                    ", metaData=" + metaData +
                    ", timestamp=" + timestamp +
                    ", causedByEventId=" + causedByEventId +
                    ", correlationId=" + correlationId +
                    ", tenant=" + tenant +
                    '}';
        }
    }
}
