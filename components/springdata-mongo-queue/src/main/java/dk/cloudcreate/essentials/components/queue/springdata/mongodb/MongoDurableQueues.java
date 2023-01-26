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

package dk.cloudcreate.essentials.components.queue.springdata.mongodb;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.MongoInterruptedException;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.shared.Exceptions;
import dk.cloudcreate.essentials.shared.functional.TripleFunction;
import dk.cloudcreate.essentials.shared.reflection.Classes;
import org.slf4j.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.*;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static dk.cloudcreate.essentials.shared.interceptor.InterceptorChain.newInterceptorChainForOperation;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Spring-data MongoDB version of the {@link DurableQueues} concept.<br>
 * Supports both {@link TransactionalMode#FullyTransactional} in collaboration with a {@link UnitOfWorkFactory} in order to support queuing message together with business logic (such as failing to handle an Event, etc.)
 * as well as {@link TransactionalMode#ManualAcknowledgement}
 */
public class MongoDurableQueues implements DurableQueues {

    protected static final Logger log                                    = LoggerFactory.getLogger(MongoDurableQueues.class);
    public static final    String DEFAULT_DURABLE_QUEUES_COLLECTION_NAME = "durable_queues";

    protected       SpringMongoTransactionAwareUnitOfWorkFactory        unitOfWorkFactory;
    protected final MongoTemplate                                       mongoTemplate;
    private final   TransactionalMode                                   transactionalMode;
    private final   ObjectMapper                                        messagePayloadObjectMapper;
    protected final String                                              sharedQueueCollectionName;
    private final   ConcurrentMap<QueueName, MongoDurableQueueConsumer> durableQueueConsumers = new ConcurrentHashMap<>();
    private final   ConcurrentMap<QueueName, ReentrantLock>             localQueuePollLock    = new ConcurrentHashMap<>();
    private final   List<DurableQueuesInterceptor>                      interceptors          = new ArrayList<>();

    private volatile boolean                           started;
    private          int                               messageHandlingTimeoutMs;
    /**
     * Contains the timestamp of the last performed {@link #resetMessagesStuckBeingDelivered(QueueName)} check
     */
    protected        ConcurrentMap<QueueName, Instant> lastResetStuckMessagesCheckTimestamps = new ConcurrentHashMap<>();

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#ManualAcknowledgement} with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}, default {@link ObjectMapper}
     * configuration
     *
     * @param mongoTemplate          the {@link MongoTemplate} used
     * @param messageHandlingTimeout Defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                               After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              Duration messageHandlingTimeout) {
        this(TransactionalMode.ManualAcknowledgement,
             mongoTemplate,
             null,
             messageHandlingTimeout,
             createObjectMapper(),
             DEFAULT_DURABLE_QUEUES_COLLECTION_NAME);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#FullyTransactional} with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME} and a default {@link ObjectMapper}
     * configuration
     *
     * @param mongoTemplate     the {@link MongoTemplate} used
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} needed to access the database
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory) {
        this(TransactionalMode.FullyTransactional,
             mongoTemplate,
             unitOfWorkFactory,
             null,
             createObjectMapper(),
             DEFAULT_DURABLE_QUEUES_COLLECTION_NAME);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#FullyTransactional} with custom messagePayloadObjectMapper and sharedQueueTableName
     *
     * @param mongoTemplate              the {@link MongoTemplate} used
     * @param unitOfWorkFactory          the {@link UnitOfWorkFactory} needed to access the database
     * @param messagePayloadObjectMapper the {@link ObjectMapper} that is used to serialize/deserialize message payloads
     * @param sharedQueueCollectionName  the name of the collection that will contain all messages (across all {@link QueueName}'s)
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                              ObjectMapper messagePayloadObjectMapper,
                              String sharedQueueCollectionName) {
        this(TransactionalMode.FullyTransactional,
             mongoTemplate,
             unitOfWorkFactory,
             null,
             messagePayloadObjectMapper,
             sharedQueueCollectionName);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#FullyTransactional} with custom messagePayloadObjectMapper and with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}
     *
     * @param mongoTemplate              the {@link MongoTemplate} used
     * @param unitOfWorkFactory          the {@link UnitOfWorkFactory} needed to access the database
     * @param messagePayloadObjectMapper the {@link ObjectMapper} that is used to serialize/deserialize message payloads
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                              ObjectMapper messagePayloadObjectMapper) {
        this(TransactionalMode.FullyTransactional,
             mongoTemplate,
             unitOfWorkFactory,
             null,
             messagePayloadObjectMapper,
             DEFAULT_DURABLE_QUEUES_COLLECTION_NAME);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#ManualAcknowledgement} with custom messagePayloadObjectMapper and sharedQueueTableName
     *
     * @param mongoTemplate              the {@link MongoTemplate} used
     * @param messageHandlingTimeout     Defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                                   After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     * @param messagePayloadObjectMapper the {@link ObjectMapper} that is used to serialize/deserialize message payloads
     * @param sharedQueueCollectionName  the name of the collection that will contain all messages (across all {@link QueueName}'s)
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              Duration messageHandlingTimeout,
                              ObjectMapper messagePayloadObjectMapper,
                              String sharedQueueCollectionName) {
        this(TransactionalMode.ManualAcknowledgement,
             mongoTemplate,
             null,
             messageHandlingTimeout,
             messagePayloadObjectMapper,
             sharedQueueCollectionName);
    }

    /**
     * Create {@link DurableQueues} running in {@link TransactionalMode#ManualAcknowledgement} with custom messagePayloadObjectMapper and with sharedQueueCollectionName: {@value DEFAULT_DURABLE_QUEUES_COLLECTION_NAME}
     *
     * @param mongoTemplate              the {@link MongoTemplate} used
     * @param messageHandlingTimeout     Defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                                   After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     * @param messagePayloadObjectMapper the {@link ObjectMapper} that is used to serialize/deserialize message payloads
     */
    public MongoDurableQueues(MongoTemplate mongoTemplate,
                              Duration messageHandlingTimeout,
                              ObjectMapper messagePayloadObjectMapper) {
        this(TransactionalMode.ManualAcknowledgement,
             mongoTemplate,
             null,
             messageHandlingTimeout,
             messagePayloadObjectMapper,
             DEFAULT_DURABLE_QUEUES_COLLECTION_NAME);
    }

    /**
     * Create {@link DurableQueues} custom messagePayloadObjectMapper and sharedQueueTableName
     *
     * @param transactionalMode          The transactional behaviour mode of this {@link MongoDurableQueues}
     * @param mongoTemplate              the {@link MongoTemplate} used
     * @param unitOfWorkFactory          the {@link UnitOfWorkFactory} needed to access the database
     * @param messageHandlingTimeout     Only relevant when using {@link TransactionalMode#ManualAcknowledgement} and defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                                   After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     * @param messagePayloadObjectMapper the {@link ObjectMapper} that is used to serialize/deserialize message payloads
     * @param sharedQueueCollectionName  the name of the collection that will contain all messages (across all {@link QueueName}'s)
     */
    protected MongoDurableQueues(TransactionalMode transactionalMode,
                                 MongoTemplate mongoTemplate,
                                 SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                                 Duration messageHandlingTimeout,
                                 ObjectMapper messagePayloadObjectMapper,
                                 String sharedQueueCollectionName) {
        this.transactionalMode = requireNonNull(transactionalMode, "No transactionalMode instance provided");
        this.mongoTemplate = requireNonNull(mongoTemplate, "No mongoTemplate instance provided");
        log.info("Using transactionalMode: {}", transactionalMode);
        switch (transactionalMode) {
            case FullyTransactional:
                this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory instance provided");
                break;
            case ManualAcknowledgement:
                this.messageHandlingTimeoutMs = (int) requireNonNull(messageHandlingTimeout, "No messageHandlingTimeout instance provided").toMillis();
                log.info("Using messageHandlingTimeout: {} seconds", messageHandlingTimeout);
                break;
        }
        this.messagePayloadObjectMapper = requireNonNull(messagePayloadObjectMapper, "No messagePayloadObjectMapper");
        this.sharedQueueCollectionName = requireNonNull(sharedQueueCollectionName, "No sharedQueueCollectionName provided").toLowerCase(Locale.ROOT);
        initializeQueueCollection();
    }

    /**
     * Override only if the default collection or index name approach doesn't work for you
     */
    protected void initializeQueueCollection() {
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
        var indexes = List.of(new Index(sharedQueueCollectionName + "_next_msg", Sort.Direction.ASC)
                                      .on("nextDeliveryTimestamp", Sort.Direction.ASC)
                                      .on("isDeadLetterMessage", Sort.Direction.ASC)
                                      .on("isBeingDelivered", Sort.Direction.ASC),
                              new Index(this.sharedQueueCollectionName + "_stuck_msgs", Sort.Direction.ASC)
                                      .on("deliveryTimestamp", Sort.Direction.ASC)
                                      .on("isBeingDelivered", Sort.Direction.ASC),
                              new Index(this.sharedQueueCollectionName + "_find_msg", Sort.Direction.ASC)
                                      .on("id", Sort.Direction.ASC)
                                      .on("isBeingDelivered", Sort.Direction.ASC),
                              new Index(this.sharedQueueCollectionName + "_resurrect_msg", Sort.Direction.ASC)
                                      .on("id", Sort.Direction.ASC)
                                      .on("isDeadLetterMessage", Sort.Direction.ASC));
        indexes.forEach(index -> {
            log.debug("Ensuring Index on Collection '{}': {}",
                      sharedQueueCollectionName,
                      index);
            mongoTemplate.indexOps(this.sharedQueueCollectionName)
                         .ensureIndex(index);
        });
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
            log.info("Starting");
            durableQueueConsumers.values().forEach(MongoDurableQueueConsumer::start);
            log.info("Started");
        }
    }

    @Override
    public void stop() {
        if (started) {
            log.info("Stopping");
            durableQueueConsumers.values().forEach(MongoDurableQueueConsumer::stop);
            started = false;
            log.info("Stopped");
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public TransactionalMode getTransactionalMode() {
        return transactionalMode;
    }

    @Override
    public Optional<UnitOfWorkFactory<? extends UnitOfWork>> getUnitOfWorkFactory() {
        return Optional.ofNullable(unitOfWorkFactory);
    }

    @Override
    public DurableQueues addInterceptor(DurableQueuesInterceptor interceptor) {
        requireNonNull(interceptor, "No interceptor provided");
        log.debug("Adding interceptor: {}", interceptor);
        interceptors.add(interceptor);
        return this;
    }

    @Override
    public DurableQueues removeInterceptor(DurableQueuesInterceptor interceptor) {
        requireNonNull(interceptor, "No interceptor provided");
        log.debug("Removing interceptor: {}", interceptor);
        interceptors.remove(interceptor);
        return this;
    }

    @Override
    public QueueEntryId queueMessage(QueueMessage operation) {
        requireNonNull(operation, "You must provide a QueueMessage instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> queueMessage(operation.queueName,
                                                                  operation.getPayload(),
                                                                  false,
                                                                  operation.getCauseOfEnqueuing(),
                                                                  operation.getDeliveryDelay()))
                .proceed();
    }

    @Override
    public QueueEntryId queueMessageAsDeadLetterMessage(QueueMessageAsDeadLetterMessage operation) {
        requireNonNull(operation, "You must provide a QueueMessageAsDeadLetterMessage instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> queueMessage(operation.queueName,
                                                                  operation.getPayload(),
                                                                  true,
                                                                  Optional.of(operation.getCauseOfError()),
                                                                  Optional.empty()))
                .proceed();
    }

    protected QueueEntryId queueMessage(QueueName queueName, Object payload, boolean isDeadLetterMessage, Optional<Exception> causeOfEnqueuing, Optional<Duration> deliveryDelay) {
        requireNonNull(queueName, "You must provide a queueName");
        requireNonNull(payload, "You must provide a payload");
        requireNonNull(causeOfEnqueuing, "You must provide a causeOfEnqueuing option");
        requireNonNull(deliveryDelay, "You must provide a deliveryDelay option");

        var queueEntryId          = QueueEntryId.random();
        var addedTimestamp        = Instant.now();
        var nextDeliveryTimestamp = isDeadLetterMessage ? null : addedTimestamp.plus(deliveryDelay.orElse(Duration.ZERO));
        log.debug("[{}] Queuing {}Message with entry-id {} and nextDeliveryTimestamp {}. TransactionalMode: {}",
                  queueName,
                  isDeadLetterMessage ? "Dead Letter " : "",
                  queueEntryId,
                  nextDeliveryTimestamp,
                  transactionalMode);
        if (transactionalMode == TransactionalMode.FullyTransactional) {
            unitOfWorkFactory.getRequiredUnitOfWork();
        }

        byte[] jsonPayload;
        try {
            jsonPayload = messagePayloadObjectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new DurableQueueException(e, queueName);
        }

        var message = new DurableQueuedMessage(queueEntryId,
                                               queueName,
                                               false,
                                               jsonPayload,
                                               payload.getClass().getName(),
                                               addedTimestamp,
                                               nextDeliveryTimestamp,
                                               null,
                                               0,
                                               0,
                                               causeOfEnqueuing.map(Exceptions::getStackTrace).orElse(null),
                                               isDeadLetterMessage);

        mongoTemplate.save(message, sharedQueueCollectionName);
        log.debug("[{}] Queued {}Message with entry-id {} and nextDeliveryTimestamp {}. TransactionalMode: {}",
                  queueName,
                  isDeadLetterMessage ? "Dead Letter " : "",
                  message.getId(),
                  nextDeliveryTimestamp,
                  transactionalMode);
        return message.getId();
    }

    @Override
    public Optional<QueuedMessage> getDeadLetterMessage(GetDeadLetterMessage operation) {
        requireNonNull(operation, "You must specify a GetDeadLetterMessage instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> getQueuedMessage(operation.queueEntryId, true))
                .proceed();
    }

    @Override
    public Optional<QueuedMessage> getQueuedMessage(GetQueuedMessage operation) {
        requireNonNull(operation, "You must specify a GetQueuedMessage instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> getQueuedMessage(operation.queueEntryId, false))
                .proceed();
    }

    protected Optional<QueuedMessage> getQueuedMessage(QueueEntryId queueEntryId, boolean isDeadLetterMessage) {
        var query = new Query(where("id").is(queueEntryId)
                                         .and("isDeadLetterMessage").is(isDeadLetterMessage));
        return Optional.ofNullable(mongoTemplate.findOne(query,
                                                         DurableQueuedMessage.class,
                                                         sharedQueueCollectionName))
                       .map(durableQueuedMessage -> durableQueuedMessage.setDeserializeMessagePayloadFunction(this::deserializeMessagePayload));
    }

    private Object deserializeMessagePayload(QueueName queueName, byte[] messagePayload, String messagePayloadType) {
        try {
            return messagePayloadObjectMapper.readValue(messagePayload, Classes.forName(messagePayloadType));
        } catch (IOException e) {
            throw new DurableQueueException(msg("Failed to deserialize message payload of type {}", messagePayloadType), e, queueName);
        }
    }

    @Override
    public List<QueueEntryId> queueMessages(QueueMessages operation) {
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
                                                   var payloads              = operation.getPayloads();
                                                   var addedTimestamp        = Instant.now();
                                                   var nextDeliveryTimestamp = addedTimestamp.plus(deliveryDelay.orElse(Duration.ZERO));


                                                   var messages = payloads.stream().map(payload -> {
                                                       byte[] jsonPayload;
                                                       try {
                                                           jsonPayload = messagePayloadObjectMapper.writeValueAsBytes(payload);
                                                       } catch (JsonProcessingException e) {
                                                           throw new DurableQueueException(e, queueName);
                                                       }
                                                       return new DurableQueuedMessage(QueueEntryId.random(),
                                                                                       queueName,
                                                                                       false,
                                                                                       jsonPayload,
                                                                                       payload.getClass().getName(),
                                                                                       addedTimestamp,
                                                                                       nextDeliveryTimestamp,
                                                                                       null,
                                                                                       0,
                                                                                       0,
                                                                                       null,
                                                                                       false);


                                                   }).collect(Collectors.toList());

                                                   var insertedEntryIds = mongoTemplate.insert(messages,
                                                                                               sharedQueueCollectionName)
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

    @Override
    public Optional<QueuedMessage> retryMessage(RetryMessage operation) {
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
                                                                            .set("lastDeliveryError", Exceptions.getStackTrace(operation.getCauseForRetry()));
                                                   var updateResult = mongoTemplate.findAndModify(findMessageToRetry,
                                                                                                  update,
                                                                                                  FindAndModifyOptions.options().returnNew(true),
                                                                                                  DurableQueuedMessage.class,
                                                                                                  sharedQueueCollectionName);
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
    public Optional<QueuedMessage> markAsDeadLetterMessage(MarkAsDeadLetterMessage operation) {
        requireNonNull(operation, "You must provide a MarkAsDeadLetterMessage instance");
        operation.validate();
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }


                                                   var queueEntryId = operation.queueEntryId;
                                                   var findMessageToMarkAsDeadLetterMessage = query(where("id").is(queueEntryId)
                                                                                                               .and("isBeingDelivered").is(true));

                                                   var update = new Update().inc("redeliveryAttempts", 1)
                                                                            .set("isBeingDelivered", false)
                                                                            .set("deliveryTimestamp", null)
                                                                            .set("isDeadLetterMessage", true)
                                                                            .set("lastDeliveryError", Exceptions.getStackTrace(operation.getCauseForBeingMarkedAsDeadLetter()));
                                                   var updateResult = mongoTemplate.findAndModify(findMessageToMarkAsDeadLetterMessage,
                                                                                                  update,
                                                                                                  FindAndModifyOptions.options().returnNew(true),
                                                                                                  DurableQueuedMessage.class,
                                                                                                  sharedQueueCollectionName);
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
    public Optional<QueuedMessage> resurrectDeadLetterMessage(ResurrectDeadLetterMessage operation) {
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
                                                                                                  sharedQueueCollectionName);
                                                   if (updateResult != null && !updateResult.isDeadLetterMessage) {
                                                       log.debug("[{}] Resurrected Dead Letter Message with id '{}' and nextDeliveryTimestamp: {}. Message entry after update: {}",
                                                                 updateResult.queueName,
                                                                 queueEntryId,
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
    public boolean acknowledgeMessageAsHandled(AcknowledgeMessageAsHandled operation) {
        requireNonNull(operation, "You must provide a AcknowledgeMessageAsHandled instance");

        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   log.debug("Acknowledging-Message-As-Handled regarding Message with id '{}'", operation.queueEntryId);
                                                   return deleteMessage(new DeleteMessage(operation.queueEntryId));
                                               })
                .proceed();

    }

    @Override
    public boolean deleteMessage(DeleteMessage operation) {
        requireNonNull(operation, "You must provide a DeleteMessage instance");

        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }


                                                   var queueEntryId    = operation.queueEntryId;
                                                   var messagesDeleted = mongoTemplate.remove(query(where("_id").is(queueEntryId.toString())), sharedQueueCollectionName).getDeletedCount();
                                                   if (messagesDeleted == 1) {
                                                       log.debug("Deleted Message with id '{}'", queueEntryId);
                                                       return true;
                                                   } else {
                                                       log.error("Failed to Delete Message with id '{}'", queueEntryId);
                                                       return false;
                                                   }
                                               }).proceed();
    }

    @Override
    public Optional<QueuedMessage> getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery operation) {
        requireNonNull(operation, "You must specify a GetNextMessageReadyForDelivery instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   log.trace("[{}] Performing GetNextMessageReadyForDelivery using transactionalMode: {}", operation.queueName, transactionalMode);
                                                   if (transactionalMode == TransactionalMode.FullyTransactional) {
                                                       unitOfWorkFactory.getRequiredUnitOfWork();
                                                   }

                                                   // Use a lock to ensure less WriteConflict if multiple competing threads a busy polling the same queue
                                                   var queueName = operation.queueName;
                                                   var lock      = localQueuePollLock.computeIfAbsent(queueName, queueName_ -> new ReentrantLock(true));
                                                   try {
                                                       if (!lock.tryLock(1, TimeUnit.SECONDS)) {
                                                           log.trace("[{}]Timed out waiting to acquire lock to poll for next message ready to be delivered", queueName);
                                                           return Optional.<QueuedMessage>empty();
                                                       }
                                                   } catch (InterruptedException e) {
                                                       return Optional.<QueuedMessage>empty();
                                                   }
                                                   try {
                                                       resetMessagesStuckBeingDelivered(queueName);

                                                       var nextMessageReadyForDeliveryQuery = query(where("queueName").is(queueName)
                                                                                                                      .and("nextDeliveryTimestamp").lte(Instant.now())
                                                                                                                      .and("isDeadLetterMessage").is(false)
                                                                                                                      .and("isBeingDelivered").is(false)
                                                                                                   ).limit(1)
                                                                                                    .with(Sort.by(Sort.Direction.ASC, "nextDeliveryTimestamp"));

                                                       var update = new Update().inc("totalDeliveryAttempts", 1)
                                                                                .set("isBeingDelivered", true)
                                                                                .set("deliveryTimestamp", Instant.now());

                                                       var updateResult = mongoTemplate.findAndModify(nextMessageReadyForDeliveryQuery,
                                                                                                      update,
                                                                                                      FindAndModifyOptions.options().returnNew(true),
                                                                                                      DurableQueuedMessage.class,
                                                                                                      sharedQueueCollectionName);
                                                       if (updateResult != null && updateResult.isBeingDelivered) {
                                                           log.debug("[{}] Found a message ready for delivery: {}", queueName, updateResult.id);
                                                           return Optional.of(updateResult)
                                                                          .map(durableQueuedMessage -> (QueuedMessage) durableQueuedMessage.setDeserializeMessagePayloadFunction(this::deserializeMessagePayload));

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
                                                       }
                                                       throw new DurableQueueException(msg("Failed to perform getNextMessageReadyForDelivery for queue '{}'", queueName), e, queueName);
                                                   } finally {
                                                       lock.unlock();
                                                   }
                                               }).proceed();
    }

    /**
     * This operation will scan for messages that has been marked as {@link DurableQueuedMessage#isBeingDelivered()} for longer
     * than {@link #messageHandlingTimeoutMs}<br>
     * All messages found will have {@link DurableQueuedMessage#isBeingDelivered()} and {@link DurableQueuedMessage#getDeliveryTimestamp()}
     * reset<br>
     * Only relevant for when using {@link TransactionalMode#ManualAcknowledgement}
     *
     * @param queueName the queue for which we're looking for messages stuck being marked as {@link DurableQueuedMessage#isBeingDelivered()}
     */
    protected void resetMessagesStuckBeingDelivered(QueueName queueName) {
        // Reset stuck messages
        if (transactionalMode == TransactionalMode.ManualAcknowledgement) {
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
                        .set("isBeingDelivered", false)
                        .set("deliveryTimestamp", null);

                var updateResult = mongoTemplate.updateMulti(stuckMessagesQuery,
                                                             update,
                                                             sharedQueueCollectionName);
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
    public boolean hasMessagesQueuedFor(QueueName queueName) {
        return getTotalMessagesQueuedFor(queueName) > 0;
    }

    @Override
    public long getTotalMessagesQueuedFor(GetTotalMessagesQueuedFor operation) {
        requireNonNull(operation, "You must specify a GetTotalMessagesQueuedFor instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> mongoTemplate.count(query(where("queueName").is(operation.queueName)
                                                                                                 .and("isDeadLetterMessage").is(false)),
                                                                         sharedQueueCollectionName))
                .proceed();
    }

    @Override
    public List<QueuedMessage> getQueuedMessages(GetQueuedMessages operation) {
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

    protected List<QueuedMessage> queryQueuedMessages(QueueName queueName, QueueingSortOrder queueingSortOrder, IncludeMessages includeMessages, long startIndex, long pageSize) {
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
                                  sharedQueueCollectionName)
                            .stream()
                            .map(durableQueuedMessage -> durableQueuedMessage.setDeserializeMessagePayloadFunction(this::deserializeMessagePayload))
                            .collect(Collectors.toList());
    }

    @Override
    public int purgeQueue(PurgeQueue operation) {
        requireNonNull(operation, "You must specify a PurgeQueue instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> (int) mongoTemplate.remove(query(where("queueName").is(operation.queueName)), sharedQueueCollectionName).getDeletedCount())
                .proceed();
    }

    @Override
    public DurableQueueConsumer consumeFromQueue(ConsumeFromQueue operation) {
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
                                                                                                                                                                        this::removeQueueConsumer)).proceed();
            if (started) {
                consumer.start();
            }
            return consumer;
        });
    }

    void removeQueueConsumer(DurableQueueConsumer durableQueueConsumer) {
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

    private static ObjectMapper createObjectMapper() {
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

        private String  lastDeliveryError;
        private boolean isDeadLetterMessage;

        private TripleFunction<QueueName, byte[], String, Object> deserializeMessagePayloadFunction;

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
                                    boolean isDeadLetterMessage) {
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
        }

        @Override
        public QueueEntryId getId() {
            return id;
        }

        @Override
        public QueueName getQueueName() {
            return queueName;
        }

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

        public Instant getDeliveryTimestamp() {
            return deliveryTimestamp;
        }

        public Object getPayload() {
            requireNonNull(deserializeMessagePayloadFunction, "Internal Error: deserializeMessagePayloadFunction is null");
            return deserializeMessagePayloadFunction.apply(queueName,
                                                           messagePayload,
                                                           messagePayloadType);
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
                    ", lastDeliveryError='" + lastDeliveryError + '\'' +
                    ", isDeadLetterMessage=" + isDeadLetterMessage +
                    '}';
        }

        public DurableQueuedMessage setDeserializeMessagePayloadFunction(TripleFunction<QueueName, byte[], String, Object> deserializeMessagePayloadFunction) {
            this.deserializeMessagePayloadFunction = requireNonNull(deserializeMessagePayloadFunction, "No deserializeMessagePayloadFunction provided");
            return this;
        }
    }
}
