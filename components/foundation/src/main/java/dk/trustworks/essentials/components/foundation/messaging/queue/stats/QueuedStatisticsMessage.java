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

package dk.trustworks.essentials.components.foundation.messaging.queue.stats;

import dk.trustworks.essentials.components.foundation.messaging.queue.MessageMetaData;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueEntryId;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueName;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueuedMessage.DeliveryMode;

import java.time.OffsetDateTime;

/**
 * Represents a message in a queue along with its associated statistics.
 * This interface provides various methods to obtain metadata and statistical
 * information for a message, including details such as the queue name, timestamps
 * for important operations, delivery mode, and attempts metadata.
 */
public interface QueuedStatisticsMessage {

    /**
     * The unique queue entry id for this message
     *
     * @return
     */
    QueueEntryId getId();

    /**
     * Name of the Queue that the message is enqueued on
     *
     * @return name of the Queue that the message is enqueued on
     */
    QueueName getQueueName();

    /**
     * When was this message first enqueued (or directly added as a Dead-letter-message)
     *
     * @return when was this message first enqueued
     */
    OffsetDateTime getAddedTimestamp();

    /**
     * Timestamp for when the message was delivered.
     *
     * @return timestamp for when the message was delivered.
     */
    OffsetDateTime getDeliveryTimestamp();

    /**
     * Timestamp for when the message was deleted from durable_queues.
     *
     * @return timestamp for when the message was deleted from durable_queues.
     */
    OffsetDateTime getDeletionTimestamp();

    /**
     * The total delivery attempts
     *
     * @return total delivery attempts
     */
    Integer getTotalAttempts();

    /**
     * The total redelivery attempts
     *
     * @return total redelivery attempts
     */
    Integer getRedeliveryAttempts();

    /**
     * Get the message's {@link DeliveryMode}
     *
     * @return the message's {@link DeliveryMode}
     */
    DeliveryMode getDeliveryMode();

    /**
     * Get the {@link MessageMetaData}
     *
     * @return the {@link MessageMetaData}
     */
    MessageMetaData getMetaData();

    /**
     * Represents statistical information for a specific queue.
     * Provides metadata and insights into the performance and activity of the queue over a defined time period.
     *
     * @param queueName              The name of the queue for which statistics are gathered.
     * @param fromTimestamp          The starting timestamp from which the statistics are considered.
     * @param totalMessagesDelivered The total number of messages delivered from the queue during the specified period.
     * @param avgDeliveryLatencyMs   The average message delivery latency in milliseconds.
     * @param firstDelivery          The timestamp of the first message delivery within the specified period.
     * @param lastDelivery           The timestamp of the last message delivery within the specified period.
     */
    record QueueStatistics(
            QueueName queueName,
            OffsetDateTime fromTimestamp,
            long totalMessagesDelivered,
            int avgDeliveryLatencyMs,
            OffsetDateTime firstDelivery,
            OffsetDateTime lastDelivery
    ) { }
}
