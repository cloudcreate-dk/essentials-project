/*
 * Copyright 2021-2022 the original author or authors.
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

import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;

import java.time.Duration;
import java.util.*;

/**
 * Durable Queue concept that supports queuing a message on to a named Queue. Each message is associated with a unique {@link QueueEntryId}<br>
 * Each Queue is uniquely identified by its {@link QueueName}<br>
 * Queued messages can, per Queue, asynchronously be consumed by a {@link QueuedMessageHandler}, by registering it as a {@link DurableQueueConsumer} using
 * {@link #consumeFromQueue(QueueName, RedeliveryPolicy, int, QueuedMessageHandler)}<br>
 * The Durable Queue concept supports competing consumers guaranteeing that a message is only consumed by one message handler at a time<br>
 * <br>
 * The {@link DurableQueueConsumer} supports retrying failed messages, according to the specified {@link RedeliveryPolicy}, and ultimately marking a repeatedly failing message
 * as a Poison-Message/Dead-Letter-Message.
 * <br>
 * The {@link RedeliveryPolicy} supports fixed, linear and exponential backoff strategies.
 * The {@link DurableQueues} supports delayed message delivery as well as Poison-Message/Dead-Letter-Messages, which are messages that have repeatedly failed processing.<br>
 * Poison Messages/Dead-Letter-Messages won't be delivered to a {@link DurableQueueConsumer}, unless they're explicitly resurrected call {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
 * <br>
 */
public interface DurableQueues extends Lifecycle {

    /**
     * The sorting order for the {@link DefaultQueuedMessage#id}
     */
    enum QueueingSortOrder {
        /**
         * Ascending order
         */
        ASC,
        /**
         * Descending order
         */
        DESC
    }

    /**
     * Get a queued message that's marked as a {@link DefaultQueuedMessage#isDeadLetterMessage}
     *
     * @param queueEntryId the messages unique queue entry id
     * @return the message wrapped in an {@link Optional} if the message exists and {@link DefaultQueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    Optional<QueuedMessage> getDeadLetterMessage(QueueEntryId queueEntryId);

    /**
     * Get a queued message that is NOT marked as a {@link DefaultQueuedMessage#isDeadLetterMessage}
     *
     * @param queueEntryId the messages unique queue entry id
     * @return the message wrapped in an {@link Optional} if the message exists and NOT a {@link DefaultQueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    Optional<QueuedMessage> getQueuedMessage(QueueEntryId queueEntryId);

    /**
     * The transactional behaviour mode of this {@link DurableQueues} instance<br>
     *
     * @return The transactional behaviour mode of a {@link DurableQueues} instance<br>
     */
    TransactionalMode getTransactionalMode();

    /**
     * Start an asynchronous message consumer.<br>https://www.enterpriseintegrationpatterns.com
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName}
     *
     * @param queueName           the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @param redeliveryPolicy    the redelivery policy in case the handling of a message fails
     * @param parallelConsumers   the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @param queueMessageHandler the message handler that will receive {@link DefaultQueuedMessage}'s
     * @return the queue consumer
     */
    DurableQueueConsumer consumeFromQueue(QueueName queueName,
                                          RedeliveryPolicy redeliveryPolicy,
                                          int parallelConsumers,
                                          QueuedMessageHandler queueMessageHandler);

