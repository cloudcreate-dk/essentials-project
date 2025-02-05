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

package dk.cloudcreate.essentials.components.queue.springdata.mongodb;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.MongoInterruptedException;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import dk.cloudcreate.essentials.components.foundation.IOExceptionUtil;
import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.Message;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueuePollingOptimizer.SimpleQueuePollingOptimizer;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.shared.Exceptions;
import dk.cloudcreate.essentials.shared.functional.TripleFunction;
import org.slf4j.*;
import org.springframework.data.annotation.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.messaging.*;
import org.springframework.data.mongodb.core.query.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static dk.cloudcreate.essentials.shared.interceptor.DefaultInterceptorChain.sortInterceptorsByOrder;
import static dk.cloudcreate.essentials.shared.interceptor.InterceptorChain.newInterceptorChainForOperation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Spring-data MongoDB version of the {@link DurableQueues} concept.<br>
 * Supports both {@link TransactionalMode#FullyTransactional} in collaboration with a {@link UnitOfWorkFactory} in order to support queuing message together with business logic (such as failing to handle an Event, etc.)
 * as well as {@link TransactionalMode#SingleOperationTransaction}
 * <br>
 * <u><b>Security:</b></u><br>
 * To support customization of storage collection name, the {@link #getSharedQueueCollectionName()} will be directly used as Collection name,
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
 */
public final class MongoDurableQueues implements DurableQueues {

    protected static final Logger                                            log                                    = LoggerFactory.getLogger(MongoDurableQueues.class);
    public static final    String                                            DEFAULT_DURABLE_QUEUES_COLLECTION_NAME = "durable_queues";
    private final          Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory;

    protected       SpringMongoTransactionAwareUnitOfWorkFactory        unitOfWorkFactory;
    protected final MongoTemplate                                       mongoTemplate;
    private final   TransactionalMode                                   transactionalMode;
    private final   JSONSerializer                                      jsonSerializer;
    protected final String                                              sharedQueueCollectionName;
    private final   ConcurrentMap<QueueName, MongoDurableQueueConsumer> durableQueueConsumers = new ConcurrentHashMap<>();
    private final   ConcurrentMap<QueueName, ReentrantLock>             localQueuePollLock    = new ConcurrentHashMap<>();
    private final   List<DurableQueuesInterceptor>                      interceptors          = new CopyOnWriteArrayList<>();
    private final   MessageListenerContainer                            messageListenerContainer;


    private volatile boolean                           started;
    private          int                               messageHandlingTimeoutMs;
    /**
     * Contains the timestamp of the last performed {@link #resetMessagesStuckBeingDelivered(QueueName)} check
     */
    protected        ConcurrentMap<QueueName, Instant> lastResetStuckMessagesCheckTimestamps = new ConcurrentHashMap<>();
    private          Subscription                      changeSubscription;

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#SingleOperationTransaction} with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}, default {@link ObjectMapper}
     * configuration
     *
     * @param mongoTemplate          the {@link MongoTemplate} used
     * @param messageHandlingTimeout Defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                               After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              Duration messageHandlingTimeout) {
        this(mongoTemplate,
             messageHandlingTimeout,
             null);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#SingleOperationTransaction} with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME},
     * the default {@link JacksonJSONSerializer} using {@link #createDefaultObjectMapper()}
     * configuration
     *
     * @param mongoTemplate                the {@link MongoTemplate} used
     * @param messageHandlingTimeout       Defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                                     After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              Duration messageHandlingTimeout,
                              Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(TransactionalMode.SingleOperationTransaction,
             mongoTemplate,
             null,
             messageHandlingTimeout,
             new JacksonJSONSerializer(createDefaultObjectMapper()),
             DEFAULT_DURABLE_QUEUES_COLLECTION_NAME,
             queuePollingOptimizerFactory);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#FullyTransactional} with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME} and
     * the default {@link JacksonJSONSerializer} using {@link #createDefaultObjectMapper()}
     * configuration
     *
     * @param mongoTemplate     the {@link MongoTemplate} used
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} needed to access the database
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory) {
        this(mongoTemplate,
             unitOfWorkFactory,
             null);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#FullyTransactional} with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME} and
     * the default {@link JacksonJSONSerializer} using {@link #createDefaultObjectMapper()}
     * configuration
     *
     * @param mongoTemplate                the {@link MongoTemplate} used
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                              Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(TransactionalMode.FullyTransactional,
             mongoTemplate,
             unitOfWorkFactory,
             null,
             new JacksonJSONSerializer(createDefaultObjectMapper()),
             DEFAULT_DURABLE_QUEUES_COLLECTION_NAME,
             queuePollingOptimizerFactory);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#FullyTransactional} with custom jsonSerializer and sharedQueueCollectionName
     *
     * @param mongoTemplate                the {@link MongoTemplate} used
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param sharedQueueCollectionName    the name of the collection that will contain all messages (across all {@link QueueName}'s)<br>
     *                                     <strong>Note:</strong><br>
     *                                     To support customization of storage collection name, the {@code sharedQueueCollectionName} will be directly used as Collection name,
     *                                     which exposes the component to the risk of malicious input.<br>
     *                                     <br>
     *                                     <strong>Security Note:</strong><br>
     *                                     It is the responsibility of the user of this component to sanitize the {@code sharedQueueCollectionName}
     *                                     to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoDurableQueues}, will
     *                                     call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
     *                                     The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                     However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
     *                                     <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     *                                     Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
     *                                     Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
     *                                     <br>
     *                                     It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
     *                                     To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
     *                                     <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                              JSONSerializer jsonSerializer,
                              String sharedQueueCollectionName,
                              Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(TransactionalMode.FullyTransactional,
             mongoTemplate,
             unitOfWorkFactory,
             null,
             jsonSerializer,
             sharedQueueCollectionName,
             queuePollingOptimizerFactory);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#FullyTransactional} with custom jsonSerializer and with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}
     *
     * @param mongoTemplate                the {@link MongoTemplate} used
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                              JSONSerializer jsonSerializer,
                              Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(TransactionalMode.FullyTransactional,
             mongoTemplate,
             unitOfWorkFactory,
             null,
             jsonSerializer,
             DEFAULT_DURABLE_QUEUES_COLLECTION_NAME,
             queuePollingOptimizerFactory);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#SingleOperationTransaction} with custom jsonSerializer and sharedQueueCollectionName
     *
     * @param mongoTemplate                the {@link MongoTemplate} used
     * @param messageHandlingTimeout       Defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                                     After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param sharedQueueCollectionName    the name of the collection that will contain all messages (across all {@link QueueName}'s)<br>
     *                                     <strong>Note:</strong><br>
     *                                     To support customization of storage collection name, the {@code sharedQueueCollectionName} will be directly used as Collection name,
     *                                     which exposes the component to the risk of malicious input.<br>
     *                                     <br>
     *                                     <strong>Security Note:</strong><br>
     *                                     It is the responsibility of the user of this component to sanitize the {@code sharedQueueCollectionName}
     *                                     to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoDurableQueues}, will
     *                                     call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
     *                                     The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                     However, {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
     *                                     <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     *                                     Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
     *                                     Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
     *                                     <br>
     *                                     It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
     *                                     To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
     *                                     <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              Duration messageHandlingTimeout,
                              JSONSerializer jsonSerializer,
                              String sharedQueueCollectionName,
                              Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(TransactionalMode.SingleOperationTransaction,
             mongoTemplate,
             null,
             messageHandlingTimeout,
             jsonSerializer,
             sharedQueueCollectionName,
             queuePollingOptimizerFactory);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#SingleOperationTransaction} with custom jsonSerializer and with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}
     *
     * @param mongoTemplate                the {@link MongoTemplate} used
     * @param messageHandlingTimeout       Defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                                     After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              Duration messageHandlingTimeout,
                              JSONSerializer jsonSerializer,
                              Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(TransactionalMode.SingleOperationTransaction,
             mongoTemplate,
             null,
             messageHandlingTimeout,
             jsonSerializer,
             DEFAULT_DURABLE_QUEUES_COLLECTION_NAME,
             queuePollingOptimizerFactory);
    }

    /**
     * Create {@link DurableQueues} custom jsonSerializer and sharedQueueCollectionName
     *
     * @param transactionalMode            The transactional behaviour mode of this {@link MongoDurableQueues}
     * @param mongoTemplate                the {@link MongoTemplate} used
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param messageHandlingTimeout       Only relevant when using {@link TransactionalMode#SingleOperationTransaction} and defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                                     After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param sharedQueueCollectionName    the name of the collection that will contain all messages (across all {@link QueueName}'s)<br>
     *                                     <strong>Note:</strong><br>
     *                                     To support customization of storage collection name, the {@code sharedQueueCollectionName} will be directly used as Collection name,
     *                                     which exposes the component to the risk of malicious input.<br>
     *                                     <br>
     *                                     <strong>Security Note:</strong><br>
     *                                     It is the responsibility of the user of this component to sanitize the {@code sharedQueueCollectionName}
     *                                     to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoDurableQueues}, will
     *                                     call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
     *                                     The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                     However, {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
     *                                     <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     *                                     Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
     *                                     Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
     *                                     <br>
     *                                     It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
     *                                     To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
     *                                     <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    protected MongoDurableQueues(TransactionalMode transactionalMode,
                                 MongoTemplate mongoTemplate,
                                 SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                                 Duration messageHandlingTimeout,
                                 JSONSerializer jsonSerializer,
                                 String sharedQueueCollectionName,
                                 Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this.transactionalMode = requireNonNull(transactionalMode, "No transactionalMode instance provided");
        this.mongoTemplate = requireNonNull(mongoTemplate, "No mongoTemplate instance provided");
        log.info("Using transactionalMode: {}", transactionalMode);
        switch (transactionalMode) {
            case FullyTransactional:
                this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory instance provided");
                break;
            case SingleOperationTransaction:
                this.messageHandlingTimeoutMs = (int) requireNonNull(messageHandlingTimeout, "No messageHandlingTimeout instance provided").toMillis();
                log.info("Using messageHandlingTimeout: {} seconds", messageHandlingTimeout);
                break;
        }
        this.jsonSerializer = requireNonNull(jsonSerializer, "No messagePayloadObjectMapper");
        this.sharedQueueCollectionName = requireNonNull(sharedQueueCollectionName, "No sharedQueueCollectionName provided").toLowerCase(Locale.ROOT);
        this.queuePollingOptimizerFactory = queuePollingOptimizerFactory != null ? queuePollingOptimizerFactory : this::createQueuePollingOptimizerFor;
        MongoUtil.checkIsValidCollectionName(this.sharedQueueCollectionName);
        initializeQueueCollection();
        this.messageListenerContainer = new DefaultMessageListenerContainer(mongoTemplate);
    }

    /**
     * Context: support customization of storage collection name, the {@link #sharedQueueCollectionName} will be directly used as Collection name,
     * which exposes the component to the risk of malicious input.<br>
     * <br>
     * <strong>Security Note:</strong><br>
     * It is the responsibility of the user of this component to sanitize the {@code sharedQueueCollectionName}
     * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoDurableQueues}, will
     * call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
     * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
     * However, {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
     * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
     * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
     * <br>
     * It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
     * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
     * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
     */
    private void initializeQueueCollection() {
        MongoUtil.checkIsValidCollectionName(this.sharedQueueCollectionName);
        if (!mongoTemplate.collectionExists(this.sharedQueueCollectionName)) {
            try {
                mongoTemplate.createCollection(this.sharedQueueCollectionName);
            } catch (Exception e) {
                if (!mongoTemplate.collectionExists(this.sharedQueueCollectionName)) {
                    throw new RuntimeException(msg("Failed to create Queue collection '{}'", this.sharedQueueCollectionName), e);
                }
            }
        }

        // Ensure indexes
        var indexes = Map.of("next_msg", new Index()
                                     .named("next_msg")
                                     .on("queueName", Sort.Direction.ASC)
                                     .on("nextDeliveryTimestamp", Sort.Direction.ASC)
                                     .on("isDeadLetterMessage", Sort.Direction.ASC)
                                     .on("isBeingDelivered", Sort.Direction.ASC)
                                     .on("key", Sort.Direction.ASC)
                                     .on("keyOrder", Sort.Direction.ASC),
                             "ordered_msg", new Index()
                                     .named("ordered_msg")
                                     .on("queueName", Sort.Direction.ASC)
                                     .on("key", Sort.Direction.ASC)
                                     .on("keyOrder", Sort.Direction.ASC),
                             "stuck_msgs", new Index()
                                     .named("stuck_msgs")
                                     .on("queueName", Sort.Direction.ASC)
                                     .on("deliveryTimestamp", Sort.Direction.ASC)
                                     .on("isBeingDelivered", Sort.Direction.ASC),
                             "find_msg", new Index()
                                     .named("find_msg")
                                     .on("id", Sort.Direction.ASC)
                                     .on("isBeingDelivered", Sort.Direction.ASC),
                             "resurrect_msg", new Index()
                                     .named("resurrect_msg")
                                     .on("id", Sort.Direction.ASC)
                                     .on("isDeadLetterMessage", Sort.Direction.ASC));
        var indexOperations = mongoTemplate.indexOps(this.sharedQueueCollectionName);
        var allIndexes      = indexOperations.getIndexInfo();
        indexes.forEach((indexName, index) -> {
            log.debug("Ensuring Index '{}' on Collection '{}': {}",
                      indexName,
                      this.sharedQueueCollectionName,
                      index);
            try {
                allIndexes.stream().filter(indexInfo -> indexInfo.getName().equals(indexName)).findFirst()
                          .ifPresent(indexInfo -> {
                              log.trace("[{}] Index '{}' - Existing index: {}\nNew index: {}",
                                        this.sharedQueueCollectionName,
                                        indexName,
                                        indexInfo,
                                        index);
                              if (!indexInfo.isIndexForFields(index.getIndexKeys().keySet())) {
                                  log.debug("[{}] Deleting outdated index '{}'",
                                            this.sharedQueueCollectionName,
                                            indexInfo.getName());
                                  indexOperations.dropIndex(indexInfo.getName());
                              }
                          });
                indexOperations
                        .ensureIndex(index);
            } catch (Exception e) {
                throw new IllegalStateException(msg("Failed to create index '{}'", indexName), e);
            }
        });
    }

    public final String getSharedQueueCollectionName() {
        return sharedQueueCollectionName;
    }

    @Override
    public final void start() {
        if (!started) {
            MongoUtil.checkIsValidCollectionName(this.sharedQueueCollectionName);
            started = true;
            log.info("Starting");
            interceptors.forEach(durableQueuesInterceptor -> durableQueuesInterceptor.setDurableQueues(this));
            sortInterceptorsByOrder(interceptors);
            durableQueueConsumers.values().forEach(MongoDurableQueueConsumer::start);
            startCollectionListener();
            log.info("Started");
        }
    }

    @Override
    public final void stop() {
        if (started) {
            log.info("Stopping");
            for (MongoDurableQueueConsumer mongoDurableQueueConsumer : durableQueueConsumers.values()) {
                try {
                    mongoDurableQueueConsumer.stop();
                } catch (Exception ex) {
                    if (IOExceptionUtil.isIOException(ex)) {
                        log.debug("Error occurred while stopping DurableQueueConsumer", ex);
                    } else {
                        log.error("Error occurred while stopping DurableQueueConsumer", ex);
                    }
                }
            }
            stopCollectionListener();
            started = false;
            log.info("Stopped");
        }
    }


    @Override
    public final boolean isStarted() {
        return started;
    }

    protected final void startCollectionListener() {
        MessageListener<ChangeStreamDocument<Document>, DurableQueuedMessage> listener = message -> {
            try {
                if (message.getBody() == null) {
                    // Ignore, as these are
                    log.error("Received notification with null payload: {}", message.getRaw());
                    return;
                }

                var queueName = message.getBody().queueName;
                log.trace("[{}:{}] Received QueueMessage notification",
                          queueName,
                          message.getBody().id);
                durableQueueConsumers.values()
                                     .stream()
                                     .filter(mongoDurableQueueConsumer -> mongoDurableQueueConsumer.queueName.equals(queueName))
                                     .forEach(mongoDurableQueueConsumer -> {
                                         mongoDurableQueueConsumer.messageAdded(message.getBody());
                                     });
            } catch (Exception e) {
                log.error("An error occurred while handling notification", e);
            }
        };
        ChangeStreamRequest<DurableQueuedMessage> request = ChangeStreamRequest.builder()
                                                                               .collection(this.sharedQueueCollectionName)
                                                                               .filter(newAggregation(
                                                                                       match(where("operationType").in("insert", "update", "replace")),
                                                                                       match(where("queueName").exists(true))
                                                                                                     ))
                                                                               .publishTo(listener)
                                                                               .build();

        changeSubscription = messageListenerContainer.register(request, DurableQueuedMessage.class, cause -> {
            if (cause instanceof UncategorizedMongoDbException && cause.getMessage() != null && cause.getMessage().contains("error 136")) {
                log.info("ChangeStream is NOT ENABLED for this collection/database/cluster. Error message received: {}",
                         cause.getMessage());
                log.info("ℹ️ If you're using DocumentDB then please see: https://docs.aws.amazon.com/documentdb/latest/developerguide/change_streams.html");
            } else {
                log.error(msg("ChangeStream listener error: {}", cause.getMessage()), cause);
            }
        });
        messageListenerContainer.start();
    }

    protected final void stopCollectionListener() {
        if (changeSubscription != null) {
            changeSubscription.cancel();
        }
        if (messageListenerContainer != null && messageListenerContainer.isRunning()) {
            messageListenerContainer.stop();
        }
    }

    @Override
    public final TransactionalMode getTransactionalMode() {
        return transactionalMode;
    }

    @Override
    public final Optional<UnitOfWorkFactory<? extends UnitOfWork>> getUnitOfWorkFactory() {
        return Optional.ofNullable(unitOfWorkFactory);
    }

    @Override
    public final DurableQueues addInterceptor(DurableQueuesInterceptor interceptor) {
        requireNonNull(interceptor, "No interceptor provided");
        log.info("Adding interceptor: {}", interceptor);
        interceptor.setDurableQueues(this);
        interceptors.add(interceptor);
        sortInterceptorsByOrder(interceptors);
        return this;
    }

    @Override
    public final DurableQueues removeInterceptor(DurableQueuesInterceptor interceptor) {
        requireNonNull(interceptor, "No interceptor provided");
        log.info("Removing interceptor: {}", interceptor);
        interceptors.remove(interceptor);
        sortInterceptorsByOrder(interceptors);
        return this;
    }


    @Override
    public final Set<QueueName> getQueueNames() {
        var consumerQueueNames = durableQueueConsumers.keySet();
        var dbQueueNames = new HashSet<>(mongoTemplate.findDistinct(new Query(),
                                                                    "queueName",
                                                                    this.sharedQueueCollectionName,
                                                                    QueueName.class));

        dbQueueNames.addAll(consumerQueueNames);
        return dbQueueNames;
    }

    @Override
    public Set<QueueName> getActiveQueueNames() {
        return durableQueueConsumers.keySet();
    }

    @Override
    public final QueueEntryId queueMessage(QueueMessage operation) {
        requireNonNull(operation, "You must provide a QueueMessage instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> queueMessage(operation.queueName,
                                                                  operation.getMessage(),
                                                                  false,
                                                                  operation.getCauseOfEnqueuing(),
                                                                  operation.getDeliveryDelay()))
                .proceed();
    }

    @Override
    public final QueueEntryId queueMessageAsDeadLetterMessage(QueueMessageAsDeadLetterMessage operation) {
        requireNonNull(operation, "You must provide a QueueMessageAsDeadLetterMessage instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> queueMessage(operation.queueName,
                                                                  operation.getMessage(),
                                                                  true,
                                                                  Optional.ofNullable(operation.getCauseOfError()),
                                                                  Optional.empty()))
                .proceed();
    }

    protected final QueueEntryId queueMessage(QueueName queueName, Message message, boolean isDeadLetterMessage, Optional<Exception> causeOfEnqueuing, Optional<Duration> deliveryDelay) {
        requireNonNull(queueName, "You must provide a queueName");
        requireNonNull(message, "You must provide a message");
        requireNonNull(causeOfEnqueuing, "You must provide a causeOfEnqueuing option");
        requireNonNull(deliveryDelay, "You must provide a deliveryDelay option");

        var queueEntryId          = QueueEntryId.random();
        var addedTimestamp        = Instant.now();
        var nextDeliveryTimestamp = isDeadLetterMessage ? null : addedTimestamp.plus(deliveryDelay.orElse(Duration.ZERO));

        var isOrderedMessage = message instanceof OrderedMessage;
        log.trace("[{}:{}] Queuing {}{}message{} with nextDeliveryTimestamp {}. TransactionalMode: {}",
                  queueName,
                  queueEntryId,
                  isDeadLetterMessage ? "Dead Letter " : "",
                  isOrderedMessage ? "Ordered " : "",
                  isOrderedMessage ? msg(" {}:{}", ((OrderedMessage) message).getKey(), ((OrderedMessage) message).getOrder()) : "",
                  nextDeliveryTimestamp,
                  transactionalMode);

        if (transactionalMode == TransactionalMode.FullyTransactional) {
            unitOfWorkFactory.getRequiredUnitOfWork();
        }

        var durableQueuedMessage = createDurableQueuedMessage(queueName, isDeadLetterMessage, addedTimestamp, nextDeliveryTimestamp, message);

        mongoTemplate.save(durableQueuedMessage, this.sharedQueueCollectionName);
        log.debug("[{}:{}] Queued {}{}message{} with nextDeliveryTimestamp {}. TransactionalMode: {}",
                  queueName,
                  queueEntryId,
                  isDeadLetterMessage ? "Dead Letter " : "",
                  isOrderedMessage ? "Ordered " : "",
                  isOrderedMessage ? msg(" {}:{}", ((OrderedMessage) message).getKey(), ((OrderedMessage) message).getOrder()) : "",
                  nextDeliveryTimestamp,
                  transactionalMode);

        return durableQueuedMessage.getId();
    }

    @Override
    public final Optional<QueuedMessage> getDeadLetterMessage(GetDeadLetterMessage operation) {
        requireNonNull(operation, "You must specify a GetDeadLetterMessage instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> getQueuedMessage(operation.queueEntryId, true))
                .proceed();
    }

    @Override
    public final Optional<QueueName> getQueueNameFor(QueueEntryId queueEntryId) {
        var query = new Query(where("id").is(queueEntryId.toString()));
        return Optional.ofNullable(mongoTemplate.findOne(query,
                                                         DurableQueuedMessage.class,
                                                         this.sharedQueueCollectionName))
                       .map(DurableQueuedMessage::getQueueName);

    }

    @Override
    public final Optional<QueuedMessage> getQueuedMessage(GetQueuedMessage operation) {
        requireNonNull(operation, "You must specify a GetQueuedMessage instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> getQueuedMessage(operation.queueEntryId, false))
                .proceed();
    }

    protected final Optional<QueuedMessage> getQueuedMessage(QueueEntryId queueEntryId, boolean isDeadLetterMessage) {
        var query = new Query(where("id").is(queueEntryId)
                                         .and("isDeadLetterMessage").is(isDeadLetterMessage));
        return Optional.ofNullable(mongoTemplate.findOne(query,
                                                         DurableQueuedMessage.class,
                                                         this.sharedQueueCollectionName))
                       .map(durableQueuedMessage -> durableQueuedMessage.setDeserializeMessagePayloadFunction(this::deserializeMessagePayload));
    }

    private Object deserializeMessagePayload(QueueName queueName, byte[] messagePayload, String messagePayloadType) {
        try {
            return jsonSerializer.deserialize(messagePayload, messagePayloadType);
        } catch (Throwable e) {
            throw new DurableQueueException(msg("Failed to deserialize message payload of type {}", messagePayloadType), e, queueName);
        }
    }

    @Override
    public final List<QueueEntryId> queueMessages(QueueMessages operation) {
        requireNonNull(operation, "You must provide a QueueMessages instance");
        operation.validate();

        if (transactionalMode == TransactionalMode.FullyTransactional) {
            unitOfWorkFactory.getRequiredUnitOfWork();
        }

        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   var queueName             = operation.getQueueName();
                                                   var deliveryDelay         = operation.getDeliveryDelay();
                                                   var payloads              = operation.getMessages();
                                                   var addedTimestamp        = Instant.now();
                                                   var nextDeliveryTimestamp = addedTimestamp.plus(deliveryDelay.orElse(Duration.ZERO));


                                                   var messages = payloads.stream()
                                                                          .map(message -> createDurableQueuedMessage(queueName, false, addedTimestamp, nextDeliveryTimestamp, message))
                                                                          .collect(Collectors.toList());

                                                   var insertedEntryIds = mongoTemplate.insert(messages,
                                                                                               this.sharedQueueCollectionName)
                                                                                       .stream()
                                                                                       .map(DurableQueuedMessage::getId)
                                                                                       .collect(Collectors.toList());
                                                   if (insertedEntryIds.size() != payloads.size()) {
                                                       throw new DurableQueueException(msg("Attempted to queue {} messages but only inserted {} messages", payloads.size(), insertedEntryIds.size()),
                                                                                       queueName);
                                                   }

                                                   log.debug("[{}] Queued {} Messages with nextDeliveryTimestamp {} and entry-id's: {}",
                                                             queueName,
                                                             payloads.size(),
                                                             nextDeliveryTimestamp,
                                                             insertedEntryIds);
                                                   return insertedEntryIds;
                                               }).proceed();
    }

    private DurableQueuedMessage createDurableQueuedMessage(QueueName queueName, boolean isDeadLetterMessage, Instant addedTimestamp, Instant nextDeliveryTimestamp, Message message) {
        byte[] jsonPayload;
        try {
            jsonPayload = jsonSerializer.serializeAsBytes(message.getPayload());
        } catch (JSONSerializationException e) {
            throw new DurableQueueException(msg("Failed to serialize message payload of type", message.getPayload().getClass().getName()), e, queueName);
        }

        var    deliveryMode = QueuedMessage.DeliveryMode.NORMAL;
        String key          = null;
        var    keyOrder     = -1L;
        if (message instanceof OrderedMessage) {
            var orderedMessage = (OrderedMessage) message;
            deliveryMode = QueuedMessage.DeliveryMode.IN_ORDER;
            key = requireNonNull(orderedMessage.getKey(), "An OrderedMessage requires a non null key");
            requireTrue(orderedMessage.getOrder() >= 0, "An OrderedMessage requires an order >= 0");
            keyOrder = orderedMessage.getOrder();
        }

        return new DurableQueuedMessage(QueueEntryId.random(),
                                        queueName,
                                        false,
                                        jsonPayload,
                                        message.getPayload().getClass().getName(),
                                        addedTimestamp,
                                        nextDeliveryTimestamp,
                                        null,
                                        0,
                                        0,
                                        null,
                                        isDeadLetterMessage,
                                        message.getMetaData(),
                                        deliveryMode,
                                        key,
                                        keyOrder);
    }

    @Override
    public final Optional<QueuedMessage> retryMessage(RetryMessage operation) {
        requireNonNull(operation, "You must provide a RetryMessage instance");
        operation.validate();
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {

                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }

                                                   var nextDeliveryTimestamp = Instant.now().plus(operation.getDeliveryDelay());
                                                   var queueEntryId          = operation.queueEntryId;
                                                   var findMessageToRetry = query(where("id").is(queueEntryId)
                                                                                             .and("isBeingDelivered").is(true)
                                                                                 );

                                                   var update = new Update().inc("redeliveryAttempts", 1)
                                                                            .set("isBeingDelivered", false)
                                                                            .set("deliveryTimestamp", null)
                                                                            .set("lastDeliveryError", operation.getCauseForRetry() != null ? Exceptions.getStackTrace(operation.getCauseForRetry()) : RetryMessage.MANUALLY_REQUESTED_REDELIVERY);
                                                   var updateResult = mongoTemplate.findAndModify(findMessageToRetry,
                                                                                                  update,
                                                                                                  FindAndModifyOptions.options().returnNew(true),
                                                                                                  DurableQueuedMessage.class,
                                                                                                  this.sharedQueueCollectionName);
                                                   if (updateResult != null && !updateResult.isBeingDelivered) {
                                                       log.debug("[{}] Marked Message with id '{}' for Retry at {}. Message entry after update: {}",
                                                                 updateResult.queueName,
                                                                 queueEntryId,
                                                                 nextDeliveryTimestamp,
                                                                 updateResult);
                                                       return Optional.of((QueuedMessage) updateResult);
                                                   } else {
                                                       log.error("Failed to Mark Message with id '{}' for Retry", queueEntryId);
                                                       return Optional.<QueuedMessage>empty();
                                                   }
                                               }).proceed();
    }

    @Override
    public final Optional<QueuedMessage> markAsDeadLetterMessage(MarkAsDeadLetterMessage operation) {
        requireNonNull(operation, "You must provide a MarkAsDeadLetterMessage instance");
        operation.validate();
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }


                                                   var queueEntryId                         = operation.queueEntryId;
                                                   var findMessageToMarkAsDeadLetterMessage = query(where("id").is(queueEntryId));

                                                   var update = new Update().set("isBeingDelivered", false)
                                                                            .set("deliveryTimestamp", null)
                                                                            .set("nextDeliveryTimestamp", null)
                                                                            .set("isDeadLetterMessage", true)
                                                                            .set("lastDeliveryError", operation.getCauseForBeingMarkedAsDeadLetter());
                                                   var updateResult = mongoTemplate.findAndModify(findMessageToMarkAsDeadLetterMessage,
                                                                                                  update,
                                                                                                  FindAndModifyOptions.options().returnNew(true),
                                                                                                  DurableQueuedMessage.class,
                                                                                                  this.sharedQueueCollectionName);
                                                   if (updateResult != null && updateResult.isDeadLetterMessage) {
                                                       log.debug("[{}] Marked message with id '{}' as Dead Letter Message. Message entry after update: {}", updateResult.queueName, queueEntryId, updateResult);
                                                       return Optional.of((QueuedMessage) updateResult);
                                                   } else {
                                                       log.error("Failed to Mark as Message message with id '{}' as Dead Letter Message", queueEntryId);
                                                       return Optional.<QueuedMessage>empty();
                                                   }
                                               }).proceed();
    }

    @Override
    public final Optional<QueuedMessage> resurrectDeadLetterMessage(ResurrectDeadLetterMessage operation) {
        requireNonNull(operation, "You must provide a ResurrectDeadLetterMessage instance");
        operation.validate();
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {

                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }

                                                   var nextDeliveryTimestamp = Instant.now().plus(operation.getDeliveryDelay());

                                                   var queueEntryId = operation.queueEntryId;
                                                   var findMessageToResurrect = query(where("id").is(queueEntryId)
                                                                                                 .and("isDeadLetterMessage").is(true));

                                                   var update = new Update()
                                                           .set("nextDeliveryTimestamp", nextDeliveryTimestamp)
                                                           .set("isDeadLetterMessage", false);
                                                   var updateResult = mongoTemplate.findAndModify(findMessageToResurrect,
                                                                                                  update,
                                                                                                  FindAndModifyOptions.options().returnNew(true),
                                                                                                  DurableQueuedMessage.class,
                                                                                                  this.sharedQueueCollectionName);

                                                   if (updateResult != null && !updateResult.isDeadLetterMessage) {
                                                       var isOrderedMessage = updateResult.deliveryMode == QueuedMessage.DeliveryMode.IN_ORDER;
                                                       log.debug("[{}] Resurrected Dead Letter {}Message with id '{}' {} and nextDeliveryTimestamp: {}. Message entry after update: {}",
                                                                 updateResult.queueName,
                                                                 isOrderedMessage ? "Ordered " : "",
                                                                 queueEntryId,
                                                                 isOrderedMessage ? "(key: " + updateResult.getKey() + ", order: " + updateResult.getKeyOrder() + ")" : "",
                                                                 nextDeliveryTimestamp,
                                                                 updateResult);
                                                       return Optional.of((QueuedMessage) updateResult);
                                                   } else {
                                                       log.error("Failed to resurrect Dead Letter Message with id '{}'", queueEntryId);
                                                       return Optional.<QueuedMessage>empty();
                                                   }
                                               }).proceed();
    }

    @Override
    public final boolean acknowledgeMessageAsHandled(AcknowledgeMessageAsHandled operation) {
        requireNonNull(operation, "You must provide a AcknowledgeMessageAsHandled instance");

        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   log.debug("Acknowledging-Message-As-Handled regarding Message with id '{}'", operation.queueEntryId);
                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }


                                                   var queueEntryId = operation.queueEntryId;
                                                   var messagesDeleted = mongoTemplate.remove(
                                                           query(
                                                                   where("_id").is(queueEntryId.toString())
                                                                               .and("isDeadLetterMessage").is(false)
                                                                ), this.sharedQueueCollectionName).getDeletedCount();
                                                   if (messagesDeleted == 1) {
                                                       log.debug("Acknowledged message as handled and deleted it. Id: '{}'", queueEntryId);
                                                       return true;
                                                   } else if (getDeadLetterMessage(new GetDeadLetterMessage(operation.queueEntryId)).isPresent()) {
                                                       log.debug("Couldn't acknowledge message as it was marked as a Dead-Letter-Message during the message handling. Id: '{}'", queueEntryId);
                                                       return true;
                                                   } else {
                                                       log.error("Couldn't Acknowledge with id '{}' - it may already have been deleted", queueEntryId);
                                                       return false;
                                                   }
                                               })
                .proceed();

    }

    @Override
    public final boolean deleteMessage(DeleteMessage operation) {
        requireNonNull(operation, "You must provide a DeleteMessage instance");

        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }


                                                   var queueEntryId    = operation.queueEntryId;
                                                   var messagesDeleted = mongoTemplate.remove(query(where("_id").is(queueEntryId.toString())), this.sharedQueueCollectionName).getDeletedCount();
                                                   if (messagesDeleted == 1) {
                                                       log.debug("Deleted Message with id '{}'", queueEntryId);
                                                       return true;
                                                   } else {
                                                       log.error("Couldn't Delete Message with id '{}' - it may already have been deleted", queueEntryId);
                                                       return false;
                                                   }
                                               }).proceed();
    }

    @Override
    public final Optional<QueuedMessage> getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery operation) {
        requireNonNull(operation, "You must specify a GetNextMessageReadyForDelivery instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   log.trace("[{}] Performing GetNextMessageReadyForDelivery using transactionalMode: {}", operation.queueName, transactionalMode);
                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }

                                                   // Use a lock to ensure less WriteConflict if multiple competing threads are busy polling the same queue
                                                   var queueName = operation.queueName;
                                                   var lock      = localQueuePollLock.computeIfAbsent(queueName, queueName_ -> new ReentrantLock(true));
                                                   try {
                                                       if (!lock.tryLock(1, TimeUnit.SECONDS)) {
                                                           log.trace("[{}]Timed out waiting to acquire lock to poll for next message ready to be delivered", queueName);
                                                           return Optional.<QueuedMessage>empty();
                                                       }
                                                   } catch (InterruptedException e) {
                                                       Thread.currentThread().interrupt();
                                                       return Optional.<QueuedMessage>empty();
                                                   }
                                                   try {
                                                       resetMessagesStuckBeingDelivered(queueName);

                                                       var whereCriteria = where("queueName").is(queueName)
                                                                                             .and("nextDeliveryTimestamp").lte(Instant.now())
                                                                                             .and("isDeadLetterMessage").is(false)
                                                                                             .and("isBeingDelivered").is(false);
                                                       var excludedKeys = operation.getExcludeOrderedMessagesWithKey() != null ? operation.getExcludeOrderedMessagesWithKey() : List.of();
                                                       if (!excludedKeys.isEmpty()) {
                                                           whereCriteria.and("key").not().in(excludedKeys);
                                                       }

                                                       var nextMessageReadyForDeliveryQuery = query(whereCriteria)
                                                               .with(Sort.by(Sort.Direction.ASC, "keyOrder, nextDeliveryTimestamp"))
                                                               .limit(1);

                                                       var update = new Update().inc("totalDeliveryAttempts", 1)
                                                                                .set("isBeingDelivered", true)
                                                                                .set("deliveryTimestamp", Instant.now());

                                                       var nextMessageToDeliver = mongoTemplate.findAndModify(nextMessageReadyForDeliveryQuery,
                                                                                                              update,
                                                                                                              FindAndModifyOptions.options().returnNew(true),
                                                                                                              DurableQueuedMessage.class,
                                                                                                              this.sharedQueueCollectionName);

                                                       if (nextMessageToDeliver != null && nextMessageToDeliver.isBeingDelivered()) {
                                                           var deliverMessage = resolveIfMessageShouldBeDelivered(queueName, nextMessageToDeliver);

                                                           if (deliverMessage) {
                                                               log.debug("[{}] Found a message ready for delivery: {}", queueName, nextMessageToDeliver.id);
                                                               return Optional.of(nextMessageToDeliver)
                                                                              .map(durableQueuedMessage -> (QueuedMessage) durableQueuedMessage.setDeserializeMessagePayloadFunction(this::deserializeMessagePayload));
                                                           } else {
                                                               log.trace("[{}] Didn't find a message ready for delivery (deliverMessage: {} for '{}')", queueName, deliverMessage, nextMessageToDeliver.getId());
                                                               return Optional.<QueuedMessage>empty();
                                                           }
                                                       } else {
                                                           log.trace("[{}] Didn't find a message ready for delivery", queueName);
                                                           return Optional.<QueuedMessage>empty();
                                                       }
                                                   } catch (Exception e) {
                                                       if (e instanceof UncategorizedMongoDbException && e.getMessage() != null && (e.getMessage().contains("WriteConflict") || e.getMessage().contains("Write Conflict"))) {
                                                           log.trace("[{}] WriteConflict finding next message ready for delivery. Will retry", queueName);
                                                           if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                               unitOfWorkFactory.getRequiredUnitOfWork().markAsRollbackOnly(e);
                                                           }

                                                           return Optional.<QueuedMessage>empty();
                                                       } else if (e instanceof UncategorizedMongoDbException && e.getCause() instanceof MongoInterruptedException) {
                                                           log.trace("[{}] MongoInterruptedException", queueName);
                                                           return Optional.<QueuedMessage>empty();
                                                       } else if (IOExceptionUtil.isIOException(e)) {
                                                           log.trace("[{}] Experienced a Mongo related IO exception '{}'", queueName, e.getClass().getSimpleName());
                                                           return Optional.<QueuedMessage>empty();
                                                       } else if (e instanceof IllegalStateException && Objects.equals(e.getMessage(), "state should be: open")) {
                                                           log.trace("[{}] Mongo Session is not open", queueName);
                                                           return Optional.<QueuedMessage>empty();
                                                       }
                                                       throw new DurableQueueException(msg("Failed to perform getNextMessageReadyForDelivery for queue '{}'", queueName), e, queueName);
                                                   } finally {
                                                       lock.unlock();
                                                   }
                                               }).proceed();
    }

    private boolean resolveIfMessageShouldBeDelivered(QueueName queueName, DurableQueuedMessage nextMessageToDeliver) {
        if (nextMessageToDeliver.getDeliveryMode() == QueuedMessage.DeliveryMode.IN_ORDER) {
            // Check if there's another ordered message queued with the same key and a keyOrder lower than the one we found
            // in which case we need to return the message and adjust the message, so it won't be delivered immediately (or mark it as a dead letter messasge in case the message with a
            // lower keyOrder is marked as such

            var queuedMessagesWithSameKeyAndALowerKeyOrder = mongoTemplate.find(query(where("queueName")
                                                                                              .is(queueName)
                                                                                              .and("key").is(nextMessageToDeliver.getKey())
                                                                                              .and("keyOrder").lt(nextMessageToDeliver.getKeyOrder()))
                                                                                        .with(Sort.by(Sort.Direction.ASC, "keyOrder"))
                                                                                        .limit(10),
                                                                                DurableQueuedMessage.class,
                                                                                this.sharedQueueCollectionName);
            if (queuedMessagesWithSameKeyAndALowerKeyOrder.size() > 0) {
                var findOrderedMessagesWithTheSameKeyAndAHigherOrder = query(where("queueName").is(queueName.toString())
                                                                                               .and("key").is(nextMessageToDeliver.key)
                                                                                               .and("keyOrder").gt(nextMessageToDeliver.keyOrder));
                var firstDeadLetterMessage = queuedMessagesWithSameKeyAndALowerKeyOrder.stream()
                                                                                       .filter(DurableQueuedMessage::isDeadLetterMessage)
                                                                                       .findFirst();
                if (firstDeadLetterMessage.isPresent()) {
                    var firstDeadLetterMessageWithSameKey = firstDeadLetterMessage.get();

                    var updateResult = mongoTemplate.findAndModify(query(where("id").is(nextMessageToDeliver.id)),
                                                                   new Update().set("totalDeliveryAttempts", nextMessageToDeliver.getTotalDeliveryAttempts() - 1)
                                                                               .set("isBeingDelivered", false)
                                                                               .set("deliveryTimestamp", null)
                                                                               .set("isDeadLetterMessage", true)
                                                                               .set("lastDeliveryError", msg("Marked as Dead Letter Message because message '{}' with same key and lower order '{}' is already marked as a Dead Letter Message",
                                                                                                             firstDeadLetterMessageWithSameKey.getId(),
                                                                                                             firstDeadLetterMessageWithSameKey.getKeyOrder())),
                                                                   FindAndModifyOptions.options().returnNew(true),
                                                                   DurableQueuedMessage.class,
                                                                   this.sharedQueueCollectionName);
                    if (updateResult != null && !updateResult.isBeingDelivered()) {
                        var reason = msg("Resetting message with id '{}' (key: '{}', order: {}) as not being delivered and marking it as a Dead Letter Message, because message '{}' (order: {}) is marked as a Dead Letter Message",
                                         nextMessageToDeliver.getId(),
                                         nextMessageToDeliver.getKey(),
                                         nextMessageToDeliver.getKeyOrder(),
                                         firstDeadLetterMessageWithSameKey.getId(),
                                         firstDeadLetterMessageWithSameKey.getKeyOrder());
                        log.debug("** [{}] {}. Message entry after update: {}", updateResult.getQueueName(), reason, updateResult);
                    } else {
                        log.error("** Failed to update isBeingDelivered for message with id '{}'", nextMessageToDeliver.getId());
                    }


                    var markAsDeadLetterMessageUpdate = new Update().set("nextDeliveryTimestamp", null)
                                                                    .set("isDeadLetterMessage", true)
                                                                    .set("lastDeliveryError", msg("Marked as Dead Letter Message because message '{}' with same key and lower order '{}' is already marked as a Dead Letter Message",
                                                                                                  firstDeadLetterMessageWithSameKey.getId(),
                                                                                                  firstDeadLetterMessageWithSameKey.getKeyOrder()));
                    var updatedResult = mongoTemplate.updateMulti(findOrderedMessagesWithTheSameKeyAndAHigherOrder,
                                                                  markAsDeadLetterMessageUpdate,
                                                                  this.sharedQueueCollectionName);
                    if (updatedResult.getModifiedCount() > 0) {
                        log.debug("** [{}] Marked {} message(s) with key '{}' and order > '{}' as Dead Letter Messages, because Message '{}' with same key '{}' and lower order '{}' was already marked as a Dead Letter Message",
                                  queueName,
                                  updatedResult.getModifiedCount(),
                                  firstDeadLetterMessageWithSameKey.getKey(),
                                  firstDeadLetterMessageWithSameKey.getKeyOrder(),
                                  firstDeadLetterMessageWithSameKey.getId(),
                                  firstDeadLetterMessageWithSameKey.getKey(),
                                  firstDeadLetterMessageWithSameKey.getKeyOrder());
                    }
                    return false;
                } else {
                    var messageWithTheHighestNextDeliveryTimestamp = queuedMessagesWithSameKeyAndALowerKeyOrder.stream()
                                                                                                               .sorted(Comparator.comparing(DurableQueuedMessage::getNextDeliveryTimestamp).reversed())
                                                                                                               .findFirst()
                                                                                                               .get();
                    var nextDeliveryTimestamp = messageWithTheHighestNextDeliveryTimestamp.getNextDeliveryTimestamp().plus(100, ChronoUnit.MILLIS);
                    var adjustNextDeliveryTimestampUpdate = new Update().set("totalDeliveryAttempts", nextMessageToDeliver.getTotalDeliveryAttempts() - 1)
                                                                        .set("isBeingDelivered", false)
                                                                        .set("nextDeliveryTimestamp", nextDeliveryTimestamp.toInstant());
                    var updateResult = mongoTemplate.findAndModify(query(where("id").is(nextMessageToDeliver.id)),
                                                                   adjustNextDeliveryTimestampUpdate,
                                                                   FindAndModifyOptions.options().returnNew(true),
                                                                   DurableQueuedMessage.class,
                                                                   this.sharedQueueCollectionName);
                    if (updateResult != null && updateResult.getNextDeliveryTimestamp().equals(nextDeliveryTimestamp)) {
                        var reason = msg("Adjusting message nextDeliveryTimestamp for message with id '{}' (key: '{}', order: {}) to ´{}´ because message '{}' (order: {}) has nextDeliveryTimestamp '{}'",
                                         nextMessageToDeliver.getId(),
                                         nextMessageToDeliver.getKey(),
                                         nextMessageToDeliver.getKeyOrder(),
                                         nextDeliveryTimestamp,
                                         messageWithTheHighestNextDeliveryTimestamp.getId(),
                                         messageWithTheHighestNextDeliveryTimestamp.getKeyOrder(),
                                         messageWithTheHighestNextDeliveryTimestamp.getNextDeliveryTimestamp());
                        log.debug("** [{}] {}. Message entry after update: {}", updateResult.getQueueName(), reason, updateResult);
                    } else {
                        log.error("** Failed to update nextDeliveryTimestamp for message with id '{}'", nextMessageToDeliver.getId());
                    }

                    var updateNextDeliveryTimestamp = new Update().set("nextDeliveryTimestamp", nextDeliveryTimestamp.plus(100, ChronoUnit.MILLIS).toInstant());

                    var updatedResult = mongoTemplate.updateMulti(findOrderedMessagesWithTheSameKeyAndAHigherOrder, updateNextDeliveryTimestamp, this.sharedQueueCollectionName);
                    if (updatedResult.getModifiedCount() > 0) {
                        log.debug("** [{}] Updated {} messages nextDeliveryTimestamp to '{}', because Message '{}' with same key '{}' and lower order '{}' has nextDeliveryTimestamp '{}'",
                                  queueName,
                                  updatedResult.getModifiedCount(),
                                  nextDeliveryTimestamp,
                                  messageWithTheHighestNextDeliveryTimestamp.getId(),
                                  messageWithTheHighestNextDeliveryTimestamp.getKey(),
                                  messageWithTheHighestNextDeliveryTimestamp.getKeyOrder(),
                                  messageWithTheHighestNextDeliveryTimestamp.getNextDeliveryTimestamp());
                    }

                    return false;
                }
            }
        }
        return true;
    }

    /**
     * This operation will scan for messages that has been marked as {@link QueuedMessage#isBeingDelivered()} for longer
     * than {@link #messageHandlingTimeoutMs}<br>
     * All messages found will have {@link QueuedMessage#isBeingDelivered()} and {@link QueuedMessage#getDeliveryTimestamp()}
     * reset<br>
     * Only relevant for when using {@link TransactionalMode#SingleOperationTransaction}
     *
     * @param queueName the queue for which we're looking for messages stuck being marked as {@link QueuedMessage#isBeingDelivered()}
     */
    protected final void resetMessagesStuckBeingDelivered(QueueName queueName) {
        // Reset stuck messages
        if (transactionalMode == TransactionalMode.SingleOperationTransaction) {
            var now                            = Instant.now();
            var lastStuckMessageResetTimestamp = lastResetStuckMessagesCheckTimestamps.get(queueName);
            if (lastStuckMessageResetTimestamp == null || Duration.between(now, lastStuckMessageResetTimestamp).abs().toMillis() > messageHandlingTimeoutMs) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Looking for messages stuck marked as isBeingDelivered. Last check was performed: {}", queueName, lastStuckMessageResetTimestamp);
                }
                var stuckMessagesQuery = query(where("queueName").is(queueName)
                                                                 .and("isBeingDelivered").is(true)
                                                                 .and("deliveryTimestamp").lte(now.minusMillis(messageHandlingTimeoutMs)));

                var update = new Update()
                        .inc("redeliveryAttempts", 1)
                        .set("lastDeliveryError", "Handler Processing of the Message was determined to have Timed Out")
                        .set("isBeingDelivered", false)
                        .set("nextDeliveryTimestamp", now)
                        .set("deliveryTimestamp", null);

                var updateResult = mongoTemplate.updateMulti(stuckMessagesQuery,
                                                             update,
                                                             this.sharedQueueCollectionName);
                if (updateResult.getModifiedCount() > 0) {
                    log.debug("[{}] Reset {} messages stuck marked as isBeingDelivered", queueName, updateResult.getModifiedCount());
                } else {
                    log.debug("[{}] Didn't find any messages being stuck marked as isBeingDelivered", queueName);
                }
                lastResetStuckMessagesCheckTimestamps.put(queueName, now);
            }
        }
    }

    @Override
    public final boolean hasMessagesQueuedFor(QueueName queueName) {
        return getTotalMessagesQueuedFor(queueName) > 0;
    }

    @Override
    public final long getTotalMessagesQueuedFor(GetTotalMessagesQueuedFor operation) {
        requireNonNull(operation, "You must specify a GetTotalMessagesQueuedFor instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> mongoTemplate.count(query(where("queueName").is(operation.queueName)
                                                                                                 .and("isDeadLetterMessage").is(false)),
                                                                         this.sharedQueueCollectionName))
                .proceed();
    }

    @Override
    public QueuedMessageCounts getQueuedMessageCountsFor(GetQueuedMessageCountsFor operation) {
        requireNonNull(operation, "You must specify a GetQueuedMessageCountsFor instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   var matchOperation = Aggregation.match(Criteria.where("queueName").is(operation.queueName));
                                                   var aggregation = Aggregation.newAggregation(
                                                           matchOperation,
                                                           Aggregation.group("isDeadLetterMessage")
                                                                      .count().as("count")
                                                                      .first("isDeadLetterMessage").as("isDeadLetterMessage"));
                                                   var results = mongoTemplate.aggregate(aggregation, this.sharedQueueCollectionName, Map.class);

                                                   var numberOfQueuedMessages           = 0L;
                                                   var numberOfQueuedDeadLetterMessages = 0L;

                                                   for (var result : results.getMappedResults()) {
                                                       boolean isDeadLetter = (boolean) result.get("isDeadLetterMessage");
                                                       int     count        = (int) result.get("count");
                                                       if (isDeadLetter) {
                                                           numberOfQueuedDeadLetterMessages = count;
                                                       } else {
                                                           numberOfQueuedMessages = count;
                                                       }
                                                   }

                                                   return new QueuedMessageCounts(operation.queueName, numberOfQueuedMessages, numberOfQueuedDeadLetterMessages);
                                               })
                .proceed();
    }

    @Override
    public final long getTotalDeadLetterMessagesQueuedFor(GetTotalDeadLetterMessagesQueuedFor operation) {
        requireNonNull(operation, "You must specify a GetTotalDeadLetterMessagesQueuedFor instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> mongoTemplate.count(query(where("queueName").is(operation.queueName)
                                                                                                 .and("isDeadLetterMessage").is(true)),
                                                                         this.sharedQueueCollectionName))
                .proceed();
    }

    @Override
    public final List<QueuedMessage> getQueuedMessages(GetQueuedMessages operation) {
        requireNonNull(operation, "You must specify a GetQueuedMessages instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> queryQueuedMessages(operation.queueName, operation.getQueueingSortOrder(), IncludeMessages.QUEUED_MESSAGES, operation.getStartIndex(), operation.getPageSize()))
                .proceed();
    }

    @Override
    public List<QueuedMessage> getDeadLetterMessages(GetDeadLetterMessages operation) {
        requireNonNull(operation, "You must specify a GetDeadLetterMessages instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> queryQueuedMessages(operation.queueName, operation.getQueueingSortOrder(), IncludeMessages.DEAD_LETTER_MESSAGES, operation.getStartIndex(), operation.getPageSize()))
                .proceed();
    }

    protected enum IncludeMessages {
        ALL, DEAD_LETTER_MESSAGES, QUEUED_MESSAGES
    }

    protected final List<QueuedMessage> queryQueuedMessages(QueueName queueName, QueueingSortOrder queueingSortOrder, IncludeMessages includeMessages, long startIndex, long pageSize) {
        requireNonNull(queueName, "No queueName provided");
        requireNonNull(queueingSortOrder, "No queueingOrder provided");
        requireNonNull(includeMessages, "No includeMessages provided");

        var criteria = new AtomicReference<>(where("queueName").is(queueName));
        switch (includeMessages) {
            case ALL:
                // Do nothing
                break;
            case DEAD_LETTER_MESSAGES:
                criteria.set(criteria.get().and("isDeadLetterMessage").is(true));
                break;
            case QUEUED_MESSAGES:
                criteria.set(criteria.get().and("isDeadLetterMessage").is(false));
                break;
            default:
                throw new IllegalArgumentException("Unsupported IncludeMessages value: " + includeMessages);
        }

        return mongoTemplate.find(query(criteria.get())
                                          .limit((int) pageSize)
                                          .skip(startIndex),
                                  DurableQueuedMessage.class,
                                  this.sharedQueueCollectionName)
                            .stream()
                            .map(durableQueuedMessage -> durableQueuedMessage.setDeserializeMessagePayloadFunction(this::deserializeMessagePayload))
                            .collect(Collectors.toList());
    }

    @Override
    public final int purgeQueue(PurgeQueue operation) {
        requireNonNull(operation, "You must specify a PurgeQueue instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> (int) mongoTemplate.remove(query(where("queueName").is(operation.queueName)), this.sharedQueueCollectionName).getDeletedCount())
                .proceed();
    }

    @Override
    public final List<NextQueuedMessage> queryForMessagesSoonReadyForDelivery(QueueName queueName, Instant withNextDeliveryTimestampAfter, int maxNumberOfMessagesToReturn) {
        requireNonNull(queueName, "No queueName provided");
        requireNonNull(withNextDeliveryTimestampAfter, "No withNextDeliveryTimestampAfter provided");
        var criteria = where("queueName").is(queueName.toString())
                                         .and("isDeadLetterMessage").is(false)
                                         .and("isBeingDelivered").in(false)
                                         .and("nextDeliveryTimestamp").gt(withNextDeliveryTimestampAfter);
        var query = query(criteria)
                .limit(maxNumberOfMessagesToReturn)
                .with(Sort.by(Sort.Direction.ASC, "nextDeliveryTimestamp"));

        query.fields()
             .include("addedTimestamp", "nextDeliveryTimestamp");
        return mongoTemplate.find(query,
                                  DurableQueuedMessage.class,
                                  this.sharedQueueCollectionName)
                            .stream()
                            .map(durableQueuedMessage -> new NextQueuedMessage(durableQueuedMessage.id,
                                                                               queueName,
                                                                               durableQueuedMessage.addedTimestamp,
                                                                               durableQueuedMessage.nextDeliveryTimestamp))
                            .collect(Collectors.toList());
    }

    @Override
    public final DurableQueueConsumer consumeFromQueue(ConsumeFromQueue operation) {
        requireNonNull(operation, "No operation provided");
        if (durableQueueConsumers.containsKey(operation.queueName)) {
            throw new DurableQueueException("There is already an DurableConsumer for this queue", operation.queueName);
        }
        operation.validate();
        return durableQueueConsumers.computeIfAbsent(operation.queueName, _queueName -> {
            MongoDurableQueueConsumer consumer = (MongoDurableQueueConsumer) newInterceptorChainForOperation(operation,
                                                                                                             interceptors,
                                                                                                             (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                                                                                             () -> (DurableQueueConsumer) new MongoDurableQueueConsumer(operation,
                                                                                                                                                                        unitOfWorkFactory,
                                                                                                                                                                        this,
                                                                                                                                                                        this::removeQueueConsumer,
                                                                                                                                                                        operation.getPollingInterval().toMillis(),
                                                                                                                                                                        createQueuePollingOptimizerFor(operation))).proceed();
            if (started) {
                consumer.start();
            }
            log.info("[{}] {} - {} {}",
                     operation.queueName,
                     operation.consumerName,
                     started ? "Started" : "Created",
                     consumer.getClass().getSimpleName()
                    );
            return consumer;
        });
    }

    /**
     * Override this method to provide another {@link QueuePollingOptimizer} than the default {@link SimpleQueuePollingOptimizer}
     *
     * @param operation the operation for which the {@link QueuePollingOptimizer} will be responsible
     * @return the {@link QueuePollingOptimizer}
     */
    protected final QueuePollingOptimizer createQueuePollingOptimizerFor(ConsumeFromQueue operation) {
        var pollingIntervalMs = operation.getPollingInterval().toMillis();
        return new SimpleQueuePollingOptimizer(operation,
                                               (long) (pollingIntervalMs * 0.5d),
                                               pollingIntervalMs * 20);
    }

    final void removeQueueConsumer(DurableQueueConsumer durableQueueConsumer) {
        requireNonNull(durableQueueConsumer, "You must provide a durableQueueConsumer");
        requireFalse(durableQueueConsumer.isStarted(), msg("Cannot remove DurableQueueConsumer '{}' since it's started!", durableQueueConsumer.queueName()));
        var operation = new StopConsumingFromQueue(durableQueueConsumer);
        try {
            newInterceptorChainForOperation(operation,
                                            interceptors,
                                            (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                            () -> {
                                                lastResetStuckMessagesCheckTimestamps.remove(operation.durableQueueConsumer.queueName());
                                                return (DurableQueueConsumer) durableQueueConsumers.remove(durableQueueConsumer.queueName());
                                            })
                    .proceed();
        } catch (Exception e) {
            log.error(msg("Failed to perform {}", operation), e);
        }
    }

    private static ObjectMapper createDefaultObjectMapper() {
        var objectMapper = JsonMapper.builder()
                                     .disable(MapperFeature.AUTO_DETECT_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_SETTERS)
                                     .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                                     .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                     .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                     .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                     .enable(MapperFeature.AUTO_DETECT_CREATORS)
                                     .enable(MapperFeature.AUTO_DETECT_FIELDS)
                                     .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                                     .addModule(new Jdk8Module())
                                     .addModule(new JavaTimeModule())
                                     .addModule(new EssentialTypesJacksonModule())
                                     .addModule(new EssentialsImmutableJacksonModule())
                                     .build();

        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return objectMapper;
    }

    @Document
    public static class DurableQueuedMessage implements QueuedMessage {
        @Id
        private QueueEntryId id;
        private QueueName    queueName;
        private boolean      isBeingDelivered;
        private byte[]       messagePayload;
        private String       messagePayloadType;
        private Instant      addedTimestamp;
        private Instant      nextDeliveryTimestamp;

        private Instant deliveryTimestamp;
        private int     totalDeliveryAttempts;
        private int     redeliveryAttempts;

        private String          lastDeliveryError;
        private boolean         isDeadLetterMessage;
        private MessageMetaData metaData;

        private DeliveryMode deliveryMode = DeliveryMode.NORMAL;
        private String       key;
        private long         keyOrder     = -1L;

        @Transient
        private transient TripleFunction<QueueName, byte[], String, Object> deserializeMessagePayloadFunction;
        @Transient
        private transient Message                                           message;
        @Transient
        private           Duration                                          manuallyRequestedRedeliveryDelay;

        public DurableQueuedMessage() {
        }

        public DurableQueuedMessage(QueueEntryId id,
                                    QueueName queueName,
                                    boolean isBeingDelivered,
                                    byte[] messagePayload,
                                    String messagePayloadType,
                                    Instant addedTimestamp,
                                    Instant nextDeliveryTimestamp,
                                    Instant deliveryTimestamp,
                                    int totalDeliveryAttempts,
                                    int redeliveryAttempts,
                                    String lastDeliveryError,
                                    boolean isDeadLetterMessage,
                                    MessageMetaData metaData,
                                    DeliveryMode deliveryMode,
                                    String key,
                                    long keyOrder) {
            this.id = id;
            this.queueName = queueName;
            this.isBeingDelivered = isBeingDelivered;
            this.messagePayload = messagePayload;
            this.messagePayloadType = messagePayloadType;
            this.addedTimestamp = addedTimestamp;
            this.nextDeliveryTimestamp = nextDeliveryTimestamp;
            this.deliveryTimestamp = deliveryTimestamp;
            this.totalDeliveryAttempts = totalDeliveryAttempts;
            this.redeliveryAttempts = redeliveryAttempts;
            this.lastDeliveryError = lastDeliveryError;
            this.isDeadLetterMessage = isDeadLetterMessage;
            this.metaData = metaData;
            this.deliveryMode = deliveryMode;
            this.key = key;
            this.keyOrder = keyOrder;
        }

        @Override
        public QueueEntryId getId() {
            return id;
        }

        @Override
        public QueueName getQueueName() {
            return queueName;
        }

        @Override
        public boolean isBeingDelivered() {
            return isBeingDelivered;
        }

        public byte[] getMessagePayload() {
            return messagePayload;
        }

        public String getMessagePayloadType() {
            return messagePayloadType;
        }

        @Override
        public OffsetDateTime getAddedTimestamp() {
            return addedTimestamp.atOffset(ZoneOffset.UTC);
        }

        @Override
        public OffsetDateTime getNextDeliveryTimestamp() {
            return nextDeliveryTimestamp.atOffset(ZoneOffset.UTC);
        }

        @Override
        public int getTotalDeliveryAttempts() {
            return totalDeliveryAttempts;
        }

        @Override
        public int getRedeliveryAttempts() {
            return redeliveryAttempts;
        }

        @Override
        public String getLastDeliveryError() {
            return lastDeliveryError;
        }

        @Override
        public boolean isDeadLetterMessage() {
            return isDeadLetterMessage;
        }

        @Override
        public OffsetDateTime getDeliveryTimestamp() {
            return deliveryTimestamp.atOffset(ZoneOffset.UTC);
        }

        @Override
        public void markForRedeliveryIn(Duration deliveryDelay) {
            this.manuallyRequestedRedeliveryDelay = deliveryDelay;
        }

        @Override
        public boolean isManuallyMarkedForRedelivery() {
            return manuallyRequestedRedeliveryDelay != null;
        }

        @Override
        public Duration getRedeliveryDelay() {
            return manuallyRequestedRedeliveryDelay;
        }

        @Override
        public DeliveryMode getDeliveryMode() {
            return deliveryMode;
        }

        public String getKey() {
            return key;
        }

        public long getKeyOrder() {
            return keyOrder;
        }

        @Override
        public Message getMessage() {
            requireNonNull(deserializeMessagePayloadFunction, "Internal Error: deserializeMessagePayloadFunction is null");
            if (message == null) {
                switch (deliveryMode) {
                    case NORMAL:
                        message = new Message(deserializeMessagePayloadFunction.apply(queueName,
                                                                                      messagePayload,
                                                                                      messagePayloadType),
                                              getMetaData());
                        break;
                    case IN_ORDER:
                        message = new OrderedMessage(deserializeMessagePayloadFunction.apply(queueName,
                                                                                             messagePayload,
                                                                                             messagePayloadType),
                                                     key,
                                                     keyOrder,
                                                     getMetaData());

                        break;
                }
            }
            return message;
        }

        @Override
        public MessageMetaData getMetaData() {
            if (metaData == null) {
                metaData = new MessageMetaData();
            }
            return metaData;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DurableQueuedMessage that = (DurableQueuedMessage) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "DurableQueuedMessage{" +
                    "id=" + id +
                    ", queueName=" + queueName +
                    ", isBeingDelivered=" + isBeingDelivered +
                    ", messagePayloadType='" + messagePayloadType + '\'' +
                    ", addedTimestamp=" + addedTimestamp +
                    ", nextDeliveryTimestamp=" + nextDeliveryTimestamp +
                    ", deliveryTimestamp=" + deliveryTimestamp +
                    ", totalDeliveryAttempts=" + totalDeliveryAttempts +
                    ", redeliveryAttempts=" + redeliveryAttempts +
                    ", isDeadLetterMessage=" + isDeadLetterMessage +
                    ", manuallyRequestedRedeliveryDelay=" + manuallyRequestedRedeliveryDelay +
                    ", deliveryMode=" + deliveryMode +
                    ", key=" + key +
                    ", keyOrder=" + keyOrder +
                    ", metaData=" + metaData +
                    '}';
        }

        public DurableQueuedMessage setDeserializeMessagePayloadFunction(TripleFunction<QueueName, byte[], String, Object> deserializeMessagePayloadFunction) {
            this.deserializeMessagePayloadFunction = requireNonNull(deserializeMessagePayloadFunction, "No deserializeMessagePayloadFunction provided");
            return this;
        }
    }
}
