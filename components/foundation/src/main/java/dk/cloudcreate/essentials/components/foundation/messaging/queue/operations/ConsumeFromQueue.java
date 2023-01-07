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
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static dk.cloudcreate.essentials.shared.FailFast.*;

/**
 * Start an asynchronous message consumer.<br>
 * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
 * Operation also matches {@link DurableQueuesInterceptor#intercept(ConsumeFromQueue, InterceptorChain)}
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ConsumeFromQueue {
    public final  QueueName                          queueName;
    private       RedeliveryPolicy                   redeliveryPolicy;
    public final  QueuedMessageHandler               queueMessageHandler;
    private final int                                parallelConsumers;
    private       Optional<ScheduledExecutorService> consumerExecutorService = Optional.empty();

    /**
     * Create a new builder that produces a new {@link ConsumeFromQueue} instance
     *
     * @return a new {@link ConsumeFromQueueBuilder} instance
     */
    public static ConsumeFromQueueBuilder builder() {
        return new ConsumeFromQueueBuilder();
    }

    /**
     * Start an asynchronous message consumer.<br>
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
     *
     * @param queueName           the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @param redeliveryPolicy    the redelivery policy in case the handling of a message fails
     * @param parallelConsumers   the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @param queueMessageHandler the message handler that will receive {@link QueuedMessage}'s
     */
    public ConsumeFromQueue(QueueName queueName, RedeliveryPolicy redeliveryPolicy, int parallelConsumers, QueuedMessageHandler queueMessageHandler) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
        this.parallelConsumers = parallelConsumers;
        this.queueMessageHandler = requireNonNull(queueMessageHandler, "No queueMessageHandler provided");
    }

    /**
     * Start an asynchronous message consumer.<br>
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
     *
     * @param queueName               the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @param redeliveryPolicy        the redelivery policy in case the handling of a message fails
     * @param parallelConsumers       the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @param consumerExecutorService the optional {@link ScheduledExecutorService} that's responsible for scheduling the <code>parallelConsumers</code>. Also see {@link ThreadFactoryBuilder}
     * @param queueMessageHandler     the message handler that will receive {@link QueuedMessage}'s
     */
    public ConsumeFromQueue(QueueName queueName,
                            RedeliveryPolicy redeliveryPolicy,
                            int parallelConsumers,
                            ScheduledExecutorService consumerExecutorService,
                            QueuedMessageHandler queueMessageHandler) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
        this.parallelConsumers = parallelConsumers;
        this.consumerExecutorService = Optional.of(consumerExecutorService);
        this.queueMessageHandler = requireNonNull(queueMessageHandler, "No queueMessageHandler provided");
    }

    /**
     * Start an asynchronous message consumer.<br>
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
     *
     * @param queueName               the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @param redeliveryPolicy        the redelivery policy in case the handling of a message fails
     * @param parallelConsumers       the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @param consumerExecutorService the optional {@link ScheduledExecutorService} that's responsible for scheduling the <code>parallelConsumers</code>. Also see {@link ThreadFactoryBuilder}
     * @param queueMessageHandler     the message handler that will receive {@link QueuedMessage}'s
     */
    public ConsumeFromQueue(QueueName queueName,
                            RedeliveryPolicy redeliveryPolicy,
                            int parallelConsumers,
                            Optional<ScheduledExecutorService> consumerExecutorService,
                            QueuedMessageHandler queueMessageHandler) {
        this.queueName = queueName;
        this.redeliveryPolicy = redeliveryPolicy;
        this.queueMessageHandler = queueMessageHandler;
        this.parallelConsumers = parallelConsumers;
        this.consumerExecutorService = requireNonNull(consumerExecutorService, "No consumerExecutorService Optional provided");
    }

    /**
     * @return the redelivery policy in case the handling of a message fails
     */
    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * @param redeliveryPolicy the redelivery policy in case the handling of a message fails
     */
    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
    }

    /**
     * @return the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     * @return the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     */
    public int getParallelConsumers() {
        return parallelConsumers;
    }

    /**
     * @return the optional {@link ScheduledExecutorService} that's responsible for controlling the number of Message consumer instance.
     */
    public Optional<ScheduledExecutorService> getConsumerExecutorService() {
        return consumerExecutorService;
    }

    /**
     * @return the message handler that will receive {@link QueuedMessage}'s
     */
    public QueuedMessageHandler getQueueMessageHandler() {
        return queueMessageHandler;
    }

    @Override
    public String toString() {
        return "ConsumeFromQueue{" +
                "queueName=" + queueName +
                ", redeliveryPolicy=" + redeliveryPolicy +
                ", queueMessageHandler=" + queueMessageHandler +
                ", parallelConsumers=" + parallelConsumers +
                ", consumerExecutorService=" + consumerExecutorService +
                '}';
    }

    public void validate() {
        requireNonNull(queueName, "You must provide a queueName");
        requireNonNull(redeliveryPolicy, "You must provide a redeliveryPolicy");
        requireNonNull(queueMessageHandler, "You must provide a queueMessageHandler");
        requireTrue(parallelConsumers >= 1,
                    "parallelConsumers must be >= 1");
    }
}
