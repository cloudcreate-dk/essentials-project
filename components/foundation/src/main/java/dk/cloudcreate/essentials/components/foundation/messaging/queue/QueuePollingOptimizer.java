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

import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import org.slf4j.*;

import java.util.concurrent.atomic.AtomicLong;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Optimizer designed to work together with the {@link DefaultDurableQueueConsumer}<br>
 * The optimizer is responsible for optimizing the frequency by which the {@link DefaultDurableQueueConsumer}
 * is polling the underlying database for new messages related to a given {@link QueueName}.<br>
 * If a given Queue doesn't experience a high influx of message, or a lot of message's have ({@link QueuedMessage#getNextDeliveryTimestamp()})
 * that is further into the future, then it doesn't make sense to poll the database too often.<br>
 * The {@link SimpleQueuePollingOptimizer} supports extending the polling sleep time (i.e. the time between calls to
 * {@link DurableQueues#getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)}) up until a given threshold.
 *
 * @see #None()
 * @see SimpleQueuePollingOptimizer
 */
public interface QueuePollingOptimizer extends DurableQueueConsumerNotifications {
    static QueuePollingOptimizer None() {
        return new QueuePollingOptimizer() {
            @Override
            public void queuePollingReturnedNoMessages() {
            }

            @Override
            public void queuePollingReturnedMessage(QueuedMessage queuedMessage) {
            }

            @Override
            public boolean shouldSkipPolling() {
                return false;
            }

            @Override
            public void messageAdded(QueuedMessage queuedMessage) {
            }

            @Override
            public String toString() {
                return "NoQueuePollingOptimizer";
            }
        };
    }

    void queuePollingReturnedNoMessages();

    void queuePollingReturnedMessage(QueuedMessage queuedMessage);

    boolean shouldSkipPolling();

    /**
     * The {@link SimpleQueuePollingOptimizer} supports extending the polling sleep time (i.e. the time between calls to
     * {@link DurableQueues#getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)}) up until a given threshold for a given queue consumer
     */
    class SimpleQueuePollingOptimizer implements QueuePollingOptimizer {
        private static Logger           log                     = LoggerFactory.getLogger(SimpleQueuePollingOptimizer.class);
        private final  AtomicLong       pollingThreadDelay      = new AtomicLong();
        private final  AtomicLong       shouldSkipPollCallCount = new AtomicLong();
        private final  long             delayIncrementMs;
        private final  long             maxDelayMs;
        private final  ConsumeFromQueue consumeFromQueue;
        private final  long             pollingIntervalMs;

        /**
         * Create a new {@link SimpleQueuePollingOptimizer}
         *
         * @param consumeFromQueue the {@link ConsumeFromQueue} command
         * @param delayIncrementMs when ever {@link QueuePollingOptimizer#queuePollingReturnedNoMessages()} is called
         *                         the delay between calls to {@link DurableQueues#getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)}
         *                         will be increased by <code>delayIncrementMs</code><br>
         *                         If {@link QueuePollingOptimizer#messageAdded(QueuedMessage)} or {@link QueuePollingOptimizer#queuePollingReturnedMessage(QueuedMessage)}
         *                         all called, then the delay between calls to {@link DurableQueues#getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)}
         *                         is reset back to {@link ConsumeFromQueue#getPollingInterval()}
         * @param maxDelayMs       The overall delay between calls to {@link DurableQueues#getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)}
         *                         cannot exceed this value
         */
        public SimpleQueuePollingOptimizer(ConsumeFromQueue consumeFromQueue,
                                           long delayIncrementMs,
                                           long maxDelayMs) {
            this.consumeFromQueue = consumeFromQueue;
            this.pollingIntervalMs = consumeFromQueue.getPollingInterval().toMillis();
            this.delayIncrementMs = delayIncrementMs;
            this.maxDelayMs = maxDelayMs;

            if (pollingIntervalMs == 0) {
                throw new IllegalArgumentException(msg("pollingIntervalMs {} is zero", pollingIntervalMs));
            }

            if (pollingIntervalMs > maxDelayMs) {
                throw new IllegalArgumentException(msg("pollingIntervalMs {} > maxDelayMs {}", pollingIntervalMs, maxDelayMs));
            }
            if (delayIncrementMs > maxDelayMs) {
                throw new IllegalArgumentException(msg("delayIncrementMs {} > maxDelayMs {}", delayIncrementMs, maxDelayMs));
            }
        }

        @Override
        public void queuePollingReturnedNoMessages() {
            var currentDelay = pollingThreadDelay.get();
            if (currentDelay < maxDelayMs) {
                var newDelayMs = currentDelay + delayIncrementMs;
                pollingThreadDelay.set(newDelayMs);
                log.trace("[{}] {} - queuePollingReturnedNoMessages - increasing pollingThreadDelay to {} ms",
                          consumeFromQueue.queueName,
                          consumeFromQueue.consumerName,
                          newDelayMs);
            } else {
                log.trace("[{}] {} - queuePollingReturnedNoMessages - retaining pollingThreadDelay at {} ms",
                          consumeFromQueue.queueName,
                          consumeFromQueue.consumerName,
                          currentDelay);
            }
            shouldSkipPollCallCount.set(0L);
        }

        @Override
        public void queuePollingReturnedMessage(QueuedMessage queuedMessage) {
            log.trace("[{}] {} - queuePollingReturnedMessage - resetting pollingThreadDelay: 0 ms",
                      consumeFromQueue.queueName,
                      consumeFromQueue.consumerName);
            pollingThreadDelay.set(0L);
            shouldSkipPollCallCount.set(0L);
        }

        @Override
        public boolean shouldSkipPolling() {
            var pollingDelay = pollingThreadDelay.get();
            if (pollingDelay == 0L) {
                log.trace("[{}] {} - shouldSkipPolling: false - pollingDelay: 0 ms",
                          consumeFromQueue.queueName,
                          consumeFromQueue.consumerName);
                return false;
            }
            var callCount = shouldSkipPollCallCount.get();
            callCount++;
            shouldSkipPollCallCount.set(callCount);
            if (pollingIntervalMs * callCount > pollingDelay) {
                log.trace("[{}] {} - shouldSkipPolling: false - pollingInterval: {} ms * callCount: {} > pollingDelay: {} ms",
                          consumeFromQueue.queueName,
                          consumeFromQueue.consumerName,
                          pollingIntervalMs,
                          callCount,
                          pollingDelay);
                return false;
            } else {
                log.trace("[{}] {} - shouldSkipPolling: true - pollingInterval: {} ms * callCount: {} <= pollingDelay: {} ms",
                          consumeFromQueue.queueName,
                          consumeFromQueue.consumerName,
                          pollingIntervalMs,
                          callCount,
                          pollingDelay);
                return true;
            }

        }

        @Override
        public void messageAdded(QueuedMessage queuedMessage) {
            log.trace("[{}] {} - messageAdded - resetting pollingThreadDelay: 0 ms and shouldSkipPollCallCount: 0. Message: {}",
                      consumeFromQueue.queueName,
                      consumeFromQueue.consumerName,
                      queuedMessage);
            pollingThreadDelay.set(0L);
            shouldSkipPollCallCount.set(0L);
        }

        @Override
        public String toString() {
            return "SimpleQueuePollingOptimizer{" +
                    "pollingIntervalMs=" + pollingIntervalMs +
                    ", delayIncrementMs=" + delayIncrementMs +
                    ", maxDelayMs=" + maxDelayMs +
                    ", consumeFromQueue=" + consumeFromQueue +
                    '}';
        }
    }
}
