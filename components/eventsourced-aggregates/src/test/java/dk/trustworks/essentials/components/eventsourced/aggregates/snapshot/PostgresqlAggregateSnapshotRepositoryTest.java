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

package dk.trustworks.essentials.components.eventsourced.aggregates.snapshot;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JSONEventSerializer;
import dk.trustworks.essentials.components.foundation.postgresql.InvalidTableOrColumnNameException;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PostgresqlAggregateSnapshotRepositoryTest {
    @Test
    void test_with_invalid_table_names() {
        assertThatThrownBy(() -> {
            new PostgresqlAggregateSnapshotRepository(mock(ConfigurableEventStore.class),
                                                      mock(HandleAwareUnitOfWorkFactory.class),
                                                      Optional.of("where"),
                                                      mock(JSONEventSerializer.class),
                                                      mock(AddNewAggregateSnapshotStrategy.class),
                                                      mock(AggregateSnapshotDeletionStrategy.class));
        }).isInstanceOf(InvalidTableOrColumnNameException.class);

        assertThatThrownBy(() -> {
            new PostgresqlAggregateSnapshotRepository(mock(ConfigurableEventStore.class),
                                                      mock(HandleAwareUnitOfWorkFactory.class),
                                                      Optional.of("1; DROP TABLE name --"),
                                                      mock(JSONEventSerializer.class),
                                                      mock(AddNewAggregateSnapshotStrategy.class),
                                                      mock(AggregateSnapshotDeletionStrategy.class));
        }).isInstanceOf(InvalidTableOrColumnNameException.class);

        assertThatThrownBy(() -> {
            new PostgresqlAggregateSnapshotRepository(mock(ConfigurableEventStore.class),
                                                      mock(HandleAwareUnitOfWorkFactory.class),
                                                      Optional.of("1=1--"),
                                                      mock(JSONEventSerializer.class),
                                                      mock(AddNewAggregateSnapshotStrategy.class),
                                                      mock(AggregateSnapshotDeletionStrategy.class));
        }).isInstanceOf(InvalidTableOrColumnNameException.class);

    }
}