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

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

/**
 * Builder for {@link ConsumeFromQueue}
 */
public class ConsumeFromQueueBuilder {
    private QueueName            queueName;
    private RedeliveryPolicy     redeliveryPolicy;
    private int                  parallelConsumers;
    private QueuedMessageHandler queueMessageHandler;

    /**
     * @param queueName the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }


    /**
     * @param redeliveryPolicy the redelivery policy in case the handling of a message fails
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
        return this;
    }

    /**
     * @param parallelConsumers the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setParallelConsumers(int parallelConsumers) {
        this.parallelConsumers = parallelConsumers;
        return this;
    }

    /**
     * @param queueMessageHandler the message handler that will receive {@link QueuedMessage}'s
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setQueueMessageHandler(QueuedMessageHandler queueMessageHandler) {
        this.queueMessageHandler = queueMessageHandler;
        return this;
    }

    /**
     * Builder an {@link ConsumeFromQueue} instance from the builder properties
     * @return the {@link ConsumeFromQueue} instance
     */
    public ConsumeFromQueue build() {
        return new ConsumeFromQueue(queueName, redeliveryPolicy, parallelConsumers, queueMessageHandler);
    }
}