/*
 * Copyright 2021-2022 the original author or authors.
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DurableQueuesIT<DURABLE_QUEUES extends DurableQueues, UOW extends UnitOfWork, UOW_FACTORY extends UnitOfWorkFactory<UOW>> {
    protected UOW_FACTORY    unitOfWorkFactory;
    protected DURABLE_QUEUES durableQueues;

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

    protected <R> R withDurableQueue(Supplier<R> supplier) {
        if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
            return unitOfWorkFactory.withUnitOfWork(uow -> supplier.get());
        } else {
            return supplier.get();
        }
    }

    protected void usingDurableQueue(Runnable action) {
        if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
            unitOfWorkFactory.usingUnitOfWork(uow -> action.run());
        } else {
            action.run();
        }
    }

    @Test
    void test_simple_enqueueing_and_afterwards_querying_queued_messages() {
        // Given
        var queueName = QueueName.of("TestQueue");

        // When
        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234);
        var idMsg1   = withDurableQueue(() ->durableQueues.queueMessage(queueName, message1));
        var message2 = new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), 2);

        var idMsg2   = withDurableQueue(() ->durableQueues.queueMessage(queueName, message2));
        var message3 = new OrderEvent.OrderAccepted(OrderId.random());
        var idMsg3   = withDurableQueue(() ->durableQueues.queueMessage(queueName, message3));

        // Then
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(3);
        var queuedMessages = durableQueues.getQueuedMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
        assertThat(queuedMessages.size()).isEqualTo(3);

        assertThat(durableQueues.getQueuedMessage(idMsg1).get()).isEqualTo(queuedMessages.get(0));
        assertThat(queuedMessages.get(0).getPayload()).usingRecursiveComparison().isEqualTo(message1);
        assertThat((CharSequence) queuedMessages.get(0).getId()).isEqualTo(idMsg1);
        assertThat((CharSequence) queuedMessages.get(0).getQueueName()).isEqualTo(queueName);
        assertThat(queuedMessages.get(0).getAddedTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(0).getNextDeliveryTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(0).isDeadLetterMessage()).isFalse();
        assertThat(queuedMessages.get(0).getLastDeliveryError()).isEqualTo(null);
        assertThat(queuedMessages.get(0).getRedeliveryAttempts()).isEqualTo(0);
        assertThat(queuedMessages.get(0).getTotalDeliveryAttempts()).isEqualTo(0);

        assertThat(durableQueues.getQueuedMessage(idMsg2).get()).isEqualTo(queuedMessages.get(1));
        assertThat(queuedMessages.get(1).getPayload()).usingRecursiveComparison().isEqualTo(message2);
        assertThat((CharSequence) queuedMessages.get(1).getId()).isEqualTo(idMsg2);
        assertThat((CharSequence) queuedMessages.get(1).getQueueName()).isEqualTo(queueName);
        assertThat(queuedMessages.get(1).getAddedTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(1).getNextDeliveryTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(1).isDeadLetterMessage()).isFalse();
        assertThat(queuedMessages.get(1).getLastDeliveryError()).isEqualTo(null);
        assertThat(queuedMessages.get(1).getRedeliveryAttempts()).isEqualTo(0);
        assertThat(queuedMessages.get(1).getTotalDeliveryAttempts()).isEqualTo(0);

        assertThat(durableQueues.getQueuedMessage(idMsg3).get()).isEqualTo(queuedMessages.get(2));
        assertThat(queuedMessages.get(2).getPayload()).usingRecursiveComparison().isEqualTo(message3);
        assertThat((CharSequence) queuedMessages.get(2).getId()).isEqualTo(idMsg3);
        assertThat((CharSequence) queuedMessages.get(2).getQueueName()).isEqualTo(queueName);
        assertThat(queuedMessages.get(2).getAddedTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(2).getNextDeliveryTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(2).isDeadLetterMessage()).isFalse();
        assertThat(queuedMessages.get(2).getLastDeliveryError()).isEqualTo(null);
        assertThat(queuedMessages.get(2).getRedeliveryAttempts()).isEqualTo(0);
        assertThat(queuedMessages.get(2).getTotalDeliveryAttempts()).isEqualTo(0);

        // And When
        var numberOfDeletedMessages = durableQueues.purgeQueue(queueName);

        // Then
        assertThat(numberOfDeletedMessages).isEqualTo(3);
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
    }

    @Test
    void verify_queued_messages_are_dequeued_in_order() {
        // Given
        var queueName = QueueName.of("TestQueue");
        durableQueues.purgeQueue(queueName);

        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234);
        var idMsg1   = withDurableQueue(() ->durableQueues.queueMessage(queueName, message1));
        var message2 = new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), 2);

        var idMsg2   = withDurableQueue(() ->durableQueues.queueMessage(queueName, message2));
        var message3 = new OrderEvent.OrderAccepted(OrderId.random());
        var idMsg3   = withDurableQueue(() ->durableQueues.queueMessage(queueName, message3));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(3);
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();

        // When
        var consumer = durableQueues.consumeFromQueue(queueName,
                                                      RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5),
                                                      1,
                                                      recordingQueueMessageHandler
                                                     );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(3));
        assertThat(recordingQueueMessageHandler.messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(1)).usingRecursiveComparison().isEqualTo(message2);
        assertThat(recordingQueueMessageHandler.messages.get(2)).usingRecursiveComparison().isEqualTo(message3);

        consumer.cancel();
    }

    @Test
    void verify_a_message_queues_as_a_dead_letter_message_is_marked_as_such_and_will_not_be_delivered_to_the_consumer() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234);
        usingDurableQueue(() ->durableQueues.queueMessageAsDeadLetterMessage(queueName, message1, new RuntimeException("On purpose")));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
        assertThat(deadLetterMessages.size()).isEqualTo(1);

        // When
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();
        var consumer                     = durableQueues.consumeFromQueue(queueName, RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler);

        // When
        Awaitility.await()
                  .during(Duration.ofMillis(1990))
                  .atMost(Duration.ofSeconds(2000))
                  .until(() -> recordingQueueMessageHandler.messages.size() == 0);

        consumer.cancel();
    }

    @Test
    void verify_failed_messages_are_redelivered() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 12345);
        usingDurableQueue(() ->durableQueues.queueMessage(queueName, message1));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);

        AtomicInteger deliveryCountForMessage1 = new AtomicInteger();
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler(msg -> {
            var count = deliveryCountForMessage1.incrementAndGet();
            if (count <= 3) {
                throw new RuntimeException("Thrown on purpose. Delivery count: " + deliveryCountForMessage1);
            }
        });

        // When
        var consumer = durableQueues.consumeFromQueue(queueName, RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler);

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(4)); // 3 redeliveries and 1 final delivery
        assertThat(recordingQueueMessageHandler.messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(1)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(2)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(3)).usingRecursiveComparison().isEqualTo(message1);

        consumer.cancel();
    }

    @Test
    void verify_a_message_that_failed_too_many_times_is_marked_as_dead_letter_message_AND_the_message_can_be_resurrected() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1   = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 123456);
        var message1Id = withDurableQueue(() ->durableQueues.queueMessage(queueName, message1));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
        AtomicInteger deliveryCountForMessage1 = new AtomicInteger();
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler(msg -> {
            var count = deliveryCountForMessage1.incrementAndGet();
            if (count <= 6) { // 1 initial delivery and 5 redeliveries - after that the message will be marked as a dead letter message. On resurrection that message will be delivered without error
                throw new RuntimeException("Thrown on purpose. Delivery count: " + deliveryCountForMessage1);
            }
        });

        // When
        var consumer = durableQueues.consumeFromQueue(queueName,
                                                      RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler
                                                     );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(4))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(6)); // 6 in total (1 initial delivery and 5 redeliveries)
        assertThat(recordingQueueMessageHandler.messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(1)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(2)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(3)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(4)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(5)).usingRecursiveComparison().isEqualTo(message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0); // Dead letter messages is not counted
        var deadLetterMessage = withDurableQueue(() ->durableQueues.getDeadLetterMessage(message1Id));
        assertThat(deadLetterMessage).isPresent();
        assertThat(deadLetterMessage.get().getPayload()).usingRecursiveComparison().isEqualTo(message1);
        var firstDeadLetterMessage = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20).get(0);
        assertThat(deadLetterMessage.get()).usingRecursiveComparison().isEqualTo(firstDeadLetterMessage);

        // And When
        var wasResurrectedResult = withDurableQueue(() ->durableQueues.resurrectDeadLetterMessage(message1Id, Duration.ofMillis(1000)));
        assertThat(wasResurrectedResult).isPresent();

        // Then
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(4))
                  .untilAsserted(() -> {
                      assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(7); // 7 in total (1 initial delivery and 5 redeliveries and 1 final delivery for the resurrected message)
                  });
        assertThat(recordingQueueMessageHandler.messages.get(6)).usingRecursiveComparison().isEqualTo(message1);
        Awaitility.waitAtMost(Duration.ofMillis(500))
                          .untilAsserted(() -> assertThat(durableQueues.getQueuedMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20)).isEmpty());
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20)).isEmpty();

        consumer.cancel();
    }


    static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        Consumer<Object> functionLogic;
        List<Object>     messages = new ArrayList<>();

        RecordingQueuedMessageHandler() {
        }

        RecordingQueuedMessageHandler(Consumer<Object> functionLogic) {
            this.functionLogic = functionLogic;
        }

        @Override
        public void handle(QueuedMessage message) {
            messages.add(message.getPayload());
            if (functionLogic != null) {
                functionLogic.accept(message.getPayload());
            }
        }
    }
}
