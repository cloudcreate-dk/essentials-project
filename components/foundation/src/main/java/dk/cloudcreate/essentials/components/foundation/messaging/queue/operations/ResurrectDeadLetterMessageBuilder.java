package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

import java.time.Duration;

/**
 * Builder for {@link ResurrectDeadLetterMessage}
 */
public class ResurrectDeadLetterMessageBuilder {
    private QueueEntryId queueEntryId;
    private Duration deliveryDelay;

    /**
     *
     * @param queueEntryId the unique id of the Dead Letter Message that must we will retry the delivery of
     * @return this builder instance
     */
    public ResurrectDeadLetterMessageBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    /**
     *
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public ResurrectDeadLetterMessageBuilder setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
        return this;
    }

    /**
     * Builder an {@link ResurrectDeadLetterMessage} instance from the builder properties
     * @return the {@link ResurrectDeadLetterMessage} instance
     */
    public ResurrectDeadLetterMessage build() {
        return new ResurrectDeadLetterMessage(queueEntryId, deliveryDelay);
    }
}