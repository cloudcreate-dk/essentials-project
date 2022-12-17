package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Stop an asynchronous message consumer.<br>
 * Is initiated when {@link DurableQueueConsumer#cancel()} is called
 * Operation also matches {@link DurableQueuesInterceptor#intercept(StopConsumingFromQueue, InterceptorChain)}
 */
public class StopConsumingFromQueue {
    /**
     * The durable queue consumer being stopped
     */
    public final DurableQueueConsumer durableQueueConsumer;

    public static ConsumeFromQueueBuilder builder() {
        return new ConsumeFromQueueBuilder();
    }

    /**
     * Stop an asynchronous message consumer.<br>
     *
     * @param durableQueueConsumer the durable queue consumer being stopped
     */
    public StopConsumingFromQueue(DurableQueueConsumer durableQueueConsumer) {
        this.durableQueueConsumer = requireNonNull(durableQueueConsumer, "No durableQueueConsumer provided");
    }

    /**
     * @return the durable queue consumer being stopped
     */
    public DurableQueueConsumer getDurableQueueConsumer() {
        return durableQueueConsumer;
    }

    @Override
    public String toString() {
        return "StopConsumingFromQueue{" +
                "durableQueueConsumer=" + durableQueueConsumer +
                '}';
    }
}
