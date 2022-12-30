package dk.cloudcreate.essentials.components.boot.autoconfigure.mongodb;

import dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
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

        /**
         * Get the name of the collection that will contain all messages (across all {@link QueueName}'s)
         *
         * @return the name of the collection that will contain all messages (across all {@link QueueName}'s)
         */
        public String getSharedQueueCollectionName() {
            return sharedQueueCollectionName;
        }

        /**
         * Set the name of the collection that will contain all messages (across all {@link QueueName}'s)
         *
         * @param sharedQueueCollectionName the name of the collection that will contain all messages (across all {@link QueueName}'s)
         */
        public void setSharedQueueCollectionName(String sharedQueueCollectionName) {
            this.sharedQueueCollectionName = sharedQueueCollectionName;
        }

        /**
         * Get the transactional behaviour mode of the {@link MongoDurableQueues}
         * @return the transactional behaviour mode of the {@link MongoDurableQueues
         */
        public TransactionalMode getTransactionalMode() {
            return transactionalMode;
        }

        /**
         * Set the transactional behaviour mode of the {@link MongoDurableQueues
         * @param transactionalMode the transactional behaviour mode of the {@link MongoDurableQueues
         */
        public void setTransactionalMode(TransactionalMode transactionalMode) {
            this.transactionalMode = transactionalMode;
        }

        /**
         * Get the Message Handling timeout - Only relevant for {@link TransactionalMode#ManualAcknowledgement}<br>
         * The Message Handling timeout defines the timeout for messages being delivered, but haven't yet been acknowledged.
         * After this timeout the message delivery will be reset and the message will again be a candidate for delivery
         * @return the Message Handling timeout
         */
        public Duration getMessageHandlingTimeout() {
            return messageHandlingTimeout;
        }

        /**
         * Get the Message Handling timeout - Only relevant for {@link TransactionalMode#ManualAcknowledgement}<br>
         * The Message Handling timeout defines the timeout for messages being delivered, but haven't yet been acknowledged.
         * After this timeout the message delivery will be reset and the message will again be a candidate for delivery
         * @param messageHandlingTimeout the Message Handling timeout
         */
        public void setMessageHandlingTimeout(Duration messageHandlingTimeout) {
            this.messageHandlingTimeout = messageHandlingTimeout;
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
