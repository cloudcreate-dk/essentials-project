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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;
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
    private final EventStore        eventStore        = new EventStore();
    private final FencedLockManager fencedLockManager = new FencedLockManager();
    private final DurableQueues     durableQueues     = new DurableQueues();

    public EventStore getEventStore() {
        return eventStore;
    }

    public FencedLockManager getFencedLockManager() {
        return fencedLockManager;
    }

    public DurableQueues getDurableQueues() {
        return durableQueues;
    }

    public static class DurableQueues {
        private String sharedQueueTableName = PostgresqlDurableQueues.DEFAULT_DURABLE_QUEUES_TABLE_NAME;

        /**
         * Get the name of the table that will contain all messages (across all {@link QueueName}'s)
         *
         * @return the name of the table that will contain all messages (across all {@link QueueName}'s)
         */
        public String getSharedQueueTableName() {
            return sharedQueueTableName;
        }

        /**
         * Set the name of the table that will contain all messages (across all {@link QueueName}'s)
         *
         * @param sharedQueueTableName the name of the table that will contain all messages (across all {@link QueueName}'s)
         */
        public void setSharedQueueTableName(String sharedQueueTableName) {
            this.sharedQueueTableName = sharedQueueTableName;
        }
    }

    public static class FencedLockManager {
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

    public static class EventStore {
        private IdentifierColumnType identifierColumnType     = IdentifierColumnType.TEXT;
        private JSONColumnType       jsonColumnType           = JSONColumnType.JSONB;
        private boolean              useEventStreamGapHandler = true;

        private final EventStoreSubscriptionManagerProperties subscriptionManager = new EventStoreSubscriptionManagerProperties();

        /**
         * The {@link IdentifierColumnType} used for all Aggregate-Ids
         *
         * @return The {@link IdentifierColumnType} used for all Aggregate-Ids
         */
        public IdentifierColumnType getIdentifierColumnType() {
            return identifierColumnType;
        }

        /**
         * The {@link IdentifierColumnType} used for all Aggregate-Ids
         *
         * @param identifierColumnType The {@link IdentifierColumnType} used for all Aggregate-Ids
         */
        public void setIdentifierColumnType(IdentifierColumnType identifierColumnType) {
            this.identifierColumnType = identifierColumnType;
        }

        /**
         * The {@link JSONColumnType} used for all JSON columns
         *
         * @return The {@link JSONColumnType} used for all JSON columns
         */
        public JSONColumnType getJsonColumnType() {
            return jsonColumnType;
        }

        /**
         * The {@link JSONColumnType} used for all JSON columns
         *
         * @param jsonColumnType The {@link JSONColumnType} used for all JSON columns
         */
        public void setJsonColumnType(JSONColumnType jsonColumnType) {
            this.jsonColumnType = jsonColumnType;
        }

        /**
         * Get the {@link EventStoreSubscriptionManager} properties
         *
         * @return the {@link EventStoreSubscriptionManager} properties
         */
        public EventStoreSubscriptionManagerProperties getSubscriptionManager() {
            return subscriptionManager;
        }

        /**
         * Should the {@link PostgresqlEventStore} use {@link PostgresqlEventStreamGapHandler} or the {@link NoEventStreamGapHandler}?
         *
         * @return Should the {@link PostgresqlEventStore} use {@link PostgresqlEventStreamGapHandler} or the {@link NoEventStreamGapHandler}?
         */
        public boolean isUseEventStreamGapHandler() {
            return useEventStreamGapHandler;
        }

        /**
         * Should the {@link PostgresqlEventStore} use {@link PostgresqlEventStreamGapHandler} or the {@link NoEventStreamGapHandler}?
         *
         * @param useEventStreamGapHandler Should the {@link PostgresqlEventStore} use {@link PostgresqlEventStreamGapHandler} or the {@link NoEventStreamGapHandler}?
         */
        public void setUseEventStreamGapHandler(boolean useEventStreamGapHandler) {
            this.useEventStreamGapHandler = useEventStreamGapHandler;
        }
    }

    /**
     * {@link EventStoreSubscriptionManager} properties
     */
    public static class EventStoreSubscriptionManagerProperties {
        private int      eventStorePollingBatchSize = 10;
        private Duration eventStorePollingInterval  = Duration.ofMillis(100);
        private Duration snapshotResumePointsEvery  = Duration.ofSeconds(10);

        /**
         * How many events should The {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore} maximum return when polling for events
         *
         * @return how many events should The {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore} maximum return when polling for events
         */
        public int getEventStorePollingBatchSize() {
            return eventStorePollingBatchSize;
        }

        /**
         * How many events should The {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore} maximum return when polling for events
         *
         * @param eventStorePollingBatchSize how many events should The {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore} maximum return when polling for events
         */
        public void setEventStorePollingBatchSize(int eventStorePollingBatchSize) {
            this.eventStorePollingBatchSize = eventStorePollingBatchSize;
        }

        /**
         * How often should the {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore} be polled for new events
         *
         * @return how often should the {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore} be polled for new events
         */
        public Duration getEventStorePollingInterval() {
            return eventStorePollingInterval;
        }

        /**
         * How often should the {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore} be polled for new events
         *
         * @param eventStorePollingInterval how often should the {@link dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore} be polled for new events
         */
        public void setEventStorePollingInterval(Duration eventStorePollingInterval) {
            this.eventStorePollingInterval = eventStorePollingInterval;
        }

        /**
         * How often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
         *
         * @return how often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
         */
        public Duration getSnapshotResumePointsEvery() {
            return snapshotResumePointsEvery;
        }

        /**
         * How often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
         *
         * @param snapshotResumePointsEvery How often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
         */
        public void setSnapshotResumePointsEvery(Duration snapshotResumePointsEvery) {
            this.snapshotResumePointsEvery = snapshotResumePointsEvery;
        }
    }
}
