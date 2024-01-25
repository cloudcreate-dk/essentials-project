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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.foundation.types.*;

import java.util.function.Function;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Helper Factory for creating {@link SeparateTablePerAggregateEventStreamConfiguration}
 * with a shared/common configuration that's compatible with the {@link SeparateTablePerAggregateTypePersistenceStrategy}
 */
public class SeparateTablePerAggregateTypeEventStreamConfigurationFactory implements AggregateEventStreamConfigurationFactory<SeparateTablePerAggregateEventStreamConfiguration> {
    /**
     * The Function that resolves the unique name of the Postgresql table name where ONLY {@link PersistedEvent}'s related to supplied {@link AggregateType} will be stored stored<br>
     * <b>Note: The table name provided will automatically be converted to <u>lower case</u></b>
     */
    public final Function<AggregateType, String> resolveEventStreamTableName;
    /**
     * The names of the event stream columns - The actual implementation must be compatible with the chosen {@link AggregateEventStreamPersistenceStrategy}
     */
    public final EventStreamTableColumnNames     eventStreamTableColumnNames;
    /**
     * The SQL fetch size for Queries
     */
    public final int                             queryFetchSize;
    /**
     * The {@link JSONEventSerializer} used to serialize and deserialize {@link PersistedEvent#event()} and {@link PersistedEvent#metaData()}
     */
    public final JSONEventSerializer             jsonSerializer;
    /**
     * The SQL Column type for the Aggregate Id<br>
     * We've deliberately split {@link AggregateIdSerializer} and {@link #aggregateIdColumnType}
     * as it's possible to use e.g. a {@link AggregateIdSerializer.StringIdSerializer} or {@link AggregateIdSerializer.CharSequenceTypeIdSerializer}
     * which purely contains {@link java.util.UUID} string values, in which case it should be possible to
     * map these to {@link IdentifierColumnType#UUID}
     */
    public final IdentifierColumnType            aggregateIdColumnType;
    /**
     * The SQL column type for the {@link EventId} column
     */
    public final IdentifierColumnType            eventIdColumnType;
    /**
     * The SQL column type for the {@link CorrelationId} column
     */
    public final IdentifierColumnType            correlationIdColumnType;
    /**
     * The SQL column type for the {@link JSONEventSerializer} serialized Event
     */
    public final JSONColumnType                  eventJsonColumnType;
    /**
     * The SQL column type for the {@link JSONEventSerializer} serialized {@link EventMetaData}
     */
    public final JSONColumnType                  eventMetadataJsonColumnType;
    /**
     * The serializer for the {@link Tenant} value (or {@link TenantSerializer.TenantIdSerializer.NoSupportForMultiTenancySerializer} if it's a single tenant application)
     */
    public final TenantSerializer                tenantSerializer;

