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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Builder for {@link QueueMessageAsDeadLetterMessage}
 */
public final class QueueMessageAsDeadLetterMessageBuilder {
    private QueueName queueName;
    private Object    payload;
    private Exception causeOfError;

    private MessageMetaData metaData = new MessageMetaData();

    /**
     * @param queueName the name of the Queue the message is added to
     * @return this builder instance
     */
    public QueueMessageAsDeadLetterMessageBuilder setQueueName(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        return this;
    }

    /**
     * @param payload the message payload being queued directly as a dead letter message
     * @return this builder instance
     */
    public QueueMessageAsDeadLetterMessageBuilder setPayload(Object payload) {
        this.payload = requireNonNull(payload, "No payload provided");
        return this;
    }

    /**
     * @param message the message being queued directly as a dead letter message
     * @return this builder instance
     */
    public QueueMessageAsDeadLetterMessageBuilder setMessage(Message message) {
        requireNonNull(message, "No message provided");
        this.payload = message.getPayload();
        this.metaData = message.getMetaData();
        return this;
    }

    /**
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     * @return this builder instance
     */
    public QueueMessageAsDeadLetterMessageBuilder setCauseOfError(Exception causeOfError) {
        this.causeOfError = causeOfError;
        return this;
    }

    /**
     * @param metaData metadata related to the message/payload
     * @return this builder instance
     */
    public QueueMessageAsDeadLetterMessageBuilder setMetaData(MessageMetaData metaData) {
        this.metaData = requireNonNull(metaData, "No metaData provided");
        return this;
    }

    /**
     * Builder an {@link QueueMessageAsDeadLetterMessage} instance from the builder properties
     *
     * @return the {@link QueueMessageAsDeadLetterMessage} instance
     */
    public QueueMessageAsDeadLetterMessage build() {
        return new QueueMessageAsDeadLetterMessage(queueName,
                                                   new Message(payload, metaData),
                                                   causeOfError);
    }
}