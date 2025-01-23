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

package dk.cloudcreate.essentials.components.foundation.postgresql;

import org.jdbi.v3.core.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ListenNotifyTest {
    @Test
    void resolveTableChangeChannelName_with_invalid_tableName() {
        assertThatThrownBy(() -> ListenNotify.resolveTableChangeChannelName("where"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() -> ListenNotify.resolveTableChangeChannelName("1=1"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
    }

    @Test
    void addChangeNotificationTriggerToTable_with_invalid_tableName_and_no_column_names() {
        assertThatThrownBy(() -> {
            ListenNotify.addChangeNotificationTriggerToTable(mock(Handle.class),
                                                             "and",
                                                             List.of(ListenNotify.SqlOperation.TRUNCATE,
                                                                     ListenNotify.SqlOperation.UPDATE,
                                                                     ListenNotify.SqlOperation.DELETE,
                                                                     ListenNotify.SqlOperation.INSERT));
        }).isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() -> {
            ListenNotify.addChangeNotificationTriggerToTable(mock(Handle.class),
                                                             ";--",
                                                             List.of(ListenNotify.SqlOperation.TRUNCATE,
                                                                     ListenNotify.SqlOperation.UPDATE,
                                                                     ListenNotify.SqlOperation.DELETE,
                                                                     ListenNotify.SqlOperation.INSERT));
        }).isInstanceOf(InvalidTableOrColumnNameException.class);
    }

    @Test
    void addChangeNotificationTriggerToTable_with_valid_tableName_and_invalid_column_names() {
        assertThatThrownBy(() ->
                                   ListenNotify.addChangeNotificationTriggerToTable(mock(Handle.class),
                                                                                    "valid_name",
                                                                                    List.of(ListenNotify.SqlOperation.TRUNCATE,
                                                                                            ListenNotify.SqlOperation.UPDATE,
                                                                                            ListenNotify.SqlOperation.DELETE,
                                                                                            ListenNotify.SqlOperation.INSERT),
                                                                                    "where"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() ->
                                   ListenNotify.addChangeNotificationTriggerToTable(mock(Handle.class),
                                                                                    "valid_name",
                                                                                    List.of(ListenNotify.SqlOperation.TRUNCATE,
                                                                                            ListenNotify.SqlOperation.UPDATE,
                                                                                            ListenNotify.SqlOperation.DELETE,
                                                                                            ListenNotify.SqlOperation.INSERT),
                                                                                    "--"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() ->
                                   ListenNotify.addChangeNotificationTriggerToTable(mock(Handle.class),
                                                                                    "valid_name",
                                                                                    List.of(ListenNotify.SqlOperation.TRUNCATE,
                                                                                            ListenNotify.SqlOperation.UPDATE,
                                                                                            ListenNotify.SqlOperation.DELETE,
                                                                                            ListenNotify.SqlOperation.INSERT),
                                                                                    "test_column", "1=1"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
    }

    @Test
    void removeChangeNotificationTriggerFromTable_with_invalid_tableName() {
        assertThatThrownBy(() -> ListenNotify.removeChangeNotificationTriggerFromTable(mock(Handle.class), "where"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() -> ListenNotify.removeChangeNotificationTriggerFromTable(mock(Handle.class), "1=1"))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
    }

    @Test
    void listen_with_invalid_tableName() {
        assertThatThrownBy(() ->
                                   ListenNotify.listen(mock(Jdbi.class), "where", Duration.ofSeconds(2)))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
        assertThatThrownBy(() ->
                                   ListenNotify.listen(mock(Jdbi.class), "1 OR TRUE", Duration.ofSeconds(2)))
                .isInstanceOf(InvalidTableOrColumnNameException.class);
    }
}