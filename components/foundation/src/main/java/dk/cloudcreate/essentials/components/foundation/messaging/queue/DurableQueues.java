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

import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;

import java.time.*;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link DurableQueues} concept supports <b>intra-service</b> point-to-point messaging using durable Queues that guarantee At-Least-Once delivery of messages.<br>
 * The only requirement is that message producers and message consumers can access the same underlying durable Queue storage.
 * <p>
 * In a service oriented architecture it's common for all deployed instances of a given service (e.g. a Sales service) to share the same underlying
 * database(s). As long as the different deployed (Sales) services instances can share the same underlying database, then you use the {@link DurableQueues} concept for point to point messaging across all deployed (Sales service)
 * instances in the cluster. <br>
 * If you need cross-service point to point messaging support, e.g. across instances of different services (such as across Sales, Billing and Shipping services), then you need to use a dedicated distributed Queueing service such as RabbitMQ.
 * <p>
 * This library focuses on providing a Durable Queue supporting message redelivery and Dead Letter Message functionality
 * and comes in two flavours <code>PostgresqlDurableQueues</code> and <code>MongoDurableQueues</code> which both implement the {@link DurableQueues} interface.
 * <p>
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
 * <p>
 * <b>Ordered Messages</b><br>
 * If you're queuing with {@link OrderedMessage} then, IF and only IF, only a single cluster node is consuming from the Queue,
 * such as with an {@link Inbox} or {@link Outbox} configured with {@link MessageConsumptionMode#SingleGlobalConsumer} (which uses a {@link FencedLock} to
 * coordinate message consumption across cluster nodes)
 * in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per
 * {@link OrderedMessage#getKey()} across as many parallel message consumers as you wish to use.
 */
public interface DurableQueues extends Lifecycle {
    /**
     * The sorting order for the {@link QueuedMessage#getId()}
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
     * Add a {@link DurableQueuesInterceptor} to this {@link DurableQueues} instance<br>
     * The {@link DurableQueuesInterceptor} allows you to intercept all high level operations
     *
     * @param interceptor the interceptor to add
     * @return this {@link DurableQueues} instance
     */
    DurableQueues addInterceptor(DurableQueuesInterceptor interceptor);

    /**
     * Add {@link DurableQueuesInterceptor}'s to this {@link DurableQueues} instance<br>
     * The {@link DurableQueuesInterceptor} allows you to intercept all high level operations
     *
     * @param interceptors the interceptors to add
     * @return this {@link DurableQueues} instance
     */
    default DurableQueues addInterceptors(List<DurableQueuesInterceptor> interceptors) {
        requireNonNull(interceptors, "No interceptors list provided");
        interceptors.forEach(this::addInterceptor);
        return this;
    }

    /**
     * Remove a {@link DurableQueuesInterceptor} from this {@link DurableQueues} instance<br>
     *
     * @param interceptor the interceptor to remove
     * @return this {@link DurableQueues} instance
     */
    DurableQueues removeInterceptor(DurableQueuesInterceptor interceptor);

    /**
     * Get a queued message that's marked as a {@link QueuedMessage#isDeadLetterMessage}
     *
     * @param queueEntryId the messages unique queue entry id
     * @return the message wrapped in an {@link Optional} if the message exists and {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    default Optional<QueuedMessage> getDeadLetterMessage(QueueEntryId queueEntryId) {
        return getDeadLetterMessage(new GetDeadLetterMessage(queueEntryId));
    }

    /**
     * Get a queued message that's marked as a {@link QueuedMessage#isDeadLetterMessage}
     *
     * @param operation the {@link GetDeadLetterMessage} operation
     * @return the message wrapped in an {@link Optional} if the message exists and {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    Optional<QueuedMessage> getDeadLetterMessage(GetDeadLetterMessage operation);

    /**
     * Get a queued message that is NOT marked as a {@link QueuedMessage#isDeadLetterMessage}
     *
     * @param queueEntryId the messages unique queue entry id
     * @return the message wrapped in an {@link Optional} if the message exists and NOT a {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    default Optional<QueuedMessage> getQueuedMessage(QueueEntryId queueEntryId) {
        return getQueuedMessage(new GetQueuedMessage(queueEntryId));
    }

    /**
     * Get a queued message that is NOT marked as a {@link QueuedMessage#isDeadLetterMessage}
     *
     * @param operation the {@link GetQueuedMessage} operation
     * @return the message wrapped in an {@link Optional} if the message exists and NOT a {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    Optional<QueuedMessage> getQueuedMessage(GetQueuedMessage operation);

    /**
     * The transactional behaviour mode of this {@link DurableQueues} instance<br>
     *
     * @return The transactional behaviour mode of a {@link DurableQueues} instance<br>
     */
    TransactionalMode getTransactionalMode();

    /**
     * @return If {@link #getTransactionalMode()} is {@link TransactionalMode#FullyTransactional} then
     * it will return the {@link UnitOfWorkFactory} wrapped in an {@link Optional}, otherwise it will return
     * an {@link Optional#empty()}
     */
    Optional<UnitOfWorkFactory<? extends UnitOfWork>> getUnitOfWorkFactory();

    /**
     * Start an asynchronous message consumer.<br>
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
     *
     * @param consumerName        the name of the consumer (for logging purposes)
     * @param queueName           the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @param redeliveryPolicy    the redelivery policy in case the handling of a message fails
     * @param parallelConsumers   the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @param queueMessageHandler the message handler that will receive {@link QueuedMessage}'s. See {@link PatternMatchingQueuedMessageHandler}
     * @return the queue consumer
     */
    default DurableQueueConsumer consumeFromQueue(String consumerName,
                                                  QueueName queueName,
                                                  RedeliveryPolicy redeliveryPolicy,
                                                  int parallelConsumers,
                                                  QueuedMessageHandler queueMessageHandler) {
        return consumeFromQueue(ConsumeFromQueue.builder()
                                                .setConsumerName(consumerName)
                                                .setQueueName(queueName)
                                                .setRedeliveryPolicy(redeliveryPolicy)
                                                .setParallelConsumers(parallelConsumers)
                                                .setQueueMessageHandler(queueMessageHandler)
                                                .build());
    }

    /**
     * Start an asynchronous message consumer.<br>
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
     *
     * @param queueName           the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @param redeliveryPolicy    the redelivery policy in case the handling of a message fails
     * @param parallelConsumers   the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @param queueMessageHandler the message handler that will receive {@link QueuedMessage}'s. See {@link PatternMatchingQueuedMessageHandler}
     * @return the queue consumer
     */
    default DurableQueueConsumer consumeFromQueue(QueueName queueName,
                                                  RedeliveryPolicy redeliveryPolicy,
                                                  int parallelConsumers,
                                                  QueuedMessageHandler queueMessageHandler) {
        return consumeFromQueue(ConsumeFromQueue.builder()
                                                .setQueueName(queueName)
                                                .setRedeliveryPolicy(redeliveryPolicy)
                                                .setParallelConsumers(parallelConsumers)
                                                .setQueueMessageHandler(queueMessageHandler)
                                                .build());
    }

    /**
     * Start an asynchronous message consumer.<br>
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
     *
     * @param operation The {@link ConsumeFromQueue} operation
     * @return the queue consumer
     */
    DurableQueueConsumer consumeFromQueue(ConsumeFromQueue operation);

    /**
     * Queue a message for asynchronous delivery without delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName the name of the Queue the message is added to
     * @param message   the message
     * @return the unique entry id for the message queued
     */
    default QueueEntryId queueMessage(QueueName queueName, Message message) {
        return queueMessage(queueName,
                            message,
                            Optional.empty(),
                            Optional.empty());
    }

    /**
     * Queue a message for asynchronous delivery without delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param operation the {@link QueueMessage} operation
     * @return the unique entry id for the message queued
     */
    QueueEntryId queueMessage(QueueMessage operation);

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the message is added to
     * @param message       the message  ({@link Message}/{@link OrderedMessage})
     * @param deliveryDelay Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return the unique entry id for the message queued
     * @see OrderedMessage
     */
    default QueueEntryId queueMessage(QueueName queueName, Message message, Optional<Duration> deliveryDelay) {
        return queueMessage(queueName,
                            message,
                            Optional.empty(),
                            deliveryDelay);
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the message is added to
     * @param message       the message  ({@link Message}/{@link OrderedMessage})
     * @param deliveryDelay Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return the unique entry id for the message queued
     * @see OrderedMessage
     */
    default QueueEntryId queueMessage(QueueName queueName, Message message, Duration deliveryDelay) {
        return queueMessage(queueName,
                            message,
                            Optional.empty(),
                            Optional.ofNullable(deliveryDelay));
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName        the name of the Queue the message is added to
     * @param message          the message  ({@link Message}/{@link OrderedMessage})
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @param deliveryDelay    Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return the unique entry id for the message queued
     * @see OrderedMessage
     */
    default QueueEntryId queueMessage(QueueName queueName, Message message, Optional<Exception> causeOfEnqueuing, Optional<Duration> deliveryDelay) {
        return queueMessage(new QueueMessage(queueName,
                                             message,
                                             causeOfEnqueuing,
                                             deliveryDelay));
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName        the name of the Queue the message is added to
     * @param message          the message payload  ({@link Message}/{@link OrderedMessage})
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @param deliveryDelay    Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return the unique entry id for the message queued
     * @see OrderedMessage
     */
    default QueueEntryId queueMessage(QueueName queueName, Message message, Exception causeOfEnqueuing, Duration deliveryDelay) {
        return queueMessage(queueName,
                            message,
                            Optional.ofNullable(causeOfEnqueuing),
                            Optional.ofNullable(deliveryDelay));
    }

    /**
     * Queue the message directly as a Dead Letter Message. Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer}<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}
     *
     * @param queueName    the name of the Queue the message is added to
     * @param message      the message  ({@link Message}/{@link OrderedMessage})
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     * @return the unique entry id for the message queued
     * @see OrderedMessage
     */
    default QueueEntryId queueMessageAsDeadLetterMessage(QueueName queueName, Message message, Exception causeOfError) {
        return queueMessageAsDeadLetterMessage(new QueueMessageAsDeadLetterMessage(queueName,
                                                                                   message,
                                                                                   causeOfError));
    }

    /**
     * Queue the message directly as a Dead Letter Message. Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer}<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}
     *
     * @param operation the {@link QueueMessageAsDeadLetterMessage} operation
     * @return the unique entry id for the message queued
     */
    QueueEntryId queueMessageAsDeadLetterMessage(QueueMessageAsDeadLetterMessage operation);

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the messages will be added to
     * @param messages      the message to enqueue  ({@link Message}/{@link OrderedMessage})
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     * @return the unique entry id's for the messages queued ordered in the same order as the payloads that were queued
     */
    default List<QueueEntryId> queueMessages(QueueName queueName, List<? extends Message> messages, Optional<Duration> deliveryDelay) {
        return queueMessages(new QueueMessages(queueName,
                                               messages,
                                               deliveryDelay));
    }

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the messages will be added to
     * @param messages      the messages to enqueue  ({@link Message}/{@link OrderedMessage})
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     * @return the unique entry id's for the messages queued ordered in the same order as the payloads that were queued
     */
    default List<QueueEntryId> queueMessages(QueueName queueName, List<? extends Message> messages, Duration deliveryDelay) {
        return queueMessages(queueName,
                             messages,
                             Optional.ofNullable(deliveryDelay));
    }


    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName the name of the Queue the messages will be added to
     * @param messages  the message to enqueue  ({@link Message}/{@link OrderedMessage})
     * @return the unique entry id's for the messages queued, ordered in the same order as the payloads that were queued
     */
    default List<QueueEntryId> queueMessages(QueueName queueName, List<? extends Message> messages) {
        return queueMessages(queueName, messages, Optional.empty());
    }


    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param operation The {@link QueueMessages} operation
     * @return the unique entry id's for the messages queued ordered in the same order as the payloads that were queued
     */
    List<QueueEntryId> queueMessages(QueueMessages operation);


    /**
     * Schedule the message for redelivery after the specified <code>deliveryDelay</code> (called by the {@link DurableQueueConsumer})<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId  the unique id of the message that must we will retry the delivery of
     * @param causeForRetry the reason why the message delivery has to be retried
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    default Optional<QueuedMessage> retryMessage(QueueEntryId queueEntryId,
                                                 Exception causeForRetry,
                                                 Duration deliveryDelay) {
        return retryMessage(new RetryMessage(queueEntryId,
                                             causeForRetry,
                                             deliveryDelay));
    }

    /**
     * Schedule the message for redelivery after the specified <code>deliveryDelay</code> (called by the {@link DurableQueueConsumer})<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param operation the {@link RetryMessage} operation
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    Optional<QueuedMessage> retryMessage(RetryMessage operation);

    /**
     * Mark an already Queued Message as a Dead Letter Message (or Poison Message).<br>
     * Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer} (called by the {@link DurableQueueConsumer})<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId                    the unique id of the message that must be marked as a Dead Letter Message
     * @param causeForBeingMarkedAsDeadLetter the reason for the message being marked as a Dead Letter Message
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    default Optional<QueuedMessage> markAsDeadLetterMessage(QueueEntryId queueEntryId,
                                                            Exception causeForBeingMarkedAsDeadLetter) {
        return markAsDeadLetterMessage(new MarkAsDeadLetterMessage(queueEntryId,
                                                                   causeForBeingMarkedAsDeadLetter));
    }

    /**
     * Mark an already Queued Message as a Dead Letter Message (or Poison Message).<br>
     * Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer} (called by the {@link DurableQueueConsumer})<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param operation the {@link MarkAsDeadLetterMessage} operation
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    Optional<QueuedMessage> markAsDeadLetterMessage(MarkAsDeadLetterMessage operation);

    /**
     * Resurrect a Dead Letter Message for redelivery after the specified <code>deliveryDelay</code><br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId  the unique id of the Dead Letter Message that must we will retry the delivery of
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    default Optional<QueuedMessage> resurrectDeadLetterMessage(QueueEntryId queueEntryId,
                                                               Duration deliveryDelay) {
        return resurrectDeadLetterMessage(new ResurrectDeadLetterMessage(queueEntryId,
                                                                         deliveryDelay));
    }

    /**
     * Resurrect a Dead Letter Message for redelivery after the specified <code>deliveryDelay</code><br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param operation the {@link ResurrectDeadLetterMessage} operation
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    Optional<QueuedMessage> resurrectDeadLetterMessage(ResurrectDeadLetterMessage operation);

    /**
     * Mark the message as acknowledged - this operation deletes the messages from the Queue<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId the unique id of the Message to acknowledge
     * @return true if the operation went well, otherwise false
     */
    default boolean acknowledgeMessageAsHandled(QueueEntryId queueEntryId) {
        return acknowledgeMessageAsHandled(new AcknowledgeMessageAsHandled(queueEntryId));
    }

    /**
     * Mark the message as acknowledged - this operation also deletes the messages from the Queue<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param operation the {@link AcknowledgeMessageAsHandled} operation
     * @return true if the operation went well, otherwise false
     */
    boolean acknowledgeMessageAsHandled(AcknowledgeMessageAsHandled operation);

    /**
     * Delete a message (Queued or Dead Letter Message)<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId the unique id of the Message to delete
     * @return true if the operation went well, otherwise false
     */
    default boolean deleteMessage(QueueEntryId queueEntryId) {
        return deleteMessage(new DeleteMessage(queueEntryId));
    }

    /**
     * Delete a message (Queued or Dead Letter Message)<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param operation the {@link DeleteMessage} operation
     * @return true if the operation went well, otherwise false
     */
    boolean deleteMessage(DeleteMessage operation);

    /**
     * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName the name of the Queue where we will query for the next message ready for delivery
     * @return the next message ready to be delivered (wrapped in an {@link Optional}) or {@link Optional#empty()} if no message is ready for delivery
     */
    default Optional<QueuedMessage> getNextMessageReadyForDelivery(QueueName queueName) {
        return getNextMessageReadyForDelivery(new GetNextMessageReadyForDelivery(queueName));
    }

    /**
     * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     * <p>
     * The normal message processing flow looks like this:
     * <pre>{@code
     * durableQueues.queueMessage(queueName, message);
     * var msgUnderDelivery = durableQueues.getNextMessageReadyForDelivery(queueName);
     * if (msgUnderDelivery.isPresent()) {
     *    try {
     *       handleMessage(msgUnderDelivery.get());
     *       durableQueues.acknowledgeMessageAsHandled(msgUnderDelivery.get().getId());
     *    } catch (Exception e) {
     *       durableQueues.retryMessage(msgUnderDelivery.get().getId(),
     *                                  e,
     *                                  Duration.ofMillis(500));
     *    }
     * }
     * }</pre>
     * <p>
     * When using {@link TransactionalMode#SingleOperationTransaction} then depending on
     * the type of errors that can occur this MAY leave a dequeued message in a state of being marked as "being delivered" forever<br>
     * This is why {@link DurableQueues} supporting these modes must ensure that they periodically
     * discover messages that have been under delivery for a long time (aka. stuck messages or timed-out messages) and reset them in order for them to be redelivered.<br>
     *
     * @param operation the {@link GetNextMessageReadyForDelivery} operation
     * @return the next message ready to be delivered (wrapped in an {@link Optional}) or {@link Optional#empty()} if no message is ready for delivery
     */
    Optional<QueuedMessage> getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery operation);

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
     * @param queueName the name of the Queue where we will query for the number of queued messages
     * @return the number of queued messages for the given queue
     */
    default long getTotalMessagesQueuedFor(QueueName queueName) {
        return getTotalMessagesQueuedFor(new GetTotalMessagesQueuedFor(queueName));
    }

    /**
     * Get the total number of messages queued (i.e. not including Dead Letter Messages) for the given queue
     *
     * @param operation the {@link GetTotalMessagesQueuedFor} operation
     * @return the number of queued messages for the given queue
     */
    long getTotalMessagesQueuedFor(GetTotalMessagesQueuedFor operation);

    /**
     * Query Queued Messages (i.e. not including any Dead Letter Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for queued messages
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#getId()}
     * @param startIndex        the index of the first message to include in the result (used for pagination)
     * @param pageSize          how many messages to include in the result (used for pagination)
     * @return the messages matching the criteria
     */
    default List<QueuedMessage> getQueuedMessages(QueueName queueName,
                                                  QueueingSortOrder queueingSortOrder,
                                                  long startIndex,
                                                  long pageSize) {
        return getQueuedMessages(GetQueuedMessages.builder()
                                                  .setQueueName(queueName)
                                                  .setQueueingSortOrder(queueingSortOrder)
                                                  .setStartIndex(startIndex)
                                                  .setPageSize(pageSize)
                                                  .build());
    }

    /**
     * Query Queued Messages (i.e. not including any Dead Letter Messages) for the given Queue
     *
     * @param operation the {@link GetQueuedMessages} operation
     * @return the messages matching the criteria
     */
    List<QueuedMessage> getQueuedMessages(GetQueuedMessages operation);

    /**
     * Query Dead Letter Messages (i.e. not normal Queued Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for Dead letter messages
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#getId()}
     * @param startIndex        the index of the first message to include in the result (used for pagination)
     * @param pageSize          how many messages to include in the result (used for pagination)
     * @return the dead letter messages matching the criteria
     */
    default List<QueuedMessage> getDeadLetterMessages(QueueName queueName,
                                                      QueueingSortOrder queueingSortOrder,
                                                      long startIndex,
                                                      long pageSize) {
        return getDeadLetterMessages(GetDeadLetterMessages.builder()
                                                          .setQueueName(queueName)
                                                          .setQueueingSortOrder(queueingSortOrder)
                                                          .setStartIndex(startIndex)
                                                          .setPageSize(pageSize)
                                                          .build());
    }

    /**
     * Query Dead Letter Messages (i.e. not normal Queued Messages) for the given Queue
     *
     * @param operation the {@link GetDeadLetterMessages} operation
     * @return the dead letter messages matching the criteria
     */
    List<QueuedMessage> getDeadLetterMessages(GetDeadLetterMessages operation);

    /**
     * Delete all messages (Queued or Dead letter Messages) in the given queue
     *
     * @param queueName the name of the Queue where all the messages will be deleted
     * @return the number of deleted messages
     */
    default int purgeQueue(QueueName queueName) {
        return purgeQueue(new PurgeQueue(queueName));
    }

    /**
     * Delete all messages (Queued or Dead letter Messages) in the given queue
     *
     * @param operation the {@link PurgeQueue} operation
     * @return the number of deleted messages
     */
    int purgeQueue(PurgeQueue operation);

    /**
     * Query for the next <code>maxNumberOfMessagesToReturn</code> queued messages
     * that are soon ready to be delivered using {@link Instant#now()} as <code>withNextDeliveryTimestampAfter</code><br>
     * This is a useful method for a custom queue consumer to know if it's necessary to
     * call {@link #getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)}
     *
     * @param queueName                   the name of the queue being queried
     * @param maxNumberOfMessagesToReturn the maximum number of messages to return
     * @return the messages soon ready to be delivered
     */
    default List<NextQueuedMessage> queryForMessagesSoonReadyForDelivery(QueueName queueName,
                                                                         int maxNumberOfMessagesToReturn) {
        return queryForMessagesSoonReadyForDelivery(queueName,
                                                    Instant.now(),
                                                    maxNumberOfMessagesToReturn);
    }

    /**
     * Query for the next <code>maxNumberOfMessagesToReturn</code> queued messages
     * that are soon ready to be delivered<br>
     * This is a useful method for a custom queue consumer to know if it's necessary to
     * call {@link #getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)}
     *
     * @param queueName                      the name of the queue being queried
     * @param withNextDeliveryTimestampAfter return {@link NextQueuedMessage} with a {@link NextQueuedMessage#nextDeliveryTimestamp} > than this timestamp
     * @param maxNumberOfMessagesToReturn    the maximum number of messages to return
     * @return the messages soon ready to be delivered
     */
    List<NextQueuedMessage> queryForMessagesSoonReadyForDelivery(QueueName queueName,
                                                                 Instant withNextDeliveryTimestampAfter,
                                                                 int maxNumberOfMessagesToReturn);
}
