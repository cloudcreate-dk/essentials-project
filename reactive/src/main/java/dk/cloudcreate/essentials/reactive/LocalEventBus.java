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

package dk.cloudcreate.essentials.reactive;

import dk.cloudcreate.essentials.shared.Exceptions;
import org.slf4j.*;
import reactor.core.Disposable;
import reactor.core.publisher.*;
import reactor.core.scheduler.*;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import static dk.cloudcreate.essentials.shared.Exceptions.isCriticalError;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Simple event bus that supports both synchronous and asynchronous subscribers that are registered and listening for events published within the local the JVM<br>
 * You can have multiple instances of the LocalEventBus deployed with the local JVM, but usually one event bus is sufficient.<br>
 * <br>
 * Example:
 * <pre>{@code
 *  LocalEventBus localEventBus    = new LocalEventBus("TestBus", 3, (failingSubscriber, event, exception) -> log.error("...."));
 *
 *   localEventBus.addAsyncSubscriber(orderEvent -> {
 *             ...
 *         });
 *
 *   localEventBus.addSyncSubscriber(orderEvent -> {
 *               ...
 *         });
 *
 *   localEventBus.publish(new OrderCreatedEvent());
 * }</pre>
 * If you wish to colocate multiple related Event handling methods inside the same class and use it together with the {@link LocalEventBus} then you can extend the {@link AnnotatedEventHandler} class:<br>
 * <pre>{@code
 * public class OrderEventsHandler extends AnnotatedEventHandler {
 *
 *     @Handler
 *     void handle(OrderCreated event) {
 *     }
 *
 *     @Handler
 *     void handle(OrderCancelled event) {
 *     }
 * }}</pre>
 * <br>
 * Example of registering the {@link AnnotatedEventHandler} with the {@link LocalEventBus}:
 * <pre>{@code
 * LocalEventBus localEventBus    = new LocalEventBus("TestBus", 3, (failingSubscriber, event, exception) -> log.error("...."));
 * localEventBus.addAsyncSubscriber(new OrderEventsHandler(...));
 * localEventBus.addSyncSubscriber(new OrderEventsHandler(...));
 * }</pre>
 *
 * @see AnnotatedEventHandler
 */
public final class LocalEventBus implements EventBus {
    public static final int    DEFAULT_BACKPRESSURE_BUFFER_SIZE = 1024;
    public static final int    DEFAULT_OVERFLOW_MAX_RETRIES     = 20;
    public static final double QUEUED_TASK_CAP_FACTOR           = 1.5d;

    private final Logger                                  log;
    private final String                                  busName;
    private final Scheduler                               listenerScheduler;
    private final Flux<Object>                            eventFlux;
    private final ConcurrentMap<EventHandler, Disposable> asyncSubscribers;
    private final Set<EventHandler>                       syncSubscribers;
    private final OnErrorHandler                          onErrorHandler;
    private final int                                     overflowMaxRetries;
    private final Sinks.Many<Object>                      eventSink;
    private       boolean                                 started;

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a {@link LocalEventBus} with the given parameters. After creation the {@link LocalEventBus} is already started!
     *
     * @param busName                the name of the bus
     * @param parallelThreads        the number of parallel asynchronous processing threads
     * @param backpressureBufferSize the backpressure size for {@link Sinks.Many}'s onBackpressureBuffer size
     * @param onErrorHandler         the error handler which will be called if any asynchronous subscriber/consumer fails to handle an event
     * @param overflowMaxRetries     the maximum number of retries for events that overflow the Flux
     * @param queuedTaskCapFactor    the factor to calculate queued task capacity from the backpressureBufferSize
     */
    public LocalEventBus(String busName, int parallelThreads, int backpressureBufferSize, OnErrorHandler onErrorHandler, int overflowMaxRetries, double queuedTaskCapFactor) {
        this.busName = requireNonNull(busName, "busName was null");
        this.listenerScheduler = Schedulers.newBoundedElastic(parallelThreads, (int) (backpressureBufferSize * queuedTaskCapFactor), busName, 60, true);
        this.log = LoggerFactory.getLogger("LocalEventBus - " + busName);
        this.onErrorHandler = requireNonNull(onErrorHandler, "onErrorHandler is null");
        this.overflowMaxRetries = overflowMaxRetries;
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer(backpressureBufferSize, false);
        this.eventFlux = eventSink.asFlux()
                                  .onErrorResume(throwable -> {
                                      if (isCriticalError(throwable)) {
                                          return Flux.error(throwable);
                                      }
                                      log.error("Error in event stream", throwable);
                                      return Flux.empty();
                                  });
        this.asyncSubscribers = new ConcurrentHashMap<>();
        this.syncSubscribers = new CopyOnWriteArraySet<>();
        start();
    }

