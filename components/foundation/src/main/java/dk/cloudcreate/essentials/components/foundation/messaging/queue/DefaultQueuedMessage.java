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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import java.time.OffsetDateTime;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Represents a message queued onto a Durable Queue
 */
public class DefaultQueuedMessage implements QueuedMessage {
    public final QueueEntryId   id;
    public final QueueName      queueName;
    /**
     * The deserialized payload
     */
    public final Object         payload;
    public final OffsetDateTime addedTimestamp;
    public final OffsetDateTime nextDeliveryTimestamp;
    public final String         lastDeliveryError;
    public final int            totalDeliveryAttempts;
    public final int            redeliveryAttempts;
    public final boolean        isDeadLetterMessage;

    public DefaultQueuedMessage(QueueEntryId id,
                                QueueName queueName,
                                Object payload,
                                OffsetDateTime addedTimestamp,
                                OffsetDateTime nextDeliveryTimestamp,
                                String lastDeliveryError,
                                int totalDeliveryAttempts,
                                int redeliveryAttempts,
                                boolean isDeadLetterMessage) {
        this.id = requireNonNull(id, "No queue entry id provided");
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.payload = requireNonNull(payload, "No payload provided");
        this.addedTimestamp = requireNonNull(addedTimestamp, "No addedTimestamp provided");
        this.nextDeliveryTimestamp = nextDeliveryTimestamp;
        this.lastDeliveryError = lastDeliveryError;
        this.totalDeliveryAttempts = totalDeliveryAttempts;
        this.redeliveryAttempts = redeliveryAttempts;
        this.isDeadLetterMessage = isDeadLetterMessage;
    }

    @Override
    public QueueEntryId getId() {
        return id;
    }

    @Override
    public QueueName getQueueName() {
        return queueName;
    }

    @Override
    public Object getPayload() {
        return payload;
    }

    @Override
    public OffsetDateTime getAddedTimestamp() {
        return addedTimestamp;
    }

    @Override
    public OffsetDateTime getNextDeliveryTimestamp() {
        return nextDeliveryTimestamp;
    }

    @Override
    public String getLastDeliveryError() {
        return lastDeliveryError;
    }

    @Override
    public int getTotalDeliveryAttempts() {
        return totalDeliveryAttempts;
    }

    @Override
    public int getRedeliveryAttempts() {
        return redeliveryAttempts;
    }

    @Override
    public boolean isDeadLetterMessage() {
        return isDeadLetterMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultQueuedMessage)) return false;
        DefaultQueuedMessage that = (DefaultQueuedMessage) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "QueuedMessage{" +
                "id=" + id +
                ", queueName=" + queueName +
                ", payload-type=" + payload.getClass().getName() +
                ", addedTimestamp=" + addedTimestamp +
                ", nextDeliveryTimestamp=" + nextDeliveryTimestamp +
                ", totalDeliveryAttempts=" + totalDeliveryAttempts +
                ", redeliveryAttempts=" + redeliveryAttempts +
                ", isDeadLetterMessage=" + isDeadLetterMessage +
                ", lastDeliveryError='" + lastDeliveryError + '\'' +
                '}';
    }
}
