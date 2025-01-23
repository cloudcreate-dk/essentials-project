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
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.test_data.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.types.CorrelationId;
import dk.cloudcreate.essentials.shared.collections.Lists;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
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
        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var idMsg1 = withDurableQueue(() -> durableQueues.queueMessage(queueName, message1));

        assertThat(durableQueues.getQueueNameFor(idMsg1)).isEqualTo(Optional.of(queueName));
        assertThat(durableQueues.getQueueNameFor(QueueEntryId.random())).isEmpty();
        assertThat(durableQueues.getQueueNames()).isEqualTo(Set.of(queueName));

        var message2 = Message.of(new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), 2),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));

        var idMsg2 = withDurableQueue(() -> durableQueues.queueMessage(queueName, message2));

        var message3 = Message.of(new OrderEvent.OrderAccepted(OrderId.random()));
        var idMsg3   = withDurableQueue(() -> durableQueues.queueMessage(queueName, message3));

        // Then
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(3);
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, 3, 0));

        var queuedMessages = durableQueues.getQueuedMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
        assertThat(queuedMessages).hasSize(3);

        assertThat(durableQueues.getQueuedMessage(idMsg1).get()).isEqualTo(queuedMessages.get(0));
        assertThat(queuedMessages.get(0).getMessage()).usingRecursiveComparison().isEqualTo(message1);
        assertThat((CharSequence) queuedMessages.get(0).getId()).isEqualTo(idMsg1);
        assertThat((CharSequence) queuedMessages.get(0).getQueueName()).isEqualTo(queueName);
        assertThat(queuedMessages.get(0).getAddedTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(0).getNextDeliveryTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(0).isDeadLetterMessage()).isFalse();
        assertThat(queuedMessages.get(0).getLastDeliveryError()).isEqualTo(null);
        assertThat(queuedMessages.get(0).getRedeliveryAttempts()).isEqualTo(0);
        assertThat(queuedMessages.get(0).getTotalDeliveryAttempts()).isEqualTo(0);

        assertThat(durableQueues.getQueuedMessage(idMsg2).get()).isEqualTo(queuedMessages.get(1));
        assertThat(queuedMessages.get(1).getMessage()).usingRecursiveComparison().isEqualTo(message2);
        assertThat((CharSequence) queuedMessages.get(1).getId()).isEqualTo(idMsg2);
        assertThat((CharSequence) queuedMessages.get(1).getQueueName()).isEqualTo(queueName);
        assertThat(queuedMessages.get(1).getAddedTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(1).getNextDeliveryTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(1).isDeadLetterMessage()).isFalse();
        assertThat(queuedMessages.get(1).getLastDeliveryError()).isEqualTo(null);
        assertThat(queuedMessages.get(1).getRedeliveryAttempts()).isEqualTo(0);
        assertThat(queuedMessages.get(1).getTotalDeliveryAttempts()).isEqualTo(0);

        assertThat(durableQueues.getQueuedMessage(idMsg3).get()).isEqualTo(queuedMessages.get(2));
        assertThat(queuedMessages.get(2).getMessage()).usingRecursiveComparison().isEqualTo(message3);
        assertThat((CharSequence) queuedMessages.get(2).getId()).isEqualTo(idMsg3);
        assertThat((CharSequence) queuedMessages.get(2).getQueueName()).isEqualTo(queueName);
        assertThat(queuedMessages.get(2).getAddedTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(2).getNextDeliveryTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(2).isDeadLetterMessage()).isFalse();
        assertThat(queuedMessages.get(2).getLastDeliveryError()).isEqualTo(null);
        assertThat(queuedMessages.get(2).getRedeliveryAttempts()).isEqualTo(0);
        assertThat(queuedMessages.get(2).getTotalDeliveryAttempts()).isEqualTo(0);

        // Then verify queryForMessagesSoonReadyForDelivery
        var nextMessages = durableQueues.queryForMessagesSoonReadyForDelivery(queueName,
                                                                              Instant.now().minusSeconds(2),
                                                                              10);
        assertThat(nextMessages).hasSize(3);
        assertThat((CharSequence) nextMessages.get(0).id).isEqualTo(queuedMessages.get(0).getId());
        assertThat(nextMessages.get(0).addedTimestamp).isEqualTo(queuedMessages.get(0).getAddedTimestamp().toInstant());
        assertThat(nextMessages.get(0).nextDeliveryTimestamp).isEqualTo(queuedMessages.get(0).getNextDeliveryTimestamp().toInstant());

        assertThat((CharSequence) nextMessages.get(1).id).isEqualTo(queuedMessages.get(1).getId());
        assertThat(nextMessages.get(1).addedTimestamp).isEqualTo(queuedMessages.get(1).getAddedTimestamp().toInstant());
        assertThat(nextMessages.get(1).nextDeliveryTimestamp).isEqualTo(queuedMessages.get(1).getNextDeliveryTimestamp().toInstant());

        assertThat((CharSequence) nextMessages.get(2).id).isEqualTo(queuedMessages.get(2).getId());
        assertThat(nextMessages.get(2).addedTimestamp).isEqualTo(queuedMessages.get(2).getAddedTimestamp().toInstant());
        assertThat(nextMessages.get(2).nextDeliveryTimestamp).isEqualTo(queuedMessages.get(2).getNextDeliveryTimestamp().toInstant());


        nextMessages = durableQueues.queryForMessagesSoonReadyForDelivery(queueName,
                                                                          Instant.now().minusSeconds(2),
                                                                          2);
        assertThat(nextMessages).hasSize(2);

        // And When
        var numberOfDeletedMessages = durableQueues.purgeQueue(queueName);

        // Then
        assertThat(numberOfDeletedMessages).isEqualTo(3);
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, 0, 0));
    }

    @Test
    void verify_queued_messages_are_dequeued_in_order() {
        // Given
        var queueName = QueueName.of("TestQueue");
        durableQueues.purgeQueue(queueName);

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var idMsg1 = withDurableQueue(() -> durableQueues.queueMessage(queueName, message1));
        assertThat(durableQueues.getQueueNames()).isEqualTo(Set.of(queueName));

        var message2 = Message.of(new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), 2),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));

        var idMsg2 = withDurableQueue(() -> durableQueues.queueMessage(queueName, message2));

        var message3 = Message.of(new OrderEvent.OrderAccepted(OrderId.random()));
        var idMsg3   = withDurableQueue(() -> durableQueues.queueMessage(queueName, message3));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(3);
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, 3, 0));
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
        assertThat(durableQueues.getQueueNames()).isEqualTo(Set.of(queueName));

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages).hasSize(3));
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0));
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, 0, 0));

        var messages = new ArrayList<>(recordingQueueMessageHandler.messages);
        assertThat(messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(1)).usingRecursiveComparison().isEqualTo(message2);
        assertThat(messages.get(2)).usingRecursiveComparison().isEqualTo(message3);

        // Event when the Queue is empty DurableQueues still return queues that have an active consumer
        assertThat(durableQueues.getQueueNames()).isEqualTo(Set.of(queueName));
        consumer.cancel();
        assertThat(durableQueues.getQueueNames()).isEqualTo(Set.of());
    }

    @Test
    void verify_a_message_queues_as_a_dead_letter_message_is_marked_as_such_and_will_not_be_delivered_to_the_consumer() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var idMsg1 = withDurableQueue(() -> durableQueues.queueMessageAsDeadLetterMessage(queueName, message1, new RuntimeException("On purpose")));
        assertThat(durableQueues.getQueueNames()).isEqualTo(Set.of(queueName));
        assertThat(durableQueues.getQueueNameFor(idMsg1)).isEqualTo(Optional.of(queueName));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(1);
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, 0, 1));
        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
        assertThat(deadLetterMessages).hasSize(1);
        assertThat((CharSequence) deadLetterMessages.get(0).getId()).isEqualTo(idMsg1);

        // When
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();
        var consumer                     = durableQueues.consumeFromQueue(queueName, RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler);

        // When
        Awaitility.await()
                  .during(Duration.ofMillis(1990))
                  .atMost(Duration.ofSeconds(2000))
                  .until(() -> recordingQueueMessageHandler.messages.size() == 0);

        consumer.cancel();
        // Even when the consumer is stopped, DurableQueues will return our queuename as it has a deadletter message
        assertThat(durableQueues.getQueueNames()).isEqualTo(Set.of(queueName));
    }

    @Test
    void verify_a_that_as_long_as_an_ordered_message_with_same_key_and_a_lower_key_order_exists_as_a_dead_letter_message_then_no_further_messages_with_the_same_key_will_be_delivered() {
        // Given
        var queueName = QueueName.of("TestQueue");


        var key1         = "Key1";
        var key1Messages = List.of("Key1Msg1", "Key1Msg2", "Key1Msg3", "Key1Msg4", "Key1Msg5");
        var key2         = "Key2";
        var key2Messages = List.of("Key2Msg1", "Key2Msg2", "Key2Msg3", "Key2Msg4", "Key2Msg5");


        usingDurableQueue(() -> {
            // Queue all key1 messages, but queue Key1Msg3 as a DeadLetterMessage
            Lists.toIndexedStream(key1Messages).forEach(indexedMessage -> {
                if (indexedMessage._2.equals("Key1Msg3")) {
                    durableQueues.queueMessageAsDeadLetterMessage(queueName,
                                                                  OrderedMessage.of(indexedMessage._2,
                                                                                    key1,
                                                                                    indexedMessage._1),
                                                                  new RuntimeException("On purpose"));
                } else {
                    durableQueues.queueMessage(queueName,
                                               OrderedMessage.of(indexedMessage._2,
                                                                 key1,
                                                                 indexedMessage._1),
                                               Duration.ofMillis(100));
                }
            });
            // Queue all key2 messages
            Lists.toIndexedStream(key2Messages).forEach(indexedMessage -> {
                durableQueues.queueMessage(queueName,
                                           OrderedMessage.of(indexedMessage._2,
                                                             key2,
                                                             indexedMessage._1),
                                           Duration.ofMillis(100));
            });
        });

        var expectedQueueMessageCount = key1Messages.size() + key2Messages.size() - 1; // -1 because Key1Msg3 is queued as a DeadLetterMessage
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(expectedQueueMessageCount);
        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
        assertThat(deadLetterMessages).hasSize(1);
        assertThat(deadLetterMessages.get(0).getPayload()).isEqualTo(key1Messages.get(2));
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, expectedQueueMessageCount, 1));
        var key1Msg3EntryId = deadLetterMessages.get(0).getId();

        // When
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();
        var consumer                     = durableQueues.consumeFromQueue(queueName, RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 1), 2, recordingQueueMessageHandler);

        // Then all key2 messages are delivered, but only messages with key_order < Key1Msg3 are delivered for key1
        Awaitility.waitAtMost(Duration.ofSeconds(5000))
                  .untilAsserted(() -> {
                      assertThat(recordingQueueMessageHandler.messages).hasSize(key2Messages.size() + 2); // + 2 for Key1Msg1 and Key1Msg2
                  });

        assertThat(recordingQueueMessageHandler.messages.stream().map(o -> (String) o.getPayload()))
                .containsOnly("Key1Msg1", "Key1Msg2", "Key2Msg1", "Key2Msg2", "Key2Msg3", "Key2Msg4", "Key2Msg5");
        recordingQueueMessageHandler.messages.clear();

        // And When Resurrecting key1Msg3
        usingDurableQueue(() -> durableQueues.resurrectDeadLetterMessage(key1Msg3EntryId, Duration.ofMillis(10)));

        // Then the remaining key1 messages are delivered
        Awaitility.waitAtMost(Duration.ofSeconds(2000))
                  .untilAsserted(() -> {
                      // Some DurableQueues implementations may choose to mark messages as dead letter messages in case a message with the same key and a lower order is already marked as a dead letter message
                      // So in this case let's resurrect these messages
                      var newDeadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
                      if (!newDeadLetterMessages.isEmpty()) {
                          newDeadLetterMessages.forEach(queuedMessage -> {
                              System.out.println("Resurrected new DeadLetterMessage: " + queuedMessage);
                              usingDurableQueue(() -> durableQueues.resurrectDeadLetterMessage(queuedMessage.getId(), Duration.ofMillis(10)));
                          });
                      }
                      assertThat(recordingQueueMessageHandler.messages).hasSize(3); // 3 for "Key1Msg3", "Key1Msg4", "Key1Msg5"
                  });

        assertThat(recordingQueueMessageHandler.messages.stream().map(o -> (String) o.getPayload()))
                .containsOnly("Key1Msg3", "Key1Msg4", "Key1Msg5");

        consumer.cancel();
    }

    @Test
    void verify_failed_messages_are_redelivered() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 12345),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        usingDurableQueue(() -> durableQueues.queueMessage(queueName, message1));

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
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages).hasSize(4)); // 3 redeliveries and 1 final delivery
        var messages = new ArrayList<>(recordingQueueMessageHandler.messages);
        assertThat(messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(1)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(2)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(3)).usingRecursiveComparison().isEqualTo(message1);

        consumer.cancel();
    }

    @Test
    void verify_a_message_that_failed_too_many_times_is_marked_as_dead_letter_message_AND_the_message_can_be_resurrected() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 123456),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var message1Id = withDurableQueue(() -> durableQueues.queueMessage(queueName, message1));

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
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages).hasSize(6)); // 6 in total (1 initial delivery and 5 redeliveries)
        var messages = new ArrayList<>(recordingQueueMessageHandler.messages);
        assertThat(messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(1)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(2)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(3)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(4)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(5)).usingRecursiveComparison().isEqualTo(message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0); // Dead letter messages is not counted
        var deadLetterMessage = withDurableQueue(() -> durableQueues.getDeadLetterMessage(message1Id));
        assertThat(deadLetterMessage).isPresent();
        assertThat(deadLetterMessage.get().getMessage()).usingRecursiveComparison().isEqualTo(message1);
        assertThat(deadLetterMessage.get().getTotalDeliveryAttempts()).isEqualTo(6); // 1 initial delivery and 5 redeliveries
        assertThat(deadLetterMessage.get().getRedeliveryAttempts()).isEqualTo(5);
        var firstDeadLetterMessage = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20).get(0);
        assertThat(deadLetterMessage.get()).usingRecursiveComparison().isEqualTo(firstDeadLetterMessage);

        // And When
        var wasResurrectedResult = withDurableQueue(() -> durableQueues.resurrectDeadLetterMessage(message1Id, Duration.ofMillis(1000)));
        assertThat(wasResurrectedResult).isPresent();

        // Then
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(4))
                  .untilAsserted(() -> {
                      assertThat(recordingQueueMessageHandler.messages).hasSize(7); // 7 in total (1 initial delivery and 5 redeliveries and 1 final delivery for the resurrected message)
                  });
        messages = new ArrayList<>(recordingQueueMessageHandler.messages);
        assertThat(messages.get(6)).usingRecursiveComparison().isEqualTo(message1);

        Awaitility.waitAtMost(Duration.ofMillis(500))
                  .untilAsserted(() -> assertThat(durableQueues.getQueuedMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20)).isEmpty());
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20)).isEmpty();

        consumer.cancel();
    }

    @Test
    void test_two_stage_redelivery_where_a_message_about_to_be_marked_as_a_deadletter_message_is_queued_with_a_redelivery_delay() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 123456),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var message1Id = withDurableQueue(() -> durableQueues.queueMessage(queueName, message1));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, 1, 0));

        AtomicInteger deliveryCountForMessage1 = new AtomicInteger();
        var           totalExpectedDeliveries  = 1 + 5 + 3;
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler(msg -> {
            var count = deliveryCountForMessage1.incrementAndGet();
            if (count <= totalExpectedDeliveries) { // 1 initial delivery and 5 redeliveries - after that the message will be marked as a dead letter message.
                // After that we will queue the message from an interceptor with a delay and run with 2 additional retries (so 3 additional deliveries)
                throw new RuntimeException("Thrown on purpose. Delivery count: " + deliveryCountForMessage1);
            }
        });

        // Add interceptor
        durableQueues.addInterceptor(new DurableQueuesInterceptor() {
            private DurableQueues durableQueues;

            @Override
            public void setDurableQueues(DurableQueues durableQueues) {
                this.durableQueues = durableQueues;
            }

            @Override
            public Optional<QueuedMessage> intercept(MarkAsDeadLetterMessage operation, InterceptorChain<MarkAsDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
                var queuedMessage = durableQueues.getQueuedMessage(operation.queueEntryId).get();
                System.out.println("MarkAsDeadLetterMessage: " + queuedMessage);
                if (queuedMessage.getTotalDeliveryAttempts() < totalExpectedDeliveries + 1) {
                    System.out.println("---------------> Overriding decision to mark as dead letter message for message " + operation.queueEntryId + ". Total delivery attempts is " + queuedMessage.getTotalDeliveryAttempts());
                    // Complete marking as dead letter message
                    if (interceptorChain.proceed().isEmpty()) {
                        throw new RuntimeException(msg("Failed to mark message '{}' as Dead Letter Message", operation.queueEntryId));
                    }
                    // Resurrect the message with delay again
                    return durableQueues.resurrectDeadLetterMessage(queuedMessage.getId(),
                                                                    Duration.ofSeconds(1));
                } else {
                    // Continue and Mark as Dead Letter Message
                    System.out.println("---------------> Gave up on Redelivery");
                    return interceptorChain.proceed();
                }
            }
        });

        // When
        var consumer = durableQueues.consumeFromQueue(queueName,
                                                      RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler
                                                     );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(20))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages).hasSize(totalExpectedDeliveries + 1)); // 10 in total (1 initial delivery and 5 redeliveries + 1 resurrection delivery and 2 additional redelivieries + final delivery)
        var messages = new ArrayList<>(recordingQueueMessageHandler.messages);
        assertThat(messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(1)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(2)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(3)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(4)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(5)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(6)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(7)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(8)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(messages.get(9)).usingRecursiveComparison().isEqualTo(message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0); // Message was delivered
        var deadLetterMessage = withDurableQueue(() -> durableQueues.getDeadLetterMessage(message1Id));
        assertThat(deadLetterMessage).isEmpty();
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, 0, 0));


        consumer.cancel();
    }

    @Test
    void verify_a_message_can_manually_be_marked_as_dead_letter_message_AND_the_message_can_afterwards_be_resurrected() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 123456),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var message1Id = withDurableQueue(() -> durableQueues.queueMessage(queueName, message1));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
        var hasMessageBeenMarkedAsDeadLetterMessage = new AtomicBoolean();
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler(msg -> {
            if (!hasMessageBeenMarkedAsDeadLetterMessage.get()) {
                hasMessageBeenMarkedAsDeadLetterMessage.set(true);
                durableQueues.markAsDeadLetterMessage(message1Id);
            }
        });

        // When
        var consumer = durableQueues.consumeFromQueue(queueName,
                                                      RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler
                                                     );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(4))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages).hasSize(1)); // 1 delivery where the message was marked as a dead-letter-message
        var messages = new ArrayList<>(recordingQueueMessageHandler.messages);
        assertThat(messages.get(0)).usingRecursiveComparison().isEqualTo(message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0); // Dead letter messages is not counted
        var deadLetterMessage = withDurableQueue(() -> durableQueues.getDeadLetterMessage(message1Id));
        assertThat(deadLetterMessage).isPresent();
        assertThat(deadLetterMessage.get().getMessage()).usingRecursiveComparison().isEqualTo(message1);
        assertThat(deadLetterMessage.get().getTotalDeliveryAttempts()).isEqualTo(1); // 1 initial delivery
        assertThat(deadLetterMessage.get().getRedeliveryAttempts()).isEqualTo(0);
        assertThat(deadLetterMessage.get().getLastDeliveryError()).isNull();
        var firstDeadLetterMessage = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20).get(0);
        assertThat(deadLetterMessage.get()).usingRecursiveComparison().isEqualTo(firstDeadLetterMessage);

        // And When
        var wasResurrectedResult = withDurableQueue(() -> durableQueues.resurrectDeadLetterMessage(message1Id, Duration.ofMillis(1000)));
        assertThat(wasResurrectedResult).isPresent();

        // Then
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(4))
                  .untilAsserted(() -> {
                      assertThat(recordingQueueMessageHandler.messages).hasSize(2); // 1 initial delivery and 1 redelivery for the resurrected message
                  });
        messages = new ArrayList<>(recordingQueueMessageHandler.messages);
        assertThat(messages.get(1)).usingRecursiveComparison().isEqualTo(message1);

        Awaitility.waitAtMost(Duration.ofMillis(500))
                  .untilAsserted(() -> assertThat(durableQueues.getQueuedMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20)).isEmpty());
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20)).isEmpty();

        consumer.cancel();
    }

    @Test
    void test_messagehandler_with_call_to_markForRedeliveryIn() {
        // Given
        var queueName = QueueName.of("TestQueue");

        // When
        var message1 = Message.of(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234),
                                  MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                     "trace_id", UUID.randomUUID().toString()));
        var idMsg1 = withDurableQueue(() -> durableQueues.queueMessage(queueName, message1));

        var queuedMessages = durableQueues.getQueuedMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
        assertThat(queuedMessages).hasSize(1);

        assertThat(durableQueues.getQueuedMessage(idMsg1).get()).isEqualTo(queuedMessages.get(0));
        assertThat(queuedMessages.get(0).getMessage()).usingRecursiveComparison().isEqualTo(message1);
        assertThat((CharSequence) queuedMessages.get(0).getId()).isEqualTo(idMsg1);
        assertThat((CharSequence) queuedMessages.get(0).getQueueName()).isEqualTo(queueName);
        assertThat(queuedMessages.get(0).getAddedTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(0).getNextDeliveryTimestamp()).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(0).isDeadLetterMessage()).isFalse();
        assertThat(queuedMessages.get(0).getLastDeliveryError()).isEqualTo(null);
        assertThat(queuedMessages.get(0).getRedeliveryAttempts()).isEqualTo(0);
        assertThat(queuedMessages.get(0).getTotalDeliveryAttempts()).isEqualTo(0);

        var msgHandler = new QueuedMessageHandler() {
            List<QueuedMessage> messages = new CopyOnWriteArrayList<>();

            @Override
            public void handle(QueuedMessage message) {
                messages.add(message);
                if (message.getTotalDeliveryAttempts() < 3) {
                    message.markForRedeliveryIn(Duration.ofMillis(150));
                }
            }
        };

        // When
        durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                                                       .setQueueName(queueName)
                                                       .setRedeliveryPolicy(
                                                               RedeliveryPolicy.fixedBackoff()
                                                                               .setRedeliveryDelay(Duration.ofMillis(200))
                                                                               .setMaximumNumberOfRedeliveries(5)
                                                                               .build())
                                                       .setParallelConsumers(1)
                                                       .setQueueMessageHandler(msgHandler)
                                                       .build());
        assertThat(durableQueues.getQueueNames()).isEqualTo(Set.of(queueName));

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(msgHandler.messages).hasSize(3));
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0));
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getQueuedMessageCountsFor(queueName)).isEqualTo(new QueuedMessageCounts(queueName, 0, 0));


        assertThat(msgHandler.messages.get(0).getLastDeliveryError()).isNull();
        assertThat(msgHandler.messages.get(0).isManuallyMarkedForRedelivery()).isTrue();
        assertThat(msgHandler.messages.get(1).isManuallyMarkedForRedelivery()).isTrue();
        assertThat(msgHandler.messages.get(1).getLastDeliveryError()).isEqualTo(RetryMessage.MANUALLY_REQUESTED_REDELIVERY);
        assertThat(msgHandler.messages.get(2).getLastDeliveryError()).isEqualTo(RetryMessage.MANUALLY_REQUESTED_REDELIVERY);
        assertThat(msgHandler.messages.get(2).isManuallyMarkedForRedelivery()).isFalse();
    }

    static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        Consumer<Message>              functionLogic;
        ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<>();

        RecordingQueuedMessageHandler() {
        }

        RecordingQueuedMessageHandler(Consumer<Message> functionLogic) {
            this.functionLogic = functionLogic;
        }

        @Override
        public void handle(QueuedMessage message) {
            messages.add(message.getMessage());
            if (functionLogic != null) {
                functionLogic.accept(message.getMessage());
            }
        }
    }
}
