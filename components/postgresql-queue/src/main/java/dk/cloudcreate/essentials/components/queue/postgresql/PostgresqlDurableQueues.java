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

package dk.cloudcreate.essentials.components.queue.postgresql;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.components.queue.postgresql.jdbi.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.shared.Exceptions;
import dk.cloudcreate.essentials.shared.reflection.Classes;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.NamedArgumentBinding.arg;
import static dk.cloudcreate.essentials.shared.MessageFormatter.*;
import static dk.cloudcreate.essentials.shared.interceptor.InterceptorChain.newInterceptorChainForOperation;

/**
 * Postgresql version of the {@link DurableQueues} concept.<br>
 * Works together with {@link UnitOfWorkFactory} in order to support queuing message together with business logic (such as failing to handle an Event, etc.)
 */
public class PostgresqlDurableQueues implements DurableQueues {
    private static final Logger log                               = LoggerFactory.getLogger(PostgresqlDurableQueues.class);
    public static final  String DEFAULT_DURABLE_QUEUES_TABLE_NAME = "durable_queues";

    private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
    private final ObjectMapper                                                  messagePayloadObjectMapper;
    private final String                                                        sharedQueueTableName;
    private final ConcurrentMap<QueueName, PostgresqlDurableQueueConsumer>      durableQueueConsumers = new ConcurrentHashMap<>();
    private final QueuedMessageRowMapper                                        queuedMessageMapper;
    private final List<DurableQueuesInterceptor>                                interceptors          = new ArrayList<>();

    private volatile boolean started;

