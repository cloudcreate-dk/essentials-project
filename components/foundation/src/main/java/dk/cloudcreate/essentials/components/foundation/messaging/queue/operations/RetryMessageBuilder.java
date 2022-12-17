package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

import java.time.Duration;

/**
 * Builder for {@link RetryMessage}
 */
public class RetryMessageBuilder {
    private QueueEntryId queueEntryId;
    private Exception causeForRetry;
    private Duration  deliveryDelay;

    /**
     *
     * @param queueEntryId the unique id of the message that must we will retry the delivery of
     * @return this builder instance
     */
    public RetryMessageBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    /**
     *
     * @param causeForRetry the reason why the message delivery has to be retried
     * @return this builder instance
     */
    public RetryMessageBuilder setCauseForRetry(Exception causeForRetry) {
        this.causeForRetry = causeForRetry;
        return this;
    }

    /**
     *
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public RetryMessageBuilder setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
        return this;
    }

    /**
     * Builder an {@link RetryMessage} instance from the builder properties
     * @return the {@link RetryMessage} instance
     */
    public RetryMessage build() {
        return new RetryMessage(queueEntryId, causeForRetry, deliveryDelay);
    }
}