    /**
     * @param resolveEventStreamTableName The Function that resolves the unique name of the Postgresql table name where ONLY {@link PersistedEvent}'s related to supplied {@link AggregateType} will be stored stored<br>
     *                                    <b>Note: The table name provided will automatically be converted to <u>lower case</u></b>
     * @param eventStreamTableColumnNames Defines the names of the eventStreamTableColumnNames - The actual implementation must be compatible with the chosen {@link AggregateEventStreamPersistenceStrategy}
     * @param queryFetchSize              The SQL fetch size for Queries
     * @param jsonSerializer              The {@link JSONEventSerializer} used to serialize and deserialize {@link PersistedEvent#event()} and {@link PersistedEvent#metaData()}
     * @param aggregateIdColumnType       The SQL Column type for the Aggregate Id<br>
     *                                    We've deliberately split {@link AggregateIdSerializer} and {@link #aggregateIdColumnType}
     *                                    as it's possible to use e.g. a {@link AggregateIdSerializer.StringIdSerializer} or {@link AggregateIdSerializer.CharSequenceTypeIdSerializer}
     *                                    which purely contains {@link java.util.UUID} string values, in which case it should be possible to
     *                                    map these to {@link IdentifierColumnType#UUID}
     * @param eventIdColumnType           The SQL column type for the {@link dk.cloudcreate.essentials.components.foundation.types.EventId} column
     * @param correlationIdColumnType     The SQL column type for the {@link dk.cloudcreate.essentials.components.foundation.types.CorrelationId} column
     * @param eventJsonColumnType         The SQL column type for the {@link JSONEventSerializer} serialized Event
     * @param eventMetadataJsonColumnType The SQL column type for the {@link JSONEventSerializer} serialized {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData}
     * @param tenantSerializer            The serializer for the {@link Tenant} value (or {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.TenantSerializer.NoSupportForMultiTenancySerializer} if it's a single tenant application)
     */
    public SeparateTablePerAggregateTypeEventStreamConfigurationFactory(Function<AggregateType, String> resolveEventStreamTableName,
                                                                        EventStreamTableColumnNames eventStreamTableColumnNames,
                                                                        int queryFetchSize,
                                                                        JSONEventSerializer jsonSerializer,
                                                                        IdentifierColumnType aggregateIdColumnType,
                                                                        IdentifierColumnType eventIdColumnType,
                                                                        IdentifierColumnType correlationIdColumnType,
                                                                        JSONColumnType eventJsonColumnType,
                                                                        JSONColumnType eventMetadataJsonColumnType,
                                                                        TenantSerializer<?> tenantSerializer) {
        this.resolveEventStreamTableName = requireNonNull(resolveEventStreamTableName, "No resolveEventStreamTableName provided");
        this.eventStreamTableColumnNames = requireNonNull(eventStreamTableColumnNames, "No eventStreamTableColumnNames provided");
        this.queryFetchSize = queryFetchSize;
        this.jsonSerializer = requireNonNull(jsonSerializer, "No jsonSerializer provided");
        this.aggregateIdColumnType = requireNonNull(aggregateIdColumnType, "No aggregateIdColumnType provided");
        this.eventIdColumnType = requireNonNull(eventIdColumnType, "No eventIdColumnType provided");
        this.correlationIdColumnType = requireNonNull(correlationIdColumnType, "No correlationIdColumnType provided");
        this.eventJsonColumnType = requireNonNull(eventJsonColumnType, "No eventJsonColumnType provided");
        this.eventMetadataJsonColumnType = requireNonNull(eventMetadataJsonColumnType, "No eventMetadataJsonColumnType provided");
        this.tenantSerializer = requireNonNull(tenantSerializer, "No tenantSerializer provided");
    }

    /**
     * Create an event stream configuration factory using common configuration for all produced {@link SeparateTablePerAggregateEventStreamConfiguration}'s:<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#eventStreamTableName} = {@link AggregateType#toString()} + "_events<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#eventStreamTableColumnNames} = {@link EventStreamTableColumnNames#defaultColumnNames()}<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#queryFetchSize} = 100<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#tenantSerializer} = {@link TenantSerializer.NoSupportForMultiTenancySerializer}<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#jsonSerializer} = {@link JacksonJSONEventSerializer}<br>
     *
     * @param jsonSerializer                            The {@link JSONEventSerializer}
     * @param identifierColumnTypeUsedForAllIdentifiers The SQL Column type used for all identifier columns (aggregate id, event id, correlation id, etc.)
     * @param jsonColumnTypeUsedForAllJSONColumns       The SQL JSON column type used for all JSON columns (Event and {@link EventMetaData})
     * @return the event stream configuration factory
     */
    public static SeparateTablePerAggregateTypeEventStreamConfigurationFactory standardSingleTenantConfiguration(JSONEventSerializer jsonSerializer,
                                                                                                                 IdentifierColumnType identifierColumnTypeUsedForAllIdentifiers,
                                                                                                                 JSONColumnType jsonColumnTypeUsedForAllJSONColumns) {
        return standardConfiguration(jsonSerializer,
                                     identifierColumnTypeUsedForAllIdentifiers,
                                     jsonColumnTypeUsedForAllJSONColumns,
                                     new TenantSerializer.NoSupportForMultiTenancySerializer());
    }

