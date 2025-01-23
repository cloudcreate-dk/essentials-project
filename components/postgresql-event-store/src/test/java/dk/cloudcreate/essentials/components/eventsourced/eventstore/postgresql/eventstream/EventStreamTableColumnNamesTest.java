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

import static org.assertj.core.api.Assertions.*;

class EventStreamTableColumnNamesTest {
    public static final String validColumnName = "validColumnName";

    @Test
    void testValidColumnNamesAreCorrectlyStored() {
        // Unique valid names for each column
        var globalOrderColumn = "GLOBAL_ORDER";
        var timestampColumn = "TIMESTAMP_COL";
        var eventIdColumn = "EVENT_ID";
        var causedByEventIdColumn = "CAUSED_BY_EVENT_ID";
        var correlationIdColumn = "CORRELATION_ID";
        var aggregateIdColumn = "AGGREGATE_ID";
        var eventOrderColumn = "EVENT_ORDER";
        var eventTypeColumn = "EVENT_TYPE";
        var eventRevisionColumn = "EVENT_REVISION";
        var eventPayloadColumn = "EVENT_PAYLOAD";
        var eventMetaDataColumn = "EVENT_METADATA";
        var tenantColumn = "TENANT_COL";

        // Creating an instance with unique valid names
        EventStreamTableColumnNames columnNames = new EventStreamTableColumnNames(
                globalOrderColumn, timestampColumn, eventIdColumn, causedByEventIdColumn,
                correlationIdColumn, aggregateIdColumn, eventOrderColumn, eventTypeColumn,
                eventRevisionColumn, eventPayloadColumn, eventMetaDataColumn, tenantColumn);

        // Asserting that each value is correctly stored
        assertThat(columnNames.globalOrderColumn).isEqualTo(globalOrderColumn.toLowerCase());
        assertThat(columnNames.timestampColumn).isEqualTo(timestampColumn.toLowerCase());
        assertThat(columnNames.eventIdColumn).isEqualTo(eventIdColumn.toLowerCase());
        assertThat(columnNames.causedByEventIdColumn).isEqualTo(causedByEventIdColumn.toLowerCase());
        assertThat(columnNames.correlationIdColumn).isEqualTo(correlationIdColumn.toLowerCase());
        assertThat(columnNames.aggregateIdColumn).isEqualTo(aggregateIdColumn.toLowerCase());
        assertThat(columnNames.eventOrderColumn).isEqualTo(eventOrderColumn.toLowerCase());
        assertThat(columnNames.eventTypeColumn).isEqualTo(eventTypeColumn.toLowerCase());
        assertThat(columnNames.eventRevisionColumn).isEqualTo(eventRevisionColumn.toLowerCase());
        assertThat(columnNames.eventPayloadColumn).isEqualTo(eventPayloadColumn.toLowerCase());
        assertThat(columnNames.eventMetaDataColumn).isEqualTo(eventMetaDataColumn.toLowerCase());
        assertThat(columnNames.tenantColumn).isEqualTo(tenantColumn.toLowerCase());
    }

    @Test
    void test_globalOrderColumn() {

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                "Select", // globalOrderColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                "invalid-name", // globalOrderColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                "name; DROP TABLE users;", // globalOrderColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void test_timestampColumn() {
        var validNameForColumnsNotTested = "validColumnName";

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validNameForColumnsNotTested,
                "Select", // timestampColumn
                validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested,
                validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validNameForColumnsNotTested,
                "invalid-name",// timestampColumn
                validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested,
                validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validNameForColumnsNotTested,
                "name; DROP TABLE users;",// timestampColumn
                validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested,
                validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested, validNameForColumnsNotTested))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testEventIdColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName,
                "Select", // eventIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName,
                "invalid-name", // eventIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName,
                "name; DROP TABLE users;", // eventIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testCausedByEventIdColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName,
                "Select",// causedByEventIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName,
                "invalid-name", // causedByEventIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName,
                "name; DROP TABLE users;",// causedByEventIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testCorrelationIdColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName,
                "Select", // correlationIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName,
                "invalid-name", // correlationIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName,
                "name; DROP TABLE users;", //  correlationIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testAggregateIdColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName,
                validColumnName,
                "Select", // aggregateIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName,
                validColumnName,
                "invalid-name", // aggregateIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName,
                validColumnName,
                "name; DROP TABLE users;", // aggregateIdColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testEventOrderColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "Select", // eventOrderColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "invalid-name", // eventOrderColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "name; DROP TABLE users;", // eventOrderColumn
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testEventTypeColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "Select", // eventTypeColumn
                validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "invalid-name", // eventTypeColumn
                validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "name; DROP TABLE users;", // eventTypeColumn
                validColumnName, validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testEventRevisionColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "Select", // eventRevisionColumn
                validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "invalid-name", // eventRevisionColumn
                validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "name; DROP TABLE users;", // eventRevisionColumn
                validColumnName, validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testEventPayloadColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "Select", // eventPayloadColumn
                validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "invalid-name", // eventPayloadColumn
                validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "name; DROP TABLE users;", // eventPayloadColumn
                validColumnName, validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testEventMetaDataColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "Select", // eventMetaDataColumn
                validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "invalid-name", // eventMetaDataColumn
                validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "name; DROP TABLE users;", // eventMetaDataColumn
                validColumnName))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testTenantColumn() {
        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "Select")) // tenantColumn
                           .isInstanceOf(InvalidTableOrColumnNameException.class)
                           .hasMessageContaining("is a reserved keyword");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "invalid-name")) // tenantColumn
                                 .isInstanceOf(InvalidTableOrColumnNameException.class)
                                 .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> new EventStreamTableColumnNames(
                validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName, validColumnName,
                "name; DROP TABLE users;")) // tenantColumn
                                            .isInstanceOf(InvalidTableOrColumnNameException.class)
                                            .hasMessageContaining("Invalid table or column name");
    }

}