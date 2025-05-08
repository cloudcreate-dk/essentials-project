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

import dk.trustworks.essentials.shared.Lifecycle;

/**
 * Simple event bus concept that supports both synchronous and asynchronous subscribers that are registered and listening for events published<br>
 * <br>
 * Usage example:
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
 * @see LocalEventBus
 * @see AnnotatedEventHandler
 */
public interface EventBus extends Lifecycle {
    /**
     * Publish the event to all subscribers/consumer<br>
     * First we call all asynchronous subscribers, after which we will call all synchronous subscribers on the calling thread (i.e. on the same thread that the publish method is called on)
     *
     * @param event the event to publish
     * @return this bus instance
     */
    EventBus publish(Object event);

    /**
     * Add an asynchronous subscriber/consumer
     *
     * @param subscriber the subscriber to add
     * @return this bus instance
     */
    EventBus addAsyncSubscriber(EventHandler subscriber);

    /**
     * Remove an asynchronous subscriber/consumer
     *
     * @param subscriber the subscriber to remove
     * @return this bus instance
     */
    EventBus removeAsyncSubscriber(EventHandler subscriber);

    /**
     * Add a synchronous subscriber/consumer
     *
     * @param subscriber the subscriber to add
     * @return this bus instance
     */
    EventBus addSyncSubscriber(EventHandler subscriber);

    /**
     * Remove a synchronous subscriber/consumer
     *
     * @param subscriber the subscriber to remove
     * @return this bus instance
     */
    EventBus removeSyncSubscriber(EventHandler subscriber);

    boolean hasSyncSubscriber(EventHandler subscriber);

    boolean hasAsyncSubscriber(EventHandler subscriber);
}
