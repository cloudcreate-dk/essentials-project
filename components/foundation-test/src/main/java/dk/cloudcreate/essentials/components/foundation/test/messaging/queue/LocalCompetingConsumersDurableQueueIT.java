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

package dk.cloudcreate.essentials.components.foundation.test.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.test_data.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class LocalCompetingConsumersDurableQueueIT<DURABLE_QUEUES extends DurableQueues, UOW extends UnitOfWork, UOW_FACTORY extends UnitOfWorkFactory<UOW>> {
    public static final int            NUMBER_OF_MESSAGES = 1000;
    public static final int            PARALLEL_CONSUMERS = 10;
    private             UOW_FACTORY    unitOfWorkFactory;
    private             DURABLE_QUEUES durableQueues;

    @BeforeEach
    void setup() {
        unitOfWorkFactory = createUnitOfWorkFactory();
        resetQueueStorage(unitOfWorkFactory);
        durableQueues = createDurableQueues(unitOfWorkFactory);
        durableQueues.start();
    }

    @AfterEach
    void cleanup() {
        if (durableQueues != null) {
            durableQueues.stop();
        }
    }

    protected abstract DURABLE_QUEUES createDurableQueues(UOW_FACTORY unitOfWorkFactory);

    protected abstract UOW_FACTORY createUnitOfWorkFactory();

    protected abstract void resetQueueStorage(UOW_FACTORY unitOfWorkFactory);

    protected void usingDurableQueue(Runnable action) {
        if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
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
        durableQueues.purgeQueue(queueName);

        var numberOfMessages = NUMBER_OF_MESSAGES;
        var messages         = new ArrayList<OrderEvent>(numberOfMessages);

        for (var i = 0; i < numberOfMessages; i++) {
            OrderEvent message;
            if (i % 2 == 0) {
                message = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), random.nextInt());
            } else if (i % 3 == 0) {
                message = new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), random.nextInt());
            } else {
                message = new OrderEvent.OrderAccepted(OrderId.random());
            }
            messages.add(message);
        }

        usingDurableQueue(() -> {
            durableQueues.queueMessages(queueName, messages);
        });

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(numberOfMessages);
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();

        // When
        var consumer = durableQueues.consumeFromQueue(queueName,
                                                      RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 5),
                                                      PARALLEL_CONSUMERS,
                                                      recordingQueueMessageHandler
                                                     );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(30))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(NUMBER_OF_MESSAGES));


        var receivedMessages = new ArrayList<>(recordingQueueMessageHandler.messages);
        var softAssertions   = new SoftAssertions();
        softAssertions.assertThat(receivedMessages.stream().distinct().count()).isEqualTo(NUMBER_OF_MESSAGES);
        softAssertions.assertThat(receivedMessages).containsAll(messages);
        softAssertions.assertThat(messages).containsAll(receivedMessages);
        softAssertions.assertAll();

        // Check no messages are delivered after our assertions
        Awaitility.await()
                  .during(Duration.ofMillis(1990))
                  .atMost(Duration.ofSeconds(2000))
                  .until(() -> recordingQueueMessageHandler.messages.size() == NUMBER_OF_MESSAGES);
        consumer.cancel();
    }

    private static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        ConcurrentLinkedQueue<OrderEvent> messages = new ConcurrentLinkedQueue<>();

        @Override
        public void handle(QueuedMessage message) {
            messages.add((OrderEvent) message.getPayload());
        }
    }
}
