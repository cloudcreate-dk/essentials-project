package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;
import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(QueueMessage, InterceptorChain)}
 */
public class QueueMessage {
    public final QueueName           queueName;
    private      Object              payload;
    private      Optional<Exception> causeOfEnqueuing;
    private      Optional<Duration>  deliveryDelay;

    /**
     * Create a new builder that produces a new {@link QueueMessage} instance
     *
     * @return a new {@link QueueMessageBuilder} instance
     */
    public static QueueMessageBuilder builder() {
        return new QueueMessageBuilder();
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName        the name of the Queue the message is added to
     * @param payload          the message payload
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @param deliveryDelay    the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     */
    public QueueMessage(QueueName queueName, Object payload, Optional<Exception> causeOfEnqueuing, Optional<Duration> deliveryDelay) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.payload = requireNonNull(payload, "No payload provided");
        this.causeOfEnqueuing = requireNonNull(causeOfEnqueuing, "No causeOfEnqueuing provided");
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    /**
     * @return the name of the Queue the message is added to
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     * @return the message payload
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * @return the optional reason for the message being queued
     */
    public Optional<Exception> getCauseOfEnqueuing() {
        return causeOfEnqueuing;
    }

    /**
     * @return the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     */
    public Optional<Duration> getDeliveryDelay() {
        return deliveryDelay;
    }

    /**
     * @param payload the message payload
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     * @param deliveryDelay the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    /**
     * @param causeOfEnqueuing the optional reason for the message being queued
     */
    public void setCauseOfEnqueuing(Optional<Exception> causeOfEnqueuing) {
        this.causeOfEnqueuing = requireNonNull(causeOfEnqueuing, "No causeOfEnqueuing provided");
    }

    /**
     * @param deliveryDelay the Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = Optional.ofNullable(deliveryDelay);
    }

    /**
     * @param causeOfEnqueuing the optional reason for the message being queued
     */
    public void setCauseOfEnqueuing(Exception causeOfEnqueuing) {
        this.causeOfEnqueuing = Optional.ofNullable(causeOfEnqueuing);
    }


    @Override
    public String toString() {
        return "QueueMessage{" +
                "queueName=" + queueName +
                ", payload=" + payload +
                ", causeOfEnqueuing=" + causeOfEnqueuing +
                ", deliveryDelay=" + deliveryDelay +
                '}';
    }
}
