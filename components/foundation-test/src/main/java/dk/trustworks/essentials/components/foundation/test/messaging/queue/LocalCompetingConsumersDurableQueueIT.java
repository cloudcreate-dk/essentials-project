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

package dk.trustworks.essentials.components.foundation.test.messaging.queue;

import dk.trustworks.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.trustworks.essentials.components.foundation.test.messaging.queue.test_data.*;
import dk.trustworks.essentials.components.foundation.transaction.*;
import dk.trustworks.essentials.components.foundation.types.CorrelationId;
import dk.trustworks.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.trustworks.essentials.shared.time.*;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static dk.trustworks.essentials.shared.MessageFormatter.msg;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class LocalCompetingConsumersDurableQueueIT<DURABLE_QUEUES extends DurableQueues, UOW extends UnitOfWork, UOW_FACTORY extends UnitOfWorkFactory<UOW>> {
    public static final int            NUMBER_OF_MESSAGES = 2000;
    public static final int            PARALLEL_CONSUMERS = 20;
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

    protected Timing usingDurableQueue(String description, Runnable action) {
        var stopWatch = StopWatch.start(description);
        if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
            unitOfWorkFactory.usingUnitOfWork(uow -> action.run());
        } else {
            action.run();
        }
        var timing = stopWatch.stop();
        System.out.println(timing);
        return timing;
    }

    @Test
    void verify_queued_messages_are_dequeued_in_order() throws InterruptedException {
        // Given
        var random    = new Random();
        var queueName = QueueName.of("TestQueue");
        durableQueues.purgeQueue(queueName);

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

        var timings = new ArrayList<Timing>();
        timings.add(usingDurableQueue(msg("Queuing {} messages", numberOfMessages),
                                      () -> durableQueues.queueMessages(queueName, messages)));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(numberOfMessages);
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, numberOfMessages, 0));


        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();

        // When
        var stopWatch = StopWatch.start(msg("Consuming {} messages using {} parallel consumers", numberOfMessages, PARALLEL_CONSUMERS));
        var consumer = durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                                                                      .setQueueName(queueName)
                                                                      .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 5))
                                                                      .setQueueMessageHandler(recordingQueueMessageHandler)
                                                                      .setParallelConsumers(PARALLEL_CONSUMERS)    // Required for polling DurableQueues implementations
                                                                      .setConsumerExecutorService(Executors.newScheduledThreadPool(PARALLEL_CONSUMERS, ThreadFactoryBuilder.builder()
                                                                                                                                                                           .daemon(true)
                                                                                                                                                                           .nameFormat(queueName + "-Consume-Messages-%d")
                                                                                                                                                                           .build()))
                                                                      .build()
                                                     );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(60))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(NUMBER_OF_MESSAGES));
        var timing = stopWatch.stop();
        timings.add(timing);
        System.out.println(timing);

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

        System.out.println(timings);
    }

    private static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<>();

        @Override
        public void handle(QueuedMessage message) {
            messages.add(message.getMessage());
        }
    }
}
