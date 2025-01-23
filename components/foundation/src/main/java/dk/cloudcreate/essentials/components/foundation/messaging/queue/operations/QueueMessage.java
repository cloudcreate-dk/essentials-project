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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;
import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(QueueMessage, InterceptorChain)}
 */
public final class QueueMessage {
    public final QueueName           queueName;
    private      Message             message;
    private      Optional<Exception> causeOfEnqueuing;
    private      Optional<Duration>  deliveryDelay;

    /**
     * Create a new builder that produces a new {@link QueueMessage} instance
     *
     * @return a new {@link QueueMessageBuilder} instance
     */
    public static QueueMessageBuilder builder() {
        return new QueueMessageBuilder();
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName        the name of the Queue the message is added to
     * @param message          the message being queued ({@link Message}/{@link OrderedMessage})
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @param deliveryDelay    the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @see OrderedMessage
     */
    public QueueMessage(QueueName queueName, Message message, Optional<Exception> causeOfEnqueuing, Optional<Duration> deliveryDelay) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.message = requireNonNull(message, "No message provided");
        this.causeOfEnqueuing = requireNonNull(causeOfEnqueuing, "No causeOfEnqueuing option provided");
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay option provided");

    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName        the name of the Queue the message is added to
     * @param message          the message being queued  ({@link Message}/{@link OrderedMessage})
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @param deliveryDelay    the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @see OrderedMessage
     */
    public QueueMessage(QueueName queueName, Message message, Exception causeOfEnqueuing, Duration deliveryDelay) {
        this(queueName,
             message,
             Optional.ofNullable(causeOfEnqueuing),
             Optional.ofNullable(deliveryDelay));
    }

    /**
     * @return the name of the Queue the message is added to
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     * @return the message payload
     */
    public Object getPayload() {
        return message.getPayload();
    }

    /**
     * @return the optional reason for the message being queued
     */
    public Optional<Exception> getCauseOfEnqueuing() {
        return causeOfEnqueuing;
    }

    /**
     * @return the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     */
    public Optional<Duration> getDeliveryDelay() {
        return deliveryDelay;
    }

    /**
     * @param message the message being queued  ({@link Message}/{@link OrderedMessage})
     */
    public void setMessage(Message message) {
        this.message = requireNonNull(message, "No message provided");
    }

    /**
     * Get the message being queued
     *
     * @return Get the message being queued  ({@link Message}/{@link OrderedMessage})
     */
    public Message getMessage() {
        return message;
    }

    /**
     * @param deliveryDelay the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    /**
     * @return metadata metadata related to the message/payload
     */
    public MessageMetaData getMetaData() {
        return message.getMetaData();
    }

    /**
     * @param causeOfEnqueuing the optional reason for the message being queued
     */
    public void setCauseOfEnqueuing(Optional<Exception> causeOfEnqueuing) {
        this.causeOfEnqueuing = requireNonNull(causeOfEnqueuing, "No causeOfEnqueuing provided");
    }

    /**
     * @param deliveryDelay the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = Optional.ofNullable(deliveryDelay);
    }

    /**
     * @param causeOfEnqueuing the optional reason for the message being queued
     */
    public void setCauseOfEnqueuing(Exception causeOfEnqueuing) {
        this.causeOfEnqueuing = Optional.ofNullable(causeOfEnqueuing);
    }

    @Override
    public String toString() {
        return "QueueMessage{" +
                "queueName=" + queueName +
                ", message=" + message +
                ", causeOfEnqueuing=" + causeOfEnqueuing +
                ", deliveryDelay=" + deliveryDelay +
                '}';
    }
}
