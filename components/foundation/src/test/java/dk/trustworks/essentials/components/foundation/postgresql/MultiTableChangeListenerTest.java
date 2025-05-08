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

package dk.trustworks.essentials.components.foundation.postgresql;

import dk.trustworks.essentials.components.foundation.json.JSONSerializer;
import dk.trustworks.essentials.reactive.EventBus;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MultiTableChangeListenerTest {

    @Test
    void listenToNotificationsFor_with_invalid_column_name() {
        try (var listener = new MultiTableChangeListener<MultiTableChangeListenerIT.TestTableNotification>(
                mock(Jdbi.class),
                Duration.ofSeconds(30), // Very long poll length to avoid errors from the polling mechanism
                mock(JSONSerializer.class),
                mock(EventBus.class),
                false
        )) {
            assertThatThrownBy(() -> listener.listenToNotificationsFor("where", MultiTableChangeListenerIT.TestTableNotification.class))
                    .isInstanceOf(InvalidTableOrColumnNameException.class);
            assertThatThrownBy(() -> listener.listenToNotificationsFor("1=1", MultiTableChangeListenerIT.TestTableNotification.class))
                    .isInstanceOf(InvalidTableOrColumnNameException.class);
            assertThatThrownBy(() -> listener.listenToNotificationsFor("1; DROP TABLE", MultiTableChangeListenerIT.TestTableNotification.class))
                    .isInstanceOf(InvalidTableOrColumnNameException.class);
        }
    }

    @Test
    void unlistenToNotificationsFor_with_invalid_column_name() {
        try (var listener = new MultiTableChangeListener<MultiTableChangeListenerIT.TestTableNotification>(
                mock(Jdbi.class),
                Duration.ofSeconds(30), // Very long poll length to avoid errors from the polling mechanism
                mock(JSONSerializer.class),
                mock(EventBus.class),
                false
        )) {
            assertThatThrownBy(() -> listener.unlistenToNotificationsFor("where"))
                    .isInstanceOf(InvalidTableOrColumnNameException.class);
            assertThatThrownBy(() -> listener.unlistenToNotificationsFor("1=1"))
                    .isInstanceOf(InvalidTableOrColumnNameException.class);
            assertThatThrownBy(() -> listener.unlistenToNotificationsFor("1; DROP TABLE"))
                    .isInstanceOf(InvalidTableOrColumnNameException.class);
        }
    }
}