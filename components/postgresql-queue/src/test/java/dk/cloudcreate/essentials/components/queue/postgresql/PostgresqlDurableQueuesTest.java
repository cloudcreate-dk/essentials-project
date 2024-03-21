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

package dk.cloudcreate.essentials.components.queue.postgresql;

import dk.cloudcreate.essentials.components.foundation.json.JSONSerializer;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.TransactionalMode;
import dk.cloudcreate.essentials.components.foundation.postgresql.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PostgresqlDurableQueuesTest {

    @Test
    void initializeWithDefaultTableName() {
        var durableQueues = new PostgresqlDurableQueues(
                mock(HandleAwareUnitOfWorkFactory.class),
                mock(JSONSerializer.class),
                PostgresqlDurableQueues.DEFAULT_DURABLE_QUEUES_TABLE_NAME,
                mock(MultiTableChangeListener.class),
                null,
                mock(TransactionalMode.class),
                mock(Duration.class));
        assertThat(durableQueues.getSharedQueueTableName()).isEqualTo(PostgresqlDurableQueues.DEFAULT_DURABLE_QUEUES_TABLE_NAME);
    }

    @Test
    void initializeWithOverriddenTableName() {
        var overriddenTableName = "overridden_table_name";
        var durableQueues = new PostgresqlDurableQueues(
                mock(HandleAwareUnitOfWorkFactory.class),
                mock(JSONSerializer.class),
                overriddenTableName,
                mock(MultiTableChangeListener.class),
                null,
                mock(TransactionalMode.class),
                mock(Duration.class));
        assertThat(durableQueues.getSharedQueueTableName()).isEqualTo(overriddenTableName);
    }

    @Test
    void initializeWithInvalidOverriddenTableName() {
        assertThatThrownBy(() ->
                                   new PostgresqlDurableQueues(
                                           mock(HandleAwareUnitOfWorkFactory.class),
                                           mock(JSONSerializer.class),
                                           "where",
                                           mock(MultiTableChangeListener.class),
                                           null,
                                           mock(TransactionalMode.class),
                                           mock(Duration.class)))
                .isInstanceOf(InvalidTableOrColumnNameException.class);

        assertThatThrownBy(() ->
                                   new PostgresqlDurableQueues(
                                           mock(HandleAwareUnitOfWorkFactory.class),
                                           mock(JSONSerializer.class),
                                           "OR 1=1",
                                           mock(MultiTableChangeListener.class),
                                           null,
                                           mock(TransactionalMode.class),
                                           mock(Duration.class)))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
    }
}