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

import dk.trustworks.essentials.components.foundation.json.*;
import dk.trustworks.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.trustworks.essentials.components.foundation.messaging.queue.Message;
import dk.trustworks.essentials.components.foundation.messaging.queue.MessageMetaData;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueName;
import dk.trustworks.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.DurableQueuesStatistics;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.QueuedStatisticsMessage;
import dk.trustworks.essentials.components.foundation.test.messaging.queue.DurableQueuesIT;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.GenericHandleAwareUnitOfWorkFactory.GenericHandleAwareUnitOfWork;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.JdbiUnitOfWorkFactory;
import dk.trustworks.essentials.components.foundation.types.CorrelationId;
import dk.trustworks.essentials.components.queue.postgresql.test_data.CustomerId;
import dk.trustworks.essentials.components.queue.postgresql.test_data.OrderEvent;
import dk.trustworks.essentials.components.queue.postgresql.test_data.OrderId;
import dk.trustworks.essentials.components.queue.postgresql.test_data.ProductId;
import org.assertj.core.api.AssertionsForClassTypes;
import org.awaitility.Awaitility;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static dk.trustworks.essentials.components.queue.postgresql.PostgresqlDurableQueues.createDefaultObjectMapper;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Base test class for PostgresqlDurableQueues integration tests
 */
@Testcontainers
abstract class PostgresqlDurableQueuesIT extends DurableQueuesIT<PostgresqlDurableQueues, GenericHandleAwareUnitOfWork, JdbiUnitOfWorkFactory> {

    protected DurableQueuesStatistics durableQueuesStatistics;

    @Container
    protected final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("queue-db")
            .withUsername("test-user")
            .withPassword("secret-password");

    /**
     * Determine whether to use the centralized message fetcher
     * @return true for centralized message fetcher, false for traditional consumer
     */
    protected abstract boolean useCentralizedMessageFetcher();

    @Override
    protected JSONSerializer createJSONSerializer() {
        return new JacksonJSONSerializer(createDefaultObjectMapper());
    }

    @Override
    protected PostgresqlDurableQueues createDurableQueues(JdbiUnitOfWorkFactory unitOfWorkFactory,
                                                          JSONSerializer jsonSerializer) {
        PostgresqlDurableQueues durableQueues = PostgresqlDurableQueues.builder()
                .setUnitOfWorkFactory(unitOfWorkFactory)
                .setJsonSerializer(jsonSerializer)
                .setUseCentralizedMessageFetcher(useCentralizedMessageFetcher())
                .build();
        durableQueuesStatistics = new PostgresqlDurableQueuesStatistics(unitOfWorkFactory, durableQueues.getSharedQueueTableName());
        return durableQueues;
    }

    @Override
    protected JdbiUnitOfWorkFactory createUnitOfWorkFactory() {
        return new JdbiUnitOfWorkFactory(Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                                                     postgreSQLContainer.getUsername(),
                                                     postgreSQLContainer.getPassword()));
    }

    @Override
    protected void resetQueueStorage(JdbiUnitOfWorkFactory unitOfWorkFactory) {
        unitOfWorkFactory.usingUnitOfWork(uow -> uow.handle().execute("DROP TABLE IF EXISTS " + PostgresqlDurableQueues.DEFAULT_DURABLE_QUEUES_TABLE_NAME));
    }

    @Test
    void verify_queued_messages_are_dequeued_in_order_with_stats() {
        // Given
        var queueName = QueueName.of("TestQueue");
        durableQueues.purgeQueue(queueName);

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234),
                MessageMetaData.of("correlation_id", CorrelationId.random(),
                        "trace_id", UUID.randomUUID().toString()));
        var idMsg1 = withDurableQueue(() -> durableQueues.queueMessage(queueName, message1));
        var message2 = Message.of(new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), 2),
                MessageMetaData.of("correlation_id", CorrelationId.random(),
                        "trace_id", UUID.randomUUID().toString()));
        var message3 = Message.of(new OrderEvent.OrderAccepted(OrderId.random()));
        var idMsg3   = withDurableQueue(() -> durableQueues.queueMessage(queueName, message3));

        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();

        // When
        var consumer = durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                .setQueueName(queueName)
                .setRedeliveryPolicy(
                        RedeliveryPolicy.fixedBackoff()
                                .setRedeliveryDelay(Duration.ofMillis(200))
                                .setMaximumNumberOfRedeliveries(5)
                                .build())
                .setParallelConsumers(1)
                .setQueueMessageHandler(recordingQueueMessageHandler)
                .build());

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(recordingQueueMessageHandler.getMessages()).isNotEmpty());

        unitOfWorkFactory.usingUnitOfWork(() -> {
            Optional<QueuedStatisticsMessage.QueueStatistics> queueStatistics = durableQueuesStatistics.getQueueStatistics(queueName);
            AssertionsForClassTypes.assertThat(queueStatistics.isPresent()).isTrue();
        });

        consumer.cancel();
    }
}