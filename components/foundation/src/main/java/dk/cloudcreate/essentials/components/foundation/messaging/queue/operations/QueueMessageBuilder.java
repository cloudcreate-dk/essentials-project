package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

import java.time.Duration;
import java.util.Optional;

public class QueueMessageBuilder {
    private QueueName queueName;
    private Object              payload;
    private Optional<Exception> causeOfEnqueuing;
    private Optional<Duration>  deliveryDelay;

    /**
     *
     * @param queueName the name of the Queue the message is added to
     * @return this builder instance
     */
    public QueueMessageBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     *
     * @param payload the message payload
     * @return this builder instance
     */
    public QueueMessageBuilder setPayload(Object payload) {
        this.payload = payload;
        return this;
    }

    /**
     *
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @return this builder instance
     */
    public QueueMessageBuilder setCauseOfEnqueuing(Optional<Exception> causeOfEnqueuing) {
        this.causeOfEnqueuing = causeOfEnqueuing;
        return this;
    }

    /**
     *
     * @param deliveryDelay the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public QueueMessageBuilder setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
        return this;
    }

    /**
     *
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @return this builder instance
     */
    public QueueMessageBuilder setCauseOfEnqueuing(Exception causeOfEnqueuing) {
        this.causeOfEnqueuing = Optional.ofNullable(causeOfEnqueuing);
        return this;
    }

    /**
     *
     * @param deliveryDelay the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public QueueMessageBuilder setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = Optional.ofNullable(deliveryDelay);
        return this;
    }

    public QueueMessage build() {
        return new QueueMessage(queueName, payload, causeOfEnqueuing, deliveryDelay);
    }
}