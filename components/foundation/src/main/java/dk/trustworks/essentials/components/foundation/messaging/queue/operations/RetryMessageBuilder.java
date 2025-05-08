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

/**
 * Builder for {@link RetryMessage}
 */
public final class RetryMessageBuilder {
    private QueueEntryId queueEntryId;
    private Exception causeForRetry;
    private Duration  deliveryDelay;

    /**
     *
     * @param queueEntryId the unique id of the message that must we will retry the delivery of
     * @return this builder instance
     */
    public RetryMessageBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    /**
     *
     * @param causeForRetry the reason why the message delivery has to be retried
     * @return this builder instance
     */
    public RetryMessageBuilder setCauseForRetry(Exception causeForRetry) {
        this.causeForRetry = causeForRetry;
        return this;
    }

    /**
     *
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public RetryMessageBuilder setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
        return this;
    }

    /**
     * Builder an {@link RetryMessage} instance from the builder properties
     * @return the {@link RetryMessage} instance
     */
    public RetryMessage build() {
        return new RetryMessage(queueEntryId, causeForRetry, deliveryDelay);
    }
}