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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JSONEventSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class SeparateTablePerAggregateTypePersistenceStrategyTest {
    @Test
    void test_SeparateTablePerAggregateTypePersistenceStrategy_validates_eventStreamTableName_and_EventStreamTableColumnNames_validate_is_called() {
        try (var postgresqlUtilMock = mockStatic(PostgresqlUtil.class)) {
            var eventStreamTableColumnNames = mock(EventStreamTableColumnNames.class);
            new SeparateTablePerAggregateTypePersistenceStrategy(mock(Jdbi.class),
                                                                 mock(EventStoreUnitOfWorkFactory.class),
                                                                 mock(PersistableEventMapper.class),
                                                                 mock(AggregateEventStreamConfigurationFactory.class),
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
                                                                 ));

            // Verification happens 3 times:
            // SeparateTablePerAggregateEventStreamConfiguration.<init>
            // SeparateTablePerAggregateTypePersistenceStrategy.addAggregateEventStreamConfiguration
            // SeparateTablePerAggregateTypePersistenceStrategy.initializeEventStorageFor
            postgresqlUtilMock.verify(() -> PostgresqlUtil.checkIsValidTableOrColumnName("valid_name"), times(3));
            verify(eventStreamTableColumnNames, times(3)).validate();
        }
    }

    @Test
    void test_addAggregateEventStreamConfiguration_validates_eventStreamTableName_and_EventStreamTableColumnNames_validate_is_called() {
        try (var postgresqlUtilMock = mockStatic(PostgresqlUtil.class)) {
            var eventStreamTableColumnNames = mock(EventStreamTableColumnNames.class);
            var strategy = new SeparateTablePerAggregateTypePersistenceStrategy(mock(Jdbi.class),
                                                                                mock(EventStoreUnitOfWorkFactory.class),
                                                                                mock(PersistableEventMapper.class),
                                                                                mock(AggregateEventStreamConfigurationFactory.class)
            );
            strategy.addAggregateEventStreamConfiguration(
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
                    ));

            // Verification happens 3 times:
            // SeparateTablePerAggregateEventStreamConfiguration.<init>
            // SeparateTablePerAggregateTypePersistenceStrategy.addAggregateEventStreamConfiguration
            // SeparateTablePerAggregateTypePersistenceStrategy.initializeEventStorageFor
            postgresqlUtilMock.verify(() -> PostgresqlUtil.checkIsValidTableOrColumnName("valid_name"), times(3));
            verify(eventStreamTableColumnNames, times(3)).validate();
        }
    }

    @Test
    void test_resetEventStorageFor_validates_eventStreamTableName_and_EventStreamTableColumnNames_validate_is_called() {
        try (var postgresqlUtilMock = mockStatic(PostgresqlUtil.class)) {
            var eventStreamTableColumnNames = mock(EventStreamTableColumnNames.class);
            var strategy = new SeparateTablePerAggregateTypePersistenceStrategy(mock(Jdbi.class),
                                                                                mock(EventStoreUnitOfWorkFactory.class),
                                                                                mock(PersistableEventMapper.class),
                                                                                mock(AggregateEventStreamConfigurationFactory.class)
            );
            strategy.resetEventStorageFor(
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
                    ));

            // Verification happens 3 times:
            // SeparateTablePerAggregateEventStreamConfiguration.<init>
            // SeparateTablePerAggregateTypePersistenceStrategy.resetEventStorageFor
            // SeparateTablePerAggregateTypePersistenceStrategy.initializeEventStorageFor
            postgresqlUtilMock.verify(() -> PostgresqlUtil.checkIsValidTableOrColumnName("valid_name"), times(3));
            verify(eventStreamTableColumnNames, times(3)).validate();
        }
    }
}