    /**
     * Create {@link DurableQueues} with sharedQueueTableName: {@value DEFAULT_DURABLE_QUEUES_TABLE_NAME} and a default {@link ObjectMapper}
     * configuration
     *
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} needed to access the database
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
        this(unitOfWorkFactory,
             createObjectMapper(),
             DEFAULT_DURABLE_QUEUES_TABLE_NAME);
    }

    /**
     * Create {@link DurableQueues} with custom messagePayloadObjectMapper with sharedQueueTableName: {@value DEFAULT_DURABLE_QUEUES_TABLE_NAME}
     *
     * @param unitOfWorkFactory          the {@link UnitOfWorkFactory} needed to access the database
     * @param messagePayloadObjectMapper the {@link ObjectMapper} that is used to serialize/deserialize message payloads
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                   ObjectMapper messagePayloadObjectMapper) {
        this(unitOfWorkFactory,
             messagePayloadObjectMapper,
             DEFAULT_DURABLE_QUEUES_TABLE_NAME);
    }

    /**
     * Create {@link DurableQueues} with custom messagePayloadObjectMapper and sharedQueueTableName
     *
     * @param unitOfWorkFactory          the {@link UnitOfWorkFactory} needed to access the database
     * @param messagePayloadObjectMapper the {@link ObjectMapper} that is used to serialize/deserialize message payloads
     * @param sharedQueueTableName       the name of the table that will contain all messages (across all {@link QueueName}'s)
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                   ObjectMapper messagePayloadObjectMapper,
                                   String sharedQueueTableName) {
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory instance provided");
        this.messagePayloadObjectMapper = requireNonNull(messagePayloadObjectMapper, "No messagePayloadObjectMapper");
        this.sharedQueueTableName = requireNonNull(sharedQueueTableName, "No sharedQueueTableName provided").toLowerCase(Locale.ROOT);
        queuedMessageMapper = new QueuedMessageRowMapper();
        initializeQueueTables();
    }

    private void initializeQueueTables() {
        unitOfWorkFactory.usingUnitOfWork(handleAwareUnitOfWork -> {
            handleAwareUnitOfWork.handle().getJdbi().registerArgument(new QueueNameArgumentFactory());
            handleAwareUnitOfWork.handle().getJdbi().registerColumnMapper(new QueueNameColumnMapper());
            handleAwareUnitOfWork.handle().getJdbi().registerArgument(new QueueEntryIdArgumentFactory());
            handleAwareUnitOfWork.handle().getJdbi().registerColumnMapper(new QueueEntryIdColumnMapper());
            var numberOfUpdates = handleAwareUnitOfWork.handle().execute(bind("CREATE TABLE IF NOT EXISTS {:tableName} (\n" +
                                                                                      "  id                     TEXT PRIMARY KEY,\n" +
                                                                                      "  queue_name             TEXT NOT NULL,\n" +
                                                                                      "  message_payload        JSONB NOT NULL,\n" +
                                                                                      "  message_payload_type   TEXT NOT NULL,\n" +
                                                                                      "  added_ts               TIMESTAMPTZ NOT NULL,\n" +
                                                                                      "  next_delivery_ts       TIMESTAMPTZ,\n" +
                                                                                      "  total_attempts         INTEGER DEFAULT 0,\n" +
                                                                                      "  redelivery_attempts    INTEGER DEFAULT 0,\n" +
                                                                                      "  last_delivery_error    TEXT DEFAULT NULL,\n" +
                                                                                      "  is_dead_letter_message BOOLEAN NOT NULL DEFAULT FALSE\n" +
                                                                                      ")",
                                                                              arg("tableName", sharedQueueTableName))
                                                                        );
            log.info("Durable Queues table '{}' {}", sharedQueueTableName, numberOfUpdates == 1 ? "created" : "already existed");

            var indexName = sharedQueueTableName + "queue_name__next_delivery__id__index";
            numberOfUpdates = handleAwareUnitOfWork.handle().execute(bind("CREATE INDEX IF NOT EXISTS {:indexName} ON {:tableName} (\n" +
                                                                                  "    queue_name, next_delivery_ts, id DESC\n" +
                                                                                  ")",
                                                                          arg("indexName", indexName),
                                                                          arg("tableName", sharedQueueTableName))
                                                                    );
            log.info("Durable Queues index '{}' {}", indexName, numberOfUpdates == 1 ? "created" : "already existed");
        });
    }

    public List<DurableQueuesInterceptor> getInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
            log.info("Starting");
            durableQueueConsumers.values().forEach(PostgresqlDurableQueueConsumer::start);
            log.info("Started");
        }
    }

    @Override
    public void stop() {
        if (started) {
            log.info("Stopping");
            durableQueueConsumers.values().forEach(PostgresqlDurableQueueConsumer::stop);
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
        return TransactionalMode.FullyTransactional;
    }

    @Override
    public Optional<UnitOfWorkFactory<? extends UnitOfWork>> getUnitOfWorkFactory() {
        return Optional.ofNullable(unitOfWorkFactory);
    }

    @Override
    public DurableQueueConsumer consumeFromQueue(ConsumeFromQueue operation) {
        requireNonNull(operation, "No operation provided");
        if (durableQueueConsumers.containsKey(operation.queueName)) {
            throw new DurableQueueException("There is already an DurableConsumer for this queue", operation.queueName);
        }
        operation.validate();
        return durableQueueConsumers.computeIfAbsent(operation.queueName, _queueName -> {
            PostgresqlDurableQueueConsumer consumer = (PostgresqlDurableQueueConsumer) newInterceptorChainForOperation(operation,
                                                                                                                       interceptors,
                                                                                                                       (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                                                                                                       () -> (DurableQueueConsumer) new PostgresqlDurableQueueConsumer(operation,
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
                                            () -> (DurableQueueConsumer) durableQueueConsumers.remove(durableQueueConsumer.queueName()))
                    .proceed();
        } catch (Exception e) {
            log.error(msg("Failed to perform {}", operation), e);
        }
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
        log.debug("[{}] Queuing {}Message with entry-id {} and nextDeliveryTimestamp {}",
                  queueName,
                  isDeadLetterMessage ? "Dead Letter " : "",
                  queueEntryId,
                  nextDeliveryTimestamp);

        String jsonPayload;
        try {
            jsonPayload = messagePayloadObjectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new DurableQueueException(e, queueName);
        }
        var update = unitOfWorkFactory.getRequiredUnitOfWork().handle().createUpdate(bind("INSERT INTO {:tableName} (\n" +
                                                                                                  "       id,\n" +
                                                                                                  "       queue_name,\n" +
                                                                                                  "       message_payload,\n" +
                                                                                                  "       message_payload_type,\n" +
                                                                                                  "       added_ts,\n" +
                                                                                                  "       next_delivery_ts,\n" +
                                                                                                  "       last_delivery_error,\n" +
                                                                                                  "       is_dead_letter_message\n" +
                                                                                                  "   ) VALUES (\n" +
                                                                                                  "       :id,\n" +
                                                                                                  "       :queueName,\n" +
                                                                                                  "       :message_payload::jsonb,\n" +
                                                                                                  "       :message_payload_type,\n" +
                                                                                                  "       :addedTimestamp,\n" +
                                                                                                  "       :nextDeliveryTimestamp,\n" +
                                                                                                  "       :lastDeliveryError,\n" +
                                                                                                  "       :isDeadLetterMessage\n" +
                                                                                                  "   )",
                                                                                          arg("tableName", sharedQueueTableName)))
                                      .bind("id", queueEntryId)
                                      .bind("queueName", queueName)
                                      .bind("message_payload", jsonPayload)
                                      .bind("message_payload_type", payload.getClass().getName())
                                      .bind("addedTimestamp", addedTimestamp)
                                      .bind("nextDeliveryTimestamp", nextDeliveryTimestamp)
                                      .bind("isDeadLetterMessage", isDeadLetterMessage);

        if (causeOfEnqueuing.isPresent()) {
            update.bind("lastDeliveryError", causeOfEnqueuing.map(Exceptions::getStackTrace).get());
        } else {
            update.bindNull("lastDeliveryError", Types.VARCHAR);
        }

        var numberOfRowsUpdated = update.execute();
        if (numberOfRowsUpdated == 0) {
            throw new DurableQueueException("Failed to insert message", queueName);
        }
        log.debug("[{}] Queued {}Message with entry-id {} and nextDeliveryTimestamp {}",
                  queueName,
                  isDeadLetterMessage ? "Dead Letter " : "",
                  queueEntryId,
                  nextDeliveryTimestamp);
        return queueEntryId;
    }

    @Override
    public List<QueueEntryId> queueMessages(QueueMessages operation) {
        requireNonNull(operation, "You must provide a QueueMessages instance");
        operation.validate();

        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> {
                                                   var queueName             = operation.getQueueName();
                                                   var deliveryDelay         = operation.getDeliveryDelay();
                                                   var payloads              = operation.getPayloads();
                                                   var addedTimestamp        = OffsetDateTime.now(Clock.systemUTC());
                                                   var nextDeliveryTimestamp = addedTimestamp.plus(deliveryDelay.orElse(Duration.ZERO));

                                                   var batch = unitOfWorkFactory.getRequiredUnitOfWork().handle().prepareBatch(bind("INSERT INTO {:tableName} (\n" +
                                                                                                                                            "       id,\n" +
                                                                                                                                            "       queue_name,\n" +
                                                                                                                                            "       message_payload,\n" +
                                                                                                                                            "       message_payload_type,\n" +
                                                                                                                                            "       added_ts,\n" +
                                                                                                                                            "       next_delivery_ts,\n" +
                                                                                                                                            "       last_delivery_error,\n" +
                                                                                                                                            "       is_dead_letter_message\n" +
                                                                                                                                            "   ) VALUES (\n" +
                                                                                                                                            "       :id,\n" +
                                                                                                                                            "       :queueName,\n" +
                                                                                                                                            "       :message_payload::jsonb,\n" +
                                                                                                                                            "       :message_payload_type,\n" +
                                                                                                                                            "       :addedTimestamp,\n" +
                                                                                                                                            "       :nextDeliveryTimestamp,\n" +
                                                                                                                                            "       :lastDeliveryError,\n" +
                                                                                                                                            "       :isDeadLetterMessage\n" +
                                                                                                                                            "   )",
                                                                                                                                    arg("tableName", sharedQueueTableName)));
                                                   var queueEntryIds = payloads.stream().map(payload -> {
                                                       String jsonPayload;
                                                       try {
                                                           jsonPayload = messagePayloadObjectMapper.writeValueAsString(payload);
                                                       } catch (JsonProcessingException e) {
                                                           throw new DurableQueueException(e, queueName);
                                                       }
                                                       var queueEntryId = QueueEntryId.random();
                                                       batch.bind("id", queueEntryId)
                                                            .bind("queueName", queueName)
                                                            .bind("message_payload", jsonPayload)
                                                            .bind("message_payload_type", payload.getClass().getName())
                                                            .bind("addedTimestamp", addedTimestamp)
                                                            .bind("nextDeliveryTimestamp", nextDeliveryTimestamp)
                                                            .bind("isDeadLetterMessage", false)
                                                            .bindNull("lastDeliveryError", Types.VARCHAR);
                                                       batch.add();
                                                       return queueEntryId;
                                                   }).collect(Collectors.toList());

                                                   var numberOfRowsUpdated = Arrays.stream(batch.execute())
                                                                                   .reduce(Integer::sum).orElse(0);
                                                   if (numberOfRowsUpdated != payloads.size()) {
                                                       throw new DurableQueueException(msg("Attempted to queue {} messages but only inserted {} messages", payloads.size(), numberOfRowsUpdated),
                                                                                       queueName);
                                                   }

                                                   log.debug("[{}] Queued {} Messages with nextDeliveryTimestamp {} and entry-id's: {}",
                                                             queueName,
                                                             payloads.size(),
                                                             nextDeliveryTimestamp,
                                                             queueEntryIds);
                                                   return queueEntryIds;
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
                                                   var nextDeliveryTimestamp = OffsetDateTime.now(Clock.systemUTC()).plus(operation.getDeliveryDelay());
                                                   var result = unitOfWorkFactory.getRequiredUnitOfWork().handle().createQuery(bind("UPDATE {:tableName} SET\n" +
                                                                                                                                            "     next_delivery_ts = :nextDeliveryTimestamp,\n" +
                                                                                                                                            "     last_delivery_error = :lastDeliveryError,\n" +
                                                                                                                                            "     redelivery_attempts = redelivery_attempts + 1\n" +
                                                                                                                                            " WHERE id = :id\n" +
                                                                                                                                            " RETURNING *",
                                                                                                                                    arg("tableName", sharedQueueTableName)))
                                                                                 .bind("nextDeliveryTimestamp", nextDeliveryTimestamp)
                                                                                 .bind("lastDeliveryError", Exceptions.getStackTrace(operation.getCauseForRetry()))
                                                                                 .bind("id", operation.queueEntryId)
                                                                                 .map(queuedMessageMapper)
                                                                                 .findOne();
                                                   if (result.isPresent()) {
                                                       log.debug("Marked Message with id '{}' for Retry at {}. Message entry after update: {}", operation.queueEntryId, nextDeliveryTimestamp, result.get());
                                                       return result;
                                                   } else {
                                                       log.error("Failed to Mark Message with id '{}' for Retry", operation.queueEntryId);
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
                                                   var result = unitOfWorkFactory.getRequiredUnitOfWork().handle().createQuery(bind("UPDATE {:tableName} SET\n" +
                                                                                                                                            "     next_delivery_ts = NULL,\n" +
                                                                                                                                            "     last_delivery_error = :lastDeliveryError,\n" +
                                                                                                                                            "     redelivery_attempts = redelivery_attempts + 1,\n" +
                                                                                                                                            "     is_dead_letter_message = TRUE\n" +
                                                                                                                                            " WHERE id = :id AND is_dead_letter_message = FALSE\n" +
                                                                                                                                            " RETURNING *",
                                                                                                                                    arg("tableName", sharedQueueTableName)))
                                                                                 .bind("lastDeliveryError", Exceptions.getStackTrace(operation.getCauseForBeingMarkedAsDeadLetter()))
                                                                                 .bind("id", operation.queueEntryId)
                                                                                 .map(queuedMessageMapper)
                                                                                 .findOne();
                                                   if (result.isPresent()) {
                                                       log.debug("Marked message with id '{}' as Dead Letter Message. Message entry after update: {}", operation.queueEntryId, result.get());
                                                       return result;
                                                   } else {
                                                       log.error("Failed to Mark as Message message with id '{}' as Dead Letter Message", operation.queueEntryId);
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
                                                   var nextDeliveryTimestamp = OffsetDateTime.now(Clock.systemUTC()).plus(operation.getDeliveryDelay());
                                                   var result = unitOfWorkFactory.getRequiredUnitOfWork().handle().createQuery(bind("UPDATE {:tableName} SET\n" +
                                                                                                                                            "     next_delivery_ts = :nextDeliveryTimestamp,\n" +
                                                                                                                                            "     is_dead_letter_message = FALSE\n" +
                                                                                                                                            " WHERE id = :id AND is_dead_letter_message = TRUE\n" +
                                                                                                                                            " RETURNING *",
                                                                                                                                    arg("tableName", sharedQueueTableName)))
                                                                                 .bind("nextDeliveryTimestamp", nextDeliveryTimestamp)
                                                                                 .bind("id", operation.queueEntryId)
                                                                                 .map(queuedMessageMapper)
                                                                                 .findOne();
                                                   if (result.isPresent()) {
                                                       log.debug("Resurrected Dead Letter Message with id '{}'. Message entry after update: {}", operation.queueEntryId, result.get());
                                                       return result;
                                                   } else {
                                                       log.error("Failed to resurrect Dead Letter Message with id '{}'", operation.queueEntryId);
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
                                                   var rowsUpdated = unitOfWorkFactory.getRequiredUnitOfWork().handle().createUpdate(bind("DELETE FROM {:tableName} WHERE id = :id",
                                                                                                                                          arg("tableName", sharedQueueTableName)))
                                                                                      .bind("id", operation.queueEntryId)
                                                                                      .execute();
                                                   if (rowsUpdated == 1) {
                                                       log.debug("Deleted Message with id '{}'", operation.queueEntryId);
                                                       return true;
                                                   } else {
                                                       log.error("Failed to Delete Message with id '{}'", operation.queueEntryId);
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
                                                   var now = OffsetDateTime.now(Clock.systemUTC());
                                                   return unitOfWorkFactory.getRequiredUnitOfWork().handle().createQuery(bind("WITH queued_message_ready_for_delivery AS (\n" +
                                                                                                                                      "    SELECT id FROM {:tableName} \n" +
                                                                                                                                      "    WHERE\n" +
                                                                                                                                      "        queue_name = :queueName AND\n" +
                                                                                                                                      "        next_delivery_ts <= :now AND\n" +
                                                                                                                                      "        is_dead_letter_message = FALSE\n" +
                                                                                                                                      "    ORDER BY next_delivery_ts ASC\n" +
                                                                                                                                      "    LIMIT 1\n" +
                                                                                                                                      "    FOR UPDATE SKIP LOCKED\n" +
                                                                                                                                      " )\n" +
                                                                                                                                      " UPDATE {:tableName} queued_message SET\n" +
                                                                                                                                      "    total_attempts = total_attempts + 1,\n" +
                                                                                                                                      "    next_delivery_ts = NULL\n" +
                                                                                                                                      " FROM queued_message_ready_for_delivery\n" +
                                                                                                                                      " WHERE queued_message.id = queued_message_ready_for_delivery.id\n" +
                                                                                                                                      " RETURNING\n" +
                                                                                                                                      "     queued_message.id,\n" +
                                                                                                                                      "     queued_message.queue_name,\n" +
                                                                                                                                      "     queued_message.message_payload,\n" +
                                                                                                                                      "     queued_message.message_payload_type,\n" +
                                                                                                                                      "     queued_message.added_ts,\n" +
                                                                                                                                      "     queued_message.next_delivery_ts,\n" +
                                                                                                                                      "     queued_message.last_delivery_error,\n" +
                                                                                                                                      "     queued_message.total_attempts,\n" +
                                                                                                                                      "     queued_message.redelivery_attempts,\n" +
                                                                                                                                      "     queued_message.is_dead_letter_message",
                                                                                                                              arg("tableName", sharedQueueTableName)))
                                                                           .bind("queueName", operation.queueName)
                                                                           .bind("now", now)
                                                                           .map(queuedMessageMapper)
                                                                           .findOne();
                                               }).proceed();
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
                                               () -> unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> handleAwareUnitOfWork.handle().createQuery(bind("SELECT count(*) FROM {:tableName} \n" +
                                                                                                                                                                       " WHERE \n" +
                                                                                                                                                                       "    queue_name = :queueName AND\n" +
                                                                                                                                                                       "    is_dead_letter_message = FALSE",
                                                                                                                                                               arg("tableName", sharedQueueTableName)))
                                                                                                                                    .bind("queueName", operation.queueName)
                                                                                                                                    .mapTo(Long.class)
                                                                                                                                    .one()))
                .proceed();
    }

    @Override
    public int purgeQueue(PurgeQueue operation) {
        requireNonNull(operation, "You must specify a PurgeQueue instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> handleAwareUnitOfWork.handle().createUpdate(bind("DELETE FROM {:tableName} WHERE queue_name = :queueName",
                                                                                                                                                                arg("tableName", sharedQueueTableName)))
                                                                                                                                    .bind("queueName", operation.queueName)
                                                                                                                                    .execute()))
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

        Supplier<String> resolveIncludeMessagesSql = () -> {
            switch (includeMessages) {
                case ALL:
                    return "";
                case DEAD_LETTER_MESSAGES:
                    return "AND is_dead_letter_message = TRUE\n";
                case QUEUED_MESSAGES:
                    return "AND is_dead_letter_message = FALSE\n";
                default:
                    throw new IllegalArgumentException("Unsupported IncludeMessages value: " + includeMessages);
            }
        };

        return unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> handleAwareUnitOfWork.handle().createQuery(bind("SELECT * FROM {:tableName} \n" +
                                                                                                                                 " WHERE queue_name = :queueName\n" +
                                                                                                                                 "{:includeMessages}" +
                                                                                                                                 " LIMIT :pageSize \n" +
                                                                                                                                 " OFFSET :offset",
                                                                                                                         arg("tableName", sharedQueueTableName),
                                                                                                                         arg("includeMessages", resolveIncludeMessagesSql.get())))
                                                                                              .bind("queueName", requireNonNull(queueName, "No QueueName provided"))
                                                                                              .bind("offset", startIndex)
                                                                                              .bind("pageSize", pageSize)
                                                                                              .map(queuedMessageMapper)
                                                                                              .list());
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

    private Optional<QueuedMessage> getQueuedMessage(QueueEntryId queueEntryId, boolean isDeadLetterMessage) {
        return unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> handleAwareUnitOfWork.handle().createQuery(bind("SELECT * FROM {:tableName} WHERE \n" +
                                                                                                                                 " id = :id AND\n" +
                                                                                                                                 " is_dead_letter_message = :isDeadLetterMessage",
                                                                                                                         arg("tableName", sharedQueueTableName)))
                                                                                              .bind("id", requireNonNull(queueEntryId, "No queueEntryId provided"))
                                                                                              .bind("isDeadLetterMessage", isDeadLetterMessage)
                                                                                              .map(queuedMessageMapper)
                                                                                              .findOne());
    }

    private Object deserializedMessagePayload(QueueName queueName, String messagePayload, String messagePayloadType) {
        requireNonNull(queueName, "No queueName provided");
        requireNonNull(messagePayload, "No messagePayload provided");
        requireNonNull(messagePayloadType, "No messagePayloadType provided");
        try {
            return messagePayloadObjectMapper.readValue(messagePayload, Classes.forName(messagePayloadType));
        } catch (JsonProcessingException e) {
            throw new DurableQueueException(msg("Failed to deserialize message payload of type {}", messagePayloadType), e, queueName);
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

    private class QueuedMessageRowMapper implements RowMapper<QueuedMessage> {
        public QueuedMessageRowMapper() {
        }

        @Override
        public QueuedMessage map(ResultSet rs, StatementContext ctx) throws SQLException {
            var queueName = QueueName.of(rs.getString("queue_name"));
            return new DefaultQueuedMessage(QueueEntryId.of(rs.getString("id")),
                                            queueName,
                                            PostgresqlDurableQueues.this.deserializedMessagePayload(queueName, rs.getString("message_payload"), rs.getString("message_payload_type")),
                                            rs.getObject("added_ts", OffsetDateTime.class),
                                            rs.getObject("next_delivery_ts", OffsetDateTime.class),
                                            rs.getString("last_delivery_error"),
                                            rs.getInt("total_attempts"),
                                            rs.getInt("redelivery_attempts"),
                                            rs.getBoolean("is_dead_letter_message"));
        }
    }
}
