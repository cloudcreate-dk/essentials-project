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

package dk.cloudcreate.essentials.components.queue.postgresql;

import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.DurableQueuesIT;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.test_data.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.components.foundation.types.CorrelationId;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.UUID;

import static dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues.createDefaultObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SingleOperationTransactionPostgresqlDurableQueuesIT extends DurableQueuesIT<PostgresqlDurableQueues, GenericHandleAwareUnitOfWorkFactory.GenericHandleAwareUnitOfWork, JdbiUnitOfWorkFactory> {
    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("queue-db")
            .withUsername("test-user")
            .withPassword("secret-password");

    @Override
    protected PostgresqlDurableQueues createDurableQueues(JdbiUnitOfWorkFactory unitOfWorkFactory,
                                                          JSONSerializer jsonSerializer) {
        return PostgresqlDurableQueues.builder()
                                      .setUnitOfWorkFactory(unitOfWorkFactory)
                                      .setTransactionalMode(TransactionalMode.SingleOperationTransaction)
                                      .setMessageHandlingTimeout(Duration.ofSeconds(5))
                                      .setJsonSerializer(jsonSerializer)
                                      .build();
    }

    @Override
    protected JSONSerializer createJSONSerializer() {
        return new JacksonJSONSerializer(createDefaultObjectMapper());
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
    void test_SingleOperationTransaction_TransactionalMode() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var addedMessageId = durableQueues.queueMessage(queueName, message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);

        // And
        var nextMessage = durableQueues.getNextMessageReadyForDelivery(queueName);
        assertThat(nextMessage).isPresent();
        assertThat((CharSequence) nextMessage.get().getId()).isEqualTo(addedMessageId);
        assertThat(nextMessage.get().getMessage()).isEqualTo(message1);

        // Confirm the message is marked as being handled
        assertThat(durableQueues.getNextMessageReadyForDelivery(queueName)).isEmpty();

        // When
        durableQueues.acknowledgeMessageAsHandled(nextMessage.get().getId());

        // Then
        assertThat(durableQueues.getQueuedMessage(addedMessageId)).isEmpty();
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
    }

    @Test
    void test_SingleOperationTransaction_TransactionalMode_timeout_messages_gets_automatically_retried() throws InterruptedException {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var addedMessageId = durableQueues.queueMessage(queueName, message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);

        // And
        var nextMessage = durableQueues.getNextMessageReadyForDelivery(queueName);
        assertThat(nextMessage).isPresent();
        assertThat((CharSequence) nextMessage.get().getId()).isEqualTo(addedMessageId);
        assertThat(nextMessage.get().getMessage()).isEqualTo(message1);
        System.out.println("DeliveryTimestamp: " + nextMessage.get().getDeliveryTimestamp());

        // Confirm the message is marked as being handled
        assertThat(durableQueues.getNextMessageReadyForDelivery(queueName)).isEmpty();

        // When
        Thread.sleep(5500);
        // Confirm the message can be acquired again as it has timed out
        var redeliveredMessage = durableQueues.getNextMessageReadyForDelivery(queueName);

        assertThat(redeliveredMessage).isPresent();
        assertThat((CharSequence) redeliveredMessage.get().getId()).isEqualTo(addedMessageId);
        assertThat(redeliveredMessage.get().getMessage()).isEqualTo(message1);

        // And
        durableQueues.acknowledgeMessageAsHandled(nextMessage.get().getId());

        // Then
        assertThat(durableQueues.getQueuedMessage(addedMessageId)).isEmpty();
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
    }
}