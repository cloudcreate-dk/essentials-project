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

package dk.trustworks.essentials.components.foundation.messaging.queue.api;

import dk.trustworks.essentials.components.foundation.messaging.queue.*;

import java.time.OffsetDateTime;

/**
 * Represents a message that has been queued and its associated metadata.
 * This record provides details about the enqueued message,
 * such as its identifiers, payload, timestamps, delivery attempts, and its current state.
 */
public record ApiQueuedMessage(
        QueueEntryId id,
        QueueName queueName,
        String payload,
        OffsetDateTime addedTimestamp,
        OffsetDateTime nextDeliveryTimestamp,
        OffsetDateTime deliveryTimestamp,
        String lastDeliveryError,
        int totalDeliveryAttempts,
        int redeliveryAttempts,
        boolean isDeadLetterMessage,
        boolean isBeingDelivered
) {

    /**
     * Constructs an {@link ApiQueuedMessage} instance from a {@link QueuedMessage} and a provided payload.
     * This method extracts relevant metadata from the {@link QueuedMessage} and assigns the provided payload.
     *
     * @param message the {@link QueuedMessage} containing metadata and identifiers about the message
     * @param payload the payload to be associated with the constructed {@link ApiQueuedMessage}
     * @return an instance of {@link ApiQueuedMessage} containing the provided payload and metadata from the {@link QueuedMessage}
     */
    public static ApiQueuedMessage from(QueuedMessage message, String payload) {
        return new ApiQueuedMessage(
                message.getId(),
                message.getQueueName(),
                payload,
                message.getAddedTimestamp(),
                message.getNextDeliveryTimestamp(),
                message.getDeliveryTimestamp(),
                message.getLastDeliveryError(),
                message.getTotalDeliveryAttempts(),
                message.getRedeliveryAttempts(),
                message.isDeadLetterMessage(),
                message.isBeingDelivered()
        );
    }

    /**
     * Constructs an {@link ApiQueuedMessage} instance from a {@link QueuedMessage}
     * using metadata from the {@link QueuedMessage} and sets the payload to null.
     *
     * @param message the {@link QueuedMessage} containing metadata and identifiers about the message
     * @return an instance of {@link ApiQueuedMessage} with metadata from the {@link QueuedMessage}
     * and a null payload
     */
    public static ApiQueuedMessage from(QueuedMessage message) {
        return from(message, null);
    }

    @Override
    public String toString() {
        return "ApiQueuedMessage{" +
                "id=" + id +
                ", queueName=" + queueName +
                ", payload='" + payload + '\'' +
                ", addedTimestamp=" + addedTimestamp +
                ", nextDeliveryTimestamp=" + nextDeliveryTimestamp +
                ", deliveryTimestamp=" + deliveryTimestamp +
                ", lastDeliveryError='" + lastDeliveryError + '\'' +
                ", totalDeliveryAttempts=" + totalDeliveryAttempts +
                ", redeliveryAttempts=" + redeliveryAttempts +
                ", isDeadLetterMessage=" + isDeadLetterMessage +
                ", isBeingDelivered=" + isBeingDelivered +
                '}';
    }
}
