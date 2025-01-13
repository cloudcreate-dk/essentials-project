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

package dk.cloudcreate.essentials.components.foundation.messaging;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueuedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class MessageDeliveryErrorHandlerBuilderTest {
    @Test
    void test_builder_with_only_stop_redelivery_on() {
        var errorHandler = MessageDeliveryErrorHandler.builder()
                                                      .stopRedeliveryOn(IllegalStateException.class, IllegalArgumentException.class)
                                                      .build();

        assertThat(errorHandler.isPermanentError(Mockito.mock(QueuedMessage.class),
                                                 new RuntimeException()))
                .isFalse();
        assertThat(errorHandler.isPermanentError(Mockito.mock(QueuedMessage.class),
                                                 new IllegalStateException()))
                .isTrue();
        assertThat(errorHandler.isPermanentError(Mockito.mock(QueuedMessage.class),
                                                 new IllegalArgumentException()))
                .isTrue();
    }

    @Test
    void test_builder_with_both_always_redeliver_and_stop_redelivery_on() {
        var errorHandler = MessageDeliveryErrorHandler.builder()
                                                      .stopRedeliveryOn(IllegalStateException.class, IllegalArgumentException.class)
                                                      .alwaysRetryOn(IllegalArgumentException.class, ConnectException.class)
                                                      .build();

        assertThat(errorHandler.isPermanentError(Mockito.mock(QueuedMessage.class),
                                                 new RuntimeException()))
                .isFalse();
        assertThat(errorHandler.isPermanentError(Mockito.mock(QueuedMessage.class),
                                                 new IllegalStateException()))
                .isTrue();
        // Assert that alwaysRetryOn has higher priority than stopRedeliveryOn
        assertThat(errorHandler.isPermanentError(Mockito.mock(QueuedMessage.class),
                                                 new IllegalArgumentException()))
                .isFalse();
        assertThat(errorHandler.isPermanentError(Mockito.mock(QueuedMessage.class),
                                                 new ConnectException()))
                .isFalse();
    }
}