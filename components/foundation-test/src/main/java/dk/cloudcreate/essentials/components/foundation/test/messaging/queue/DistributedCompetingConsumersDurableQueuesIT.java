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

package dk.cloudcreate.essentials.components.foundation.test.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.test_data.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.types.CorrelationId;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DistributedCompetingConsumersDurableQueuesIT<DURABLE_QUEUES extends DurableQueues, UOW extends UnitOfWork, UOW_FACTORY extends UnitOfWorkFactory<UOW>> {
    public static final int NUMBER_OF_MESSAGES = 1000;
    public static final int PARALLEL_CONSUMERS = 20;

    private UOW_FACTORY    unitOfWorkFactory;
    private DURABLE_QUEUES durableQueues1;
    private DURABLE_QUEUES durableQueues2;

    @BeforeEach
    void setup() {
        unitOfWorkFactory = createUnitOfWorkFactory();
        resetQueueStorage(unitOfWorkFactory);
        durableQueues1 = createDurableQueues(unitOfWorkFactory);
        durableQueues1.start();
        durableQueues2 = createDurableQueues(unitOfWorkFactory);
        durableQueues2.start();
    }

    @AfterEach
    void cleanup() {
        if (durableQueues1 != null) {
            durableQueues1.stop();
        }
        if (durableQueues2 != null) {
            durableQueues2.stop();
        }
    }


    protected abstract DURABLE_QUEUES createDurableQueues(UOW_FACTORY unitOfWorkFactory);

    protected abstract UOW_FACTORY createUnitOfWorkFactory();

    protected abstract void resetQueueStorage(UOW_FACTORY unitOfWorkFactory);

    protected void usingDurableQueue(Runnable action) {
        if (durableQueues1.getTransactionalMode() == TransactionalMode.FullyTransactional) {
            unitOfWorkFactory.usingUnitOfWork(uow -> action.run());
        } else {
            action.run();
        }
    }

    @Test
    void verify_queued_messages_are_dequeued_in_order() {
        // Given
        var random    = new Random();
        var queueName = QueueName.of("TestQueue");
        durableQueues1.purgeQueue(queueName);

        var numberOfMessages = NUMBER_OF_MESSAGES;
        var messages         = new ArrayList<Message>(numberOfMessages);

        for (var i = 0; i < numberOfMessages; i++) {
            Message message;
            if (i % 2 == 0) {
                message = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), random.nextInt()),
                                     MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                        "trace_id", UUID.randomUUID().toString()));
            } else if (i % 3 == 0) {
                message = Message.of(new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), random.nextInt()),
                                     MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                        "trace_id", UUID.randomUUID().toString()));
            } else {
                message = Message.of(new OrderEvent.OrderAccepted(OrderId.random()),
                                     MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                        "trace_id", UUID.randomUUID().toString()));
            }
            messages.add(message);
        }

        usingDurableQueue(() -> durableQueues1.queueMessages(queueName, messages));

        assertThat(durableQueues1.getTotalMessagesQueuedFor(queueName)).isEqualTo(numberOfMessages);
        assertThat(durableQueues1.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, numberOfMessages, 0));
        assertThat(durableQueues2.getTotalMessagesQueuedFor(queueName)).isEqualTo(numberOfMessages);
        assertThat(durableQueues2.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, numberOfMessages, 0));
        var recordingQueueMessageHandler1 = new RecordingQueuedMessageHandler();
        var recordingQueueMessageHandler2 = new RecordingQueuedMessageHandler();

        // When
        var consumer1 = durableQueues1.consumeFromQueue(queueName,
                                                        RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 5),
                                                        PARALLEL_CONSUMERS / 2,
                                                        recordingQueueMessageHandler1
                                                       );
        var consumer2 = durableQueues2.consumeFromQueue(queueName,
                                                        RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 5),
                                                        PARALLEL_CONSUMERS / 2,
                                                        recordingQueueMessageHandler2
                                                       );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(30))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler1.messages.size() + recordingQueueMessageHandler2.messages.size()).isEqualTo(NUMBER_OF_MESSAGES));
        // Verify that both consumers were allowed to consume messages
        assertThat(recordingQueueMessageHandler1.messages.size()).isGreaterThan(0);
        assertThat(recordingQueueMessageHandler2.messages.size()).isGreaterThan(0);

        var receivedMessages = new ArrayList<>(recordingQueueMessageHandler1.messages);
        receivedMessages.addAll(recordingQueueMessageHandler2.messages);

        var softAssertions = new SoftAssertions();
        softAssertions.assertThat(receivedMessages.stream().distinct().count()).isEqualTo(NUMBER_OF_MESSAGES);
        softAssertions.assertThat(receivedMessages).containsAll(messages);
        softAssertions.assertThat(messages).containsAll(receivedMessages);
        softAssertions.assertAll();


        // Check no messages are delivered after our assertions
        Awaitility.await()
                  .during(Duration.ofMillis(1990))
                  .atMost(Duration.ofSeconds(2000))
                  .until(() -> recordingQueueMessageHandler1.messages.size() + recordingQueueMessageHandler2.messages.size() == NUMBER_OF_MESSAGES);
        consumer1.cancel();
        consumer2.cancel();
    }

    private static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<>();

        @Override
        public void handle(QueuedMessage message) {
            messages.add(message.getMessage());
        }
    }
}
