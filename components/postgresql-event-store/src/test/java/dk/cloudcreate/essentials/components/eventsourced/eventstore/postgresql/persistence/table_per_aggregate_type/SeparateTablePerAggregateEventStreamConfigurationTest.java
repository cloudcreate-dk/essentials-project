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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JSONEventSerializer;
import dk.cloudcreate.essentials.components.foundation.postgresql.InvalidTableOrColumnNameException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SeparateTablePerAggregateEventStreamConfigurationTest {
    @Test
    void test_with_invalid_table_name() {
        assertThatThrownBy(() -> {
            new SeparateTablePerAggregateEventStreamConfiguration(
                    mock(AggregateType.class),
                    "invalid name",
                    mock(EventStreamTableColumnNames.class),
                    1,
                    mock(JSONEventSerializer.class),
                    mock(AggregateIdSerializer.class),
                    IdentifierColumnType.UUID,
                    IdentifierColumnType.UUID,
                    IdentifierColumnType.UUID,
                    JSONColumnType.JSON,
                    JSONColumnType.JSON,
                    mock(TenantSerializer.TenantIdSerializer.class)
            );
        }).isInstanceOf(InvalidTableOrColumnNameException.class);

        assertThatThrownBy(() -> {
            new SeparateTablePerAggregateEventStreamConfiguration(
                    mock(AggregateType.class),
                    "where",
                    mock(EventStreamTableColumnNames.class),
                    1,
                    mock(JSONEventSerializer.class),
                    mock(AggregateIdSerializer.class),
                    IdentifierColumnType.UUID,
                    IdentifierColumnType.UUID,
                    IdentifierColumnType.UUID,
                    JSONColumnType.JSON,
                    JSONColumnType.JSON,
                    mock(TenantSerializer.TenantIdSerializer.class)
            );
        }).isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() -> {
            new SeparateTablePerAggregateEventStreamConfiguration(
                    mock(AggregateType.class),
                    "; --",
                    mock(EventStreamTableColumnNames.class),
                    1,
                    mock(JSONEventSerializer.class),
                    mock(AggregateIdSerializer.class),
                    IdentifierColumnType.UUID,
                    IdentifierColumnType.UUID,
                    IdentifierColumnType.UUID,
                    JSONColumnType.JSON,
                    JSONColumnType.JSON,
                    mock(TenantSerializer.TenantIdSerializer.class)
            );
        }).isInstanceOf(InvalidTableOrColumnNameException.class);
    }

    @Test
    void test_that_EventStreamTableColumnNames_validate_is_called() {
        var eventStreamTableColumnNames = mock(EventStreamTableColumnNames.class);
        new SeparateTablePerAggregateEventStreamConfiguration(
                mock(AggregateType.class),
                "valid_name",
                eventStreamTableColumnNames,
                1,
                mock(JSONEventSerializer.class),
                mock(AggregateIdSerializer.class),
                IdentifierColumnType.UUID,
                IdentifierColumnType.UUID,
                IdentifierColumnType.UUID,
                JSONColumnType.JSON,
                JSONColumnType.JSON,
                mock(TenantSerializer.TenantIdSerializer.class)
        );

        verify(eventStreamTableColumnNames, times(1)).validate();
    }

    @Test
    void test_static_standardConfiguration_with_invalid_AggregateType_value() {
        assertThatThrownBy(() -> {
            SeparateTablePerAggregateEventStreamConfiguration.standardConfiguration(
                    AggregateType.of("invalid name"),
                    mock(JSONEventSerializer.class),
                    mock(AggregateIdSerializer.class),
                    IdentifierColumnType.UUID,
                    JSONColumnType.JSON,
                    mock(TenantSerializer.TenantIdSerializer.class));
        }).isInstanceOf(InvalidTableOrColumnNameException.class);

        assertThatThrownBy(() -> {
            SeparateTablePerAggregateEventStreamConfiguration.standardConfiguration(
                    AggregateType.of("where"),
                    mock(JSONEventSerializer.class),
                    mock(AggregateIdSerializer.class),
                    IdentifierColumnType.UUID,
                    JSONColumnType.JSON,
                    mock(TenantSerializer.TenantIdSerializer.class));
        }).isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() -> {
            SeparateTablePerAggregateEventStreamConfiguration.standardConfiguration(
                    AggregateType.of("; --"),
                    mock(JSONEventSerializer.class),
                    mock(AggregateIdSerializer.class),
                    IdentifierColumnType.UUID,
                    JSONColumnType.JSON,
                    mock(TenantSerializer.TenantIdSerializer.class));
        }).isInstanceOf(InvalidTableOrColumnNameException.class);
    }
}