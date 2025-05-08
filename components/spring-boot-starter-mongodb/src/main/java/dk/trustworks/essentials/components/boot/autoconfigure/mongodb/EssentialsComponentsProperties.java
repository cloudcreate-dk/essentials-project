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

package dk.trustworks.essentials.components.boot.autoconfigure.mongodb;

import dk.trustworks.essentials.components.distributed.fencedlock.springdata.mongo.*;
import dk.trustworks.essentials.components.foundation.IOExceptionUtil;
import dk.trustworks.essentials.components.foundation.fencedlock.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.trustworks.essentials.components.foundation.mongo.MongoUtil;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.components.queue.springdata.mongodb.MongoDurableQueues;
import dk.trustworks.essentials.reactive.*;
import dk.trustworks.essentials.reactive.command.*;
import dk.trustworks.essentials.reactive.spring.ReactiveHandlersBeanPostProcessor;
import dk.trustworks.essentials.shared.measurement.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Sinks;

import java.time.*;

/**
 * Properties for the MongoDB focused Essentials Components auto-configuration<br>
 * <br>
 * To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
 * which exposes the component to the risk of malicious input.<br>
 * <br>
 * <strong>Security Note:</strong><br>
 * It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
 * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link dk.trustworks.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage} component, used by {@link MongoFencedLockManager} will
 * call the {@link dk.trustworks.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
 * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link dk.trustworks.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
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
 * call the {@link dk.trustworks.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
 * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link dk.trustworks.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
 * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
 * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
 * <br>
 * It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
 *
 * @see dk.trustworks.essentials.components.queue.springdata.mongodb.MongoDurableQueues
 * @see dk.trustworks.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockManager
 * @see dk.trustworks.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage
 */
@Configuration
@ConfigurationProperties(prefix = "essentials")
public class EssentialsComponentsProperties {
    private final FencedLockManager                     fencedLockManager                = new FencedLockManager();
    private final DurableQueues                         durableQueues                    = new DurableQueues();
    private final LifeCycleProperties                   lifeCycles                       = new LifeCycleProperties();
    private final MicrometerTaggingProperties           tracingProperties                = new MicrometerTaggingProperties();
    private       boolean                               immutableJacksonModuleEnabled    = true;
    private final ReactiveProperties                    reactive                         = new ReactiveProperties();
    private       boolean                               reactiveBeanPostProcessorEnabled = true;
    private final EssentialsComponentsMetricsProperties metrics                          = new EssentialsComponentsMetricsProperties();

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

    /**
     * Should the {@link ReactiveHandlersBeanPostProcessor} be enabled - default is true<br>
     * Setting this value to <code>false</code> will result in all beans implementing {@link EventHandler} and {@link CommandHandler} will not being auto registered
     * with the defined {@link EventBus} and {@link CommandBus}
     *
     * @return Should the {@link ReactiveHandlersBeanPostProcessor} be enabled
     */
    public boolean isReactiveBeanPostProcessorEnabled() {
        return reactiveBeanPostProcessorEnabled;
    }

