/*
 * Copyright 2021-2024 the original author or authors.
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

import dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.*;
import dk.cloudcreate.essentials.components.foundation.IOExceptionUtil;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.queue.springdata.mongodb.MongoDurableQueues;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.*;

/**
 * Properties for the MongoDB focused Essentials Components auto-configuration<br>
 * <br>
 * To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
 * which exposes the component to the risk of malicious input.<br>
 * <br>
 * <strong>Security Note:</strong><br>
 * It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
 * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage} component, used by {@link MongoFencedLockManager} will
 * call the {@link dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
 * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
 * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
 * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
 * <br>
 * It is highly recommended that the {@code fencedLocksCollectionName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code fencedLocksCollectionName} value.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
 * <br>
 * <u>{@link MongoDurableQueues}</u><br>
 * To support customization of storage collection name, the {@link MongoDurableQueues#getSharedQueueCollectionName()} will be directly used as Collection name,
 * which exposes the component to the risk of malicious input.<br>
 * <br>
 * <strong>Security Note:</strong><br>
 * It is the responsibility of the user of this component to sanitize the {@code sharedQueueCollectionName}
 * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoDurableQueues}, will
 * call the {@link dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
 * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
 * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
 * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
 * <br>
 * It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
 *
 * @see dk.cloudcreate.essentials.components.queue.springdata.mongodb.MongoDurableQueues
 * @see dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockManager
 * @see dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage
 */
@Configuration
@ConfigurationProperties(prefix = "essentials")
public class EssentialsComponentsProperties {
    private final FencedLockManager   fencedLockManager             = new FencedLockManager();
    private final DurableQueues       durableQueues                 = new DurableQueues();
    private final LifeCycleProperties lifeCycles                    = new LifeCycleProperties();
    private final TracingProperties   tracingProperties             = new TracingProperties();
    private       boolean             immutableJacksonModuleEnabled = true;

    /**
     * Should the EssentialsImmutableJacksonModule be included in the ObjectMapper configuration - default is true<br>
     * Setting this value to false will not include the EssentialsImmutableJacksonModule, in the ObjectMapper configuration, even if Objenesis is on the classpath
     *
     * @return Should the EssentialsImmutableJacksonModule be included in the ObjectMapper configuration
     */
    public boolean isImmutableJacksonModuleEnabled() {
        return immutableJacksonModuleEnabled;
    }

    /**
     * Should the EssentialsImmutableJacksonModule be included in the ObjectMapper configuration - default is true<br>
     * Setting this value to false will not include the EssentialsImmutableJacksonModule, in the ObjectMapper configuration, even if Objenesis is on the classpath
     *
     * @param immutableJacksonModuleEnabled Should the EssentialsImmutableJacksonModule be included in the ObjectMapper configuration
     */
    public void setImmutableJacksonModuleEnabled(boolean immutableJacksonModuleEnabled) {
        this.immutableJacksonModuleEnabled = immutableJacksonModuleEnabled;
    }

    public FencedLockManager getFencedLockManager() {
        return fencedLockManager;
    }

    public DurableQueues getDurableQueues() {
        return durableQueues;
    }

    public LifeCycleProperties getLifeCycles() {
        return this.lifeCycles;
    }

    public TracingProperties getTracingProperties() {
        return this.tracingProperties;
    }

    public static class DurableQueues {
        private String sharedQueueCollectionName = MongoDurableQueues.DEFAULT_DURABLE_QUEUES_COLLECTION_NAME;

        private TransactionalMode transactionalMode = TransactionalMode.SingleOperationTransaction;

        private Duration messageHandlingTimeout = Duration.ofSeconds(30);

        private Double pollingDelayIntervalIncrementFactor = 0.5d;

        private Duration maxPollingInterval = Duration.ofMillis(2000);

        private boolean verboseTracing = false;

        /**
         * Should the Tracing produces only include all operations or only top level operations (default false)
         *
         * @return Should the Tracing produces only include all operations or only top level operations
         */
        public boolean isVerboseTracing() {
            return verboseTracing;
        }

        /**
         * Should the Tracing produces only include all operations or only top level operations (default false)
         *
         * @param verboseTracing Should the Tracing produces only include all operations or only top level operations
         */
        public void setVerboseTracing(boolean verboseTracing) {
            this.verboseTracing = verboseTracing;
        }

        /**
         * Get the name of the collection that will contain all messages (across all {@link QueueName}'s)<br>
         * Default is {@value MongoDurableQueues#DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}<br>
         * <strong>Note:</strong><br>
         * To support customization of storage collection name, the {@code sharedQueueCollectionName} will be directly used as Collection name,
         * which exposes the component to the risk of malicious input.<br>
         * <br>
         * <strong>Security Note:</strong><br>
         * It is the responsibility of the user of this component to sanitize the {@code sharedQueueCollectionName}
         * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoDurableQueues}, will
         * call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
         * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
         * However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
         * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
         * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
         * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
         * <br>
         * It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
         * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
         * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
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
         * @param sharedQueueCollectionName the name of the collection that will contain all messages (across all {@link QueueName}'s)<br>
         *                                  <strong>Note:</strong><br>
         *                                  To support customization of storage collection name, the {@code sharedQueueCollectionName} will be directly used as Collection name,
         *                                  which exposes the component to the risk of malicious input.<br>
         *                                  <br>
         *                                  <strong>Security Note:</strong><br>
         *                                  It is the responsibility of the user of this component to sanitize the {@code sharedQueueCollectionName}
         *                                  to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoDurableQueues}, will
         *                                  call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
         *                                  The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
         *                                  However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
         *                                  <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
         *                                  Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
         *                                  Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
         *                                  <br>
         *                                  It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
         *                                  To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
         *                                  <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
         */
        public void setSharedQueueCollectionName(String sharedQueueCollectionName) {
            this.sharedQueueCollectionName = sharedQueueCollectionName;
        }

