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

import com.zaxxer.hikari.HikariDataSource;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.foundation.test.messaging.queue.test_data.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.JdbiUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.types.CorrelationId;
import dk.cloudcreate.essentials.shared.time.*;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the new centralized message fetcher design with multiple queues and different thread configurations.
 */
@Testcontainers
public class CentralizedFetcherDurableQueueIT {
    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("queue-db")
            .withUsername("test-user")
            .withPassword("secret-password");

    private static final int NUMBER_OF_MESSAGES     = 1000;
    private static final int MAX_PARALLEL_CONSUMERS = 20;

    private JdbiUnitOfWorkFactory   unitOfWorkFactory;
    private PostgresqlDurableQueues durableQueues;

    @BeforeEach
    void setup() {
        var ds = new HikariDataSource();
        ds.setJdbcUrl(postgreSQLContainer.getJdbcUrl());
        ds.setUsername(postgreSQLContainer.getUsername());
        ds.setPassword(postgreSQLContainer.getPassword());
        ds.setAutoCommit(false);
        ds.setMaximumPoolSize(MAX_PARALLEL_CONSUMERS + 1);

        unitOfWorkFactory = new JdbiUnitOfWorkFactory(Jdbi.create(ds));

        unitOfWorkFactory.usingUnitOfWork(uow -> uow.handle().execute("DROP TABLE IF EXISTS " + PostgresqlDurableQueues.DEFAULT_DURABLE_QUEUES_TABLE_NAME));

        durableQueues = PostgresqlDurableQueues.builder()
                                               .setUnitOfWorkFactory(unitOfWorkFactory)
                                               .setUseCentralizedMessageFetcher(true)
                                               .setCentralizedMessageFetcherPollingInterval(Duration.ofMillis(20))
                                               .build();
        durableQueues.start();
    }

    @AfterEach
    void cleanup() {
        if (durableQueues != null) {
            durableQueues.stop();
        }
    }

    /**
     * Test that the centralized fetcher can handle multiple queues with different thread configurations.
     */
    @Test
    void test_multiple_queues_with_different_thread_configurations() {
        // Given
        var random = new Random();

        // Set up 3 queues with different thread configs
        var queue1 = QueueName.of("HighPriorityQueue");
        var queue2 = QueueName.of("MediumPriorityQueue");
        var queue3 = QueueName.of("LowPriorityQueue");

        // Clear any existing messages
        durableQueues.purgeQueue(queue1);
        durableQueues.purgeQueue(queue2);
        durableQueues.purgeQueue(queue3);

        // Create messages for each queue
        var queue1Messages = new ArrayList<Message>();
        var queue2Messages = new ArrayList<Message>();
        var queue3Messages = new ArrayList<Message>();

        for (var i = 0; i < NUMBER_OF_MESSAGES; i++) {
            // Create random test messages
            var message = Message.of(new OrderEvent.OrderAdded(OrderId.random(),
                                                               CustomerId.random(),
                                                               random.nextInt()),
                                     MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                        "trace_id", UUID.randomUUID().toString()));

            // Add message to appropriate queue's list
            if (i % 3 == 0) {
                queue1Messages.add(message);
            } else if (i % 3 == 1) {
                queue2Messages.add(message);
            } else {
                queue3Messages.add(message);
            }
        }
        assertThat(queue1Messages.size()).isGreaterThan(0);
        assertThat(queue2Messages.size()).isGreaterThan(0);
        assertThat(queue3Messages.size()).isGreaterThan(0);

        // When - queue all messages with timing
        System.out.println("Queuing messages to all queues");
        var queueTimings = new ArrayList<Timing>();

        queueTimings.add(timeIt("Queuing " + queue1Messages.size() + " messages to queue1",
                                () -> unitOfWorkFactory.usingUnitOfWork(uow ->
                                                                                durableQueues.queueMessages(queue1, queue1Messages))));

        queueTimings.add(timeIt("Queuing " + queue2Messages.size() + " messages to queue2",
                                () -> unitOfWorkFactory.usingUnitOfWork(uow ->
                                                                                durableQueues.queueMessages(queue2, queue2Messages))));