    /**
     * Queue a message for asynchronous delivery without delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName the name of the Queue the message is added to
     * @param payload   the message payload
     * @return the unique entry id for the message queued
     */
    default QueueEntryId queueMessage(QueueName queueName, Object payload) {
        return queueMessage(queueName,
                            payload,
                            Optional.empty(),
                            Optional.empty());
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the message is added to
     * @param payload       the message payload
     * @param deliveryDelay Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return the unique entry id for the message queued
     */
    default QueueEntryId queueMessage(QueueName queueName, Object payload, Optional<Duration> deliveryDelay) {
        return queueMessage(queueName,
                            payload,
                            Optional.empty(),
                            deliveryDelay);
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName        the name of the Queue the message is added to
     * @param payload          the message payload
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @param deliveryDelay    Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return the unique entry id for the message queued
     */
    QueueEntryId queueMessage(QueueName queueName, Object payload, Optional<Exception> causeOfEnqueuing, Optional<Duration> deliveryDelay);

    /**
     * Queue the message directly as a Dead Letter Message. Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer}<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}
     *
     * @param queueName    the name of the Queue the message is added to
     * @param payload      the message payload
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     * @return the unique entry id for the message queued
     */
    QueueEntryId queueMessageAsDeadLetterMessage(QueueName queueName, Object payload, Exception causeOfError);

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link DefaultQueuedMessage#nextDeliveryTimestamp}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the messages will be added to
     * @param payloads      the message payloads
     * @param deliveryDelay how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     * @return the unique entry id's for the messages queued ordered in the same order as the payloads that were queued
     */
    List<QueueEntryId> queueMessages(QueueName queueName, List<?> payloads, Optional<Duration> deliveryDelay);

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link DefaultQueuedMessage#nextDeliveryTimestamp}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName the name of the Queue the messages will be added to
     * @param payloads  the message payloads
     * @return the unique entry id's for the messages queued ordered in the same order as the payloads that were queued
     */
    default List<QueueEntryId> queueMessages(QueueName queueName, List<?> payloads) {
        return queueMessages(queueName, payloads, Optional.empty());
    }


    /**
     * Schedule the message for redelivery after the specified <code>deliveryDelay</code> (called by the {@link DurableQueueConsumer})<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId  the unique id of the message that must we will retry the delivery of
     * @param causeForRetry the reason why the message delivery has to be retried
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return true if the message was marked for retrying, otherwise false
     */
    boolean retryMessage(QueueEntryId queueEntryId,
                         Exception causeForRetry,
                         Duration deliveryDelay);

    /**
     * Mark a Message as a Dead Letter Message.  Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer} (called by the {@link DurableQueueConsumer})<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId                    the unique id of the message that must be marked as a Dead Letter Message
     * @param causeForBeingMarkedAsDeadLetter the reason for the message being marked as a Dead Letter Message
     * @return true if the operation went well, otherwise false
     */
    boolean markAsDeadLetterMessage(QueueEntryId queueEntryId,
                                    Exception causeForBeingMarkedAsDeadLetter);

    /**
     * Resurrect a Dead Letter Message for redelivery after the specified <code>deliveryDelay</code><br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId  the unique id of the Dead Letter Message that must we will retry the delivery of
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return true if the operation went well, otherwise false
     */
    boolean resurrectDeadLetterMessage(QueueEntryId queueEntryId,
                                       Duration deliveryDelay);

    /**
     * Mark the message as acknowledged - this operation deleted the messages from the Queue<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId the unique id of the Message to acknowledge
     * @return true if the operation went well, otherwise false
     */
    default boolean acknowledgeMessageAsHandled(QueueEntryId queueEntryId) {
        return deleteMessage(queueEntryId);
    }

    /**
     * Delete a message (Queued or Dead Letter Message)<br>
     * Note this method MUST be called within an existing {@link dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId the unique id of the Message to delete
     * @return true if the operation went well, otherwise false
     */
    boolean deleteMessage(QueueEntryId queueEntryId);

    /**
     * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName the name of the Queue where we will query for the next message ready for delivery
     * @return the message wrapped in an {@link Optional} or {@link Optional#empty()} if no message is ready for delivery
     */
    Optional<QueuedMessage> getNextMessageReadyForDelivery(QueueName queueName);

    /**
     * Check if there are any messages queued  (i.e. not including Dead Letter Messages) for the given queue
     *
     * @param queueName the name of the Queue where we will query for queued messages
     * @return true if there are messages queued on the given queue, otherwise false
     */
    boolean hasMessagesQueuedFor(QueueName queueName);

    /**
     * Get the total number of messages queued (i.e. not including Dead Letter Messages) for the given queue
     *
     * @param queueName the name of the Queue where we will query for queued messages
     * @return the number of queued messages
     */
    long getTotalMessagesQueuedFor(QueueName queueName);

    /**
     * Query Queued Messages (i.e. not including Dead Letter Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for queued messages
     * @param queueingSortOrder the sort order for the {@link DefaultQueuedMessage#id}
     * @param startIndex        the index of the first message to include (used for pagination)
     * @param pageSize          how many messages to include (used for pagination)
     * @return the messages matching the criteria
     */
    List<QueuedMessage> getQueuedMessages(QueueName queueName, QueueingSortOrder queueingSortOrder, long startIndex, long pageSize);

    /**
     * Query Dead Letter Messages (i.e. not normally Queued Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for queued messages
     * @param queueingSortOrder the sort order for the {@link DefaultQueuedMessage#id}
     * @param startIndex        the index of the first message to include (used for pagination)
     * @param pageSize          how many messages to include (used for pagination)
     * @return the messages matching the criteria
     */
    List<QueuedMessage> getDeadLetterMessages(QueueName queueName, QueueingSortOrder queueingSortOrder, long startIndex, long pageSize);

    /**
     * Delete all messages (Queued or Dead letter Messages) in the given queue
     *
     * @param queueName the name of the Queue for which all messages will be deleted
     * @return the number of deleted messages
     */
    int purgeQueue(QueueName queueName);
}
