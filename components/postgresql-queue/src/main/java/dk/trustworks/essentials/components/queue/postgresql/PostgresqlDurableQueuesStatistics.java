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

package dk.trustworks.essentials.components.queue.postgresql;

import dk.trustworks.essentials.components.foundation.json.JSONSerializer;
import dk.trustworks.essentials.components.foundation.json.JacksonJSONSerializer;
import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.DefaultQueuedStatisticsMessage;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.DurableQueuesStatistics;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.QueuedStatisticsMessage;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.QueuedStatisticsMessage.QueueStatistics;
import dk.trustworks.essentials.components.foundation.postgresql.PostgresqlUtil;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory;
import dk.trustworks.essentials.components.queue.postgresql.jdbi.QueueEntryIdArgumentFactory;
import dk.trustworks.essentials.components.queue.postgresql.jdbi.QueueEntryIdColumnMapper;
import dk.trustworks.essentials.components.queue.postgresql.jdbi.QueueNameArgumentFactory;
import dk.trustworks.essentials.components.queue.postgresql.jdbi.QueueNameColumnMapper;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

import static dk.trustworks.essentials.components.queue.postgresql.PostgresqlDurableQueues.createDefaultObjectMapper;
import static dk.trustworks.essentials.shared.FailFast.requireNonNull;
import static dk.trustworks.essentials.shared.MessageFormatter.NamedArgumentBinding.arg;
import static dk.trustworks.essentials.shared.MessageFormatter.bind;
import static dk.trustworks.essentials.shared.MessageFormatter.msg;

/**
 * <pre>
 * This class provides statistics for durable queues implemented using PostgreSQL.
 * It implements the {@link DurableQueuesStatistics} interface to provide methods
 * for retrieving queue statistics and logging message delivery information.
 *
 * The class supports customization of the underlying storage table names and offers
 * features such as gathering queue statistics, managing table initialization, and
 * handling database triggers for message delivery logs.
 *
 * Thread safety and database integrity are expected to be ensured by the underlying
 * unit of work and database interaction components.
 *
 * Key features:
 * - Provides overall queue statistics, such as delivery latency and message counts.
 * - Logs metadata about delivered messages, linked with a separate statistics table.
 * - Supports extensible serialization via a custom {@link JSONSerializer}.
 *
 * Usage Notes:
 * - It is the responsibility of the user to ensure that table and column names provided
 *   as input are sanitized against SQL injection.
 * - The validation function {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)}
 *   offers a first level of protection, but developers must ensure higher security levels
 *   by properly managing inputs.
 * - The application must ensure compatibility between the database schema and queries
 *   used in this implementation.
 *   </pre>
 */
public class PostgresqlDurableQueuesStatistics implements DurableQueuesStatistics {
    private static final Logger log                               = LoggerFactory.getLogger(PostgresqlDurableQueuesStatistics.class);
    public static final  String DEFAULT_DURABLE_QUEUES_TABLE_NAME = "durable_queues_statistics";

    private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
    private final String                                                        statsQueueTableName;
    private final String                                                        durableQueueTableName;
    private final JSONSerializer                                                jsonSerializer;
    private final QueueStatisticsMessageRowMapper                               queueMessageMapper;
    private final QueueStatisticsRowMapper                                      queueStatisticsRowMapperMapper;

    /**
     * Creates an instance of PostgresqlDurableQueuesStatistics.
     *
     * @param unitOfWorkFactory      the {@link HandleAwareUnitOfWorkFactory} used to manage UnitOfWork instances tied to database handles
     * @param durableQueueTableName  the name of the table that stores durable queue messages
     */
    public PostgresqlDurableQueuesStatistics(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                             String durableQueueTableName) {
        this(unitOfWorkFactory,
                new JacksonJSONSerializer(createDefaultObjectMapper()),
                durableQueueTableName,
                DEFAULT_DURABLE_QUEUES_TABLE_NAME);
    }

