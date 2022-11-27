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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream;


import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.foundation.types.Tenant;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Builder for the {@link EventStreamTableColumnNames} type
 */
public class EventStreamTableColumnNamesBuilder {
    private String globalOrderColumn;
    private String timestampColumn;
    private String eventIdColumn;
    private String aggregateIdColumn;
    private String eventOrderColumn;
    private String eventTypeColumn;
    private String eventRevisionColumn;
    private String eventPayloadColumn;
    private String eventMetaDataColumn;
    private String causedByEventIdColumn;
    private String correlationIdColumn;
    private String tenantColumn;

    /**
     * The name of the column that contains the global order of an event<br>
     * Contains the global order an event<br>
     * The Global Order is a sequential ever-growing number, which that tracks the order in which events have been stored in the {@link EventStore} table
     * across all {@link AggregateEventStream}'s with the same {@link AggregateType}.<br>
     * The first global-event-order has value 1, since this is the initial value for a Postgresql BIGINT IDENTITY column.
     *
     * @param globalOrderColumn The name of the column that contains the global order of an event
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder globalOrderColumn(String globalOrderColumn) {
        this.globalOrderColumn = requireNonNull(globalOrderColumn, "You must supply a globalOrderColumn");
        return this;
    }

    /**
     * The name of the column that contains the timestamp of an event
     * @param timestampColumn The name of the column that contains the timestamp of an event
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder timestampColumn(String timestampColumn) {
        this.timestampColumn = requireNonNull(timestampColumn, "You must supply a timestampColumn");
        return this;
    }

    /**
     * The name of the column that contains the identifier of an event
     * @param eventIdColumn The name of the column that contains the identifier of an event
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder eventIdColumn(String eventIdColumn) {
        this.eventIdColumn = requireNonNull(eventIdColumn, "You must supply a eventIdColumn");
        return this;
    }

    /**
     * The name of the column that contains the identifier of the event that caused the Event stored in the row to occur
     * @param causedByEventIdColumn The name of the column that contains the identifier of the event that caused the Event stored in the row to occur
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder causedByEventIdColumn(String causedByEventIdColumn) {
        this.causedByEventIdColumn = requireNonNull(causedByEventIdColumn, "You must supply a causedByEventIdColumn");
        return this;
    }

    /**
     * The name of the column that contains the aggregate identifier that an event is related to
     * @param aggregateIdColumn The name of the column that contains the aggregate identifier that an event is related to
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder aggregateIdColumn(String aggregateIdColumn) {
        this.aggregateIdColumn = requireNonNull(aggregateIdColumn, "You must supply a aggregateIdColumn");
        return this;
    }

    /**
     * The name of the column that contains the order of an event relative to the aggregate instance (the {@link #aggregateIdColumn})<br>
     * Each event has its own unique position within the stream, also known as the event-order,
     * which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
     * <br>
     * The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
     * This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
     * related to a <b>specific</b> aggregate instance (as opposed to the {@link #globalOrderColumn} which contains
     * the order of ALL events related to a specific {@link AggregateType})
     * @param eventOrderColumn The name of the column that contains the order of an event relative to the aggregate instance (the {@link #aggregateIdColumn})
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder eventOrderColumn(String eventOrderColumn) {
        this.eventOrderColumn = requireNonNull(eventOrderColumn, "You must supply a eventOrderColumn");
        return this;
    }

    /**
     * The name of the column contains the type of the event stored in the row<br>
     * @param eventTypeColumn The name of the column contains the type of the event stored in the row<br>
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder eventTypeColumn(String eventTypeColumn) {
        this.eventTypeColumn = requireNonNull(eventTypeColumn, "You must supply a eventTypeColumn");
        return this;
    }

    /**
     * The name of the column that contains the revision number of the serialized event
     * @param eventRevisionColumn The name of the column that contains the revision number of the serialized event
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder eventRevisionColumn(String eventRevisionColumn) {
        this.eventRevisionColumn = requireNonNull(eventRevisionColumn, "You must supply a eventRevisionColumn");
        return this;
    }

    /**
     * The name of the column that contains the JSON serialized event payload
     * @param eventPayloadColumn The name of the column that contains the JSON serialized event payload
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder eventPayloadColumn(String eventPayloadColumn) {
        this.eventPayloadColumn = requireNonNull(eventPayloadColumn, "You must supply a eventPayloadColumn");
        return this;
    }

    /**
     * The name of the column that contains the JSON serialized metadata of an event
     * @param eventMetaDataColumn The name of the column that contains the JSON serialized metadata of an event
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder eventMetaDataColumn(String eventMetaDataColumn) {
        this.eventMetaDataColumn = requireNonNull(eventMetaDataColumn, "You must supply a eventMetaDataColumn");
        return this;
    }

    /**
     * The name of the column that contains the correlation identifier that links correlated events together
     * @param correlationIdColumn The name of the column that contains the correlation identifier that links correlated events together
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder correlationIdColumn(String correlationIdColumn) {
        this.correlationIdColumn = requireNonNull(correlationIdColumn, "You must supply a correlationIdColumn");
        return this;
    }

    /**
     * The name of the column that will contain the {@link Tenant} (i.e. the tenant that the event belongs to)<br>
     * Having a {@link #tenantColumn} is mandatory, but having a {@link Tenant} value associated with an Event, through {@link PersistedEvent#tenant()} is optional.
     *
     * @param tenantColumn The name of the column that will contain the {@link Tenant} (i.e. the tenant that the event belongs to)
     * @return this builder instance
     */
    public EventStreamTableColumnNamesBuilder tenantColumn(String tenantColumn) {
        this.tenantColumn = requireNonNull(tenantColumn, "You must supply a tenantColumn");
        return this;
    }

    /**
     * Convert the builder to an {@link EventStreamTableColumnNames} instance
     * @return an {@link EventStreamTableColumnNames} instance with all the builder values applied
     */
    public EventStreamTableColumnNames build() {
        return new EventStreamTableColumnNames(globalOrderColumn,
                                               timestampColumn,
                                               eventIdColumn,
                                               causedByEventIdColumn,
                                               correlationIdColumn,
                                               aggregateIdColumn,
                                               eventOrderColumn,
                                               eventTypeColumn,
                                               eventRevisionColumn,
                                               eventPayloadColumn,
                                               eventMetaDataColumn,
                                               tenantColumn);
    }
}