package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueueConsumer;

/**
 * Builder for {@link StopConsumingFromQueue}
 */
public class StopConsumingFromQueueBuilder {
    private DurableQueueConsumer durableQueueConsumer;

    /**
     * @param durableQueueConsumer the durable queue consumer being stopped
     * @return this builder instance
     */
    public StopConsumingFromQueueBuilder setDurableQueueConsumer(DurableQueueConsumer durableQueueConsumer) {
        this.durableQueueConsumer = durableQueueConsumer;
        return this;
    }

    /**
     * Builder an {@link StopConsumingFromQueue} instance from the builder properties
     *
     * @return the {@link StopConsumingFromQueue} instance
     */
    public StopConsumingFromQueue build() {
        return new StopConsumingFromQueue(durableQueueConsumer);
    }
}