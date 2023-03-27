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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.TenantSerializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.foundation.types.*;

import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Configuration for the persistence of an {@link AggregateEventStream} belonging to a given {@link AggregateType}
 */
public class SeparateTablePerAggregateEventStreamConfiguration extends AggregateEventStreamConfiguration {
    /**
     * The unique name of the Postgresql table name where ONLY {@link PersistedEvent}'s related to {@link #aggregateType} are stored<br>
     * <b>Note: The table name provided will automatically be converted to <u>lower case</u></b>
     */
    public final String                      eventStreamTableName;
    /**
     * The names of the {@link #eventStreamTableName} columns - The actual implementation must be compatible with the chosen {@link AggregateEventStreamPersistenceStrategy}
     */
    public final EventStreamTableColumnNames eventStreamTableColumnNames;

    /**
     * @param aggregateType               The type of Aggregate this event stream configuration relates to
     * @param eventStreamTableName        The unique name of the Postgresql table name where ONLY {@link PersistedEvent}'s related to {@link #aggregateType} are stored<br>
     *                                    <b>Note: The table name provided will automatically be converted to <u>lower case</u></b>
     * @param eventStreamTableColumnNames The names of the {@link #eventStreamTableName} columns - The actual implementation must be compatible with the chosen {@link AggregateEventStreamPersistenceStrategy}
     * @param queryFetchSize              The SQL fetch size for Queries
     * @param jsonSerializer              The {@link JSONEventSerializer} used to serialize and deserialize {@link PersistedEvent#event()} and {@link PersistedEvent#metaData()}
     * @param aggregateIdSerializer       The serializer for the Aggregate Id
     * @param aggregateIdColumnType       The SQL Column type for the Aggregate Id<br>
     *                                    We've deliberately split {@link AggregateIdSerializer} and {@link #aggregateIdColumnType}
     *                                    as it's possible to use e.g. a {@link AggregateIdSerializer.StringIdSerializer} or {@link AggregateIdSerializer.CharSequenceTypeIdSerializer}
     *                                    which purely contains {@link java.util.UUID} string values, in which case it should be possible to
     *                                    map these to {@link IdentifierColumnType#UUID}
     * @param eventIdColumnType           The SQL column type for the {@link EventId} column
     * @param correlationIdColumnType     The SQL column type for the {@link CorrelationId} column
     * @param eventJsonColumnType         The SQL column type for the {@link JSONEventSerializer} serialized Event
     * @param eventMetadataJsonColumnType The SQL column type for the {@link JSONEventSerializer} serialized {@link EventMetaData}
     * @param tenantSerializer            The serializer for the {@link Tenant} value (or {@link NoSupportForMultiTenancySerializer} if it's a single tenant application)
     */
    public SeparateTablePerAggregateEventStreamConfiguration(AggregateType aggregateType,
                                                             String eventStreamTableName,
                                                             EventStreamTableColumnNames eventStreamTableColumnNames,
                                                             int queryFetchSize,
                                                             JSONEventSerializer jsonSerializer,
                                                             AggregateIdSerializer aggregateIdSerializer,
                                                             IdentifierColumnType aggregateIdColumnType,
                                                             IdentifierColumnType eventIdColumnType,
                                                             IdentifierColumnType correlationIdColumnType,
                                                             JSONColumnType eventJsonColumnType,
                                                             JSONColumnType eventMetadataJsonColumnType,
                                                             TenantSerializer tenantSerializer) {
        super(aggregateType,
              queryFetchSize,
              jsonSerializer,
              aggregateIdSerializer,
              aggregateIdColumnType,
              eventIdColumnType,
              correlationIdColumnType,
              eventJsonColumnType,
              eventMetadataJsonColumnType,
              tenantSerializer);
        this.eventStreamTableName = requireNonNull(eventStreamTableName, "No eventStreamTableName provided").toLowerCase();
        this.eventStreamTableColumnNames = requireNonNull(eventStreamTableColumnNames, "No eventStreamTableColumnNames provided");
    }