    /**
     * Create a {@link LocalEventBus} with the given name and default settings.
     *
     * @param busName the name of the bus
     */
    public LocalEventBus(String busName) {
        this(busName, Runtime.getRuntime().availableProcessors(), DEFAULT_BACKPRESSURE_BUFFER_SIZE, (failingSubscriber, event, exception) -> {
            Logger log = LoggerFactory.getLogger("LocalEventBus - " + busName);
            log.error(msg("Error for '{}' handling {}", failingSubscriber, event), exception);
        }, DEFAULT_OVERFLOW_MAX_RETRIES, QUEUED_TASK_CAP_FACTOR);
    }

    /**
     * Create a {@link LocalEventBus} with the given name and error handler.
     *
     * @param busName        the name of the bus
     * @param onErrorHandler the error handler which will be called if any asynchronous subscriber/consumer fails to handle an event
     */
    public LocalEventBus(String busName, OnErrorHandler onErrorHandler) {
        this(busName, Runtime.getRuntime().availableProcessors(), DEFAULT_BACKPRESSURE_BUFFER_SIZE, onErrorHandler, DEFAULT_OVERFLOW_MAX_RETRIES, QUEUED_TASK_CAP_FACTOR);
    }

    /**
     * Create a {@link LocalEventBus} with the given name, number of parallel threads, and error handler.
     *
     * @param busName         the name of the bus
     * @param parallelThreads the number of parallel asynchronous processing threads
     * @param onErrorHandler  the error handler which will be called if any asynchronous subscriber/consumer fails to handle an event
     */
    public LocalEventBus(String busName, int parallelThreads, OnErrorHandler onErrorHandler) {
        this(busName, parallelThreads, DEFAULT_BACKPRESSURE_BUFFER_SIZE, onErrorHandler, DEFAULT_OVERFLOW_MAX_RETRIES, QUEUED_TASK_CAP_FACTOR);
    }

    /**
     * Create a {@link LocalEventBus} with the given name, number of parallel threads, and backpressure buffer size.
     *
     * @param busName                the name of the bus
     * @param parallelThreads        the number of parallel asynchronous processing threads
     * @param backpressureBufferSize The backpressure size for {@link Sinks.Many}'s onBackpressureBuffer size
     */
    public LocalEventBus(String busName, int parallelThreads, int backpressureBufferSize) {
        this(busName, parallelThreads, backpressureBufferSize, (failingSubscriber, event, exception) -> {
            Logger log = LoggerFactory.getLogger("LocalEventBus - " + busName);
            log.error(msg("Error for '{}' handling {}", failingSubscriber, event), exception);
        }, DEFAULT_OVERFLOW_MAX_RETRIES, QUEUED_TASK_CAP_FACTOR);
    }

    public String getName() {
        return busName;
    }

    /**
     * Builder class for {@link LocalEventBus}.
     */
    public static class Builder {
        private String busName                = "default";
        private int    parallelThreads        = Runtime.getRuntime().availableProcessors();
        private int    backpressureBufferSize = DEFAULT_BACKPRESSURE_BUFFER_SIZE;
        private int    overflowMaxRetries     = DEFAULT_OVERFLOW_MAX_RETRIES;
        private double queuedTaskCapFactor    = QUEUED_TASK_CAP_FACTOR;

        private OnErrorHandler onErrorHandler = (failingSubscriber, event, exception) -> {
            Logger log = LoggerFactory.getLogger("LocalEventBus - " + busName);
            log.error(msg("Error for '{}' handling {}", failingSubscriber, event), exception);
        };

        /**
         * Set the name of the bus. Default value is "default"
         *
         * @param busName the name of the bus
         * @return this builder instance
         */
        public Builder busName(String busName) {
            this.busName = busName;
            return this;
        }

        /**
         * Set the number of parallel asynchronous processing threads. Default value is the number of available processors
         *
         * @param parallelThreads the number of parallel asynchronous processing threads
         * @return this builder instance
         */
        public Builder parallelThreads(int parallelThreads) {
            this.parallelThreads = parallelThreads;
            return this;
        }

