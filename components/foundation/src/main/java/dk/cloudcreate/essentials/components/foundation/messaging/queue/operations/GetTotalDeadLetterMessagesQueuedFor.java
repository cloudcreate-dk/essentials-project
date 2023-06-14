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
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Get the total number of dead-letter-messages/poison-messages queued for the given queue<br>
 * Operation also matched {@link DurableQueuesInterceptor#intercept(GetTotalDeadLetterMessagesQueuedFor, InterceptorChain)}
 */
public class GetTotalDeadLetterMessagesQueuedFor {
    /**
     * the name of the Queue where we will query for the
     * number of dead-letter-messages/poison-messages queued for the given queue
     */
    public final QueueName queueName;

    /**
     * Create a new builder that produces a new {@link GetTotalDeadLetterMessagesQueuedFor} instance
     *
     * @return a new {@link GetTotalMessagesQueuedForBuilder} instance
     */
    public static GetTotalMessagesQueuedForBuilder builder() {
        return new GetTotalMessagesQueuedForBuilder();
    }

    /**
     * Get the total number of dead-letter-messages/poison-messages queued for the given queue
     *
     * @param queueName the name of the Queue where we will query for the
     *                  number of dead-letter-messages/poison-messages queued for the given queue
     */
    public GetTotalDeadLetterMessagesQueuedFor(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
    }

    /**
     * @return the name of the Queue where we will query for the number of queued messages
     */
    public QueueName getQueueName() {
        return queueName;
    }

    @Override
    public String toString() {
        return "GetTotalDeadLetterMessagesQueuedFor{" +
                "queueName=" + queueName +
                '}';
    }
}
