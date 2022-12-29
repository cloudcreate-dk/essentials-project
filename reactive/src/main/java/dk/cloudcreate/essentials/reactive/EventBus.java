package dk.cloudcreate.essentials.reactive;

/**
 * Simple event bus concept that supports both synchronous and asynchronous subscribers that are registered and listening for events published<br>
 * <br>
 * Usage example:
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
 * @see LocalEventBus
 * @see AnnotatedEventHandler
 */
public interface EventBus<EVENT_TYPE> {
    /**
     * Publish the event to all subscribers/consumer<br>
     * First we call all asynchronous subscribers, after which we will call all synchronous subscribers on the calling thread (i.e. on the same thread that the publish method is called on)
     *
     * @param event the event to publish
     * @return this bus instance
     */
    EventBus<EVENT_TYPE> publish(EVENT_TYPE event);

    /**
     * Add an asynchronous subscriber/consumer
     *
     * @param subscriber the subscriber to add
     * @return this bus instance
     */
    EventBus<EVENT_TYPE> addAsyncSubscriber(EventHandler<EVENT_TYPE> subscriber);

    /**
     * Remove an asynchronous subscriber/consumer
     *
     * @param subscriber the subscriber to remove
     * @return this bus instance
     */
    EventBus<EVENT_TYPE> removeAsyncSubscriber(EventHandler<EVENT_TYPE> subscriber);

    /**
     * Add a synchronous subscriber/consumer
     *
     * @param subscriber the subscriber to add
     * @return this bus instance
     */
    EventBus<EVENT_TYPE> addSyncSubscriber(EventHandler<EVENT_TYPE> subscriber);

    /**
     * Remove a synchronous subscriber/consumer
     *
     * @param subscriber the subscriber to remove
     * @return this bus instance
     */
    EventBus<EVENT_TYPE> removeSyncSubscriber(EventHandler<EVENT_TYPE> subscriber);

    boolean hasSyncSubscriber(EventHandler<EVENT_TYPE> subscriber);

    boolean hasAsyncSubscriber(EventHandler<EVENT_TYPE> subscriber);
}