    /**
     * Create {@link DurableQueuesStatistics} with custom jsonSerializer and sharedQueueTableName
     * <br>
     *
     * @param unitOfWorkFactory            the {@link UnitOfWorkFactory} needed to access the database
     * @param jsonSerializer               the {@link JSONSerializer} that is used to serialize/deserialize message payloads
     * @param statsQueueTableName         the name of the table that will contain all messages (across all {@link QueueName}'s)<br>
     *                                     <strong>Note:</strong><br>
     *                                     To support customization of storage table name, the {@code sharedQueueTableName} will be directly used in constructing SQL statements
     *                                     through string concatenation, which exposes the component to SQL injection attacks.<br>
     *                                     <br>
     *                                     <strong>Security Note:</strong><br>
     *                                     <b>It is the responsibility of the user of this component to sanitize the {@code sharedQueueTableName}
     *                                     to ensure the security of all the SQL statements generated by this component.</b><br>
     *                                     The {@link PostgresqlDurableQueues} component will
     *                                     call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
     *                                     The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                     However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
     *                                     <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes</b>.<br>
     *                                     Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
     *                                     Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
     *                                     <br>
     *                                     It is highly recommended that the {@code sharedQueueTableName} value is only derived from a controlled and trusted source.<br>
     *                                     To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code sharedQueueTableName} value.<br>
     */
    public PostgresqlDurableQueuesStatistics(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                             JSONSerializer jsonSerializer,
                                             String durableQueueTableName,
                                             String statsQueueTableName) {
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory instance provided");
        this.jsonSerializer = requireNonNull(jsonSerializer, "No jsonSerializer");
        this.durableQueueTableName = requireNonNull(durableQueueTableName, "No durableQueueTableName provided").toLowerCase(Locale.ROOT);
        this.statsQueueTableName = requireNonNull(statsQueueTableName, "No statsQueueTableName provided").toLowerCase(Locale.ROOT);
        PostgresqlUtil.checkIsValidTableOrColumnName(statsQueueTableName);
        this.queueMessageMapper = new QueueStatisticsMessageRowMapper();
        this.queueStatisticsRowMapperMapper = new QueueStatisticsRowMapper();

        initializeQueueTables();
    }

