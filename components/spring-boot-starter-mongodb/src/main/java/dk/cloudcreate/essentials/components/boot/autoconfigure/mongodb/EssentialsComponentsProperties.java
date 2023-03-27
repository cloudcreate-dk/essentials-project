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

package dk.cloudcreate.essentials.components.boot.autoconfigure.mongodb;

import dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.queue.springdata.mongodb.MongoDurableQueues;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Optional;

/**
 * Properties for the MongoDB focused Essentials Components auto-configuration
 */
@Configuration
@ConfigurationProperties(prefix = "essentials")
public class EssentialsComponentsProperties {
    private final FencedLockManager fencedLockManager = new FencedLockManager();
    private final DurableQueues     durableQueues     = new DurableQueues();

    public FencedLockManager getFencedLockManager() {
        return fencedLockManager;
    }

    public DurableQueues getDurableQueues() {
        return durableQueues;
    }

    public static class DurableQueues {
        private String sharedQueueCollectionName = MongoDurableQueues.DEFAULT_DURABLE_QUEUES_COLLECTION_NAME;

        private TransactionalMode transactionalMode = TransactionalMode.FullyTransactional;

        private Duration messageHandlingTimeout = Duration.ofSeconds(15);

        private Double pollingDelayIntervalIncrementFactor = 0.5d;

        private Duration maxPollingInterval = Duration.ofMillis(2000);

        /**
         * Get the name of the collection that will contain all messages (across all {@link QueueName}'s)<br>
         * Default is {@value MongoDurableQueues#DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}
         *
         * @return the name of the collection that will contain all messages (across all {@link QueueName}'s)
         */
        public String getSharedQueueCollectionName() {
            return sharedQueueCollectionName;
        }

        /**
         * Set the name of the collection that will contain all messages (across all {@link QueueName}'s)<br>
         * Default is {@value MongoDurableQueues#DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}
         *
         * @param sharedQueueCollectionName the name of the collection that will contain all messages (across all {@link QueueName}'s)
         */
        public void setSharedQueueCollectionName(String sharedQueueCollectionName) {
            this.sharedQueueCollectionName = sharedQueueCollectionName;
        }

        /**
         * Get the transactional behaviour mode of the {@link MongoDurableQueues}<br>
         * Default: {@link TransactionalMode#FullyTransactional}
         *
         * @return the transactional behaviour mode of the {@link MongoDurableQueues
         */
        public TransactionalMode getTransactionalMode() {
            return transactionalMode;
        }

        /**
         * Set the transactional behaviour mode of the {@link MongoDurableQueues<br>
         * Default: {@link TransactionalMode#FullyTransactional}
         *
         * @param transactionalMode the transactional behaviour mode of the {@link MongoDurableQueues
         */
        public void setTransactionalMode(TransactionalMode transactionalMode) {
            this.transactionalMode = transactionalMode;
        }

        /**
         * Get the Message Handling timeout - Only relevant for {@link TransactionalMode#SingleOperationTransaction}<br>
         * The Message Handling timeout defines the timeout for messages being delivered, but haven't yet been acknowledged.
         * After this timeout the message delivery will be reset and the message will again be a candidate for delivery<br>
         * Default 15 seconds
         *
         * @return the Message Handling timeout
         */
        public Duration getMessageHandlingTimeout() {
            return messageHandlingTimeout;
        }

        /**
         * Get the Message Handling timeout - Only relevant for {@link TransactionalMode#SingleOperationTransaction}<br>
         * The Message Handling timeout defines the timeout for messages being delivered, but haven't yet been acknowledged.
         * After this timeout the message delivery will be reset and the message will again be a candidate for delivery<br>
         * Default 15 seconds
         *
         * @param messageHandlingTimeout the Message Handling timeout
         */
        public void setMessageHandlingTimeout(Duration messageHandlingTimeout) {
            this.messageHandlingTimeout = messageHandlingTimeout;
        }

