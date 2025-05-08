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

package dk.trustworks.essentials.components.distributed.fencedlock.postgresql;

import dk.trustworks.essentials.components.foundation.postgresql.InvalidTableOrColumnNameException;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PostgresqlFencedLockStorageTest {
    @Test
    void initializeWithDefaultTableName() {
        var storage = new PostgresqlFencedLockStorage(mock(Jdbi.class));
        assertThat(storage.getFencedLocksTableName()).isEqualTo(PostgresqlFencedLockStorage.DEFAULT_FENCED_LOCKS_TABLE_NAME);
    }

    @Test
    void initializeWithOverriddenTableName() {
        var overriddenTableName = "overridden_table_name";
        var storage             = new PostgresqlFencedLockStorage(mock(Jdbi.class), overriddenTableName);
        assertThat(storage.getFencedLocksTableName()).isEqualTo(overriddenTableName);
    }

    @Test
    void initializeWithInvalidOverriddenTableName() {
        assertThatThrownBy(() -> new PostgresqlFencedLockStorage(mock(Jdbi.class), "where"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() -> new PostgresqlFencedLockStorage(mock(Jdbi.class), "; --"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
    }
}