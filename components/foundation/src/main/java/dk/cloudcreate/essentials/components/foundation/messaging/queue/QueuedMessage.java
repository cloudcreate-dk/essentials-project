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

import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;

import java.time.OffsetDateTime;

/**
 * Represents a {@link Message} that has been queued using {@link QueueMessage}/{@link QueueMessages}/{@link QueueMessageAsDeadLetterMessage}
 */
public interface QueuedMessage {
    enum DeliveryMode {NORMAL, IN_ORDER}

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
     * Get the {@link Message#getPayload()}
     *
     * @return the {@link Message#getPayload()}
     */
    default Object getPayload() {
        return getMessage().getPayload();
    }

    /**
     * When was this message first enqueued (or directly added as a Dead-letter-message)
     *
     * @return when was this message first enqueued
     */
    OffsetDateTime getAddedTimestamp();

    /**
     * Timestamp for when the message should be delivered next time. Null if {@link #isDeadLetterMessage()} is true
     *
     * @return timestamp for when the message should be delivered next time. Null if {@link #isDeadLetterMessage()} is true
     */
    OffsetDateTime getNextDeliveryTimestamp();

    /**
     * Get the error from the last delivery (null if no error is recorded)
     *
     * @return Get the error from the last delivery (null if no error is recorded)
     */
    String getLastDeliveryError();

    /**
     * Is this a poison/dead-letter message that will not be delivered until {@link DurableQueues#resurrectDeadLetterMessage(ResurrectDeadLetterMessage)} is called
     *
     * @return Is this a poison/dead-letter message
     */
    boolean isDeadLetterMessage();


    /**
     * Get the total number of delivery attempts for this message
     *
     * @return the total number of delivery attempts for this message
     */
    int getTotalDeliveryAttempts();


    /**
     * How many times has we attempted to re-deliver this message (same as {@link #getTotalDeliveryAttempts()}-1)
     *
     * @return how many times has we attempted to re-deliver this message (same as {@link #getTotalDeliveryAttempts()}-1)
     */
    int getRedeliveryAttempts();

    /**
     * Get the {@link Message#getMetaData()}
     *
     * @return the {@link Message#getMetaData()}
     */
    default MessageMetaData getMetaData() {
        return getMessage().getMetaData();
    }

    /**
     * Get the message queued
     */
    Message getMessage();

    /**
     * Get the message's {@link DeliveryMode}
     *
     * @return the message's {@link DeliveryMode}
     */
    DeliveryMode getDeliveryMode();

    /**
     * Is the message currently being delivered to a message consumer
     *
     * @return s the message currently being delivered to a message consumer
     */
    boolean isBeingDelivered();

    /**
     * If {@link #isBeingDelivered()} is true, then {@link #getDeliveryTimestamp()} returns the timestamp for when
     * the message delivery started. Otherwise, this property is null.
     *
     * @return the timestamp for when the message delivery started
     */
    OffsetDateTime getDeliveryTimestamp();
}
