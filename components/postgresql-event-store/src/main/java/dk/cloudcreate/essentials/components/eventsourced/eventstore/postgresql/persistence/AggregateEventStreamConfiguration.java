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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JSONSerializer;
import dk.cloudcreate.essentials.components.foundation.types.*;

import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Configuration for the persistence of an {@link AggregateEventStream} belonging to a given {@link AggregateType}
 */
public class AggregateEventStreamConfiguration {
    /**
     * The type of Aggregate this event stream configuration relates to
     */
    public final AggregateType         aggregateType;
    /**
     * The SQL fetch size for Queries
     */
    public final int                   queryFetchSize;
    /**
     * The {@link JSONSerializer} used to serialize and deserialize {@link PersistedEvent#event()} and {@link PersistedEvent#metaData()}
     */
    public final JSONSerializer        jsonSerializer;
    /**
     * The serializer for the Aggregate Id
     */
    public final AggregateIdSerializer aggregateIdSerializer;
    /**
     * The SQL Column type for the Aggregate Id<br>
     * We've deliberately split {@link AggregateIdSerializer} and {@link #aggregateIdColumnType}
     * as it's possible to use e.g. a {@link StringIdSerializer} or {@link CharSequenceTypeIdSerializer}
     * which purely contains {@link java.util.UUID} string values, in which case it should be possible to
     * map these to {@link IdentifierColumnType#UUID}
     */
    public final IdentifierColumnType  aggregateIdColumnType;
    /**
     * The SQL column type for the {@link EventId} column
     */
    public final IdentifierColumnType  eventIdColumnType;
    /**
     * The SQL column type for the {@link CorrelationId} column
     */
    public final IdentifierColumnType  correlationIdColumnType;
    /**
     * The SQL column type for the {@link JSONSerializer} serialized Event
     */
    public final JSONColumnType        eventJsonColumnType;
    /**
     * The SQL column type for the {@link JSONSerializer} serialized {@link EventMetaData}
     */
    public final JSONColumnType        eventMetadataJsonColumnType;
    /**
     * The serializer for the {@link Tenant} value (or {@link TenantSerializer.TenantIdSerializer.NoSupportForMultiTenancySerializer} if it's a single tenant application)
     */
    public final TenantSerializer      tenantSerializer;

    /**
     * @param aggregateType               The type of Aggregate this event stream configuration relates to
     * @param queryFetchSize              The SQL fetch size for Queries
     * @param jsonSerializer              The {@link JSONSerializer} used to serialize and deserialize {@link PersistedEvent#event()} and {@link PersistedEvent#metaData()}
     * @param aggregateIdSerializer       The serializer for the Aggregate Id
     * @param aggregateIdColumnType       The SQL Column type for the Aggregate Id<br>
     *                                    We've deliberately split {@link AggregateIdSerializer} and {@link #aggregateIdColumnType}
     *                                    as it's possible to use e.g. a {@link StringIdSerializer} or {@link CharSequenceTypeIdSerializer}
     *                                    which purely contains {@link java.util.UUID} string values, in which case it should be possible to
     *                                    map these to {@link IdentifierColumnType#UUID}
     * @param eventIdColumnType           The SQL column type for the {@link EventId} column
     * @param correlationIdColumnType     The SQL column type for the {@link CorrelationId} column
     * @param eventJsonColumnType         The SQL column type for the {@link JSONSerializer} serialized Event
     * @param eventMetadataJsonColumnType The SQL column type for the {@link JSONSerializer} serialized {@link EventMetaData}
     * @param tenantSerializer            The serializer for the {@link Tenant} value (or {@link TenantSerializer.TenantIdSerializer.NoSupportForMultiTenancySerializer} if it's a single tenant application)
     */
    public AggregateEventStreamConfiguration(AggregateType aggregateType,
                                             int queryFetchSize,
                                             JSONSerializer jsonSerializer,
                                             AggregateIdSerializer aggregateIdSerializer,
                                             IdentifierColumnType aggregateIdColumnType,
                                             IdentifierColumnType eventIdColumnType,
                                             IdentifierColumnType correlationIdColumnType,
                                             JSONColumnType eventJsonColumnType,
                                             JSONColumnType eventMetadataJsonColumnType,
                                             TenantSerializer tenantSerializer) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.queryFetchSize = queryFetchSize;
        this.jsonSerializer = requireNonNull(jsonSerializer, "No jsonSerializer provided");
        this.aggregateIdSerializer = requireNonNull(aggregateIdSerializer, "No aggregateIdSerializer provided");
        this.aggregateIdColumnType = requireNonNull(aggregateIdColumnType, "No aggregateIdColumnType provided");
        this.eventIdColumnType = requireNonNull(eventIdColumnType, "No eventIdColumnType provided");
        this.correlationIdColumnType = requireNonNull(correlationIdColumnType, "No correlationIdColumnType provided");
        this.eventJsonColumnType = requireNonNull(eventJsonColumnType, "No eventJsonColumnType provided");
        this.eventMetadataJsonColumnType = requireNonNull(eventMetadataJsonColumnType, "No eventMetadataJsonColumnType provided");
        this.tenantSerializer = requireNonNull(tenantSerializer, "No tenantSerializer provided");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AggregateEventStreamConfiguration)) return false;
        AggregateEventStreamConfiguration that = (AggregateEventStreamConfiguration) o;
        return aggregateType.equals(that.aggregateType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateType);
    }

    @Override
    public String toString() {
        return "AggregateEventStreamConfiguration{" +
                "aggregateType=" + aggregateType +
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
