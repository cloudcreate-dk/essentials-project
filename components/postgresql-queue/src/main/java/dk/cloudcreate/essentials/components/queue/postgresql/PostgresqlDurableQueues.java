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

package dk.cloudcreate.essentials.components.queue.postgresql;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.components.queue.postgresql.jdbi.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.shared.Exceptions;
import dk.cloudcreate.essentials.shared.collections.Lists;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;
import dk.cloudcreate.essentials.shared.reflection.Classes;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.NamedArgumentBinding.arg;
import static dk.cloudcreate.essentials.shared.MessageFormatter.*;
import static dk.cloudcreate.essentials.shared.interceptor.DefaultInterceptorChain.sortInterceptorsByOrder;
import static dk.cloudcreate.essentials.shared.interceptor.InterceptorChain.newInterceptorChainForOperation;

/**
 * Postgresql version of the {@link DurableQueues} concept.<br>
 * Works together with {@link UnitOfWorkFactory} in order to support queuing message together with business logic (such as failing to handle an Event, etc.)
 */
public class PostgresqlDurableQueues implements DurableQueues {
    private static final Logger log                               = LoggerFactory.getLogger(PostgresqlDurableQueues.class);
    public static final  String DEFAULT_DURABLE_QUEUES_TABLE_NAME = "durable_queues";
    private static final Object NO_PAYLOAD                        = new Object();

    private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
    private final JSONSerializer                                                jsonSerializer;
    private final String                                                        sharedQueueTableName;
    private final ConcurrentMap<QueueName, PostgresqlDurableQueueConsumer>      durableQueueConsumers = new ConcurrentHashMap<>();
    private final QueuedMessageRowMapper                                        queuedMessageMapper;
    private final List<DurableQueuesInterceptor>                                interceptors          = new ArrayList<>();
    private final Optional<MultiTableChangeListener<TableChangeNotification>>   multiTableChangeListener;
    private final Function<ConsumeFromQueue, QueuePollingOptimizer>             queuePollingOptimizerFactory;


    private final TransactionalMode                 transactionalMode;
    /**
     * Only used if {@link #transactionalMode} has value {@link TransactionalMode#SingleOperationTransaction}
     */
    private       int                               messageHandlingTimeoutMs;
    /**
     * Contains the timestamp of the last performed {@link #resetMessagesStuckBeingDelivered(QueueName)} check<br>
     * Only used if {@link #transactionalMode} has value {@link TransactionalMode#SingleOperationTransaction}
     */
    protected     ConcurrentMap<QueueName, Instant> lastResetStuckMessagesCheckTimestamps = new ConcurrentHashMap<>();


    private volatile boolean started;

    public static PostgresqlDurableQueuesBuilder builder() {
        return new PostgresqlDurableQueuesBuilder();
    }

