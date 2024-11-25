/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.foundation.postgresql.InvalidTableOrColumnNameException;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PostgresqlDurableSubscriptionRepositoryTest {

    @Test
    void test_with_invalid_durableSubscriptionsTableName() {
        assertThatThrownBy(() -> {
            new PostgresqlDurableSubscriptionRepository(mock(Jdbi.class),
                                                        mock(EventStore.class),
                                                        "invalid name");
        }).isInstanceOf(InvalidTableOrColumnNameException.class)
          .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> {
            new PostgresqlDurableSubscriptionRepository(mock(Jdbi.class),
                                                        mock(EventStore.class),
                                                        "name; DROP TABLE users;");
        }).isInstanceOf(InvalidTableOrColumnNameException.class)
          .hasMessageContaining("Invalid table or column name");

        assertThatThrownBy(() -> {
            new PostgresqlDurableSubscriptionRepository(mock(Jdbi.class),
                                                        mock(EventStore.class),
                                                        "drop");
        }).isInstanceOf(InvalidTableOrColumnNameException.class)
          .hasMessageContaining("is a reserved keyword");
    }
}