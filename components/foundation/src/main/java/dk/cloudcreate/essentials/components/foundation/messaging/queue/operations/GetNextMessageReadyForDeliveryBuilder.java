package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;

/**
 * Builder for {@link GetNextMessageReadyForDelivery}
 */
public class GetNextMessageReadyForDeliveryBuilder {
    private QueueName queueName;

    /**
     *
     * @param queueName the name of the Queue where we will query for the next message ready for delivery
     * @return this builder instance
     */
    public GetNextMessageReadyForDeliveryBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     * Builder an {@link GetNextMessageReadyForDelivery} instance from the builder properties
     * @return the {@link GetNextMessageReadyForDelivery} instance
     */
    public GetNextMessageReadyForDelivery build() {
        return new GetNextMessageReadyForDelivery(queueName);
    }
}