    /**
     * Should the ReactiveHandlersBeanPostProcessor be enabled  - default is true<br>
     * Setting this value to <code>false</code> will result in all beans implementing {@link EventHandler} and {@link CommandHandler} will not being auto registered
     * with the defined {@link EventBus} and {@link CommandBus}
     *
     * @param reactiveBeanPostProcessorEnabled Should the {@link ReactiveHandlersBeanPostProcessor} be enabled
     */
    public void setReactiveBeanPostProcessorEnabled(boolean reactiveBeanPostProcessorEnabled) {
        this.reactiveBeanPostProcessorEnabled = reactiveBeanPostProcessorEnabled;
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

    public MicrometerTaggingProperties getTracingProperties() {
        return this.tracingProperties;
    }

    public ReactiveProperties getReactive() {
        return reactive;
    }

    /**
     * Configuration properties for essentials metrics collection and logging.
     * <p>
     * This configuration is used to enable and fine-tune metrics gathering and logging for different components,
     * such as durable queues, command bus, and message handlers. If a given component's <code>enabled</code> property is
     * set to <code>false</code>, then no performance metrics will be collected or logged for that component.
     * <p>
     * <b>YAML example:</b>
     * <pre>{@code
     * essentials:
     *   metrics:
     *     durable-queues:
     *       enabled: true
     *       thresholds:
     *         debug: 25ms
     *         info: 200ms
     *         warn: 500ms
     *         error: 5000ms
     *     command-bus:
     *       enabled: true
     *       thresholds:
     *         debug: 25ms
     *         info: 200ms
     *         warn: 500ms
     *         error: 5000ms
     *     message-handler:
     *       enabled: true
     *       thresholds:
     *         debug: 25ms
     *         info: 200ms
     *         warn: 500ms
     *         error: 5000ms
     * }</pre>
     * <b>Properties example:</b>
     * <pre>{@code
     * essentials.metrics.durable-queues.enabled=true
     * essentials.metrics.durable-queues.thresholds.debug=25ms
     * essentials.metrics.durable-queues.thresholds.info=200ms
     * essentials.metrics.durable-queues.thresholds.warn=500ms
     * essentials.metrics.durable-queues.thresholds.error=5000ms
     *
     * essentials.metrics.command-bus.enabled=true
     * essentials.metrics.command-bus.thresholds.debug=25ms
     * essentials.metrics.command-bus.thresholds.info=200ms
     * essentials.metrics.command-bus.thresholds.warn=500ms
     * essentials.metrics.command-bus.thresholds.error=5000ms
     *
     * essentials.metrics.message-handler.enabled=true
     * essentials.metrics.message-handler.thresholds.debug=25ms
     * essentials.metrics.message-handler.thresholds.info=200ms
     * essentials.metrics.message-handler.thresholds.warn=500ms
     * essentials.metrics.message-handler.thresholds.error=5000ms
     * }</pre>
     * <p>
     * You can further control the log levels by adjusting the minimum log level for the respective loggers:
     * <table border="1">
     *     <tr><th>Metric</th><th>Logger Class</th></tr>
     *     <tr><td>essentials.metrics.durable-queues</td><td>dk.trustworks.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeDurableQueueInterceptor</td></tr>
     *     <tr><td>essentials.metrics.command-bus</td><td>dk.trustworks.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeCommandBusInterceptor</td></tr>
     *     <tr><td>essentials.metrics.message-handler</td><td>dk.trustworks.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeMessageHandlerInterceptor</td></tr>
     * </table>
     *
     * @return essentials metrics properties
     */
    public EssentialsComponentsMetricsProperties getMetrics() {
        return metrics;
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
         * This is used to avoid polling a the {@link dk.trustworks.essentials.components.foundation.messaging.queue.DurableQueues} for a queue that isn't experiencing a lot of messages
         *
         * @return the increase in the {@link ConsumeFromQueue#getPollingInterval()} when the {@link dk.trustworks.essentials.components.foundation.messaging.queue.DurableQueues} polling returns 0 messages
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
         * @return Should {@link FencedLock}'s acquired by this {@link dk.trustworks.essentials.components.foundation.fencedlock.FencedLockManager} be released in case calls to {@link FencedLockStorage#confirmLockInDB(DBFencedLockManager, UnitOfWork, DBFencedLock, OffsetDateTime)} fails
         * with an exception where {@link IOExceptionUtil#isIOException(Throwable)} returns true -
         * If releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation is true, then {@link FencedLock}'s will be released locally,
         * otherwise we will retain the {@link FencedLock}'s as locked.
         */
        public boolean isReleaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation() {
            return releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation;
        }

        /**
         * @param releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation Should {@link FencedLock}'s acquired by this {@link dk.trustworks.essentials.components.foundation.fencedlock.FencedLockManager} be released in case calls to {@link FencedLockStorage#confirmLockInDB(DBFencedLockManager, UnitOfWork, DBFencedLock, OffsetDateTime)} fails
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

    public static class MicrometerTaggingProperties {
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

    /**
     * Configuration properties for metrics collection (using {@link MeasurementTaker}) and logging thresholds.
     * <p>
     * These properties control the logging behavior for performance measurements.
     * When a measured operation’s duration exceeds a given threshold, it is logged at the
     * corresponding log level. Specifically:
     * <ul>
     *   <li><strong>errorThreshold</strong>: If the duration exceeds this threshold, the operation is logged at ERROR level.</li>
     *   <li><strong>warnThreshold</strong>: If the duration exceeds this threshold, the operation is logged at WARN level.</li>
     *   <li><strong>infoThreshold</strong>: If the duration exceeds this threshold, the operation is logged at INFO level.</li>
     *   <li><strong>debugThreshold</strong>: If the duration exceeds this threshold, the operation is logged at DEBUG level.</li>
     * </ul>
     * If none of the thresholds are exceeded and metrics collection and logging is enabled, then the statistics are logged using TRACE level.
     * <p>
     * Note that by default, the metrics collection and logging is disabled.
     * </p>
     */
    public static class MetricsProperties {
        /**
         * Flag to enable metrics collection.<br>
         * If this is set to false, then no performance metrics will be collected using the {@link MeasurementTaker},
         * and hence no metrics will be either.
         * <p>Default: {@code false}</p>
         */
        private boolean enabled = false;

        /**
         * The thresholds for logging measured execution times.
         * These thresholds determine the log level based on the measured duration.
         */
        private MetricsThresholds thresholds = new MetricsThresholds();

        /**
         * Indicates whether metrics collection is enabled.<br>
         * If this is set to false, then no performance metrics will be collected using the {@link MeasurementTaker},
         * and hence no metrics will be either.
         * <p>Default: {@code false}</p>
         *
         * @return {@code true} if metrics collection is enabled; {@code false} otherwise.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether metrics collection is enabled.<br>
         * If this is set to false, then no performance metrics will be collected using the {@link MeasurementTaker},
         * and hence no metrics will be either.
         * <p>Default: {@code false}</p>
         *
         * @param enabled {@code true} to enable metrics collection; {@code false} to disable.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the metrics threshold properties.
         * <p>
         * These thresholds determine the log level for a measured operation:
         * <ul>
         *   <li><strong>debugThreshold</strong>: Minimum duration (as a {@link Duration})
         *       for DEBUG logging (e.g. <code>25ms</code> by default).</li>
         *   <li><strong>infoThreshold</strong>: Minimum duration for INFO logging (e.g. <code>200ms</code> by default).</li>
         *   <li><strong>warnThreshold</strong>: Minimum duration for WARN logging (e.g. <code>500ms</code> by default).</li>
         *   <li><strong>errorThreshold</strong>: Minimum duration for ERROR logging (e.g. <code>5000ms</code> by default).</li>
         * </ul>
         * </p>
         *
         * @return the metrics threshold properties.
         */
        public MetricsThresholds getThresholds() {
            return thresholds;
        }

        /**
         * Converts these metrics properties into {@link LogThresholds}.
         * <p>
         * This method extracts the threshold durations (in milliseconds) from {@link MetricsThresholds}
         * and constructs a {@link LogThresholds} instance that is used to determine the log level for a given measured duration.
         * </p>
         *
         * @return a new {@link LogThresholds} instance based on the current metrics thresholds.
         */
        public LogThresholds toLogThresholds() {
            return new LogThresholds(
                    thresholds.debugThreshold.toMillis(),
                    thresholds.infoThreshold.toMillis(),
                    thresholds.warnThreshold.toMillis(),
                    thresholds.errorThreshold.toMillis());
        }
    }


    /**
     * Holds the duration thresholds that determine at which log level performance metrics are recorded.
     * <p>
     * When a measured operation completes, its execution duration is compared against these thresholds:
     * <ul>
     *   <li>If the duration is greater than or equal to the <strong>errorThreshold</strong>, the operation is logged at ERROR level.</li>
     *   <li>If the duration is greater than or equal to the <strong>warnThreshold</strong> (but less than the error threshold), it is logged at WARN level.</li>
     *   <li>If the duration is greater than or equal to the <strong>infoThreshold</strong> (but less than the warn threshold), it is logged at INFO level.</li>
     *   <li>If the duration is greater than or equal to the <strong>debugThreshold</strong> (but less than the info threshold), it is logged at DEBUG level.</li>
     * </ul>
     * <p>
     * If none of these thresholds are exceeded and metrics collection is enabled, the operation is logged at TRACE level.
     * </p>
     */
    public static class MetricsThresholds {
        /**
         * Threshold for debug-level logging.
         * <p>Default: 25 ms</p>
         */
        private Duration debugThreshold = Duration.ofMillis(25);

        /**
         * Threshold for info-level logging.
         * <p>Default: 200 ms</p>
         */
        private Duration infoThreshold = Duration.ofMillis(200);

        /**
         * Threshold for warn-level logging.
         * <p>Default: 500 ms</p>
         */
        private Duration warnThreshold = Duration.ofMillis(500);

        /**
         * Threshold for error-level logging.
         * <p>Default: 5000 ms</p>
         */
        private Duration errorThreshold = Duration.ofMillis(5000);

        /**
         * Returns the debug-level logging threshold.
         *
         * @return the debug threshold as a {@link Duration}; default is 25 ms.
         */
        public Duration getDebugThreshold() {
            return debugThreshold;
        }

        /**
         * Sets the debug-level logging threshold.
         *
         * @param debugThreshold the debug threshold as a {@link Duration}.
         */
        public void setDebugThreshold(Duration debugThreshold) {
            this.debugThreshold = debugThreshold;
        }

        /**
         * Returns the info-level logging threshold.
         *
         * @return the info threshold as a {@link Duration}; default is 200 ms.
         */
        public Duration getInfoThreshold() {
            return infoThreshold;
        }

        /**
         * Sets the info-level logging threshold.
         *
         * @param infoThreshold the info threshold as a {@link Duration}.
         */
        public void setInfoThreshold(Duration infoThreshold) {
            this.infoThreshold = infoThreshold;
        }

        /**
         * Returns the warn-level logging threshold.
         *
         * @return the warn threshold as a {@link Duration}; default is 500 ms.
         */
        public Duration getWarnThreshold() {
            return warnThreshold;
        }

        /**
         * Sets the warn-level logging threshold.
         *
         * @param warnThreshold the warn threshold as a {@link Duration}.
         */
        public void setWarnThreshold(Duration warnThreshold) {
            this.warnThreshold = warnThreshold;
        }

        /**
         * Returns the error-level logging threshold.
         *
         * @return the error threshold as a {@link Duration}; default is 5000 ms.
         */
        public Duration getErrorThreshold() {
            return errorThreshold;
        }

        /**
         * Sets the error-level logging threshold.
         *
         * @param errorThreshold the error threshold as a {@link Duration}.
         */
        public void setErrorThreshold(Duration errorThreshold) {
            this.errorThreshold = errorThreshold;
        }
    }


    /**
     * Configuration properties for essentials metrics collection and logging.
     * <p>
     * This configuration is used to enable and fine-tune metrics gathering and logging for different components,
     * such as durable queues, command bus, and message handlers. If a given component's <code>enabled</code> property is
     * set to <code>false</code>, then no performance metrics will be collected or logged for that component.
     * <p>
     * <b>YAML example:</b>
     * <pre>{@code
     * essentials:
     *   metrics:
     *     durable-queues:
     *       enabled: true
     *       thresholds:
     *         debug: 25ms
     *         info: 200ms
     *         warn: 500ms
     *         error: 5000ms
     *     command-bus:
     *       enabled: true
     *       thresholds:
     *         debug: 25ms
     *         info: 200ms
     *         warn: 500ms
     *         error: 5000ms
     *     message-handler:
     *       enabled: true
     *       thresholds:
     *         debug: 25ms
     *         info: 200ms
     *         warn: 500ms
     *         error: 5000ms
     * }</pre>
     * <b>Properties example:</b>
     * <pre>{@code
     * essentials.metrics.durable-queues.enabled=true
     * essentials.metrics.durable-queues.thresholds.debug=25ms
     * essentials.metrics.durable-queues.thresholds.info=200ms
     * essentials.metrics.durable-queues.thresholds.warn=500ms
     * essentials.metrics.durable-queues.thresholds.error=5000ms
     *
     * essentials.metrics.command-bus.enabled=true
     * essentials.metrics.command-bus.thresholds.debug=25ms
     * essentials.metrics.command-bus.thresholds.info=200ms
     * essentials.metrics.command-bus.thresholds.warn=500ms
     * essentials.metrics.command-bus.thresholds.error=5000ms
     *
     * essentials.metrics.message-handler.enabled=true
     * essentials.metrics.message-handler.thresholds.debug=25ms
     * essentials.metrics.message-handler.thresholds.info=200ms
     * essentials.metrics.message-handler.thresholds.warn=500ms
     * essentials.metrics.message-handler.thresholds.error=5000ms
     * }</pre>
     * <p>
     * You can further control the log levels by adjusting the minimum log level for the respective loggers:
     * <table border="1">
     *     <tr><th>Metric</th><th>Logger Class</th></tr>
     *     <tr><td>essentials.metrics.durable-queues</td><td>dk.trustworks.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeDurableQueueInterceptor</td></tr>
     *     <tr><td>essentials.metrics.command-bus</td><td>dk.trustworks.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeCommandBusInterceptor</td></tr>
     *     <tr><td>essentials.metrics.message-handler</td><td>dk.trustworks.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeMessageHandlerInterceptor</td></tr>
     * </table>
     */
    public static class EssentialsComponentsMetricsProperties {
        private MetricsProperties commandBus     = new MetricsProperties();
        private MetricsProperties durableQueues  = new MetricsProperties();
        private MetricsProperties messageHandler = new MetricsProperties();

        /**
         * Returns the metrics properties for the command bus.
         *
         * @return the command bus metrics properties.
         */
        public MetricsProperties getCommandBus() {
            return commandBus;
        }

        /**
         * Returns the metrics properties for durable queues.
         *
         * @return the durable queues metrics properties.
         */
        public MetricsProperties getDurableQueues() {
            return durableQueues;
        }

        /**
         * Returns the metrics properties for message handlers.
         *
         * @return the message handler metrics properties.
         */
        public MetricsProperties getMessageHandler() {
            return messageHandler;
        }
    }

    public static class ReactiveProperties {
        private int    eventBusBackpressureBufferSize             = LocalEventBus.DEFAULT_BACKPRESSURE_BUFFER_SIZE;
        private int    eventBusParallelThreads                    = Runtime.getRuntime().availableProcessors();
        private int    overflowMaxRetries                         = LocalEventBus.DEFAULT_OVERFLOW_MAX_RETRIES;
        private double queuedTaskCapFactor                        = LocalEventBus.QUEUED_TASK_CAP_FACTOR;
        private int    commandBusParallelSendAndDontWaitConsumers = Runtime.getRuntime().availableProcessors();

        /**
         * The number of parallel {@link CommandBus#sendAndDontWait(Object)} consumers. Default is the number of available processors
         *
         * @return the number of parallel {@link CommandBus#sendAndDontWait(Object)} consumers. Default is the number of available processors
         */
        public int getCommandBusParallelSendAndDontWaitConsumers() {
            return commandBusParallelSendAndDontWaitConsumers;
        }

        /**
         * The number of parallel {@link CommandBus#sendAndDontWait(Object)} consumers. Default is the number of available processors
         *
         * @param commandBusParallelSendAndDontWaitConsumers the number of parallel {@link CommandBus#sendAndDontWait(Object)} consumers. Default is the number of available processors
         */
        public void setCommandBusParallelSendAndDontWaitConsumers(int commandBusParallelSendAndDontWaitConsumers) {
            this.commandBusParallelSendAndDontWaitConsumers = commandBusParallelSendAndDontWaitConsumers;
        }

        /**
         * Get property to set as 'eventBusBackpressureBufferSize' value LocalEventBus back pressure size for {@link Sinks.Many}'s onBackpressureBuffer size. The default value set by reactor framework is {@value LocalEventBus#DEFAULT_BACKPRESSURE_BUFFER_SIZE}.
         *
         * @return property 'eventBusBackpressureBufferSize' used for value LocalEventBus Sinks.Many onBackpressureBuffer size.
         */
        public int getEventBusBackpressureBufferSize() {
            return eventBusBackpressureBufferSize;
        }

        /**
         * Set property 'eventBusBackpressureBufferSize'  value LocalEventBus back pressure size for {@link Sinks.Many}'s onBackpressureBuffer size. The default value set by reactor framework is {@value LocalEventBus#DEFAULT_BACKPRESSURE_BUFFER_SIZE}.
         *
         * @param eventBusBackpressureBufferSize property value 'eventBusBackpressureBufferSize' LocalEventBus Sinks.Many onBackpressureBuffer size.
         */
        public void setEventBusBackpressureBufferSize(int eventBusBackpressureBufferSize) {
            this.eventBusBackpressureBufferSize = eventBusBackpressureBufferSize;
        }

        /**
         * Get the number of parallel threads processing asynchronous events. Defaults to the number of available processors on the machine
         *
         * @return the number of parallel threads processing asynchronous events.
         */
        public int getEventBusParallelThreads() {
            return eventBusParallelThreads;
        }

        /**
         * Set the number of parallel threads processing asynchronous events. Defaults to the number of available processors on the machine
         *
         * @param eventBusParallelThreads the number of parallel threads processing asynchronous events.
         */
        public void setEventBusParallelThreads(int eventBusParallelThreads) {
            this.eventBusParallelThreads = eventBusParallelThreads;
        }

        /**
         * Set the maximum number of retries for events that overflow the Flux. Default is {@value LocalEventBus#DEFAULT_OVERFLOW_MAX_RETRIES}.
         *
         * @return the maximum number of retries for events that overflow the Flux
         */
        public int getOverflowMaxRetries() {
            return overflowMaxRetries;
        }

        /**
         * Get the maximum number of retries for events that overflow the Flux. Default is {@value LocalEventBus#DEFAULT_OVERFLOW_MAX_RETRIES}.
         *
         * @param overflowMaxRetries the maximum number of retries for events that overflow the Flux
         */
        public void setOverflowMaxRetries(int overflowMaxRetries) {
            this.overflowMaxRetries = overflowMaxRetries;
        }


        /**
         * Get the factor to calculate queued task capacity from the backpressureBufferSize. Default value is {@value LocalEventBus#QUEUED_TASK_CAP_FACTOR}.
         *
         * @return the factor to calculate queued task capacity from the backpressureBufferSize.
         */
        public double getQueuedTaskCapFactor() {
            return queuedTaskCapFactor;
        }

        /**
         * Set the factor to calculate queued task capacity from the backpressureBufferSize. Default value is {@value LocalEventBus#QUEUED_TASK_CAP_FACTOR}.
         *
         * @param queuedTaskCapFactor the factor to calculate queued task capacity from the backpressureBufferSize.
         */
        public void setQueuedTaskCapFactor(double queuedTaskCapFactor) {
            this.queuedTaskCapFactor = queuedTaskCapFactor;
        }
    }

}
