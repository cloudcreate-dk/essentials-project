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
import dk.trustworks.essentials.shared.interceptor.InterceptorChain;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Get the total number of (non-dead-letter) messages queued and number of queued dead-letter Messages for the given queue<br>
 * Operation also matched {@link DurableQueuesInterceptor#intercept(GetQueuedMessageCountsFor, InterceptorChain)}
 */
public final class GetQueuedMessageCountsFor {
    /**
     * the name of the Queue where we will query for the total number of messages queued and number of queued Dead Letter Messages for the given queue
     */
    public final QueueName           queueName;

    /**
     * Create a new builder that produces a new {@link GetQueuedMessageCountsFor} instance
     *
     * @return a new {@link GetQueuedMessageCountsForBuilder} instance
     */
    public static GetQueuedMessageCountsForBuilder builder() {
        return new GetQueuedMessageCountsForBuilder();
    }

    /**
     * Get the total number of messages queued (i.e. not including Dead Letter Messages) for the given queue
     *
     * @param queueName the name of the Queue where we will query for the number of queued messages
     */
    public GetQueuedMessageCountsFor(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
    }

    /**
     *
     * @return the name of the Queue where we will query for the number of queued messages
     */
    public QueueName getQueueName() {
        return queueName;
    }

    @Override
    public String toString() {
        return "GetQueuedMessageCountsFor{" +
                "queueName=" + queueName +
                '}';
    }
}
