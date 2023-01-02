package dk.cloudcreate.essentials.reactive;

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
public interface EventBus {
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
