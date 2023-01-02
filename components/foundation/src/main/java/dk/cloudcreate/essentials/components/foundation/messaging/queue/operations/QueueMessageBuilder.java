/*
 * Copyright 2021-2023 the original author or authors.
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

import java.time.Duration;
import java.util.Optional;

/**
 * Builder for {@link QueueMessage}
 */
public class QueueMessageBuilder {
    private QueueName queueName;
    private Object              payload;
    private Optional<Exception> causeOfEnqueuing;
    private Optional<Duration>  deliveryDelay;

    /**
     *
     * @param queueName the name of the Queue the message is added to
     * @return this builder instance
     */
    public QueueMessageBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     *
     * @param payload the message payload
     * @return this builder instance
     */
    public QueueMessageBuilder setPayload(Object payload) {
        this.payload = payload;
        return this;
    }

    /**
     *
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @return this builder instance
     */
    public QueueMessageBuilder setCauseOfEnqueuing(Optional<Exception> causeOfEnqueuing) {
        this.causeOfEnqueuing = causeOfEnqueuing;
        return this;
    }

    /**
     *
     * @param deliveryDelay the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public QueueMessageBuilder setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
        return this;
    }

    /**
     *
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @return this builder instance
     */
    public QueueMessageBuilder setCauseOfEnqueuing(Exception causeOfEnqueuing) {
        this.causeOfEnqueuing = Optional.ofNullable(causeOfEnqueuing);
        return this;
    }

    /**
     *
     * @param deliveryDelay the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public QueueMessageBuilder setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = Optional.ofNullable(deliveryDelay);
        return this;
    }

    /**
     * Builder an {@link QueueMessage} instance from the builder properties
     * @return the {@link QueueMessage} instance
     */
    public QueueMessage build() {
        return new QueueMessage(queueName, payload, causeOfEnqueuing, deliveryDelay);
    }
}