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
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;
import java.util.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}
 * Operation also matches {@link DurableQueuesInterceptor#intercept(QueueMessages, InterceptorChain)}
 */
public final class QueueMessages {
    public final QueueName               queueName;
    public       List<? extends Message> messages;
    private      Optional<Duration>      deliveryDelay;

    /**
     * Create a new builder that produces a new {@link QueueMessages} instance
     *
     * @return a new {@link QueueMessagesBuilder} instance
     */
    public static QueueMessagesBuilder builder() {
        return new QueueMessagesBuilder();
    }

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the messages will be added to
     * @param messages      the message payloads  ({@link Message}/{@link OrderedMessage})
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public QueueMessages(QueueName queueName, List<? extends Message> messages, Optional<Duration> deliveryDelay) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.messages = requireNonNull(messages, "No payloads provided");
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the messages will be added to
     * @param messages      the message payloads  ({@link Message}/{@link OrderedMessage})
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public QueueMessages(QueueName queueName, List<? extends Message> messages, Duration deliveryDelay) {
        this(queueName,
             messages,
             Optional.ofNullable(deliveryDelay));
    }

    /**
     * @return the name of the Queue the messages will be added to
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     * @return the message payloads  ({@link Message}/{@link OrderedMessage})
     */
    public List<? extends Message> getMessages() {
        return messages;
    }

    /**
     * @return optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public Optional<Duration> getDeliveryDelay() {
        return deliveryDelay;
    }

    /**
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    /**
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = Optional.ofNullable(deliveryDelay);
    }

    /**
     *
     * @param messages the messages being queued  ({@link Message}/{@link OrderedMessage})
     */
    public void setMessages(List<Message> messages) {
        this.messages = requireNonNull(messages, "No payloads provided");
    }

    @Override
    public String toString() {
        return "QueueMessages{" +
                "queueName=" + queueName +
                ", messages=" + messages +
                '}';
    }

    public void validate() {
        requireNonNull(queueName, "You must provide a queueName");
        requireNonNull(messages, "You must provide a messages list");
        requireNonNull(deliveryDelay, "You must provide a deliveryDelay option");
    }
}