        /**
         * Set the backpressure size for {@link Sinks.Many}'s onBackpressureBuffer size. Default value is {@value LocalEventBus#DEFAULT_BACKPRESSURE_BUFFER_SIZE}.
         *
         * @param backpressureBufferSize the backpressure size for {@link Sinks.Many}'s onBackpressureBuffer size
         * @return this builder instance
         */
        public Builder backpressureBufferSize(int backpressureBufferSize) {
            this.backpressureBufferSize = backpressureBufferSize;
            return this;
        }

        /**
         * Set the maximum number of retries for events that overflow the Flux. Default value is {@value LocalEventBus#DEFAULT_OVERFLOW_MAX_RETRIES}.
         *
         * @param maxRetries the maximum number of retries for events that overflow the Flux
         * @return this builder instance
         */
        public Builder overflowMaxRetries(int maxRetries) {
            this.overflowMaxRetries = maxRetries;
            return this;
        }

        /**
         * Set the factor to calculate queued task capacity from the backpressureBufferSize. Default value is {@value LocalEventBus#QUEUED_TASK_CAP_FACTOR}.
         *
         * @param queuedTaskCapFactor the factor to calculate queued task capacity from the backpressureBufferSize
         * @return this builder
         */
        public Builder queuedTaskCapFactor(double queuedTaskCapFactor) {
            this.queuedTaskCapFactor = queuedTaskCapFactor;
            return this;
        }

        /**
         * Set the error handler which will be called if any asynchronous subscriber/consumer fails to handle an event. Default logs an error with the failure details
         *
         * @param onErrorHandler the error handler which will be called if any asynchronous subscriber/consumer fails to handle an event
         * @return this builder instance
         */
        public Builder onErrorHandler(OnErrorHandler onErrorHandler) {
            this.onErrorHandler = onErrorHandler;
            return this;
        }

        /**
         * Build the {@link LocalEventBus} from the properties set
         *
         * @return the {@link LocalEventBus}
         */
        public LocalEventBus build() {
            return new LocalEventBus(busName, parallelThreads, backpressureBufferSize, onErrorHandler, overflowMaxRetries, queuedTaskCapFactor);
        }
    }

    @Override
    public EventBus publish(Object event) {
        requireNonNull(event, "No event was supplied");
        log.trace("Publishing event of type '{}' to {} sync-subscriber(s)", event.getClass().getName(), syncSubscribers.size());
        syncSubscribers.forEach(subscriber -> {
            try {
                subscriber.handle(event);
            } catch (Exception e) {
                log.error(msg("Subscriber '{}' failed with exception {}",
                              subscriber, Exceptions.getStackTrace(e)), e);
                throw e;
            }
        });

        log.trace("Publishing event of type '{}' to {} async-subscriber(s)", event.getClass().getName(), asyncSubscribers.size());
        if (!asyncSubscribers.isEmpty()) {
            emit(event);
        }

        return this;
    }

    private void emit(Object event) {
        if (eventSink.currentSubscriberCount() == 0) {
            log.debug("No subscribers are active. Skipping event emission.");
            return;
        }

        Sinks.EmitResult emitResult = eventSink.tryEmitNext(event);

        if (emitResult.isFailure()) {
            handleEmitFailure(emitResult, event, 1);
        }
    }

    private void handleEmitFailure(Sinks.EmitResult emitResult, Object event, int attempt) {
        switch (emitResult) {
            case FAIL_NON_SERIALIZED:
                log.debug("Non-serialized access detected when emitting event '{}'. Retrying emission (attempt {}/{}).", event, attempt, overflowMaxRetries);
                retryEmitWithBackoff(event, attempt, emitResult);
                break;

            case FAIL_OVERFLOW:
                log.debug("Buffer overflow when emitting event '{}'. Retrying emission (attempt {}/{}).", event, attempt, overflowMaxRetries);
                retryEmitWithBackoff(event, attempt, emitResult);
                break;

            case FAIL_ZERO_SUBSCRIBER:
                log.debug("No subscribers are available to receive event '{}'. Discarding the event.", event);
                break;

            case FAIL_TERMINATED:
                log.debug("Cannot emit event '{}' because the sink is terminated. Discarding the event.", event);
                break;

            case FAIL_CANCELLED:
                log.debug("Cannot emit event '{}' because the sink is cancelled. Discarding the event.", event);
                break;

            default:
                log.error("Failed to emit event '{}' due to '{}'. Notifying error handler.", event, emitResult);
                onErrorHandler.handle(null, event, new RuntimeException("Failed to emit event due to " + emitResult));
                break;
        }
    }