        queueTimings.add(timeIt("Queuing " + queue3Messages.size() + " messages to queue3",
                                () -> unitOfWorkFactory.usingUnitOfWork(uow ->
                                                                                durableQueues.queueMessages(queue3, queue3Messages))));

        // Verify all messages were queued
        assertThat(durableQueues.getTotalMessagesQueuedFor(queue1)).isEqualTo(queue1Messages.size());
        assertThat(durableQueues.getTotalMessagesQueuedFor(queue2)).isEqualTo(queue2Messages.size());
        assertThat(durableQueues.getTotalMessagesQueuedFor(queue3)).isEqualTo(queue3Messages.size());

        // Create the message handlers
        var queue1Handler = new RecordingQueuedMessageHandler();
        var queue2Handler = new RecordingQueuedMessageHandler();
        var queue3Handler = new RecordingQueuedMessageHandler();

        // Set up different thread configurations
        var queue1Consumer = durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                                                                            .setQueueName(queue1)
                                                                            .setConsumerName("HighPriority")
                                                                            .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 5))
                                                                            .setQueueMessageHandler(queue1Handler)
                                                                            .setParallelConsumers(10)    // High priority gets many threads
                                                                            .build());

        var queue2Consumer = durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                                                                            .setQueueName(queue2)
                                                                            .setConsumerName("MediumPriority")
                                                                            .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 5))
                                                                            .setQueueMessageHandler(queue2Handler)
                                                                            .setParallelConsumers(5)     // Medium priority
                                                                            .build());

        var queue3Consumer = durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                                                                            .setQueueName(queue3)
                                                                            .setConsumerName("LowPriority")
                                                                            .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 5))
                                                                            .setQueueMessageHandler(queue3Handler)
                                                                            .setParallelConsumers(2)     // Low priority with few threads
                                                                            .build());

        // Then - verify all messages are processed, high priority queue should finish first
        var consumptionStopWatch = StopWatch.start("Consuming all messages");

        // Wait for all messages to be processed
        Awaitility.waitAtMost(Duration.ofSeconds(60))
                  .untilAsserted(() -> {
                      assertThat(queue1Handler.messages.size()).isEqualTo(queue1Messages.size());
                      assertThat(queue2Handler.messages.size()).isEqualTo(queue2Messages.size());
                      assertThat(queue3Handler.messages.size()).isEqualTo(queue3Messages.size());
                  });

        var consumptionTiming = consumptionStopWatch.stop();
        System.out.println(consumptionTiming);

        // Verify that all messages were received correctly
        var queue1Received = new ArrayList<>(queue1Handler.messages);
        var queue2Received = new ArrayList<>(queue2Handler.messages);
        var queue3Received = new ArrayList<>(queue3Handler.messages);

        var softAssertions = new SoftAssertions();

        // Should have received all the messages
        softAssertions.assertThat(queue1Received).containsExactlyInAnyOrderElementsOf(queue1Messages);
        softAssertions.assertThat(queue2Received).containsExactlyInAnyOrderElementsOf(queue2Messages);
        softAssertions.assertThat(queue3Received).containsExactlyInAnyOrderElementsOf(queue3Messages);

        // No queue should have received messages meant for other queues
        queue1Messages.forEach(msg -> {
            softAssertions.assertThat(queue2Received).doesNotContain(msg);
            softAssertions.assertThat(queue3Received).doesNotContain(msg);
        });

        queue2Messages.forEach(msg -> {
            softAssertions.assertThat(queue1Received).doesNotContain(msg);
            softAssertions.assertThat(queue3Received).doesNotContain(msg);
        });

        queue3Messages.forEach(msg -> {
            softAssertions.assertThat(queue1Received).doesNotContain(msg);
            softAssertions.assertThat(queue2Received).doesNotContain(msg);
        });

        softAssertions.assertAll();

        // Cleanup
        queue1Consumer.cancel();
        queue2Consumer.cancel();
        queue3Consumer.cancel();
    }

    /**
     * Test that the centralized fetcher correctly handles ordered messages
     */
    @Test
    void verify_ordered_messages_are_processed_in_order() {
        // Given
        var queueName = QueueName.of("OrderedQueue");
        durableQueues.purgeQueue(queueName);

        // Create ordered messages with multiple keys
        var numberOfKeys   = 10;
        var messagesPerKey = 20;

        // Generate keys
        var keys = new ArrayList<String>();
        for (int i = 0; i < numberOfKeys; i++) {
            keys.add("key-" + i);
        }

        // Track expected order per key
        var expectedOrderPerKey = new ConcurrentHashMap<String, List<OrderedMessage>>();
        keys.forEach(key -> expectedOrderPerKey.put(key, new ArrayList<>()));

        // Generate all ordered messages
        var allOrderedMessages = new ArrayList<OrderedMessage>();
        for (var key : keys) {
            for (var order = 0; order < messagesPerKey; order++) {
                var orderedMessage = OrderedMessage.of(
                        "Message " + key + ":" + order,
                        key,
                        order
                                                      );
                allOrderedMessages.add(orderedMessage);
                expectedOrderPerKey.get(key).add(orderedMessage);
            }
        }

        // Shuffle the messages to ensure out-of-order queuing
        Collections.shuffle(allOrderedMessages);

        // Queue the messages
        unitOfWorkFactory.usingUnitOfWork(uow -> durableQueues.queueMessages(queueName, allOrderedMessages));

        // When - create a handler that tracks order of received messages per key
        var orderedMessageTracker = new ConcurrentHashMap<String, List<OrderedMessage>>();

        var recordingOrderedHandler = new QueuedMessageHandler() {
            @Override
            public void handle(QueuedMessage message) {
                var orderedMessage = (OrderedMessage) message.getMessage();
                orderedMessageTracker.computeIfAbsent(orderedMessage.getKey(), k -> new CopyOnWriteArrayList<>())
                                     .add(orderedMessage);

                // Add a small delay to simulate processing time
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        // Create consumer with multiple threads
        var consumer = durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                                                                      .setQueueName(queueName)
                                                                      .setConsumerName("OrderedConsumer")
                                                                      .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(10), 3))
                                                                      .setQueueMessageHandler(recordingOrderedHandler)
                                                                      .setParallelConsumers(15) // Use many parallel threads to stress test ordering
                                                                      .build());

        // Then - wait for all messages to be processed and verify ordering
        Awaitility.waitAtMost(Duration.ofSeconds(30))
                  .untilAsserted(() -> {
                      var totalReceived = orderedMessageTracker.values().stream()
                                                               .mapToInt(List::size)
                                                               .sum();
                      assertThat(totalReceived).isEqualTo(numberOfKeys * messagesPerKey);
                  });

        // Verify order is preserved per key
        var softAssertions = new SoftAssertions();
        for (var key : keys) {
            var actualMessagesForKey   = orderedMessageTracker.get(key);
            var expectedMessagesForKey = expectedOrderPerKey.get(key);

            // Should have received all messages for this key
            softAssertions.assertThat(actualMessagesForKey.size()).isEqualTo(messagesPerKey);

            // Messages should be in order by order field
            for (int i = 0; i < messagesPerKey; i++) {
                softAssertions.assertThat(actualMessagesForKey.get(i).getOrder())
                              .isEqualTo(i);
            }

            // Should match expected order
            softAssertions.assertThat(actualMessagesForKey).containsExactlyElementsOf(expectedMessagesForKey);
        }

        softAssertions.assertAll();

        // Cleanup
        consumer.cancel();
    }

    private Timing timeIt(String description, Runnable action) {
        var stopWatch = StopWatch.start(description);
        action.run();
        var timing = stopWatch.stop();
        System.out.println(timing);
        return timing;
    }

    private static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<>();

        @Override
        public void handle(QueuedMessage message) {
            messages.add(message.getMessage());
        }
    }
}