    /**
     * Create an event stream configuration factory using common configuration for all produced {@link SeparateTablePerAggregateEventStreamConfiguration}'s:<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#queryFetchSize} = 100<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#tenantSerializer} = {@link TenantSerializer.NoSupportForMultiTenancySerializer}<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#jsonSerializer} = {@link JacksonJSONEventSerializer}<br>
     *
     * @param resolveEventStreamTableName               The Function that resolves the unique name of the Postgresql table name where ONLY {@link PersistedEvent}'s related to supplied {@link AggregateType} will be stored stored<br>
     *                                                  <b>Note: The table name provided will automatically be converted to <u>lower case</u></b>
     * @param eventStreamTableColumnNames               Defines the names of the eventStreamTableColumnNames - e.g. {@link EventStreamTableColumnNames#defaultColumnNames()}
     * @param jsonSerializer                            The {@link JSONEventSerializer}
     * @param identifierColumnTypeUsedForAllIdentifiers The SQL Column type used for all identifier columns (aggregate id, event id, correlation id, etc.)
     * @param jsonColumnTypeUsedForAllJSONColumns       The SQL JSON column type used for all JSON columns (Event and {@link EventMetaData})
     * @return the event stream configuration factory
     */
    public static SeparateTablePerAggregateTypeEventStreamConfigurationFactory standardSingleTenantConfiguration(Function<AggregateType, String> resolveEventStreamTableName,
                                                                                                                 EventStreamTableColumnNames eventStreamTableColumnNames,
                                                                                                                 JSONEventSerializer jsonSerializer,
                                                                                                                 IdentifierColumnType identifierColumnTypeUsedForAllIdentifiers,
                                                                                                                 JSONColumnType jsonColumnTypeUsedForAllJSONColumns) {
        return standardConfiguration(resolveEventStreamTableName,
                                     eventStreamTableColumnNames,
                                     jsonSerializer,
                                     identifierColumnTypeUsedForAllIdentifiers,
                                     jsonColumnTypeUsedForAllJSONColumns,
                                     new TenantSerializer.NoSupportForMultiTenancySerializer());
    }

    /**
     * Create an event stream configuration factory using common configuration for all produced {@link SeparateTablePerAggregateEventStreamConfiguration}'s:<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#eventStreamTableName} = {@link AggregateType#toString()} + "_events<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#eventStreamTableColumnNames} = {@link EventStreamTableColumnNames#defaultColumnNames()}<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#queryFetchSize} = 100<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#jsonSerializer} = {@link JacksonJSONEventSerializer}<br>
     *
     * @param jsonSerializer                            The {@link JSONEventSerializer}
     * @param identifierColumnTypeUsedForAllIdentifiers The SQL Column type used for all identifier columns (aggregate id, event id, correlation id, etc.)
     * @param jsonColumnTypeUsedForAllJSONColumns       The SQL JSON column type used for all JSON columns (Event and {@link EventMetaData})
     * @return the event stream configuration factory
     */
    public static SeparateTablePerAggregateTypeEventStreamConfigurationFactory standardConfiguration(JSONEventSerializer jsonSerializer,
                                                                                                     IdentifierColumnType identifierColumnTypeUsedForAllIdentifiers,
                                                                                                     JSONColumnType jsonColumnTypeUsedForAllJSONColumns,
                                                                                                     TenantSerializer<?> tenantSerializer) {
        return new SeparateTablePerAggregateTypeEventStreamConfigurationFactory(aggregateType -> aggregateType + "_events",
                                                                                EventStreamTableColumnNames.defaultColumnNames(),
                                                                                100,
                                                                                jsonSerializer,
                                                                                identifierColumnTypeUsedForAllIdentifiers,
                                                                                identifierColumnTypeUsedForAllIdentifiers,
                                                                                identifierColumnTypeUsedForAllIdentifiers,
                                                                                jsonColumnTypeUsedForAllJSONColumns,
                                                                                jsonColumnTypeUsedForAllJSONColumns,
                                                                                tenantSerializer);
    }

