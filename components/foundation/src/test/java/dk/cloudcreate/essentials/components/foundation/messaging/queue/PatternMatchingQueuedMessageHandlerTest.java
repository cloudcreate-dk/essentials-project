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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.MessageHandler;
import dk.cloudcreate.essentials.components.foundation.messaging.test_data.*;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PatternMatchingQueuedMessageHandlerTest {
    @Test
    void test_queued_message_pattern_matching_with_1_parameter() {
        // Given
        var messageHandler = new TestPatternMatchingQueuedMessageHandler();
        var someCommand    = new SomeCommand("Test");

        // When
        messageHandler.handle(new DefaultQueuedMessage(QueueEntryId.random(),
                                                       QueueName.of("TestQueue"),
                                                       Message.of(someCommand),
                                                       OffsetDateTime.now(),
                                                       OffsetDateTime.now(),
                                                       OffsetDateTime.now(),
                                                       null,
                                                       0,
                                                       0,
                                                       false,
                                                       false));

        // Then
        assertThat(messageHandler.someCommand).isEqualTo(someCommand);
        assertThat(messageHandler.someOtherCommand).isNull();
        assertThat(messageHandler.queuedMessageForSomeOtherCommand).isNull();
        assertThat(messageHandler.unmatchedMessage).isNull();
    }

    @Test
    void test_queued_message_pattern_matching_with_2_parameters() {
        // Given
        var messageHandler   = new TestPatternMatchingQueuedMessageHandler();
        var someOtherCommand = new SomeOtherCommand("Test");

        // When
        var queuedMessage = new DefaultQueuedMessage(QueueEntryId.random(),
                                                     QueueName.of("TestQueue"),
                                                     Message.of(someOtherCommand),
                                                     OffsetDateTime.now(),
                                                     OffsetDateTime.now(),
                                                     OffsetDateTime.now(),
                                                     null,
                                                     0,
                                                     0,
                                                     false,
                                                     true);
        messageHandler.handle(queuedMessage);

        // Then
        assertThat(messageHandler.someCommand).isNull();
        assertThat(messageHandler.someOtherCommand).isEqualTo(someOtherCommand);
        assertThat(messageHandler.queuedMessageForSomeOtherCommand).isEqualTo(queuedMessage);
        assertThat(messageHandler.unmatchedMessage).isNull();
    }

    @Test
    void test_queued_message_pattern_matching_with_unmatched_message() {
        // Given
        var messageHandler       = new TestPatternMatchingQueuedMessageHandler();
        var someUnmatchedCommand = new SomeUnmatchedCommand("Test");

        // When
        var queuedMessage = new DefaultQueuedMessage(QueueEntryId.random(),
                                                     QueueName.of("TestQueue"),
                                                     Message.of(someUnmatchedCommand),
                                                     OffsetDateTime.now(),
                                                     OffsetDateTime.now(),
                                                     OffsetDateTime.now(),
                                                     null,
                                                     0,
                                                     0,
                                                     false,
                                                     true);
        messageHandler.handle(queuedMessage);

        // Then
        assertThat(messageHandler.someCommand).isNull();
        assertThat(messageHandler.someOtherCommand).isNull();
        assertThat(messageHandler.queuedMessageForSomeOtherCommand).isNull();
        assertThat(messageHandler.unmatchedMessage).isEqualTo(queuedMessage);
    }

    private static class TestPatternMatchingQueuedMessageHandler extends PatternMatchingQueuedMessageHandler {
        private SomeCommand      someCommand;
        private SomeOtherCommand someOtherCommand;
        private QueuedMessage    queuedMessageForSomeOtherCommand;
        private QueuedMessage    unmatchedMessage;

        @MessageHandler
        void handle(SomeCommand someCommand) {
            this.someCommand = someCommand;
        }

        @MessageHandler
        void handle(SomeOtherCommand someOtherCommand, QueuedMessage queuedMessageForSomeOtherCommand) {
            this.someOtherCommand = someOtherCommand;
            this.queuedMessageForSomeOtherCommand = queuedMessageForSomeOtherCommand;
        }

        @Override
        protected void handleUnmatchedMessage(QueuedMessage queuedMessage) {
            this.unmatchedMessage = queuedMessage;
        }
    }
}