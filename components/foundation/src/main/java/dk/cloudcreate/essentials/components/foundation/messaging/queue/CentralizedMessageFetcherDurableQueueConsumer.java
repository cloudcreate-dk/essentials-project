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
package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import org.slf4j.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * A specialized {@link DurableQueueConsumer} implementation that works with the
 * {@link CentralizedMessageFetcher}. Instead of each consumer polling for messages
 * independently, this consumer registers with the centralized fetcher and receives
 * messages when they become available.
 */
public class CentralizedMessageFetcherDurableQueueConsumer implements DurableQueueConsumer {
    private static final Logger log = LoggerFactory.getLogger(CentralizedMessageFetcherDurableQueueConsumer.class);

    private final QueueName                      queueName;
    private final String                         consumerName;
    private final QueuedMessageHandler           messageHandler;
    private final AtomicBoolean                  started;
    private final RedeliveryPolicy               redeliveryPolicy;
    private final Consumer<DurableQueueConsumer> removeDurableQueueConsumer;
    private final ExecutorService                workerPool;
    private final int                            parallelConsumers;
    private final CentralizedMessageFetcher      centralizedMessageFetcher;

    /**
     * Create a new CentralizedPostgresqlDurableQueueConsumer
     *
     * @param consumeFromQueue           the consume from queue operation
     * @param durableQueues              the durable queues instance
     * @param removeDurableQueueConsumer callback to invoke when the consumer is stopped
     * @param centralizedMessageFetcher  the centralized message fetcher
     */
    public CentralizedMessageFetcherDurableQueueConsumer(ConsumeFromQueue consumeFromQueue,
                                                         BatchMessageFetchingCapableDurableQueues durableQueues,
                                                         Consumer<DurableQueueConsumer> removeDurableQueueConsumer,
                                                         CentralizedMessageFetcher centralizedMessageFetcher) {
        requireNonNull(consumeFromQueue, "No consumeFromQueue provided");
        requireNonNull(durableQueues, "No durableQueues provided");

        this.queueName = consumeFromQueue.queueName;
        this.consumerName = consumeFromQueue.consumerName;
        this.messageHandler = consumeFromQueue.queueMessageHandler;
        this.redeliveryPolicy = consumeFromQueue.getRedeliveryPolicy();
        this.removeDurableQueueConsumer = requireNonNull(removeDurableQueueConsumer, "No removeDurableQueueConsumer provided");
        this.started = new AtomicBoolean(false);
        this.parallelConsumers = consumeFromQueue.getParallelConsumers();
        this.centralizedMessageFetcher = requireNonNull(centralizedMessageFetcher, "No centralizedMessageFetcher provided");

        // Create worker pool
        this.workerPool = consumeFromQueue.getConsumerExecutorService()
                                          .orElseGet(() -> Executors.newScheduledThreadPool(
                                                  consumeFromQueue.getParallelConsumers(),
                                                  new ThreadFactoryBuilder()
                                                          .nameFormat("Queue-" + queueName + "-Worker-%d")
                                                          .daemon(true)
                                                          .build()));

        log.info("[{}] '{}' - Created CentralizedMessageFetcherDurableQueueConsumer with {} workers",
                 queueName,
                 consumerName,
                 parallelConsumers);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            log.info("[{}] '{}' - Starting with {} workers",
                     queueName,
                     consumerName,
                     parallelConsumers);

            // Register with the centralized fetcher
            centralizedMessageFetcher.registerConsumer(
                    queueName,
                    this,
                    messageHandler,
                    workerPool,
                    parallelConsumers);
        }
    }

    @Override
    public void stop() {
        if (started.compareAndSet(true, false)) {
            log.info("[{}] '{}' - Stopping",
                     queueName,
                     consumerName);

            // Allow for worker pool shutdown if not provided externally
            if (!workerPool.isShutdown()) {
                workerPool.shutdown();
            }

            removeDurableQueueConsumer.accept(this);

            // If the removeDurableQueueConsumer didn't unregister the consumer, then unregister it here
            if (centralizedMessageFetcher.containsConsumerFor(queueName)) {
                centralizedMessageFetcher.unregisterConsumer(queueName);
            }

            log.info("[{}] '{}' - Stopped",
                     queueName,
                     consumerName);
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public QueueName queueName() {
        return queueName;
    }

    @Override
    public String consumerName() {
        return consumerName;
    }

    @Override
    public void cancel() {
        stop();
    }

    @Override
    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }
}