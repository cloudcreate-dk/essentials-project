package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

import java.time.Duration;
import java.util.*;

/**
 * Builder for {@link QueueMessages}
 */
public class QueueMessagesBuilder {
    private QueueName          queueName;
    private List<?>            payloads;
    private Optional<Duration> deliveryDelay;

    /**
     *
     * @param queueName the name of the Queue the messages will be added to
     * @return this builder instance
     */
    public QueueMessagesBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     *
     * @param payloads the message payloads
     * @return this builder instance
     */
    public QueueMessagesBuilder setPayloads(List<?> payloads) {
        this.payloads = payloads;
        return this;
    }

    /**
     *
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public QueueMessagesBuilder setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
        return this;
    }

    /**
     *
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     * @return this builder instance
     */
    public QueueMessagesBuilder setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = Optional.ofNullable(deliveryDelay);
        return this;
    }

    /**
     * Builder an {@link QueueMessages} instance from the builder properties
     * @return the {@link QueueMessages} instance
     */
    public QueueMessages build() {
        return new QueueMessages(queueName, payloads, deliveryDelay);
    }
}