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

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.DurableQueuesIT;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.test_data.*;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory.SpringMongoTransactionAwareUnitOfWork;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest(excludeAutoConfiguration = EmbeddedMongoAutoConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ManualAckMongoDurableQueuesIT extends DurableQueuesIT<MongoDurableQueues, SpringMongoTransactionAwareUnitOfWork, SpringMongoTransactionAwareUnitOfWorkFactory> {
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    protected MongoDurableQueues createDurableQueues(SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory) {
        return new MongoDurableQueues(mongoTemplate,
                                      Duration.ofSeconds(5));
    }

    @Override
    protected SpringMongoTransactionAwareUnitOfWorkFactory createUnitOfWorkFactory() {
        return null;
    }

    @Override
    protected void resetQueueStorage(SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory) {
        mongoTemplate.dropCollection(MongoDurableQueues.DEFAULT_DURABLE_QUEUES_COLLECTION_NAME);
    }

    @Test
    void test_ManualAcknowledgement_TransactionalMode() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234);
        var addedMessageId = durableQueues.queueMessage(queueName, message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);

        // And
        var nextMessage = durableQueues.getNextMessageReadyForDelivery(queueName);
        assertThat(nextMessage).isPresent();
        assertThat((CharSequence) nextMessage.get().getId()).isEqualTo(addedMessageId);
        assertThat(nextMessage.get().getPayload()).isEqualTo(message1);

        // Confirm the message is marked as being handled
        assertThat(durableQueues.getNextMessageReadyForDelivery(queueName)).isEmpty();

        // When
        durableQueues.acknowledgeMessageAsHandled(nextMessage.get().getId());

        // Then
        assertThat(durableQueues.getQueuedMessage(addedMessageId)).isEmpty();
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
    }

    @Test
    void test_ManualAcknowledgement_TransactionalMode_timeout_messages_gets_automatically_retried() throws InterruptedException {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234);
        var addedMessageId = durableQueues.queueMessage(queueName, message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);

        // And
        var nextMessage = durableQueues.getNextMessageReadyForDelivery(queueName);
        assertThat(nextMessage).isPresent();
        assertThat((CharSequence) nextMessage.get().getId()).isEqualTo(addedMessageId);
        assertThat(nextMessage.get().getPayload()).isEqualTo(message1);
        System.out.println("DeliveryTimestamp: " +((MongoDurableQueues.DurableQueuedMessage)nextMessage.get()).getDeliveryTimestamp());

        // Confirm the message is marked as being handled
        assertThat(durableQueues.getNextMessageReadyForDelivery(queueName)).isEmpty();

        // When
        Thread.sleep(5500);
        // Confirm the message can be acquired again as it has timed out
        var redeliveredMessage = durableQueues.getNextMessageReadyForDelivery(queueName);
        assertThat(redeliveredMessage).isPresent();
        assertThat((CharSequence) redeliveredMessage.get().getId()).isEqualTo(addedMessageId);
        assertThat(redeliveredMessage.get().getPayload()).isEqualTo(message1);

        // And
        durableQueues.acknowledgeMessageAsHandled(nextMessage.get().getId());

        // Then
        assertThat(durableQueues.getQueuedMessage(addedMessageId)).isEmpty();
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
    }
}