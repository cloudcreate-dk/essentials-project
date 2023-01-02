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
 * Resurrect a Dead Letter Message for redelivery after the specified <code>deliveryDelay</code><br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(ResurrectDeadLetterMessage, InterceptorChain)}
 */
public class ResurrectDeadLetterMessage {
    public final QueueEntryId queueEntryId;
    private      Duration     deliveryDelay;

    /**
     * Create a new builder that produces a new {@link ResurrectDeadLetterMessage} instance
     *
     * @return a new {@link ResurrectDeadLetterMessageBuilder} instance
     */
    public static ResurrectDeadLetterMessageBuilder builder() {
        return new ResurrectDeadLetterMessageBuilder();
    }

    /**
     * Resurrect a Dead Letter Message for redelivery after the specified <code>deliveryDelay</code><br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId  the unique id of the Dead Letter Message that must we will retry the delivery of
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     */
    public ResurrectDeadLetterMessage(QueueEntryId queueEntryId, Duration deliveryDelay) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
        this.deliveryDelay = deliveryDelay;
    }

    /**
     *
     * @return the unique id of the Dead Letter Message that must we will retry the delivery of
     */
    public QueueEntryId getQueueEntryId() {
        return queueEntryId;
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
        return "ResurrectDeadLetterMessage{" +
                "queueEntryId=" + queueEntryId +
                ", deliveryDelay=" + deliveryDelay +
                '}';
    }

    public void validate() {
        requireNonNull(queueEntryId, "You must provide a queueEntryId");
        requireNonNull(deliveryDelay, "You must provide a deliveryDelay");
    }
}