    /**
     * Create an event stream configuration factory using common configuration for all produced {@link SeparateTablePerAggregateEventStreamConfiguration}'s:<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#queryFetchSize} = 100<br>
     * {@link SeparateTablePerAggregateEventStreamConfiguration#jsonSerializer} = {@link JacksonJSONEventSerializer}<br>
     *
     * @param resolveEventStreamTableName               The Function that resolves the unique name of the Postgresql table name where ONLY {@link PersistedEvent}'s related to supplied {@link AggregateType} will be stored stored<br>
     *                                                  <b>Note: The table name provided will automatically be converted to <u>lower case</u></b>
     * @param eventStreamTableColumnNames               Defines the names of the eventStreamTableColumnNames - e.g. {@link EventStreamTableColumnNames#defaultColumnNames()}
     * @param jsonSerializer                            The {@link JSONEventSerializer}
     * @param identifierColumnTypeUsedForAllIdentifiers The SQL Column type used for all identifier columns (aggregate id, event id, correlation id, etc.)
     * @param jsonColumnTypeUsedForAllJSONColumns       The SQL JSON column type used for all JSON columns (Event and {@link EventMetaData})
     * @return the event stream configuration factory
     */
    public static SeparateTablePerAggregateTypeEventStreamConfigurationFactory standardConfiguration(Function<AggregateType, String> resolveEventStreamTableName,
                                                                                                     EventStreamTableColumnNames eventStreamTableColumnNames,
                                                                                                     JSONEventSerializer jsonSerializer,
                                                                                                     IdentifierColumnType identifierColumnTypeUsedForAllIdentifiers,
                                                                                                     JSONColumnType jsonColumnTypeUsedForAllJSONColumns,
                                                                                                     TenantSerializer<?> tenantSerializer) {
        return new SeparateTablePerAggregateTypeEventStreamConfigurationFactory(resolveEventStreamTableName,
                                                                                eventStreamTableColumnNames,
                                                                                100,
                                                                                jsonSerializer,
                                                                                identifierColumnTypeUsedForAllIdentifiers,
                                                                                identifierColumnTypeUsedForAllIdentifiers,
                                                                                identifierColumnTypeUsedForAllIdentifiers,
                                                                                jsonColumnTypeUsedForAllJSONColumns,
                                                                                jsonColumnTypeUsedForAllJSONColumns,
                                                                                tenantSerializer);
    }

    @Override
    public SeparateTablePerAggregateEventStreamConfiguration createEventStreamConfigurationFor(AggregateType aggregateType,
                                                                                               AggregateIdSerializer aggregateIdSerializer) {
        requireNonNull(aggregateType, "No aggregateType provided");
        return new SeparateTablePerAggregateEventStreamConfiguration(aggregateType,
                                                                     requireNonNull(resolveEventStreamTableName.apply(aggregateType), msg("Resolved EventStreamTableName for AggregateType '{}' was null", aggregateType)),
                                                                     eventStreamTableColumnNames,
                                                                     queryFetchSize,
                                                                     jsonSerializer,
                                                                     requireNonNull(aggregateIdSerializer, msg("The AggregateIdSerializer for AggregateType '{}' was null", aggregateType)),
                                                                     aggregateIdColumnType,
                                                                     eventIdColumnType,
                                                                     correlationIdColumnType,
                                                                     eventJsonColumnType,
                                                                     eventMetadataJsonColumnType,
                                                                     tenantSerializer);
    }
}