    /**
     * Create {@link DurableQueues} with sharedQueueTableName: {@value DEFAULT_DURABLE_QUEUES_TABLE_NAME} and the default {@link JacksonJSONSerializer} using {@link #createDefaultObjectMapper()}
     * configuration
     *
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} needed to access the database
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
        this(unitOfWorkFactory,
             new JacksonJSONSerializer(createDefaultObjectMapper()),
             DEFAULT_DURABLE_QUEUES_TABLE_NAME,
             null,
             null);
    }

    /**
     * Create {@link DurableQueues} with sharedQueueTableName: {@value DEFAULT_DURABLE_QUEUES_TABLE_NAME} and the default {@link JacksonJSONSerializer} using {@link #createDefaultObjectMapper()}
     * configuration
     *
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                   Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(unitOfWorkFactory,
             new JacksonJSONSerializer(createDefaultObjectMapper()),
             DEFAULT_DURABLE_QUEUES_TABLE_NAME,
             null,
             queuePollingOptimizerFactory);
    }

    /**
     * Create {@link DurableQueues} with custom jsonSerializer with sharedQueueTableName: {@value DEFAULT_DURABLE_QUEUES_TABLE_NAME}
     *
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} needed to access the database
     * @param jsonSerializer    the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                   JSONSerializer jsonSerializer) {
        this(unitOfWorkFactory,
             jsonSerializer,
             DEFAULT_DURABLE_QUEUES_TABLE_NAME,
             null,
             null);
    }

    /**
     * Create {@link DurableQueues} with custom jsonSerializer with sharedQueueTableName: {@value DEFAULT_DURABLE_QUEUES_TABLE_NAME}
     *
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                   JSONSerializer jsonSerializer,
                                   Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(unitOfWorkFactory,
             jsonSerializer,
             DEFAULT_DURABLE_QUEUES_TABLE_NAME,
             null,
             queuePollingOptimizerFactory);
    }

    /**
     * Create {@link DurableQueues} with custom jsonSerializer and sharedQueueTableName
     *
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param sharedQueueTableName         the name of the table that will contain all messages (across all {@link QueueName}'s)
     * @param multiTableChangeListener     optional {@link MultiTableChangeListener} that allows {@link PostgresqlDurableQueues} to use {@link QueuePollingOptimizer}
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                   JSONSerializer jsonSerializer,
                                   String sharedQueueTableName,
                                   MultiTableChangeListener<TableChangeNotification> multiTableChangeListener,
                                   Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this(unitOfWorkFactory,
             jsonSerializer,
             sharedQueueTableName,
             multiTableChangeListener,
             queuePollingOptimizerFactory,
             TransactionalMode.FullyTransactional,
             null);
    }

    /**
     * Create {@link DurableQueues} with custom jsonSerializer and sharedQueueTableName
     *
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param sharedQueueTableName         the name of the table that will contain all messages (across all {@link QueueName}'s)
     * @param multiTableChangeListener     optional {@link MultiTableChangeListener} that allows {@link PostgresqlDurableQueues} to use {@link QueuePollingOptimizer}
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link #createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     * @param transactionalMode            The {@link TransactionalMode} for this {@link DurableQueues} instance. If set to {@link TransactionalMode#SingleOperationTransaction}
     *                                     then the consumer MUST call the {@link DurableQueues#acknowledgeMessageAsHandled(AcknowledgeMessageAsHandled)} explicitly in a new {@link UnitOfWork}
     * @param messageHandlingTimeout       Only required if <code>transactionalMode</code> is {@link TransactionalMode#SingleOperationTransaction}.<br>
     *                                     The parameter defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                                     After this timeout the message delivery will be reset and the message will again be a candidate for delivery
     */
    public PostgresqlDurableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                   JSONSerializer jsonSerializer,
                                   String sharedQueueTableName,
                                   MultiTableChangeListener<TableChangeNotification> multiTableChangeListener,
                                   Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory,
                                   TransactionalMode transactionalMode,
                                   Duration messageHandlingTimeout) {
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory instance provided");
        this.jsonSerializer = requireNonNull(jsonSerializer, "No jsonSerializer");
        this.sharedQueueTableName = requireNonNull(sharedQueueTableName, "No sharedQueueTableName provided").toLowerCase(Locale.ROOT);
        this.queuedMessageMapper = new QueuedMessageRowMapper();
        this.multiTableChangeListener = Optional.ofNullable(multiTableChangeListener);
        this.queuePollingOptimizerFactory = queuePollingOptimizerFactory != null ? queuePollingOptimizerFactory : this::createQueuePollingOptimizerFor;
        this.transactionalMode = requireNonNull(transactionalMode, "No transactionalMode instance provided");
        if (transactionalMode == TransactionalMode.SingleOperationTransaction) {
            messageHandlingTimeoutMs = (int) requireNonNull(messageHandlingTimeout, "No messageHandlingTimeout provided").toMillis();
            addInterceptor(new SingleOperationTransactionDurableQueuesInterceptor(unitOfWorkFactory));
        }

        initializeQueueTables();
    }

    private void initializeQueueTables() {
        unitOfWorkFactory.usingUnitOfWork(handleAwareUnitOfWork -> {
            handleAwareUnitOfWork.handle().getJdbi().registerArgument(new QueueNameArgumentFactory());
            handleAwareUnitOfWork.handle().getJdbi().registerColumnMapper(new QueueNameColumnMapper());
            handleAwareUnitOfWork.handle().getJdbi().registerArgument(new QueueEntryIdArgumentFactory());
            handleAwareUnitOfWork.handle().getJdbi().registerColumnMapper(new QueueEntryIdColumnMapper());
            handleAwareUnitOfWork.handle().execute(bind("CREATE TABLE IF NOT EXISTS {:tableName} (\n" +
                                                                "  id                     TEXT PRIMARY KEY,\n" +
                                                                "  queue_name             TEXT NOT NULL,\n" +
                                                                "  message_payload        JSONB NOT NULL,\n" +
                                                                "  message_payload_type   TEXT NOT NULL,\n" +
                                                                "  added_ts               TIMESTAMPTZ NOT NULL,\n" +
                                                                "  next_delivery_ts       TIMESTAMPTZ,\n" +
                                                                "  delivery_ts            TIMESTAMPTZ DEFAULT NULL,\n" +
                                                                "  total_attempts         INTEGER DEFAULT 0,\n" +
                                                                "  redelivery_attempts    INTEGER DEFAULT 0,\n" +
                                                                "  last_delivery_error    TEXT DEFAULT NULL,\n" +
                                                                "  is_being_delivered     BOOLEAN DEFAULT FALSE,\n" +
                                                                "  is_dead_letter_message BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                                "  meta_data              JSONB DEFAULT NULL,\n" +
                                                                "  delivery_mode          TEXT DEFAULT '" + QueuedMessage.DeliveryMode.NORMAL.name() + "',\n" +
                                                                "  key                    TEXT DEFAULT NULL,\n" +
                                                                "  key_order              BIGINT DEFAULT -1\n" +
                                                                ")",
                                                        arg("tableName", sharedQueueTableName))
                                                  );
            log.info("Ensured Durable Queues table '{}' exists", sharedQueueTableName);
            handleAwareUnitOfWork.handle().execute(bind("ALTER TABLE {:tableName} ADD COLUMN IF NOT EXISTS meta_data JSONB DEFAULT NULL",
                                                        arg("tableName", sharedQueueTableName))
                                                  );
            log.info("Ensured 'meta_data' column exists in Durable Queues table '{}'", sharedQueueTableName);

            handleAwareUnitOfWork.handle().execute(bind("ALTER TABLE {:tableName} ADD COLUMN IF NOT EXISTS delivery_mode TEXT DEFAULT 'NORMAL'",
                                                        arg("tableName", sharedQueueTableName))
                                                  );
            log.info("Ensured 'delivery_mode' column exists in Durable Queues table '{}'", sharedQueueTableName);

            handleAwareUnitOfWork.handle().execute(bind("ALTER TABLE {:tableName} ADD COLUMN IF NOT EXISTS key TEXT DEFAULT NULL",
                                                        arg("tableName", sharedQueueTableName))
                                                  );
            log.info("Ensured 'key' column exists in Durable Queues table '{}'", sharedQueueTableName);

            handleAwareUnitOfWork.handle().execute(bind("ALTER TABLE {:tableName} ADD COLUMN IF NOT EXISTS key_order BIGINT DEFAULT -1",
                                                        arg("tableName", sharedQueueTableName))
                                                  );
            log.info("Ensured 'key_order' column exists in Durable Queues table '{}'", sharedQueueTableName);

            handleAwareUnitOfWork.handle().execute(bind("ALTER TABLE {:tableName} ADD COLUMN IF NOT EXISTS is_being_delivered BOOLEAN DEFAULT FALSE",
                                                        arg("tableName", sharedQueueTableName))
                                                  );
            log.info("Ensured 'is_being_delivered' column exists in Durable Queues table '{}'", sharedQueueTableName);

            handleAwareUnitOfWork.handle().execute(bind("ALTER TABLE {:tableName} ADD COLUMN IF NOT EXISTS delivery_ts TIMESTAMPTZ DEFAULT NULL",
                                                        arg("tableName", sharedQueueTableName))
                                                  );
            log.info("Ensured 'delivery_ts' column exists in Durable Queues table '{}'", sharedQueueTableName);


            var oldNextDeliveryIndexName = sharedQueueTableName + "queue_name__next_delivery__id__index";
            handleAwareUnitOfWork.handle().execute(bind("DROP INDEX IF EXISTS {:indexName}",
                                                        arg("indexName", oldNextDeliveryIndexName))
                                                  );

            createIndex("CREATE INDEX IF NOT EXISTS idx_{:tableName}_queue_name ON {:tableName} (queue_name)",
                        handleAwareUnitOfWork.handle());
            createIndex("CREATE INDEX IF NOT EXISTS idx_{:tableName}_next_delivery_ts ON {:tableName} (next_delivery_ts)",
                        handleAwareUnitOfWork.handle());
            createIndex("CREATE INDEX IF NOT EXISTS idx_{:tableName}_is_dead_letter_message ON {:tableName} (is_dead_letter_message)",
                        handleAwareUnitOfWork.handle());
            dropIndex("DROP INDEX IF EXISTS idx_{:tableName}_key_key_order",
                      handleAwareUnitOfWork.handle());
            createIndex("CREATE INDEX IF NOT EXISTS idx_{:tableName}_is_being_delivered ON {:tableName} (is_being_delivered)",
                        handleAwareUnitOfWork.handle());
            createIndex("CREATE INDEX IF NOT EXISTS idx_{:tableName}_ordered_msg ON {:tableName} (queue_name, key, key_order)",
                        handleAwareUnitOfWork.handle());
            createIndex("CREATE INDEX IF NOT EXISTS idx_{:tableName}_next_msg ON {:tableName} (queue_name, is_dead_letter_message, is_being_delivered, next_delivery_ts)",
                        handleAwareUnitOfWork.handle());

            multiTableChangeListener.ifPresent(listener -> {
                ListenNotify.addChangeNotificationTriggerToTable(handleAwareUnitOfWork.handle(),
                                                                 sharedQueueTableName,
                                                                 List.of(ListenNotify.SqlOperation.INSERT, ListenNotify.SqlOperation.UPDATE),
                                                                 "id", "queue_name", "added_ts", "next_delivery_ts", "delivery_ts", "is_dead_letter_message", "is_being_delivered");
            });
        });
    }

    private void createIndex(String indexStatement, Handle handle) {
        handle.execute(bind(indexStatement,
                            arg("tableName", sharedQueueTableName))
                      );
    }

    private void dropIndex(String indexStatement, Handle handle) {
        handle.execute(bind(indexStatement,
                            arg("tableName", sharedQueueTableName))
                      );
    }

    public List<DurableQueuesInterceptor> getInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
            log.info("Starting");
            interceptors.forEach(durableQueuesInterceptor -> durableQueuesInterceptor.setDurableQueues(this));
            sortInterceptorsByOrder(interceptors);
            durableQueueConsumers.values().forEach(PostgresqlDurableQueueConsumer::start);
            multiTableChangeListener.ifPresent(listener -> {
                listener.listenToNotificationsFor(sharedQueueTableName,
                                                  QueueTableNotification.class);
                listener.getEventBus().addAsyncSubscriber(new AnnotatedEventHandler() {
                    @Handler
                    void handle(QueueTableNotification e) {
                        try {
                            log.trace("[{}:{}] Received QueueMessage notification",
                                      e.queueName,
                                      e.id);
                            var queueName = QueueName.of(e.queueName);
                            durableQueueConsumers.values()
                                                 .stream()
                                                 .filter(durableQueueConsumer -> durableQueueConsumer.queueName.equals(queueName))
                                                 .forEach(durableQueueConsumer -> {
                                                     durableQueueConsumer.messageAdded(new DefaultQueuedMessage(QueueEntryId.of(String.valueOf(e.id)),
                                                                                                                queueName,
                                                                                                                Message.of(NO_PAYLOAD),
                                                                                                                e.addedTimestamp,
                                                                                                                e.nextDeliveryTimestamp,
                                                                                                                e.deliveryTimestamp,
                                                                                                                null,
                                                                                                                -1,
                                                                                                                -1,
                                                                                                                e.isDeadLetterMessage,
                                                                                                                e.isBeingDelivered));
                                                 });
                        } catch (Exception ex) {
                            log.error("Error occurred while handling notification", ex);
                        }

                    }
                });
            });

            log.info("Started");
        }
    }

    @Override
    public void stop() {
        if (started) {
            log.info("Stopping");
            durableQueueConsumers.values().forEach(PostgresqlDurableQueueConsumer::stop);
            multiTableChangeListener.ifPresent(listener -> {
                listener.unlistenToNotificationsFor(sharedQueueTableName);
            });
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
    public Set<QueueName> getQueueNames() {
        var consumerQueueNames = durableQueueConsumers.keySet();
        var dbQueueNames = unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> handleAwareUnitOfWork.handle()
                                                                                                          .createQuery(bind("SELECT distinct queue_name FROM {:tableName}",
                                                                                                                            arg("tableName", sharedQueueTableName)))
                                                                                                          .mapTo(QueueName.class)
                                                                                                          .set());
        dbQueueNames.addAll(consumerQueueNames);
        return dbQueueNames;
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
                                                                                                                       () -> {
                                                                                                                           var pollingIntervalMs = operation.getPollingInterval().toMillis();
                                                                                                                           var queuePollingOptimizer = multiTableChangeListener.map(_ignore -> queuePollingOptimizerFactory.apply(operation))
                                                                                                                                                                               .orElseGet(QueuePollingOptimizer::None);
                                                                                                                           return (DurableQueueConsumer) new PostgresqlDurableQueueConsumer(operation,
                                                                                                                                                                                            unitOfWorkFactory,
                                                                                                                                                                                            this,
                                                                                                                                                                                            this::removeQueueConsumer,
                                                                                                                                                                                            pollingIntervalMs,
                                                                                                                                                                                            queuePollingOptimizer);
                                                                                                                       }).proceed();
            if (started) {
                consumer.start();
            }
            return consumer;
        });
    }

    /**
     * Override this method to provide another {@link QueuePollingOptimizer} than the default {@link QueuePollingOptimizer.SimpleQueuePollingOptimizer}<br>
     * Only called if the {@link PostgresqlDurableQueues} is configured with a {@link MultiTableChangeListener}
     *
     * @param operation the operation for which the {@link QueuePollingOptimizer} will be responsible
     * @return the {@link QueuePollingOptimizer}
     */
    protected QueuePollingOptimizer createQueuePollingOptimizerFor(ConsumeFromQueue operation) {
        var pollingIntervalMs = operation.getPollingInterval().toMillis();
        return new QueuePollingOptimizer.SimpleQueuePollingOptimizer(operation,
                                                                     (long) (pollingIntervalMs * 0.5d),
                                                                     pollingIntervalMs * 20);
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
                                                                  operation.getMessage(),
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
                                                                  operation.getMessage(),
                                                                  true,
                                                                  Optional.ofNullable(operation.getCauseOfError()),
                                                                  Optional.empty()))
                .proceed();
    }

    protected QueueEntryId queueMessage(QueueName queueName, Message message, boolean isDeadLetterMessage, Optional<Exception> causeOfEnqueuing, Optional<Duration> deliveryDelay) {
        requireNonNull(queueName, "You must provide a queueName");
        requireNonNull(message, "You must provide a message");
        requireNonNull(causeOfEnqueuing, "You must provide a causeOfEnqueuing option");
        requireNonNull(deliveryDelay, "You must provide a deliveryDelay option");

        var queueEntryId          = QueueEntryId.random();
        var addedTimestamp        = Instant.now();
        var nextDeliveryTimestamp = isDeadLetterMessage ? null : addedTimestamp.plus(deliveryDelay.orElse(Duration.ZERO));

        var isOrderedMessage = message instanceof OrderedMessage;
        log.trace("[{}:{}] Queuing {}{}message{} with nextDeliveryTimestamp {}",
                  queueName,
                  queueEntryId,
                  isDeadLetterMessage ? "Dead Letter " : "",
                  isOrderedMessage ? "Ordered " : "",
                  isOrderedMessage ? msg(" {}:{}", ((OrderedMessage) message).getKey(), ((OrderedMessage) message).getOrder()) : "",
                  nextDeliveryTimestamp);

        String jsonPayload;
        try {
            jsonPayload = jsonSerializer.serialize(message.getPayload());
        } catch (JSONSerializationException e) {
            throw new DurableQueueException(msg("Failed to serialize message payload of type", message.getPayload().getClass().getName()), e, queueName);
        }

        if (transactionalMode == TransactionalMode.FullyTransactional) {
            unitOfWorkFactory.getRequiredUnitOfWork();
        }

        // TODO: Future improvement: If queueing an OrderedMessage check if another OrderMessage related to the same key and a lower order is already marked as a dead letter message,
        //  in which case this message can be queued directly as a dead letter message
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            var update = unitOfWork.handle().createUpdate(bind("INSERT INTO {:tableName} (\n" +
                                                                       "       id,\n" +
                                                                       "       queue_name,\n" +
                                                                       "       message_payload,\n" +
                                                                       "       message_payload_type,\n" +
                                                                       "       added_ts,\n" +
                                                                       "       next_delivery_ts,\n" +
                                                                       "       last_delivery_error,\n" +
                                                                       "       is_dead_letter_message,\n" +
                                                                       "       meta_data,\n" +
                                                                       "       delivery_mode,\n" +
                                                                       "       key,\n" +
                                                                       "       key_order\n" +
                                                                       "   ) VALUES (\n" +
                                                                       "       :id,\n" +
                                                                       "       :queueName,\n" +
                                                                       "       :message_payload::jsonb,\n" +
                                                                       "       :message_payload_type,\n" +
                                                                       "       :addedTimestamp,\n" +
                                                                       "       :nextDeliveryTimestamp,\n" +
                                                                       "       :lastDeliveryError,\n" +
                                                                       "       :isDeadLetterMessage,\n" +
                                                                       "       :metaData::jsonb,\n" +
                                                                       "       :deliveryMode,\n" +
                                                                       "       :key,\n" +
                                                                       "       :order\n" +
                                                                       "   )",
                                                               arg("tableName", sharedQueueTableName)))
                                   .bind("id", queueEntryId)
                                   .bind("queueName", queueName)
                                   .bind("message_payload", jsonPayload)
                                   .bind("message_payload_type", message.getPayload().getClass().getName())
                                   .bind("addedTimestamp", addedTimestamp)
                                   .bind("nextDeliveryTimestamp", nextDeliveryTimestamp)
                                   .bind("isDeadLetterMessage", isDeadLetterMessage);

            if (message instanceof OrderedMessage) {
                var orderedMessage = (OrderedMessage) message;
                requireNonNull(orderedMessage.getKey(), "An OrderedMessage requires a non null key");
                requireTrue(orderedMessage.getOrder() >= 0, "An OrderedMessage requires an order >= 0");
                update.bind("deliveryMode", QueuedMessage.DeliveryMode.IN_ORDER)
                      .bind("key", orderedMessage.getKey())
                      .bind("order", orderedMessage.getOrder());
            } else {
                update.bind("deliveryMode", QueuedMessage.DeliveryMode.NORMAL)
                      .bindNull("key", Types.VARCHAR)
                      .bind("order", -1L);

            }

            try {
                var jsonMetaData = jsonSerializer.serialize(message.getMetaData());
                update.bind("metaData", jsonMetaData);
            } catch (JSONSerializationException e) {
                throw new DurableQueueException("Failed to serialize message meta-data", e, queueName);
            }

            if (causeOfEnqueuing.isPresent()) {
                update.bind("lastDeliveryError", causeOfEnqueuing.map(Exceptions::getStackTrace).get());
            } else {
                update.bindNull("lastDeliveryError", Types.VARCHAR);
            }

            var numberOfRowsUpdated = update.execute();
            if (numberOfRowsUpdated == 0) {
                throw new DurableQueueException("Failed to insert message", queueName);
            }
        });
        log.debug("[{}:{}] Queued {}{}message{} with nextDeliveryTimestamp {}",
                  queueName,
                  queueEntryId,
                  isDeadLetterMessage ? "Dead Letter " : "",
                  isOrderedMessage ? "Ordered " : "",
                  isOrderedMessage ? msg(" {}:{}", ((OrderedMessage) message).getKey(), ((OrderedMessage) message).getOrder()) : "",
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
                                                   var messages              = operation.getMessages();
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
                                                                                                                                            "       is_dead_letter_message,\n" +
                                                                                                                                            "       is_being_delivered,\n" +
                                                                                                                                            "       meta_data,\n" +
                                                                                                                                            "       delivery_mode,\n" +
                                                                                                                                            "       key,\n" +
                                                                                                                                            "       key_order\n" +
                                                                                                                                            "   ) VALUES (\n" +
                                                                                                                                            "       :id,\n" +
                                                                                                                                            "       :queueName,\n" +
                                                                                                                                            "       :message_payload::jsonb,\n" +
                                                                                                                                            "       :message_payload_type,\n" +
                                                                                                                                            "       :addedTimestamp,\n" +
                                                                                                                                            "       :nextDeliveryTimestamp,\n" +
                                                                                                                                            "       :lastDeliveryError,\n" +
                                                                                                                                            "       :isDeadLetterMessage,\n" +
                                                                                                                                            "       :isBeingDelivered,\n" +
                                                                                                                                            "       :metaData::jsonb,\n" +
                                                                                                                                            "       :deliveryMode,\n" +
                                                                                                                                            "       :key,\n" +
                                                                                                                                            "       :order\n" +
                                                                                                                                            "   )",
                                                                                                                                    arg("tableName", sharedQueueTableName)));
                                                   var queueEntryIds = Lists.toIndexedStream(messages).map(indexedMessage -> {
                                                       var    message = indexedMessage._2;
                                                       String jsonPayload;
                                                       try {
                                                           jsonPayload = jsonSerializer.serialize(message.getPayload());
                                                       } catch (JSONSerializationException e) {
                                                           throw new DurableQueueException(msg("Failed to serialize message payload of type", message.getPayload().getClass().getName()), e, queueName);
                                                       }
                                                       var queueEntryId = QueueEntryId.random();
                                                       batch.bind("id", queueEntryId)
                                                            .bind("queueName", queueName)
                                                            .bind("message_payload", jsonPayload)
                                                            .bind("message_payload_type", message.getPayload().getClass().getName())
                                                            .bind("addedTimestamp", addedTimestamp)
                                                            .bind("nextDeliveryTimestamp", nextDeliveryTimestamp)
                                                            .bind("isDeadLetterMessage", false)
                                                            .bind("isBeingDelivered", false)
                                                            .bindNull("lastDeliveryError", Types.VARCHAR);

                                                       if (message instanceof OrderedMessage) {
                                                           var orderedMessage = (OrderedMessage) message;
                                                           requireNonNull(orderedMessage.getKey(), msg("[Index: {}] - OrderedMessage requires a non null key", indexedMessage._1));
                                                           requireTrue(orderedMessage.getOrder() >= 0, msg("[Index: {}] - OrderedMessage requires an order >= 0", indexedMessage._1));

                                                           batch.bind("deliveryMode", QueuedMessage.DeliveryMode.IN_ORDER)
                                                                .bind("key", orderedMessage.getKey())
                                                                .bind("order", orderedMessage.getOrder());
                                                       } else {
                                                           batch.bind("deliveryMode", QueuedMessage.DeliveryMode.NORMAL)
                                                                .bindNull("key", Types.VARCHAR)
                                                                .bind("order", -1L);
                                                       }

                                                       try {
                                                           var jsonMetaData = jsonSerializer.serialize(message.getMetaData());
                                                           batch.bind("metaData", jsonMetaData);
                                                       } catch (JSONSerializationException e) {
                                                           throw new DurableQueueException("Failed to serialize message meta-data", e, queueName);
                                                       }

                                                       batch.add();
                                                       return queueEntryId;
                                                   }).collect(Collectors.toList());

                                                   var numberOfRowsUpdated = Arrays.stream(batch.execute())
                                                                                   .reduce(Integer::sum).orElse(0);
                                                   if (numberOfRowsUpdated != messages.size()) {
                                                       throw new DurableQueueException(msg("Attempted to queue {} messages but only inserted {} messages", messages.size(), numberOfRowsUpdated),
                                                                                       queueName);
                                                   }

                                                   log.debug("[{}] Queued {} Messages with nextDeliveryTimestamp {} and entry-id's: {}",
                                                             queueName,
                                                             messages.size(),
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
                                                                                                                                            "     redelivery_attempts = redelivery_attempts + 1,\n" +
                                                                                                                                            "     is_being_delivered = FALSE,\n" +
                                                                                                                                            "     delivery_ts = NULL\n" +
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
                                                                                                                                            "     is_dead_letter_message = TRUE,\n" +
                                                                                                                                            "     is_being_delivered = FALSE,\n" +
                                                                                                                                            "     delivery_ts = NULL\n" +
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
                                                       var updateResult = result.get();

                                                       var isOrderedMessage = updateResult.getDeliveryMode() == QueuedMessage.DeliveryMode.IN_ORDER;
                                                       log.debug("[{}] Resurrected Dead Letter {}Message with id '{}' {} and nextDeliveryTimestamp: {}. Message entry after update: {}",
                                                                 updateResult.getQueueName(),
                                                                 isOrderedMessage ? "Ordered " : "",
                                                                 operation.getQueueEntryId(),
                                                                 isOrderedMessage ? "(key: " + ((OrderedMessage) updateResult).getKey() + ", order: " + ((OrderedMessage) updateResult).getOrder() + ")" : "",
                                                                 nextDeliveryTimestamp,
                                                                 updateResult);
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

        return unitOfWorkFactory.withUnitOfWork(() -> newInterceptorChainForOperation(operation,
                                                                                      interceptors,
                                                                                      (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                                                                      () -> {
                                                                                          log.debug("Acknowledging-Message-As-Handled regarding Message with id '{}'", operation.queueEntryId);
                                                                                          return deleteMessage(new DeleteMessage(operation.queueEntryId));
                                                                                      })
                .proceed());

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
                                                       log.error("Couldn't Delete Message with id '{}' - it may already have been deleted", operation.queueEntryId);
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
                                                   resetMessagesStuckBeingDelivered(operation.queueName);
                                                   var now                 = OffsetDateTime.now(Clock.systemUTC());
                                                   var excludeKeysLimitSql = "";
                                                   var excludedKeys        = operation.getExcludeOrderedMessagesWithKey() != null ? operation.getExcludeOrderedMessagesWithKey() : List.of();
                                                   if (!excludedKeys.isEmpty()) {
                                                       excludeKeysLimitSql = "        AND key NOT IN (<excludedKeys>)\n";
                                                   }
                                                   var sql = bind("WITH queued_message_ready_for_delivery AS (\n" +
                                                                          "    SELECT id FROM {:tableName} q1 \n" +
                                                                          "    WHERE\n" +
                                                                          "        queue_name = :queueName AND\n" +
                                                                          "        is_dead_letter_message = FALSE AND\n" +
                                                                          "        is_being_delivered = FALSE AND\n" +
                                                                          "        next_delivery_ts <= :now AND\n" +
                                                                          "        NOT EXISTS (SELECT 1 FROM {:tableName} q2 WHERE q2.key = q1.key AND q2.queue_name = q1.queue_name AND q2.key_order < q1.key_order)\n" +
                                                                          excludeKeysLimitSql +
                                                                          "    ORDER BY key_order ASC, next_delivery_ts ASC\n" + // TODO: Future improvement: Allow the user to specify if key_order or next_delivery_ts should have the highest priority
                                                                          "    LIMIT 1\n" +
                                                                          "    FOR UPDATE SKIP LOCKED\n" +
                                                                          " )\n" +
                                                                          " UPDATE {:tableName} queued_message SET\n" +
                                                                          "    total_attempts = total_attempts + 1,\n" +
                                                                          "    next_delivery_ts = NULL,\n" +
                                                                          "    is_being_delivered = TRUE,\n" +
                                                                          "    delivery_ts = :now\n" +
                                                                          " FROM queued_message_ready_for_delivery\n" +
                                                                          " WHERE queued_message.id = queued_message_ready_for_delivery.id\n" +
                                                                          " RETURNING\n" +
                                                                          "     queued_message.id,\n" +
                                                                          "     queued_message.queue_name,\n" +
                                                                          "     queued_message.message_payload,\n" +
                                                                          "     queued_message.message_payload_type,\n" +
                                                                          "     queued_message.added_ts,\n" +
                                                                          "     queued_message.next_delivery_ts,\n" +
                                                                          "     queued_message.delivery_ts,\n" +
                                                                          "     queued_message.last_delivery_error,\n" +
                                                                          "     queued_message.total_attempts,\n" +
                                                                          "     queued_message.redelivery_attempts,\n" +
                                                                          "     queued_message.is_dead_letter_message,\n" +
                                                                          "     queued_message.is_being_delivered,\n" +
                                                                          "     queued_message.meta_data,\n" +
                                                                          "     queued_message.delivery_mode,\n" +
                                                                          "     queued_message.key,\n" +
                                                                          "     queued_message.key_order",
                                                                  arg("tableName", sharedQueueTableName));

                                                   var query = unitOfWorkFactory.getRequiredUnitOfWork().handle().createQuery(sql)
                                                                                .bind("queueName", operation.queueName)
                                                                                .bind("now", now);
                                                   if (!excludedKeys.isEmpty()) {
                                                       query.bindList("excludedKeys", excludedKeys);
                                                   }


                                                   return query
                                                           .map(queuedMessageMapper)
                                                           .findOne();
                                               }).proceed();
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
    protected void resetMessagesStuckBeingDelivered(QueueName queueName) {
        // Reset stuck messages
        if (transactionalMode == TransactionalMode.SingleOperationTransaction) {
            var now                            = Instant.now();
            var lastStuckMessageResetTimestamp = lastResetStuckMessagesCheckTimestamps.get(queueName);
            if (lastStuckMessageResetTimestamp == null || Duration.between(now, lastStuckMessageResetTimestamp).abs().toMillis() > messageHandlingTimeoutMs) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Looking for messages stuck marked as isBeingDelivered. Last check was performed: {}", queueName, lastStuckMessageResetTimestamp);
                }

                var numberOfChanges = unitOfWorkFactory.getRequiredUnitOfWork().handle().createUpdate(bind("UPDATE {:tableName} SET\n" +
                                                                                                                   "     is_being_delivered = FALSE,\n" +
                                                                                                                   "     delivery_ts = NULL,\n" +
                                                                                                                   "     redelivery_attempts = redelivery_attempts + 1,\n" +
                                                                                                                   "     next_delivery_ts = :now,\n" +
                                                                                                                   "     last_delivery_error = :error\n" +
                                                                                                                   " WHERE is_being_delivered = TRUE\n" +
                                                                                                                   " AND delivery_ts <= :threshold\n",
                                                                                                           arg("tableName", sharedQueueTableName)))
                                                       .bind("threshold", now.minusMillis(messageHandlingTimeoutMs))
                                                       .bind("error", "Handler Processing of the Message was determined to have Timed Out")
                                                       .bind("now", now)
                                                       .execute();
                if (numberOfChanges > 0) {
                    log.debug("[{}] Reset {} messages stuck marked as isBeingDelivered", queueName, numberOfChanges);
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
    public long getTotalDeadLetterMessagesQueuedFor(GetTotalDeadLetterMessagesQueuedFor operation) {
        requireNonNull(operation, "You must specify a GetTotalDeadLetterMessagesQueuedFor instance");
        return newInterceptorChainForOperation(operation,
                                               interceptors,
                                               (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                               () -> unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> handleAwareUnitOfWork.handle().createQuery(bind("SELECT count(*) FROM {:tableName} \n" +
                                                                                                                                                                       " WHERE \n" +
                                                                                                                                                                       "    queue_name = :queueName AND\n" +
                                                                                                                                                                       "    is_dead_letter_message = TRUE",
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
    public List<NextQueuedMessage> queryForMessagesSoonReadyForDelivery(QueueName queueName, Instant withNextDeliveryTimestampAfter, int maxNumberOfMessagesToReturn) {
        requireNonNull(queueName, "No queueName provided");
        requireNonNull(withNextDeliveryTimestampAfter, "No withNextDeliveryTimestampAfter provided");


        return unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> handleAwareUnitOfWork.handle().createQuery(bind("SELECT id, added_ts, next_delivery_ts FROM {:tableName} \n" +
                                                                                                                                 " WHERE queue_name = :queueName\n" +
                                                                                                                                 " AND is_dead_letter_message = FALSE\n" +
                                                                                                                                 " AND is_being_delivered = FALSE\n" +
                                                                                                                                 " AND next_delivery_ts > :now\n" +
                                                                                                                                 " ORDER BY next_delivery_ts ASC\n" +
                                                                                                                                 " LIMIT :pageSize",
                                                                                                                         arg("tableName", sharedQueueTableName)))
                                                                                              .bind("queueName", requireNonNull(queueName, "No QueueName provided"))
                                                                                              .bind("now", withNextDeliveryTimestampAfter)
                                                                                              .bind("pageSize", maxNumberOfMessagesToReturn)
                                                                                              .map((rs, ctx) -> new NextQueuedMessage(QueueEntryId.of(rs.getString("id")),
                                                                                                                                      queueName,
                                                                                                                                      rs.getObject("added_ts", OffsetDateTime.class).toInstant(),
                                                                                                                                      rs.getObject("next_delivery_ts", OffsetDateTime.class).toInstant()))
                                                                                              .list());
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
        log.info("Adding interceptor: {}", interceptor);
        interceptor.setDurableQueues(this);
        interceptors.add(interceptor);
        sortInterceptorsByOrder(interceptors);
        return this;
    }

    @Override
    public DurableQueues removeInterceptor(DurableQueuesInterceptor interceptor) {
        requireNonNull(interceptor, "No interceptor provided");
        log.info("Removing interceptor: {}", interceptor);
        interceptors.remove(interceptor);
        sortInterceptorsByOrder(interceptors);
        return this;
    }

    @Override
    public Optional<QueueName> getQueueNameFor(QueueEntryId queueEntryId) {
        return unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> handleAwareUnitOfWork.handle()
                                                                                              .createQuery(bind("SELECT queue_name FROM {:tableName} WHERE \n" +
                                                                                                                        " id = :id",
                                                                                                                arg("tableName", sharedQueueTableName)))
                                                                                              .bind("id", requireNonNull(queueEntryId, "No queueEntryId provided"))
                                                                                              .mapTo(QueueName.class)
                                                                                              .findOne());
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

    private Object deserializeMessagePayload(QueueName queueName, String messagePayload, String messagePayloadType) {
        requireNonNull(queueName, "No queueName provided");
        requireNonNull(messagePayload, "No messagePayload provided");
        requireNonNull(messagePayloadType, "No messagePayloadType provided");
        try {
            return jsonSerializer.deserialize(messagePayload, Classes.forName(messagePayloadType));
        } catch (Throwable e) {
            throw new DurableQueueException(msg("Failed to deserialize message payload of type {}", messagePayloadType), e, queueName);
        }
    }

    private MessageMetaData deserializeMessageMetadata(QueueName queueName, String metaData) {
        requireNonNull(queueName, "No queueName provided");
        requireNonNull(metaData, "No messagePayload provided");
        try {
            return jsonSerializer.deserialize(metaData, MessageMetaData.class);
        } catch (Throwable e) {
            throw new DurableQueueException(msg("Failed to deserialize message meta-data"), e, queueName);
        }
    }


    /**
     * Default {@link ObjectMapper} supporting {@link Jdk8Module}, {@link JavaTimeModule}, {@link EssentialTypesJacksonModule} and {@link EssentialsImmutableJacksonModule}, which
     * is used together with the {@link JacksonJSONSerializer}
     *
     * @return the default {@link ObjectMapper}
     */
    public static ObjectMapper createDefaultObjectMapper() {
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

    private static class SingleOperationTransactionDurableQueuesInterceptor implements DurableQueuesInterceptor {
        private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
        private       DurableQueues                                                 durableQueues;

        public SingleOperationTransactionDurableQueuesInterceptor(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
            this.unitOfWorkFactory = unitOfWorkFactory;
        }

        @Override
        public void setDurableQueues(DurableQueues durableQueues) {
            this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        }

        @Override
        public Optional<QueuedMessage> intercept(GetDeadLetterMessage operation, InterceptorChain<GetDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public Optional<QueuedMessage> intercept(GetQueuedMessage operation, InterceptorChain<GetQueuedMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public DurableQueueConsumer intercept(ConsumeFromQueue operation, InterceptorChain<ConsumeFromQueue, DurableQueueConsumer, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public DurableQueueConsumer intercept(StopConsumingFromQueue operation, InterceptorChain<StopConsumingFromQueue, DurableQueueConsumer, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public QueueEntryId intercept(QueueMessage operation, InterceptorChain<QueueMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public QueueEntryId intercept(QueueMessageAsDeadLetterMessage operation, InterceptorChain<QueueMessageAsDeadLetterMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public List<QueueEntryId> intercept(QueueMessages operation, InterceptorChain<QueueMessages, List<QueueEntryId>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public Optional<QueuedMessage> intercept(RetryMessage operation, InterceptorChain<RetryMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public Optional<QueuedMessage> intercept(MarkAsDeadLetterMessage operation, InterceptorChain<MarkAsDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public Optional<QueuedMessage> intercept(ResurrectDeadLetterMessage operation, InterceptorChain<ResurrectDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public boolean intercept(AcknowledgeMessageAsHandled operation, InterceptorChain<AcknowledgeMessageAsHandled, Boolean, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public boolean intercept(DeleteMessage operation, InterceptorChain<DeleteMessage, Boolean, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public Optional<QueuedMessage> intercept(GetNextMessageReadyForDelivery operation, InterceptorChain<GetNextMessageReadyForDelivery, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public long intercept(GetTotalMessagesQueuedFor operation, InterceptorChain<GetTotalMessagesQueuedFor, Long, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public List<QueuedMessage> intercept(GetQueuedMessages operation, InterceptorChain<GetQueuedMessages, List<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public List<QueuedMessage> intercept(GetDeadLetterMessages operation, InterceptorChain<GetDeadLetterMessages, List<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }

        @Override
        public int intercept(PurgeQueue operation, InterceptorChain<PurgeQueue, Integer, DurableQueuesInterceptor> interceptorChain) {
            return unitOfWorkFactory.withUnitOfWork(interceptorChain::proceed);
        }
    }

    private class QueuedMessageRowMapper implements RowMapper<QueuedMessage> {
        public QueuedMessageRowMapper() {
        }

        @Override
        public QueuedMessage map(ResultSet rs, StatementContext ctx) throws SQLException {
            var queueName      = QueueName.of(rs.getString("queue_name"));
            var messagePayload = PostgresqlDurableQueues.this.deserializeMessagePayload(queueName, rs.getString("message_payload"), rs.getString("message_payload_type"));

            MessageMetaData messageMetaData     = null;
            var             metaDataColumnValue = rs.getString("meta_data");
            if (metaDataColumnValue != null) {
                messageMetaData = PostgresqlDurableQueues.this.deserializeMessageMetadata(queueName, metaDataColumnValue);
            } else {
                messageMetaData = new MessageMetaData();
            }

            var     deliveryMode = QueuedMessage.DeliveryMode.valueOf(rs.getString("delivery_mode"));
            Message message      = null;
            switch (deliveryMode) {
                case NORMAL:
                    message = new Message(messagePayload,
                                          messageMetaData);
                    break;
                case IN_ORDER:
                    message = new OrderedMessage(messagePayload,
                                                 rs.getString("key"),
                                                 rs.getLong("key_order"),
                                                 messageMetaData);
                    break;
                default:
                    throw new IllegalStateException(msg("Unsupported deliveryMode '{}'", deliveryMode));
            }

            return new DefaultQueuedMessage(QueueEntryId.of(rs.getString("id")),
                                            queueName,
                                            message,
                                            rs.getObject("added_ts", OffsetDateTime.class),
                                            rs.getObject("next_delivery_ts", OffsetDateTime.class),
                                            rs.getObject("delivery_ts", OffsetDateTime.class),
                                            rs.getString("last_delivery_error"),
                                            rs.getInt("total_attempts"),
                                            rs.getInt("redelivery_attempts"),
                                            rs.getBoolean("is_dead_letter_message"),
                                            rs.getBoolean("is_being_delivered"));
        }
    }

    public static class QueueTableNotification extends TableChangeNotification {
        @JsonProperty("id")
        private String id;
        @JsonProperty("queue_name")
        private String queueName;

        @JsonProperty("added_ts")
        private OffsetDateTime addedTimestamp;

        @JsonProperty("next_delivery_ts")
        private OffsetDateTime nextDeliveryTimestamp;

        @JsonProperty("delivery_ts")
        private OffsetDateTime deliveryTimestamp;

        @JsonProperty("is_dead_letter_message")
        private boolean isDeadLetterMessage;

        @JsonProperty("is_being_delivered")
        private boolean isBeingDelivered;

        public QueueTableNotification() {
        }
    }
}
