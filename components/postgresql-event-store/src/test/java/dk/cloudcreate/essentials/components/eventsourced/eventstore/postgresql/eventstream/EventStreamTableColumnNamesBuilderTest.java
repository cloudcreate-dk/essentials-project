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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream;

import dk.cloudcreate.essentials.components.foundation.postgresql.InvalidTableOrColumnNameException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

class EventStreamTableColumnNamesBuilderTest {
    @Test
    void test_with_valid_column_names() {
        var globalOrderColumn     = "GLOBAL_ORDER";
        var timestampColumn       = "TIMESTAMP_COL";
        var eventIdColumn         = "EVENT_ID";
        var causedByEventIdColumn = "CAUSED_BY_EVENT_ID";
        var correlationIdColumn   = "CORRELATION_ID";
        var aggregateIdColumn     = "AGGREGATE_ID";
        var eventOrderColumn      = "EVENT_ORDER";
        var eventTypeColumn       = "EVENT_TYPE";
        var eventRevisionColumn   = "EVENT_REVISION";
        var eventPayloadColumn    = "EVENT_PAYLOAD";
        var eventMetaDataColumn   = "EVENT_METADATA";
        var tenantColumn          = "TENANT_COL";

        var columnNames = EventStreamTableColumnNames.builder()
                                                     .globalOrderColumn(globalOrderColumn)
                                                     .timestampColumn(timestampColumn)
                                                     .eventIdColumn(eventIdColumn)
                                                     .causedByEventIdColumn(causedByEventIdColumn)
                                                     .correlationIdColumn(correlationIdColumn)
                                                     .aggregateIdColumn(aggregateIdColumn)
                                                     .eventOrderColumn(eventOrderColumn)
                                                     .eventTypeColumn(eventTypeColumn)
                                                     .eventRevisionColumn(eventRevisionColumn)
                                                     .eventPayloadColumn(eventPayloadColumn)
                                                     .eventMetaDataColumn(eventMetaDataColumn)
                                                     .tenantColumn(tenantColumn)
                                                     .build();

        assertThat(columnNames.globalOrderColumn).isEqualTo(globalOrderColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.timestampColumn).isEqualTo(timestampColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.eventIdColumn).isEqualTo(eventIdColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.causedByEventIdColumn).isEqualTo(causedByEventIdColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.correlationIdColumn).isEqualTo(correlationIdColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.aggregateIdColumn).isEqualTo(aggregateIdColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.eventOrderColumn).isEqualTo(eventOrderColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.eventTypeColumn).isEqualTo(eventTypeColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.eventRevisionColumn).isEqualTo(eventRevisionColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.eventPayloadColumn).isEqualTo(eventPayloadColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.eventMetaDataColumn).isEqualTo(eventMetaDataColumn.toLowerCase(Locale.ROOT));
        assertThat(columnNames.tenantColumn).isEqualTo(tenantColumn.toLowerCase(Locale.ROOT));
    }

    @Test
    void test_invalidation_column_name_for_globalOrderColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .globalOrderColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .globalOrderColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .globalOrderColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_timestampColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .timestampColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .timestampColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .timestampColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_eventIdColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventIdColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventIdColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventIdColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_causedByEventIdColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .causedByEventIdColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .causedByEventIdColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .causedByEventIdColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_correlationIdColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .correlationIdColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .correlationIdColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .correlationIdColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_aggregateIdColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .aggregateIdColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .aggregateIdColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .aggregateIdColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_eventOrderColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventOrderColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventOrderColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventOrderColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_eventTypeColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventTypeColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventTypeColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventTypeColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_eventRevisionColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventRevisionColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventRevisionColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventRevisionColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_eventPayloadColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventPayloadColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventPayloadColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventPayloadColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_eventMetaDataColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventMetaDataColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventMetaDataColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .eventMetaDataColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_invalidation_column_name_for_tenantColumn() {
        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .tenantColumn("Select"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .tenantColumn("invalid-name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> EventStreamTableColumnNames.builder()
                                                            .tenantColumn("name; DROP TABLE users;"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

}