    /**
     * <pre>
     * Ensures that the necessary statistics table and associated database objects for durable queues are initialized
     * and exist in the PostgreSQL database. This method handles the creation of tables, indexes, and triggers required
     * for queue statistics tracking.
     *
     * Specifically:
     * - Validates the provided stats queue table name to ensure it adheres to allowed naming conventions.
     * - Creates the statistics table, if it does not already exist, to store queue message data such as timestamps,
     *   delivery attempts, and metadata.
     * - Creates indexes on the statistics table to optimize query performance for specific fields.
     * - Defines and attaches a trigger on the primary durable queue table to populate statistics data whenever
     *   a record is deleted from the durable queue table.
     *
     * This method relies on SQL string interpolation for table names, which makes it critical to validate
     * table name inputs using safe string sanitization utilities to prevent security vulnerabilities, such as
     * SQL injection. It is the user's responsibility to ensure the safety of the provided table name.
     *
     * The statistics table stores the following information for each message:
     * - `id` (Primary key): Unique identifier for the message.
     * - `queue_name`: Name of the queue the message belongs to.
     * - `added_ts`: Timestamp when the message was added to the queue.
     * - `delivery_ts`: Timestamp when the message was delivered.
     * - `deletion_ts`: Timestamp when the message was deleted.
     * - `total_attempts`: Total number of attempts made to deliver the message.
     * - `redelivery_attempts`: Number of redelivery attempts.
     * - `delivery_mode`: Mode of delivery for the message.
     * - `delivery_latency`: Time taken to deliver the message.
     * - `delivery_error`: Flag indicating whether a delivery error occurred.
     * - `meta_data`: Optional JSON metadata associated with the message.
     *
     * Indexes created:
     * - An index on `queue_name` for quick lookup of all messages in a specific queue.
     * - An index on the combination of `queue_name` and `added_ts` to support range queries.
     *
     * Attached Trigger:
     * - A PostgreSQL trigger (`trg_log_message_delivery_stats`) is defined on the durable queue table.
     * - When a message is deleted from the durable queue table, this trigger ensures that a corresponding
     *   statistics entry is inserted into the statistics table.
     *
     * Note: The trigger is implemented using a PostgreSQL `plpgsql` function. Error handling within the trigger
     * ensures that any issues during statistics logging do not disrupt the primary operation.
     * </pre>
     */
    private void initializeQueueTables() {
        PostgresqlUtil.checkIsValidTableOrColumnName(statsQueueTableName);
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            uow.handle().getJdbi().registerArgument(new QueueNameArgumentFactory());
            uow.handle().getJdbi().registerColumnMapper(new QueueNameColumnMapper());
            uow.handle().getJdbi().registerArgument(new QueueEntryIdArgumentFactory());
            uow.handle().getJdbi().registerColumnMapper(new QueueEntryIdColumnMapper());
            uow.handle().execute(bind("""
                                CREATE TABLE IF NOT EXISTS {:tableName} (
                                    id                     TEXT PRIMARY KEY,
                                    queue_name             TEXT NOT NULL,
                                    added_ts               TIMESTAMPTZ NOT NULL,
                                    delivery_ts            TIMESTAMPTZ NOT NULL,
                                    deletion_ts            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                    total_attempts         INTEGER NOT NULL,
                                    redelivery_attempts    INTEGER NOT NULL,
                                    delivery_mode          TEXT NOT NULL,
                                    delivery_latency       INTERVAL NOT NULL,
                                    delivery_error         BOOLEAN NOT NULL,
                                    meta_data              JSONB DEFAULT NULL
                                    );
                            """,
                    arg("tableName", statsQueueTableName))
            );
            log.info("Ensured Durable Queues table '{}' exists", statsQueueTableName);

            uow.handle().execute(bind("CREATE INDEX IF NOT EXISTS idx_{:tableName}_queue_name ON {:tableName} (queue_name)",
                    arg("tableName", statsQueueTableName))
            );
            uow.handle().execute(bind("CREATE INDEX IF NOT EXISTS idx_{:tableName}_stats ON {:tableName} (queue_name, added_ts)",
                    arg("tableName", statsQueueTableName))
            );

            uow.handle().execute(bind("""
                    CREATE OR REPLACE FUNCTION log_message_delivery_stats() RETURNS TRIGGER AS $$
                        BEGIN
                          BEGIN
                            INSERT INTO {:tableName} (
                                id,
                                queue_name,
                                added_ts,
                                delivery_ts,
                                deletion_ts,
                                total_attempts,
                                redelivery_attempts,
                                delivery_mode,
                                delivery_latency,
                                delivery_error,
                                meta_data
                            )
                            VALUES (
                                OLD.id,
                                OLD.queue_name,
                                OLD.added_ts,
                                OLD.delivery_ts,
                                NOW(),
                                OLD.total_attempts,
                                OLD.redelivery_attempts,
                                OLD.delivery_mode,
                                NOW() - OLD.added_ts,
                                OLD.last_delivery_error IS NOT NULL,
                                OLD.meta_data
                            );
                          EXCEPTION WHEN OTHERS THEN
                            RAISE NOTICE 'Trigger insert into queue message stats failed: %', SQLERRM;
                          END;
                          RETURN OLD;
                        END;
                        $$ LANGUAGE plpgsql;
           
                        DROP TRIGGER IF EXISTS trg_log_message_delivery_stats ON {:durableQueueTableName};
           
                        CREATE TRIGGER trg_log_message_delivery_stats
                        AFTER DELETE ON {:durableQueueTableName}
                        FOR EACH ROW
                        EXECUTE FUNCTION log_message_delivery_stats();
                """,
                    arg("tableName", statsQueueTableName),
                    arg("durableQueueTableName", durableQueueTableName))
            );

            log.info("Ensured Durable Queues Stats trigger exists");
        });
    }

    /**
     * Retrieves statistical information for a specific queue, such as the total number of messages delivered,
     * average delivery latency, and the time range of message deliveries.
     * This method constructs and executes a query to aggregate queue statistics,
     * and maps the result to an optional {@link QueueStatistics} object.
     *
     * @param queueName the unique identifier of the queue for which statistics need to be fetched; must not be null
     * @return an {@link Optional} containing the {@link QueueStatistics} for the specified queue if available,
     *         otherwise an empty Optional
     * @throws IllegalArgumentException if the provided {@code queueName} is null
     */
    @Override
    public Optional<QueueStatistics> getQueueStatistics(QueueName queueName) {
        requireNonNull(queueName, "You must specify a QueueName");
        var sql = bind("""
                        WITH earliest AS (
                              SELECT MIN(added_ts) AS first_ts
                              FROM {:tableName}
                              WHERE queue_name = :queueName
                            )
                            SELECT
                              d.queue_name,
                              e.first_ts,
                              COUNT(*) AS total_messages_delivered,
                              AVG(
                                EXTRACT(EPOCH FROM (
                                  deletion_ts -
                                  CASE
                                    WHEN redelivery_attempts > 0 OR (redelivery_attempts = 0 AND delivery_error = true) THEN delivery_ts
                                    ELSE added_ts
                                  END
                                )) * 1000
                              ) AS avg_delivery_latency_ms,
                              MIN(deletion_ts) AS first_delivery,
                              MAX(deletion_ts) AS last_delivery
                            FROM {:tableName} d
                            CROSS JOIN earliest e
                            WHERE d.queue_name = :queueName
                            GROUP BY d.queue_name, e.first_ts;
                      """,
                arg("tableName", statsQueueTableName));

        return unitOfWorkFactory.withUnitOfWork(uow -> {
            var query = uow.handle().createQuery(sql)
                    .bind("queueName", queueName);
            return query
                    .map(queueStatisticsRowMapperMapper)
                    .findOne();
        });

    }

    @Override
    public Optional<QueuedStatisticsMessage> getQueueStatisticsMessage(QueueEntryId id) {
        requireNonNull(id, "You must specify a QueueEntryId");
        var sql = bind("""    
                    SELECT *
                     FROM {:tableName}
                     WHERE id = :id
                    """,
                arg("tableName", statsQueueTableName));

        return unitOfWorkFactory.withUnitOfWork(uow -> {
            var query = uow.handle().createQuery(sql)
                    .bind("id", id);
            return query
                    .map(queueMessageMapper)
                    .findOne();
        });
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

//    private void configureTTL() {
//        if (enableQueueStatisticsTTL) {
//            log.info("Configuring Queue Statistics TTL with '{}'", queueStatisticsTTL);
//            long days = queueStatisticsTTL.toDays();
//            if (queueStatisticsTTL.toDays() < 1) {
//                log.warn("Queue Statistics TTL requires at least one day");
//                days = 1L;
//            }
//            String deleteStatement = bind("DELETE FROM {:tableName} WHERE deletion_ts < (NOW() - INTERVAL '{:days} day')",
//                    arg("tableName", statsQueueTableName), arg("days", days));
//            DefaultTTLJobAction deleteAction = new DefaultTTLJobAction(statsQueueTableName, deleteStatement);
//            CronScheduleConfiguration scheduleConfig = new CronScheduleConfiguration(CronExpression.ONE_DAY, Optional.of(PostgresqlScheduler.FixedDelay.ONE_DAY));
//            TTLJobDefinition jobDefinition = new TTLJobDefinition(deleteAction, scheduleConfig);
//
//            timeToLiveManager.scheduleTTLJob(jobDefinition);
//        } else {
//            log.info("Queue Statistics TTL not enabled");
//        }
//    }

    private static class QueueStatisticsRowMapper implements RowMapper<QueueStatistics> {
        public QueueStatisticsRowMapper() {
        }

        @Override
        public QueueStatistics map(ResultSet rs, StatementContext ctx) throws SQLException {
            var queueName = QueueName.of(rs.getString("queue_name"));

            return new QueueStatistics(
                    queueName,
                    rs.getObject("first_ts", OffsetDateTime.class),
                    rs.getLong("total_messages_delivered"),
                    rs.getInt("avg_delivery_latency_ms"),
                    rs.getObject("first_delivery", OffsetDateTime.class),
                    rs.getObject("last_delivery", OffsetDateTime.class)
            );
        }
    }

    private class QueueStatisticsMessageRowMapper implements RowMapper<QueuedStatisticsMessage> {
        public QueueStatisticsMessageRowMapper() {
        }

        @Override
        public QueuedStatisticsMessage map(ResultSet rs, StatementContext ctx) throws SQLException {
            var queueName = QueueName.of(rs.getString("queue_name"));

            MessageMetaData messageMetaData = null;
            var metaDataColumnValue = rs.getString("meta_data");
            if (metaDataColumnValue != null) {
                messageMetaData = PostgresqlDurableQueuesStatistics.this.deserializeMessageMetadata(queueName, metaDataColumnValue);
            } else {
                messageMetaData = new MessageMetaData();
            }

            var deliveryMode = QueuedMessage.DeliveryMode.valueOf(rs.getString("delivery_mode"));

            return new DefaultQueuedStatisticsMessage(QueueEntryId.of(rs.getString("id")),
                    queueName,
                    rs.getObject("added_ts", OffsetDateTime.class),
                    rs.getObject("delivery_ts", OffsetDateTime.class),
                    rs.getObject("deletion_ts", OffsetDateTime.class),
                    deliveryMode,
                    rs.getInt("total_attempts"),
                    rs.getInt("redelivery_attempts"),
                    rs.getInt("delivery_latency"),
                    messageMetaData);
        }
    }
}
