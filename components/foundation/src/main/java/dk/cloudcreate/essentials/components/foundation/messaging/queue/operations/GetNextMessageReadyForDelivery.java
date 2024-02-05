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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matched {@link DurableQueuesInterceptor#intercept(GetNextMessageReadyForDelivery, InterceptorChain)}
 */
public class GetNextMessageReadyForDelivery {
    /**
     * the name of the Queue where we will query for the next message ready for delivery
     */
    public final  QueueName          queueName;
    private final Collection<String> excludeOrderedMessagesWithKey;

    /**
     * Create a new builder that produces a new {@link GetNextMessageReadyForDelivery} instance
     *
     * @return a new {@link GetNextMessageReadyForDeliveryBuilder} instance
     */
    public static GetNextMessageReadyForDeliveryBuilder builder() {
        return new GetNextMessageReadyForDeliveryBuilder();
    }

    /**
     * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName the name of the Queue where we will query for the next message ready for delivery
     */
    public GetNextMessageReadyForDelivery(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.excludeOrderedMessagesWithKey = List.of();
    }

    /**
     * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName the name of the Queue where we will query for the next message ready for delivery
     * @param excludeOrderedMessagesWithKey collection of {@link OrderedMessage#getKey()}'s to exclude in the search for the next message
     */
    public GetNextMessageReadyForDelivery(QueueName queueName, Collection<String> excludeOrderedMessagesWithKey) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.excludeOrderedMessagesWithKey = requireNonNull(excludeOrderedMessagesWithKey, "No excludeOrderedMessagesWithKey collection provided");
    }

    /**
     * @return the name of the Queue where we will query for the next message ready for delivery
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     * @return collection of {@link OrderedMessage#getKey()}'s to exclude in the search for the next message
     */
    public Collection<String> getExcludeOrderedMessagesWithKey() {
        return excludeOrderedMessagesWithKey;
    }

    @Override
    public String toString() {
        return "GetNextMessageReadyForDelivery{" +
                "queueName=" + queueName +
                ", excludeOrderedMessagesWithKey=" + excludeOrderedMessagesWithKey +
                '}';
    }
}