    /**
     * Create an event stream configuration for the given aggregate type using common configuration:<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#eventStreamTableName} = {@link AggregateType#toString()} + "_events<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#eventStreamTableColumnNames} = {@link EventStreamTableColumnNames#defaultColumnNames()}<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#queryFetchSize} = 100<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#tenantSerializer} = {@link NoSupportForMultiTenancySerializer}<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#jsonSerializer} = {@link JacksonJSONEventSerializer}<br>
     *
     * @param aggregateType                             The type of Aggregate this event stream configuration relates to
     * @param objectMapper                              The {@link ObjectMapper} that will be wrapped in a {@link JacksonJSONEventSerializer}
     * @param aggregateIdSerializer                     The serializer for the Aggregate Id
     * @param identifierColumnTypeUsedForAllIdentifiers The SQL Column type used for all identifier columns (aggregate id, event id, correlation id, etc.)
     * @param jsonColumnTypeUsedForAllJSONColumns       The SQL JSON column type used for all JSON columns (Event and {@link EventMetaData})
     * @return the event stream configuration for the given aggregate type
     */
    public static SeparateTablePerAggregateEventStreamConfiguration standardSingleTenantConfigurationUsingJackson(AggregateType aggregateType,
                                                                                                                  ObjectMapper objectMapper,
                                                                                                                  AggregateIdSerializer aggregateIdSerializer,
                                                                                                                  IdentifierColumnType identifierColumnTypeUsedForAllIdentifiers,
                                                                                                                  JSONColumnType jsonColumnTypeUsedForAllJSONColumns) {
        return standardConfigurationUsingJackson(aggregateType,
                                                 objectMapper,
                                                 aggregateIdSerializer,
                                                 identifierColumnTypeUsedForAllIdentifiers,
                                                 jsonColumnTypeUsedForAllJSONColumns,
                                                 new NoSupportForMultiTenancySerializer());
    }

    /**
     * Create an event stream configuration for the given aggregate type using common configuration:<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#eventStreamTableName} = {@link AggregateType#toString()} + "_events<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#eventStreamTableColumnNames} = {@link EventStreamTableColumnNames#defaultColumnNames()}<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#queryFetchSize} = 100<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#jsonSerializer} = {@link JacksonJSONEventSerializer}<br>
     *
     * @param aggregateType                             The type of Aggregate this event stream configuration relates to
     * @param objectMapper                              The {@link ObjectMapper} that will be wrapped in a {@link JacksonJSONEventSerializer}
     * @param aggregateIdSerializer                     The serializer for the Aggregate Id
     * @param identifierColumnTypeUsedForAllIdentifiers The SQL Column type used for all identifier columns (aggregate id, event id, correlation id, etc.)
     * @param jsonColumnTypeUsedForAllJSONColumns       The SQL JSON column type used for all JSON columns (Event and {@link EventMetaData})
     * @param tenantSerializer                          The {@link TenantSerializer} to use (e.g. {@link TenantIdSerializer})
     * @return the event stream configuration for the given aggregate type
     */
    public static SeparateTablePerAggregateEventStreamConfiguration standardConfigurationUsingJackson(AggregateType aggregateType,
                                                                                                      ObjectMapper objectMapper,
                                                                                                      AggregateIdSerializer aggregateIdSerializer,
                                                                                                      IdentifierColumnType identifierColumnTypeUsedForAllIdentifiers,
                                                                                                      JSONColumnType jsonColumnTypeUsedForAllJSONColumns,
                                                                                                      TenantSerializer<?> tenantSerializer) {
        requireNonNull(aggregateType, "No aggregateType provided");
        return new SeparateTablePerAggregateEventStreamConfiguration(aggregateType,
                                                                     aggregateType.toString() + "_events",
                                                                     EventStreamTableColumnNames.defaultColumnNames(),
                                                                     100,
                                                                     new JacksonJSONEventSerializer(objectMapper),
                                                                     aggregateIdSerializer,
                                                                     identifierColumnTypeUsedForAllIdentifiers,
                                                                     identifierColumnTypeUsedForAllIdentifiers,
                                                                     identifierColumnTypeUsedForAllIdentifiers,
                                                                     jsonColumnTypeUsedForAllJSONColumns,
                                                                     jsonColumnTypeUsedForAllJSONColumns,
                                                                     tenantSerializer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeparateTablePerAggregateEventStreamConfiguration)) return false;
        SeparateTablePerAggregateEventStreamConfiguration that = (SeparateTablePerAggregateEventStreamConfiguration) o;
        return aggregateType.equals(that.aggregateType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateType);
    }

    @Override
    public String toString() {
        return "SeparateTablePerAggregateEventStreamConfiguration{" +
                "eventStreamTableName='" + eventStreamTableName + '\'' +
                ", eventStreamTableColumnNames=" + eventStreamTableColumnNames +
                ", aggregateType=" + aggregateType +
                ", queryFetchSize=" + queryFetchSize +
                ", jsonSerializer=" + jsonSerializer +
                ", aggregateIdSerializer=" + aggregateIdSerializer +
                ", aggregateIdColumnType=" + aggregateIdColumnType +
                ", eventIdColumnType=" + eventIdColumnType +
                ", correlationIdColumnType=" + correlationIdColumnType +
                ", eventJsonColumnType=" + eventJsonColumnType +
                ", eventMetadataJsonColumnType=" + eventMetadataJsonColumnType +
                ", tenantSerializer=" + tenantSerializer +
                '}';
    }
}
