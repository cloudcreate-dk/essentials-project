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
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Schedule the message for redelivery after the specified <code>deliveryDelay</code> (called by the {@link DurableQueueConsumer})<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(RetryMessage, InterceptorChain)}
 */
public class RetryMessage {
    public final QueueEntryId queueEntryId;
    private      Throwable    causeForRetry;
    private      Duration     deliveryDelay;

    /**
     * Create a new builder that produces a new {@link RetryMessage} instance
     *
     * @return a new {@link RetryMessageBuilder} instance
     */
    public static RetryMessageBuilder builder() {
        return new RetryMessageBuilder();
    }

    /**
     * Schedule the message for redelivery after the specified <code>deliveryDelay</code> (called by the {@link DurableQueueConsumer})<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId  the unique id of the message that must we will retry the delivery of
     * @param causeForRetry the reason why the message delivery has to be retried
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     */
    public RetryMessage(QueueEntryId queueEntryId, Throwable causeForRetry, Duration deliveryDelay) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
        this.causeForRetry = causeForRetry;
        this.deliveryDelay = deliveryDelay;
    }

    /**
     *
     * @return the unique id of the message that must we will retry the delivery of
     */
    public QueueEntryId getQueueEntryId() {
        return queueEntryId;
    }

    /**
     *
     * @return the reason why the message delivery has to be retried
     */
    public Throwable getCauseForRetry() {
        return causeForRetry;
    }

    /**
     *
     * @param causeForRetry the reason why the message delivery has to be retried
     */
    public void setCauseForRetry(Exception causeForRetry) {
        this.causeForRetry = causeForRetry;
    }

    /**
     *
     * @return how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     */
    public Duration getDeliveryDelay() {
        return deliveryDelay;
    }

    /**
     *
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
    }

    @Override
    public String toString() {
        return "RetryMessage{" +
                "queueEntryId=" + queueEntryId +
                ", causeForRetry=" + causeForRetry +
                ", deliveryDelay=" + deliveryDelay +
                '}';
    }

    public void validate() {
        requireNonNull(queueEntryId, "You must provide a queueEntryId");
        requireNonNull(causeForRetry, "You must provide a causeForRetry");
        requireNonNull(deliveryDelay, "You must provide a deliveryDelay");
    }
}
