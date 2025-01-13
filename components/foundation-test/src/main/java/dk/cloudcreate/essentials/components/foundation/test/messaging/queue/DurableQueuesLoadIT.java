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

package dk.cloudcreate.essentials.components.foundation.test.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.test_data.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.types.CorrelationId;
import dk.cloudcreate.essentials.shared.time.StopWatch;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class DurableQueuesLoadIT<DURABLE_QUEUES extends DurableQueues, UOW extends UnitOfWork, UOW_FACTORY extends UnitOfWorkFactory<UOW>> {
    protected UOW_FACTORY          unitOfWorkFactory;
    protected DURABLE_QUEUES       durableQueues;
    private   DurableQueueConsumer consumer;

    @BeforeEach
    void setup() {
        unitOfWorkFactory = createUnitOfWorkFactory();
        resetQueueStorage(unitOfWorkFactory);
        durableQueues = createDurableQueues(unitOfWorkFactory);
        durableQueues.start();
    }

    @AfterEach
    void cleanup() {
        if (consumer != null) {
            consumer.cancel();
        }
        if (durableQueues != null) {
            durableQueues.stop();
        }
    }

    protected abstract DURABLE_QUEUES createDurableQueues(UOW_FACTORY unitOfWorkFactory);

    protected abstract UOW_FACTORY createUnitOfWorkFactory();

    protected abstract void resetQueueStorage(UOW_FACTORY unitOfWorkFactory);

    /**
     * Queue a large number of messages and verify that message consumption isn't too delayed (testing index optimizations)
     */
    @Test
    void queue_a_large_number_of_messages() {
        // Given
        var queueName = QueueName.of("TestQueue");
        var now       = Instant.now();

        var msgHandler = new RecordingQueuedMessageHandler();
        consumer = durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                                                                  .setQueueName(queueName)
                                                                  .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(100), 0))
                                                                  .setParallelConsumers(1)
                                                                  .setConsumerName("TestConsumer")
                                                                  .setQueueMessageHandler(msgHandler)
                                                                  .build());

        var count     = 20000;
        var stopwatch = StopWatch.start();
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            IntStream.range(0, count).forEach(i -> {
                durableQueues.queueMessage(queueName,
                                           Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), count + 1),
                                                      MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                                         "trace_id", UUID.randomUUID().toString())));
            });
        });
        System.out.println(msg("Queueing {} messages took {}", count, stopwatch.stop()));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(count);
        var nextMessages = durableQueues.queryForMessagesSoonReadyForDelivery(queueName,
                                                                              now,
                                                                              10);
        assertThat(nextMessages).hasSize(10);

        // Verify that a large number of messages added within a single UnitOfWork doesn't delay message consumption
        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertThat(msgHandler.messagesReceived.get()).isGreaterThan(10));
        consumer.cancel();
        consumer = null;

    }

    static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        AtomicLong messagesReceived = new AtomicLong();

        @Override
        public void handle(QueuedMessage message) {
            messagesReceived.getAndIncrement();
        }
    }
}
