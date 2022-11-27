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

package dk.cloudcreate.essentials.reactive;

import dk.cloudcreate.essentials.shared.Exceptions;
import org.slf4j.*;
import reactor.core.Disposable;
import reactor.core.publisher.*;
import reactor.core.scheduler.*;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Simple event bus that supports both synchronous and asynchronous subscribers that are registered and listening for events published within the local the JVM<br>
 * You can have multiple instances of the LocalEventBus deployed with the local JVM, but usually one event bus is sufficient.<br>
 * <br>
 * Example:
 * <pre>{@code
 *  LocalEventBus<OrderEvent> localEventBus    = new LocalEventBus<>("TestBus", 3, (failingSubscriber, event, exception) -> log.error("...."));
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
 * public class OrderEventsHandler extends AnnotatedEventHandler<OrderEvent> {
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
 * LocalEventBus<OrderEvent> localEventBus    = new LocalEventBus<>("TestBus", 3, (failingSubscriber, event, exception) -> log.error("...."));
 * localEventBus.addAsyncSubscriber(new OrderEventsHandler(...));
 * localEventBus.addSyncSubscriber(new OrderEventsHandler(...));
 * }</pre>
 *
 * @param <EVENT_TYPE> the event type being published by the event bus
 * @see AnnotatedEventHandler
 */
public class LocalEventBus<EVENT_TYPE> {
    private final Logger log;

    private final String                                              busName;
    private final Scheduler                                           listenerScheduler;
    private final ParallelFlux<EVENT_TYPE>                            eventFlux;
    private final Sinks.Many<EVENT_TYPE>                              eventSink;
    private final ConcurrentMap<EventHandler<EVENT_TYPE>, Disposable> asyncSubscribers;
    private final Set<EventHandler<EVENT_TYPE>>                       syncSubscribers;
    private final OnErrorHandler<EVENT_TYPE>                          onErrorHandler;

    /**
     * Create a {@link LocalEventBus} with the given name,
     * using system available processors of parallel asynchronous processing threads
     *
     * @param busName        the name of the bus
     * @param onErrorHandler the error handler which will be called if any subscriber/consumer fails to handle an event
     */
    public LocalEventBus(String busName, OnErrorHandler<EVENT_TYPE> onErrorHandler) {
        this(busName,
             Schedulers.newBoundedElastic(Runtime.getRuntime().availableProcessors(), 1000, requireNonNull(busName, "busName was null"), 60, true),
             onErrorHandler);
    }


    /**
     * Create a {@link LocalEventBus} with the given name, the given number of parallel asynchronous processing threads
     *
     * @param busName         the name of the bus
     * @param parallelThreads the number of parallel asynchronous processing threads
     * @param onErrorHandler  the error handler which will be called if any subscriber/consumer fails to handle an event
     */
    public LocalEventBus(String busName, int parallelThreads, OnErrorHandler<EVENT_TYPE> onErrorHandler) {
        this(busName,
             Schedulers.newBoundedElastic(parallelThreads, 1000, requireNonNull(busName, "busName was null"), 60, true),
             onErrorHandler);
    }

    /**
     * Create a {@link LocalEventBus} with the given name, the given number of parallel asynchronous processing threads
     *
     * @param busName                   the name of the bus
     * @param asyncSubscribersScheduler the asynchronous event scheduler (for the asynchronous consumers/subscribers)
     * @param onErrorHandler            the error handler which will be called if any subscriber/consumer fails to handle an event
     */
    public LocalEventBus(String busName, Scheduler asyncSubscribersScheduler, OnErrorHandler<EVENT_TYPE> onErrorHandler) {
        this.busName = requireNonNull(busName, "busName was null");
        listenerScheduler = requireNonNull(asyncSubscribersScheduler, "asyncSubscribersScheduler is null");
        log = LoggerFactory.getLogger("LocalEventBus - " + busName);
        this.onErrorHandler = requireNonNull(onErrorHandler, "onErrorHandler is null");
        eventSink = Sinks.many().multicast().onBackpressureBuffer();
        eventFlux = eventSink.asFlux().parallel().runOn(listenerScheduler);
        asyncSubscribers = new ConcurrentHashMap<>();
        syncSubscribers = ConcurrentHashMap.newKeySet();
    }

    /**
     * Publish the event to all subscribers/consumer<br>
     * First we call all asynchronous subscribers, after which we will call all synchronous subscribers on the calling thread (i.e. on the same thread that the publish method is called on)
     *
     * @param event the event to publish
     * @return this bus instance
     */
    public LocalEventBus<EVENT_TYPE> publish(EVENT_TYPE event) {
        requireNonNull(event, "No event was supplied");
        log.debug("Publishing event of type '{}' to {} async-subscriber(s)", event.getClass().getName(), asyncSubscribers.size());
        if (asyncSubscribers.size() > 0) {

            eventSink.emitNext(event, (signalType, emitResult) -> {
                if (Sinks.EmitResult.FAIL_NON_SERIALIZED == emitResult) {
                    // Retry with a timeout
//                    log.trace("Will retry publishing of event of type '{}' to {} async-subscriber(s): due to {}", event.getClass().getName(), asyncSubscribers.size(), emitResult);
                    LockSupport.parkNanos(100);
                    return true;
                }
                if (emitResult.isFailure()) {
                    log.error("Failed to publish event of type '{}' to {} async-subscriber(s): {}", event.getClass().getName(), asyncSubscribers.size(), emitResult);
                    onErrorHandler.handle(null, event, null);
                }
                return false;
            });
        }

        log.debug("Publishing event of type '{}' to {} sync-subscriber(s)", event.getClass().getName(), syncSubscribers.size());
        syncSubscribers.forEach(subscriber -> {
            try {
                subscriber.handle(event);
            } catch (Exception e) {
                try {
                    onErrorHandler.handle(subscriber, event, e);
                } catch (Exception ex) {
                    log.error(msg("onErrorHandler failed to handle subscriber {} failing to handle exception {}", subscriber, Exceptions.getStackTrace(e)), ex);
                }
            }
        });
        return this;
    }

    /**
     * Add an asynchronous subscriber/consumer
     *
     * @param subscriber the subscriber to add
     * @return this bus instance
     */
    public LocalEventBus<EVENT_TYPE> addAsyncSubscriber(EventHandler<EVENT_TYPE> subscriber) {
        requireNonNull(subscriber, "You must supply a subscriber instance");
        log.info("[{}] Adding asynchronous subscriber {}", busName, subscriber);
        asyncSubscribers.computeIfAbsent(subscriber, busEventSubscriber -> eventFlux.subscribe(event -> {
            try {
                subscriber.handle(event);
            } catch (Exception e) {
                try {
                    onErrorHandler.handle(subscriber, event, e);
                } catch (Exception ex) {
                    log.error(msg("onErrorHandler failed to handle subscriber {} failing to handle exception {}", subscriber, Exceptions.getStackTrace(e)), ex);
                }
            }
        }));
        return this;
    }

    /**
     * Remove an asynchronous subscriber/consumer
     *
     * @param subscriber the subscriber to remove
     * @return this bus instance
     */
    public LocalEventBus<EVENT_TYPE> removeAsyncSubscriber(EventHandler<EVENT_TYPE> subscriber) {
        requireNonNull(subscriber, "You must supply a subscriber instance");
        log.info("[{}] Removing asynchronous subscriber {}", busName, subscriber);
        var processorSubscription = asyncSubscribers.remove(subscriber);
        if (processorSubscription != null) {
            processorSubscription.dispose();
        }
        return this;
    }

    /**
     * Add a synchronous subscriber/consumer
     *
     * @param subscriber the subscriber to add
     * @return this bus instance
     */
    public LocalEventBus<EVENT_TYPE> addSyncSubscriber(EventHandler<EVENT_TYPE> subscriber) {
        requireNonNull(subscriber, "You must supply a subscriber instance");
        log.info("[{}] Adding synchronous subscriber {}", busName, subscriber);
        syncSubscribers.add(subscriber);
        return this;
    }

    /**
     * Remove a synchronous subscriber/consumer
     *
     * @param subscriber the subscriber to remove
     * @return this bus instance
     */
    public LocalEventBus<EVENT_TYPE> removeSyncSubscriber(EventHandler<EVENT_TYPE> subscriber) {
        requireNonNull(subscriber, "You must supply a subscriber instance");
        log.info("[{}] Removing synchronous subscriber {}", busName, subscriber);
        syncSubscribers.remove(subscriber);
        return this;
    }

    public boolean hasSyncSubscriber(EventHandler<EVENT_TYPE> subscriber) {
        return syncSubscribers.contains(subscriber);
    }

    public boolean hasAsyncSubscriber(EventHandler<EVENT_TYPE> subscriber) {
        return asyncSubscribers.containsKey(subscriber);
    }

    @Override
    public String toString() {
        return "LocalEventBus - " + busName;
    }
}
