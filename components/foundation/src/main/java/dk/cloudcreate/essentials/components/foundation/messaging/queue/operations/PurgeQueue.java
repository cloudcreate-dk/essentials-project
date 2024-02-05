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
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Delete all messages (Queued or Dead letter Messages) in the given queue<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(PurgeQueue, InterceptorChain)}
 */
public class PurgeQueue {
    public final QueueName queueName;

    /**
     * Create a new builder that produces a new {@link PurgeQueue} instance
     *
     * @return a new {@link PurgeQueueBuilder} instance
     */
    public static PurgeQueueBuilder builder() {
        return new PurgeQueueBuilder();
    }

    /**
     * Delete all messages (Queued or Dead letter Messages) in the given queue
     *
     * @param queueName the name of the Queue where all the messages will be deleted
     */
    public PurgeQueue(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
    }

    /**
     * @return the name of the Queue where all the messages will be deleted
     */
    public QueueName getQueueName() {
        return queueName;
    }

    @Override
    public String toString() {
        return "PurgeQueue{" +
                "queueName=" + queueName +
                '}';
    }
}
