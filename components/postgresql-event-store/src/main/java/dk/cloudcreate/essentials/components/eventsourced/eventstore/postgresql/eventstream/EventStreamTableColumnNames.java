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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamPersistenceStrategy;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypePersistenceStrategy;
import dk.cloudcreate.essentials.components.foundation.types.Tenant;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Defines the column names for a {@link AggregateType} event table<br>
 * You can use the {@link EventStreamTableColumnNames#EventStreamTableColumnNames} constructor or the {@link #builder()}
 * method for defining your own columns names.<br>
 * Alternatively you can use {@link #defaultColumnNames()} to use the default columns names<br>
 * <br>
 * <b>Note: All column names provided will automatically be converted to <u>lower case</u></b><br>
 * This set of column names is tailored for the {@link SeparateTablePerAggregateTypePersistenceStrategy}<br>
 * Other types of {@link AggregateEventStreamPersistenceStrategy} may require a sub-class of the {@link EventStreamTableColumnNames}
 */
public class EventStreamTableColumnNames {
    /**
     * Create a new {@link EventStreamTableColumnNamesBuilder} that allows for defining the
     * {@link EventStreamTableColumnNames} using a fluent API
     */
    public static EventStreamTableColumnNamesBuilder builder() {
        return new EventStreamTableColumnNamesBuilder();
    }

    /**
     * The name of the column that contains the global order of an event<br>
     * Contains the global order an event<br>
     * The Global Order is a sequential ever-growing number, which that tracks the order in which events have been stored in the {@link EventStore} table
     * across all {@link AggregateEventStream}'s with the same {@link AggregateType}.<br>
     * The first global-event-order has value 1, since this is the initial value for a Postgresql BIGINT IDENTITY column.
     */
    public final String globalOrderColumn;
    /**
     * The name of the column that contains the timestamp of an event
     */
    public final String timestampColumn;
    /**
     * The name of the column that contains the identifier of an event
     */
    public final String eventIdColumn;
    /**
     * The name of the column that contains the identifier of the event that caused the Event stored in the row to occur
     */
    public final String causedByEventIdColumn;
    /**
     * The name of the column that contains the correlation identifier that links correlated events together
     */
    public final String correlationIdColumn;
    /**
     * The name of the column that contains the aggregate identifier that an event is related to
     */
    public final String aggregateIdColumn;
    /**
     * The name of the column that contains the order of an event relative to the aggregate instance (the {@link #aggregateIdColumn})<br>
     * Each event has its own unique position within the stream, also known as the event-order,
     * which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
     * <br>
     * The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
     * This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
     * related to a <b>specific</b> aggregate instance (as opposed to the {@link #globalOrderColumn} which contains
     * the order of ALL events related to a specific {@link AggregateType})
     */
    public final String eventOrderColumn;
    /**
     * The name of the column contains the type of the event stored in the row<br>
     */
    public final String eventTypeColumn;
    /**
     * The name of the column that contains the revision number of the serialized event
     */
    public final String eventRevisionColumn;
    /**
     * The name of the column that contains the JSON serialized event payload
     */
    public final String eventPayloadColumn;
    /**
     * The name of the column that contains the JSON serialized metadata of an event
     */
    public final String eventMetaDataColumn;
    /**
     * The name of the column that will contain the {@link Tenant} (i.e. the tenant that the event belongs to)<br>
     * Having a {@link #tenantColumn} is mandatory, but having a {@link Tenant} value associated with an Event, through {@link PersistedEvent#tenant()} is optional.
     */
    public final String tenantColumn;

    public EventStreamTableColumnNames(String globalOrderColumn,
                                       String timestampColumn,
                                       String eventIdColumn,
                                       String causedByEventIdColumn,
                                       String correlationIdColumn,
                                       String aggregateIdColumn,
                                       String eventOrderColumn,
                                       String eventTypeColumn,
                                       String eventRevisionColumn,
                                       String eventPayloadColumn,
                                       String eventMetaDataColumn,
                                       String tenantColumn) {
        this.globalOrderColumn = requireNonNull(globalOrderColumn, "You must provide a globalOrderColumn").toLowerCase();
        this.timestampColumn = requireNonNull(timestampColumn, "You must provide a timestampColumn").toLowerCase();
        this.eventIdColumn = requireNonNull(eventIdColumn, "You must provide a eventIdColumn").toLowerCase();
        this.causedByEventIdColumn = requireNonNull(causedByEventIdColumn, "You must provide a causedByEventIdColumn").toLowerCase();
        this.correlationIdColumn = requireNonNull(correlationIdColumn, "You must provide a correlationIdColumn").toLowerCase();
        this.aggregateIdColumn = requireNonNull(aggregateIdColumn, "You must provide a aggregateIdColumn").toLowerCase();
        this.eventOrderColumn = requireNonNull(eventOrderColumn, "You must provide a eventOrderColumn").toLowerCase();
        this.eventTypeColumn = requireNonNull(eventTypeColumn, "You must provide a eventTypeNameColumn").toLowerCase();
        this.eventRevisionColumn = requireNonNull(eventRevisionColumn, "You must provide a eventRevisionColumn").toLowerCase();
        this.eventPayloadColumn = requireNonNull(eventPayloadColumn, "You must provide a eventPayloadColumn").toLowerCase();
        this.eventMetaDataColumn = requireNonNull(eventMetaDataColumn, "You must provide a eventMetaDataColumn").toLowerCase();
        this.tenantColumn = requireNonNull(tenantColumn, "You must provide a tenantColumn").toLowerCase();
    }

    public static EventStreamTableColumnNames defaultColumnNames() {
        return new EventStreamTableColumnNames("global_order",
                                               "timestamp",
                                               "event_id",
                                               "caused_by_event_id",
                                               "correlation_id",
                                               "aggregate_id",
                                               "event_order",
                                               "event_type",
                                               "event_revision",
                                               "event_payload",
                                               "event_metadata",
                                               "tenant");
    }


}