        /**
         * Get the transactional behaviour mode of the {@link MongoDurableQueues}<br>
         * Default: {@link TransactionalMode#SingleOperationTransaction}
         *
         * @return the transactional behaviour mode of the {@link MongoDurableQueues}
         */
        public TransactionalMode getTransactionalMode() {
            return transactionalMode;
        }

        /**
         * Set the transactional behaviour mode of the {@link MongoDurableQueues}
         * Default: {@link TransactionalMode#SingleOperationTransaction}
         *
         * @param transactionalMode the transactional behaviour mode of the {@link MongoDurableQueues}
         */
        public void setTransactionalMode(TransactionalMode transactionalMode) {
            this.transactionalMode = transactionalMode;
        }

        /**
         * Get the Message Handling timeout - Only relevant for {@link TransactionalMode#SingleOperationTransaction}<br>
         * The Message Handling timeout defines the timeout for messages being delivered, but haven't yet been acknowledged.
         * After this timeout the message delivery will be reset and the message will again be a candidate for delivery<br>
         * Default is 30 seconds
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
         * Default is 30 seconds
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
        private Duration lockTimeOut                                                    = Duration.ofSeconds(15);
        private Duration lockConfirmationInterval                                       = Duration.ofSeconds(4);
        private String   fencedLocksCollectionName                                      = MongoFencedLockStorage.DEFAULT_FENCED_LOCKS_COLLECTION_NAME;
        private boolean  releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation = false;

        /**
         * @return Should {@link FencedLock}'s acquired by this {@link dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLockManager} be released in case calls to {@link FencedLockStorage#confirmLockInDB(DBFencedLockManager, UnitOfWork, DBFencedLock, OffsetDateTime)} fails
         * with an exception where {@link IOExceptionUtil#isIOException(Throwable)} returns true -
         * If releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation is true, then {@link FencedLock}'s will be released locally,
         * otherwise we will retain the {@link FencedLock}'s as locked.
         */
        public boolean isReleaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation() {
            return releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation;
        }

        /**
         * @param releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation Should {@link FencedLock}'s acquired by this {@link dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLockManager} be released in case calls to {@link FencedLockStorage#confirmLockInDB(DBFencedLockManager, UnitOfWork, DBFencedLock, OffsetDateTime)} fails
         *                                                                       with an exception where {@link IOExceptionUtil#isIOException(Throwable)} returns true -
         *                                                                       If releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation is true, then {@link FencedLock}'s will be released locally,
         *                                                                       otherwise we will retain the {@link FencedLock}'s as locked.
         */
        public void setReleaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation(boolean releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation) {
            this.releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation = releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation;
        }

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
         * The name of the collection where the locks will be stored.
         * <strong>Note:</strong><br>
         * To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
         * which exposes the component to the risk of malicious input.<br>
         * <br>
         * <strong>Security Note:</strong><br>
         * It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
         * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoFencedLockStorage} component, used by the {@link MongoFencedLockManager}, will
         * call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
         * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
         * However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
         * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
         * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
         * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
         * <br>
         * It is highly recommended that the {@code fencedLocksCollectionName} value is only derived from a controlled and trusted source.<br>
         * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code fencedLocksCollectionName} value.<br>
         * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>         *
         *
         * @return the name of the collection where the locks will be stored.
         */
        public String getFencedLocksCollectionName() {
            return fencedLocksCollectionName;
        }

        /**
         * The name of the collection where the locks will be stored.
         *
         * @param fencedLocksCollectionName the name of the collection where the locks will be stored.<br>
         *                                  <strong>Note:</strong><br>
         *                                  To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
         *                                  which exposes the component to the risk of malicious input.<br>
         *                                  <br>
         *                                  <strong>Security Note:</strong><br>
         *                                  It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
         *                                  to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoFencedLockStorage} component, used by the {@link MongoFencedLockManager}, will
         *                                  call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
         *                                  The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
         *                                  However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
         *                                  <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
         *                                  Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
         *                                  Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
         *                                  <br>
         *                                  It is highly recommended that the {@code fencedLocksCollectionName} value is only derived from a controlled and trusted source.<br>
         *                                  To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code fencedLocksCollectionName} value.<br>
         *                                  <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
         */
        public void setFencedLocksCollectionName(String fencedLocksCollectionName) {
            this.fencedLocksCollectionName = fencedLocksCollectionName;
        }
    }

    public static class LifeCycleProperties {
        private boolean startLifeCycles = true;

        /**
         * Get property that determines if lifecycle beans should be started automatically
         *
         * @return the property that determined if lifecycle beans should be started automatically
         */
        public boolean isStartLifeCycles() {
            return startLifeCycles;
        }

        /**
         * Set property that determines if lifecycle beans should be started automatically
         *
         * @param startLifeCycles the property that determines if lifecycle beans should be started automatically
         */
        public void setStartLifeCycles(boolean startLifeCycles) {
            this.startLifeCycles = startLifeCycles;
        }
    }

    public static class TracingProperties {
        private String moduleTag;

        /**
         * Get property to set as 'module' tag value for all micrometer metrics. This to differentiate metrics across different modules.
         *
         * @return property to set as 'module' tag value for all micrometer metrics
         */
        public String getModuleTag() {
            return moduleTag;
        }

        /**
         * Set property to use as 'module' tag value for all micrometer metrics. This to differentiate metrics across different modules.
         *
         * @param moduleTag property to set as 'module' tag value for all micrometer metrics
         */
        public void setModuleTag(String moduleTag) {
            this.moduleTag = moduleTag;
        }
    }

}