        /**
         * When the {@link MongoDurableQueues} polling returns 0 messages, what should the increase in the {@link ConsumeFromQueue#getPollingInterval()}
         * be? (logic: new_polling_interval = current_polling_interval + base_polling_interval * polling_delay_interval_increment_factor)<br>
         * Default is 0.5d<br>
         * This is used to avoid polling a the {@link dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues} for a queue that isn't experiencing a lot of messages
         *
         * @return the increase in the {@link ConsumeFromQueue#getPollingInterval()} when the {@link dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues} polling returns 0 messages
         */
        public Double getPollingDelayIntervalIncrementFactor() {
            return pollingDelayIntervalIncrementFactor;
        }

        /**
         * When the {@link MongoDurableQueues} polling returns 0 messages, what should the increase in the {@link ConsumeFromQueue#getPollingInterval()}
         * be? (logic: new_polling_interval = current_polling_interval + base_polling_interval * polling_delay_interval_increment_factor)<br>
         * Default is 0.5d<br>
         * This is used to avoid polling a the {@link DurableQueues} for a queue that isn't experiencing a lot of messages
         *
         * @param pollingDelayIntervalIncrementFactor the increase in the {@link ConsumeFromQueue#getPollingInterval()} when the {@link DurableQueues} polling returns 0 messages
         */
        public void setPollingDelayIntervalIncrementFactor(Double pollingDelayIntervalIncrementFactor) {
            this.pollingDelayIntervalIncrementFactor = pollingDelayIntervalIncrementFactor;
        }

        /**
         * What is the maximum polling interval (when adjusted using {@link #setPollingDelayIntervalIncrementFactor(Double)})<br>
         * Default is 2 seconds
         *
         * @return What is the maximum polling interval (when adjusted using {@link #setPollingDelayIntervalIncrementFactor(Double)})
         */
        public Duration getMaxPollingInterval() {
            return maxPollingInterval;
        }

        /**
         * What is the maximum polling interval (when adjusted using {@link #setPollingDelayIntervalIncrementFactor(Double)})<br>
         * Default is 2 seconds
         *
         * @param maxPollingInterval the maximum polling interval (when adjusted using {@link #setPollingDelayIntervalIncrementFactor(Double)})
         */
        public void setMaxPollingInterval(Duration maxPollingInterval) {
            this.maxPollingInterval = maxPollingInterval;
        }
    }

    public static class FencedLockManager {
        private Duration lockTimeOut               = Duration.ofSeconds(15);
        private Duration lockConfirmationInterval  = Duration.ofSeconds(4);
        private String   fencedLocksCollectionName = MongoFencedLockStorage.DEFAULT_FENCED_LOCKS_COLLECTION_NAME;

        /**
         * Get the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
         *
         * @return the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
         */
        public Duration getLockTimeOut() {
            return lockTimeOut;
        }

        /**
         * Set the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
         *
         * @param lockTimeOut the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
         */
        public void setLockTimeOut(Duration lockTimeOut) {
            this.lockTimeOut = lockTimeOut;
        }

        /**
         * Get how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
         *
         * @return how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
         */
        public Duration getLockConfirmationInterval() {
            return lockConfirmationInterval;
        }

        /**
         * Set how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
         *
         * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
         */
        public void setLockConfirmationInterval(Duration lockConfirmationInterval) {
            this.lockConfirmationInterval = lockConfirmationInterval;
        }

        /**
         * Get the name of the collection where the locks will be stored. If left {@link Optional#empty()} then the {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME} value will be used
         *
         * @return the name of the collection where the locks will be stored. If left {@link Optional#empty()} then the {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME} value will be used
         */
        public String getFencedLocksCollectionName() {
            return fencedLocksCollectionName;
        }

        /**
         * Set the name of the collection where the locks will be stored. If left {@link Optional#empty()} then the {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME} value will be used
         *
         * @param fencedLocksCollectionName the name of the collection where the locks will be stored. If left {@link Optional#empty()} then the {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME} value will be used
         */
        public void setFencedLocksCollectionName(String fencedLocksCollectionName) {
            this.fencedLocksCollectionName = fencedLocksCollectionName;
        }
    }
}