    private void retryEmitWithBackoff(Object event, int attempt, Sinks.EmitResult emitResult) {
        if (emitResult != Sinks.EmitResult.FAIL_NON_SERIALIZED && attempt > overflowMaxRetries) {
            log.error("Failed to emit event '{}' after {} attempts. Notifying error handler.", event, overflowMaxRetries);
            onErrorHandler.handle(null, event, convertToException(event, attempt, emitResult));
            return;
        }

        // Exponential backoff strategy
        long delay = Math.min(100L * (1L << (attempt - 1)), 1000L); // Delay doubles each time up to 1000 ms
        log.debug("Retrying emission of event '{}' in {} ms (attempt {}/{})", event, delay, attempt, overflowMaxRetries);
        LockSupport.parkNanos(delay * 1_000_000); // Convert milliseconds to nanoseconds

        Sinks.EmitResult retryResult = eventSink.tryEmitNext(event);
        if (retryResult.isSuccess()) {
            log.debug("Successfully re-emitted event '{}' on attempt {}/{}", event, attempt, overflowMaxRetries);
        } else {
            handleEmitFailure(retryResult, event, attempt + 1);
        }
    }

    private RuntimeException convertToException(Object event, int attempt, Sinks.EmitResult emitResult) {
        switch (emitResult) {
            case FAIL_OVERFLOW:
                return new EventPublishOverflowException(msg("Overflow: Failed to emit event after {} attempts", overflowMaxRetries));
            default:
                return new RuntimeException(msg("Failed to emit event after {} attempts", overflowMaxRetries));
        }
    }

    @Override
    public EventBus addAsyncSubscriber(EventHandler subscriber) {
        requireNonNull(subscriber, "You must supply a subscriber instance");
        log.info("[{}] Adding asynchronous subscriber {}", busName, subscriber);
        asyncSubscribers.computeIfAbsent(subscriber, busEventSubscriber ->
                eventFlux.publishOn(listenerScheduler)
                         .flatMap(event -> Mono.fromRunnable(() -> subscriber.handle(event))
                                               // Kept as we may expand with retry and timeout for even handling
//                                          .timeout(Duration.ofSeconds(5))
//                                          .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
//                                                          .filter(throwable -> !(throwable instanceof TimeoutException)))
                                               .onErrorResume(throwable -> {
                                                   if (isCriticalError(throwable)) {
                                                       return Mono.error(throwable);
                                                   }
                                                   try {
                                                       onErrorHandler.handle(subscriber, event, (Exception) throwable);
                                                   } catch (Exception ex) {
                                                       log.error(msg("onErrorHandler failed to handle subscriber {} failing to handle exception {}", subscriber, Exceptions.getStackTrace(throwable)), ex);
                                                   }
                                                   return Mono.empty();
                                               }),
                                  1 // Per-handler concurrency limit
                                 ).subscribe());

        return this;
    }

    @Override
    public EventBus removeAsyncSubscriber(EventHandler subscriber) {
        requireNonNull(subscriber, "You must supply a subscriber instance");
        log.info("[{}] Removing asynchronous subscriber {}", busName, subscriber);
        var processorSubscription = asyncSubscribers.remove(subscriber);
        if (processorSubscription != null) {
            processorSubscription.dispose();
        }
        return this;
    }

    @Override
    public EventBus addSyncSubscriber(EventHandler subscriber) {
        requireNonNull(subscriber, "You must supply a subscriber instance");
        log.info("[{}] Adding synchronous subscriber {}", busName, subscriber);
        syncSubscribers.add(subscriber);
        return this;
    }

    @Override
    public EventBus removeSyncSubscriber(EventHandler subscriber) {
        requireNonNull(subscriber, "You must supply a subscriber instance");
        log.info("[{}] Removing synchronous subscriber {}", busName, subscriber);
        syncSubscribers.remove(subscriber);
        return this;
    }

    @Override
    public boolean hasSyncSubscriber(EventHandler subscriber) {
        return syncSubscribers.contains(subscriber);
    }

    @Override
    public boolean hasAsyncSubscriber(EventHandler subscriber) {
        return asyncSubscribers.containsKey(subscriber);
    }

    @Override
    public String toString() {
        return "LocalEventBus - " + busName;
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
            log.info("Started event bus");
        }
    }

    @Override
    public void stop() {
        if (started) {
            log.info("Stopping event bus");
            eventSink.emitComplete((signalType, emitResult) -> {
                log.error(msg("Failed to complete eventSink: {}", emitResult));
                return false;
            });
            listenerScheduler.dispose();
            started = false;
            log.info("Stopped event bus");
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    public static class EventPublishOverflowException extends RuntimeException {
        public EventPublishOverflowException(String msg) {
            super(msg);
        }
    }
}
