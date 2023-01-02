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
 * Stop an asynchronous message consumer.<br>
 * Is initiated when {@link DurableQueueConsumer#cancel()} is called
 * Operation also matches {@link DurableQueuesInterceptor#intercept(StopConsumingFromQueue, InterceptorChain)}
 */
public class StopConsumingFromQueue {
    /**
     * The durable queue consumer being stopped
     */
    public final DurableQueueConsumer durableQueueConsumer;

    /**
     * Create a new builder that produces a new {@link ConsumeFromQueue} instance
     *
     * @return a new {@link ConsumeFromQueueBuilder} instance
     */
    public static ConsumeFromQueueBuilder builder() {
        return new ConsumeFromQueueBuilder();
    }

    /**
     * Stop an asynchronous message consumer.<br>
     *
     * @param durableQueueConsumer the durable queue consumer being stopped
     */
    public StopConsumingFromQueue(DurableQueueConsumer durableQueueConsumer) {
        this.durableQueueConsumer = requireNonNull(durableQueueConsumer, "No durableQueueConsumer provided");
    }

    /**
     * @return the durable queue consumer being stopped
     */
    public DurableQueueConsumer getDurableQueueConsumer() {
        return durableQueueConsumer;
    }

    @Override
    public String toString() {
        return "StopConsumingFromQueue{" +
                "durableQueueConsumer=" + durableQueueConsumer +
                '}';
    }
}
