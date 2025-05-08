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

package dk.trustworks.essentials.reactive;


import dk.trustworks.essentials.shared.functional.tuple.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEventBusTest {
    private static final Logger        log = LoggerFactory.getLogger(LocalEventBusTest.class);
    private              LocalEventBus localEventBus;

    @AfterEach
    void tearDown() {
        if (localEventBus != null) {
            System.out.println("Stopping LocalEventBus '" + localEventBus.getName() + "'");
            localEventBus.stop();
        }
    }

    @Test
    void test_with_both_sync_and_async_subscribers() {
        var onErrorHandler = new TestOnErrorHandler();
        localEventBus = new LocalEventBus("Test-Sync-Async", 3, onErrorHandler);
        var asyncSubscriber1 = new RecordingEventHandler();
        var asyncSubscriber2 = new RecordingEventHandler();
        var syncSubscriber1  = new RecordingEventHandler();
        var syncSubscriber2  = new RecordingEventHandler();

        localEventBus.addAsyncSubscriber(asyncSubscriber1);
        localEventBus.addAsyncSubscriber(asyncSubscriber2);
        localEventBus.addSyncSubscriber(syncSubscriber1);
        localEventBus.addSyncSubscriber(syncSubscriber2);
        var events = List.of(new OrderCreatedEvent(), new OrderPaidEvent(), new OrderCancelledEvent());

        // When
        events.forEach(localEventBus::publish);

        // Then
        Awaitility.waitAtMost(Duration.ofMillis(2000))
                  .untilAsserted(() -> assertThat(asyncSubscriber1.eventsReceived).containsAll(events));
        assertThat(asyncSubscriber2.eventsReceived).containsAll(events);
        assertThat(syncSubscriber1.eventsReceived).isEqualTo(events);
        assertThat(syncSubscriber2.eventsReceived).isEqualTo(events);
        assertThat(onErrorHandler.errorsHandled).isEmpty();
    }

    @Test
    void test_multi_threaded_publishing_with_async_subscribers() {
        var onErrorHandler = new TestOnErrorHandler();
        localEventBus = new LocalEventBus("Test-MultiThread", 3, onErrorHandler);
        var asyncSubscriber1 = new RecordingEventHandler();
        var asyncSubscriber2 = new RecordingEventHandler();

        localEventBus.addAsyncSubscriber(asyncSubscriber1);
        localEventBus.addAsyncSubscriber(asyncSubscriber2);
        var events = List.of(new OrderCreatedEvent(), new OrderPaidEvent(), new OrderCancelledEvent());

        // When
        events.stream().parallel().forEach(localEventBus::publish);

        // Then
        Awaitility.waitAtMost(Duration.ofMillis(10000))
                  .untilAsserted(() -> assertThat(asyncSubscriber1.eventsReceived).containsAll(events));
        assertThat(asyncSubscriber2.eventsReceived).containsAll(events);
        assertThat(onErrorHandler.errorsHandled).isEmpty();
    }

    @Test
    void test_with_async_subscriber_throwing_an_exception() {
        var onErrorHandler = new TestOnErrorHandler();
        localEventBus = new LocalEventBus("Test-Async-Exception", 3, onErrorHandler);
        var syncSubscriber = new RecordingEventHandler();
        localEventBus.addSyncSubscriber(syncSubscriber);

        EventHandler asyncSubscriber = orderEvent -> {
            throw new RuntimeException("On purpose");
        };
        localEventBus.addAsyncSubscriber(asyncSubscriber);
        var events = List.of(new OrderCreatedEvent(), new OrderPaidEvent(), new OrderCancelledEvent());

        // When
        events.forEach(localEventBus::publish);

        // Then
        assertThat(syncSubscriber.eventsReceived).isEqualTo(events);
        Awaitility.waitAtMost(Duration.ofMillis(5000))
                  .untilAsserted(() -> assertThat(onErrorHandler.errorsHandled.size()).isEqualTo(events.size()));
        assertThat(onErrorHandler.errorsHandled.stream().filter(failure -> failure._1 == asyncSubscriber).count()).isEqualTo(events.size());
        assertThat(onErrorHandler.errorsHandled.stream().map(failure -> failure._2).collect(Collectors.toList())).containsAll(events);
    }

    @Test
    void test_with_sync_subscriber_throwing_an_exception() {
        var onErrorHandler = new TestOnErrorHandler();
        localEventBus = new LocalEventBus("Test-Sync-Exception", 3, onErrorHandler);
        var asyncSubscriber = new RecordingEventHandler();
        localEventBus.addAsyncSubscriber(asyncSubscriber);

        EventHandler syncSubscriber = orderEvent -> {
            throw new RuntimeException("On purpose");
        };
        localEventBus.addSyncSubscriber(syncSubscriber);
        var events = List.of(new OrderCreatedEvent(), new OrderPaidEvent(), new OrderCancelledEvent());

        // When
        var errors = new ArrayList<>();
        events.forEach(event -> {
            try {
                localEventBus.publish(event);
            } catch (Exception e) {
                errors.add(e);
            }
        });

        // Then
        assertThat(onErrorHandler.errorsHandled).isEmpty(); // Only contains async errors
        assertThat(errors).hasSize(events.size());
        // Since a synchronous event handler failed, verify that no async event handlers are notified
        Awaitility.await()
                  .during(Duration.ofMillis(2000))
                  .atMost(Duration.ofMillis(3000))
                  .until(() -> asyncSubscriber.eventsReceived.isEmpty());
        assertThat(onErrorHandler.errorsHandled).isEmpty();
    }

    @Test
    void sequentiallyPublishedEventsAreDeliveredInOrderToAsyncSubscribers() {
        localEventBus = LocalEventBus.builder()
                                     .busName("InOrderAsyncSingleThreaded")
                                     .parallelThreads(1)
                                     .backpressureBufferSize(2)
                                     .queuedTaskCapFactor(100.0d)
                                     .overflowMaxRetries(2)
                                     .build();

        var asyncSubscriber = new BlockingEventHandler(1000);
        localEventBus.addAsyncSubscriber(asyncSubscriber);

        // Emit multiple events to overflow the buffer
        int count           = 5;
        var publishedEvents = new ArrayList<>();
        for (var i = 0; i < count; i++) {
            var event = "Event " + i;
            System.out.println("Publishing: " + event);
            localEventBus.publish(event);
            publishedEvents.add(event);
        }

        // Wait to ensure events are processed
        Awaitility.await()
                  .atMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> assertThat(asyncSubscriber.eventsReceived).hasSize(count));

        assertThat(asyncSubscriber.eventsReceived).containsExactlyElementsOf(publishedEvents);
    }

    @Test
    void sequentiallyPublishedEventsAreDeliveredInOrderToAsyncSubscribersEvenWhenUsingMultipleParallelThreads() {
        localEventBus = LocalEventBus.builder()
                                     .busName("InOrderAsyncMultiThreaded")
                                     .parallelThreads(10)
                                     .backpressureBufferSize(2)
                                     .queuedTaskCapFactor(100.0d)
                                     .overflowMaxRetries(2)
                                     .build();

        var asyncSubscriber = new BlockingEventHandler(100);
        localEventBus.addAsyncSubscriber(asyncSubscriber);

        int count           = 50;
        var publishedEvents = new ArrayList<>();
        for (var i = 0; i < count; i++) {
            var event = "Event " + i;
            System.out.println("Publishing: " + event);
            localEventBus.publish(event);
            publishedEvents.add(event);
        }

        // Wait to ensure events are processed
        Awaitility.await()
                  .atMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> assertThat(asyncSubscriber.eventsReceived).hasSize(count));

        assertThat(asyncSubscriber.eventsReceived).containsExactlyElementsOf(publishedEvents);
    }

    @Test
    void testEventsPublishedInParallelCanRetryInCaseOfOverflow() {
        var overflowEventErrors      = new CopyOnWriteArrayList<String>();
        var otherEventErrors         = new CopyOnWriteArrayList<String>();
        localEventBus = LocalEventBus.builder()
                                     .busName("AsyncMultiThreaded")
                                     .parallelThreads(1)
                                     .backpressureBufferSize(1)
                                     .queuedTaskCapFactor(50.0d)
                                     .overflowMaxRetries(2)
                                     .onErrorHandler((failingSubscriber, event, exception) -> {
                                         if (exception instanceof LocalEventBus.EventPublishOverflowException) {
                                             overflowEventErrors.add((String) event);
                                         } else {
                                             otherEventErrors.add((String) event);
                                         }
                                     })
                                     .build();

        var asyncSubscriber = new BlockingEventHandler(100);
        localEventBus.addAsyncSubscriber(asyncSubscriber);

        var numberOfThreads         = 50;
        var numberOfEventsPerThread = 100;
        var publishedEvents         = new CopyOnWriteArrayList<>();
        var executorService         = Executors.newFixedThreadPool(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            int finalI = i;

            executorService.submit(() -> {
                var event = "ParallelEvent " + finalI;
                for (int j = 0; j < numberOfEventsPerThread; j++) {
                    var publishedEvent = event + " - " + j;
                    localEventBus.publish(publishedEvent);
                    publishedEvents.add(publishedEvent);
                }
            });

        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Awaitility.await()
                  .atMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> assertThat(asyncSubscriber.eventsReceived.size()).isGreaterThan(50));
        System.out.println("EventsReceived:" + asyncSubscriber.eventsReceived);
        System.out.println("overflowEventErrors:" + overflowEventErrors);
        System.out.println("otherEventErrors:" + otherEventErrors);

        assertThat(asyncSubscriber.eventsReceived).doesNotContainAnyElementsOf(overflowEventErrors);
        assertThat(overflowEventErrors).isNotEmpty();
        assertThat(otherEventErrors).isEmpty();
    }

    // ------------------------------------------------------------------------------------
    private abstract static class OrderEvent {
    }

    private static class OrderCreatedEvent extends OrderEvent {
    }

    private static class OrderPaidEvent extends OrderEvent {
    }

    private static class OrderCancelledEvent extends OrderEvent {
    }

    private static class RecordingEventHandler implements EventHandler {
        final List<OrderEvent> eventsReceived = new ArrayList<>();

        @Override
        public void handle(Object orderEvent) {
            eventsReceived.add((OrderEvent) orderEvent);
        }
    }

    private static class BlockingEventHandler implements EventHandler {
        private static final Logger log = LoggerFactory.getLogger(BlockingEventHandler.class);
        long         blockForMilliseconds;
        List<Object> eventsReceived = new CopyOnWriteArrayList<>();

        public BlockingEventHandler(long blockForMilliseconds) {
            this.blockForMilliseconds = blockForMilliseconds;
        }

        @Override
        public void handle(Object event) {
            try {
                log.info("Handling event {}", event);
                Thread.sleep(blockForMilliseconds);
                eventsReceived.add(event);
                log.info("Completed handling event {}", event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class TestOnErrorHandler implements OnErrorHandler {
        final List<Triple<EventHandler, OrderEvent, Exception>> errorsHandled = new ArrayList<>();

        @Override
        public void handle(EventHandler failingSubscriber, Object event, Exception error) {
            errorsHandled.add(Tuple.of(failingSubscriber, (OrderEvent) event, error));
        }

    }
}