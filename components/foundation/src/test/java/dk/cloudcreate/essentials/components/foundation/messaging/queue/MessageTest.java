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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.types.CorrelationId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {
    @Test
    void verify_two_identical_messages_are_equal() {
        var msg1CorrelationId = CorrelationId.random();
        var msg1TraceId       = UUID.randomUUID().toString();
        var message1 = Message.of("Test",
                                  MessageMetaData.of("correlation_id", msg1CorrelationId,
                                                     "trace_id", msg1TraceId));
        var message1Copy = Message.of("Test",
                                      MessageMetaData.of("correlation_id", msg1CorrelationId,
                                                         "trace_id", msg1TraceId));

        assertThat(message1Copy).isEqualTo(message1);
    }

    @Test
    void verify_two_identical_messages_with_empty_metadata_are_equal() {
        var msg1CorrelationId = CorrelationId.random();
        var msg1TraceId       = UUID.randomUUID().toString();
        var message1 = Message.of("Test",
                                  MessageMetaData.empty());
        var message1Copy = Message.of("Test",
                                      MessageMetaData.empty());

        assertThat(message1Copy).isEqualTo(message1);
    }

    @Test
    void verify_two_messages_with_the_different_payload_but_same_metadata_are_not_equal() {
        var msg1CorrelationId = CorrelationId.random();
        var msg1TraceId       = UUID.randomUUID().toString();
        var message1 = Message.of("Test1",
                                  MessageMetaData.of("correlation_id", msg1CorrelationId,
                                                     "trace_id", msg1TraceId));
        var message2 = Message.of("Test2",
                                  MessageMetaData.of("correlation_id", msg1CorrelationId,
                                                     "trace_id", msg1TraceId));

        assertThat(message2).isNotEqualTo(message1);
    }

    @Test
    void verify_two_messages_with_the_same_payload_but_different_metadata_are_not_equal() {
        var msg1CorrelationId = CorrelationId.random();
        var message1 = Message.of("Test",
                                  MessageMetaData.of("correlation_id", msg1CorrelationId,
                                                     "trace_id", UUID.randomUUID().toString()));
        var message2 = Message.of("Test",
                                  MessageMetaData.of("correlation_id", msg1CorrelationId,
                                                     "trace_id", UUID.randomUUID().toString()));

        assertThat(message2).isNotEqualTo(message1);
    }
}