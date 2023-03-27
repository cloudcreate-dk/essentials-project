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

package dk.cloudcreate.essentials.components.boot.autoconfigure.postgresql;

import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockStorage;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.foundation.postgresql.MultiTableChangeListener;
import dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Properties for the Postgresql focused Essentials Components auto-configuration
 */
@Configuration
@ConfigurationProperties(prefix = "essentials")
public class EssentialsComponentsProperties {
    private final FencedLockManagerProperties fencedLockManager = new FencedLockManagerProperties();
    private final DurableQueuesProperties     durableQueues     = new DurableQueuesProperties();

    private final MultiTableChangeListenerProperties multiTableChangeListener = new MultiTableChangeListenerProperties();


    public FencedLockManagerProperties getFencedLockManager() {
        return fencedLockManager;
    }

    public DurableQueuesProperties getDurableQueues() {
        return durableQueues;
    }

    public MultiTableChangeListenerProperties getMultiTableChangeListener() {
        return multiTableChangeListener;
    }

    public static class MultiTableChangeListenerProperties {
        private Duration pollingInterval = Duration.ofMillis(50);

        /**
         * Get the interval with which the {@link MultiTableChangeListener} is polling Postgresql for notification
         *
         * @return the interval with which the {@link MultiTableChangeListener} is polling Postgresql for notification
         */
        public Duration getPollingInterval() {
            return pollingInterval;
        }

        /**
         * Set the interval with which the {@link MultiTableChangeListener} is polling Postgresql for notification
         *
         * @param pollingInterval the interval with which the {@link MultiTableChangeListener} is polling Postgresql for notification
         */
        public void setSharedQueueTableName(Duration pollingInterval) {
            this.pollingInterval = pollingInterval;
        }
    }

    public static class DurableQueuesProperties {
        private String sharedQueueTableName = PostgresqlDurableQueues.DEFAULT_DURABLE_QUEUES_TABLE_NAME;

        private Double pollingDelayIntervalIncrementFactor = 0.5d;

        private Duration maxPollingInterval = Duration.ofMillis(2000);

        /**
         * Get the name of the table that will contain all messages (across all {@link QueueName}'s)<br>
         * Default is {@value PostgresqlDurableQueues#DEFAULT_DURABLE_QUEUES_TABLE_NAME}
         *
         * @return the name of the table that will contain all messages (across all {@link QueueName}'s)
         */
        public String getSharedQueueTableName() {
            return sharedQueueTableName;
        }

        /**
         * Set the name of the table that will contain all messages (across all {@link QueueName}'s)<br>
         * Default is {@value PostgresqlDurableQueues#DEFAULT_DURABLE_QUEUES_TABLE_NAME}
         *
         * @param sharedQueueTableName the name of the table that will contain all messages (across all {@link QueueName}'s)
         */
        public void setSharedQueueTableName(String sharedQueueTableName) {
            this.sharedQueueTableName = sharedQueueTableName;
        }

        /**
         * When the {@link PostgresqlDurableQueues} polling returns 0 messages, what should the increase in the {@link ConsumeFromQueue#getPollingInterval()}
         * be? (logic: new_polling_interval = current_polling_interval + base_polling_interval * polling_delay_interval_increment_factor)<br>
         * Default is 0.5d<br>
         * This is used to avoid polling a the {@link DurableQueues} for a queue that isn't experiencing a lot of messages
         *
         * @return the increase in the {@link ConsumeFromQueue#getPollingInterval()} when the {@link DurableQueues} polling returns 0 messages
         */
        public Double getPollingDelayIntervalIncrementFactor() {
            return pollingDelayIntervalIncrementFactor;
        }

        /**
         * When the {@link PostgresqlDurableQueues} polling returns 0 messages, what should the increase in the {@link ConsumeFromQueue#getPollingInterval()}
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

    public static class FencedLockManagerProperties {
        private Duration lockTimeOut              = Duration.ofSeconds(15);
        private Duration lockConfirmationInterval = Duration.ofSeconds(4);
        private String   fencedLocksTableName     = PostgresqlFencedLockStorage.DEFAULT_FENCED_LOCKS_TABLE_NAME;

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
         * Get the name of the table where the fenced locks will be maintained - {@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME}
         *
         * @return the name of the table where the fenced locks will be maintained - {@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME}
         */
        public String getFencedLocksTableName() {
            return fencedLocksTableName;
        }

        /**
         * Set the name of the table where the fenced locks will be maintained - {@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME}
         *
         * @param fencedLocksTableName the name of the table where the fenced locks will be maintained - {@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME}
         */
        public void setFencedLocksTableName(String fencedLocksTableName) {
            this.fencedLocksTableName = fencedLocksTableName;
        }
    }
}
