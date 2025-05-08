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

import dk.trustworks.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.shared.concurrent.ThreadFactoryBuilder;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Builder for {@link ConsumeFromQueue}
 */
public final class ConsumeFromQueueBuilder {
    private String                             consumerName            = UUID.randomUUID().toString();
    private QueueName                          queueName;
    private RedeliveryPolicy                   redeliveryPolicy;
    private int                                parallelConsumers;
    private Optional<ScheduledExecutorService> consumerExecutorService = Optional.empty();
    private Duration                           pollingInterval         = Duration.ofMillis(100);
    private QueuedMessageHandler               queueMessageHandler;

    /**
     * @param queueName the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     * @param pollingInterval the interval with which the consumer poll the queue db for new messages to process
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setPollingInterval(Duration pollingInterval) {
        this.pollingInterval = pollingInterval;
        return this;
    }

    /**
     * @param consumerName the name of the consumer (for logging purposes)
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setConsumerName(String consumerName) {
        this.consumerName = consumerName;
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
     * @param parallelConsumers the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)<br>
     *                          Optional if you provide {@link #setConsumerExecutorService(ScheduledExecutorService)} instead
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setParallelConsumers(int parallelConsumers) {
        this.parallelConsumers = parallelConsumers;
        return this;
    }

    /**
     * @param consumerExecutorService the {@link ScheduledExecutorService} that's responsible for controlling the number of Message consumer instance. E.g. if you provide an {@link Executors#newSingleThreadExecutor()}
     *                                then there will only be a single message handler instance running on the local node. Also see {@link ThreadFactoryBuilder}<br>
     *                                Optional if you provide {@link #setParallelConsumers(int)} instead
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setConsumerExecutorService(ScheduledExecutorService consumerExecutorService) {
        this.consumerExecutorService = Optional.of(consumerExecutorService);
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
     *
     * @return the {@link ConsumeFromQueue} instance
     */
    public ConsumeFromQueue build() {
        return new ConsumeFromQueue(consumerName,
                                    queueName,
                                    redeliveryPolicy,
                                    parallelConsumers,
                                    consumerExecutorService,
                                    queueMessageHandler,
                                    pollingInterval);
    }
}