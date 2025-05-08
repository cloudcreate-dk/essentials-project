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

package dk.trustworks.essentials.components.foundation.messaging.queue.operations;

import dk.trustworks.essentials.components.foundation.messaging.queue.*;

import java.time.Duration;
import java.util.*;

/**
 * Builder for {@link QueueMessages}
 */
public final class QueueMessagesBuilder {
    private QueueName          queueName;
    private List<Message>            messages;
    private Optional<Duration> deliveryDelay;

    /**
     *
     * @param queueName the name of the Queue the messages will be added to
     * @return this builder instance
     */
    public QueueMessagesBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     *
     * @param messages the messages being enqueued
     * @return this builder instance
     */
    public QueueMessagesBuilder setMessages(List<Message> messages) {
        this.messages = messages;
        return this;
    }

    /**
     *
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public QueueMessagesBuilder setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
        return this;
    }

    /**
     *
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public QueueMessagesBuilder setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = Optional.ofNullable(deliveryDelay);
        return this;
    }

    /**
     * Builder an {@link QueueMessages} instance from the builder properties
     * @return the {@link QueueMessages} instance
     */
    public QueueMessages build() {
        return new QueueMessages(queueName, messages, deliveryDelay);
    }
}