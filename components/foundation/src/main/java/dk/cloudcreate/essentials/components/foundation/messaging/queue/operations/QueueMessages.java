package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}
 * Operation also matches {@link DurableQueuesInterceptor#intercept(QueueMessages, InterceptorChain)}
 */
public class QueueMessages {
    public final QueueName queueName;
    public final List<?>   payloads;
    private Optional<Duration> deliveryDelay;

    /**
     * Create a new builder that produces a new {@link QueueMessages} instance
     *
     * @return a new {@link QueueMessagesBuilder} instance
     */
    public static QueueMessagesBuilder builder() {
        return new QueueMessagesBuilder();
    }

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the messages will be added to
     * @param payloads      the message payloads
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public QueueMessages(QueueName queueName, List<?> payloads, Optional<Duration> deliveryDelay) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.payloads = requireNonNull(payloads, "No payloads provided");
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the messages will be added to
     * @param payloads      the message payloads
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public QueueMessages(QueueName queueName, List<?> payloads, Duration deliveryDelay) {
        this(queueName,
             payloads,
             Optional.ofNullable(deliveryDelay));
    }

    /**
     *
     * @return the name of the Queue the messages will be added to
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     *
     * @return the message payloads
     */
    public List<?> getPayloads() {
        return payloads;
    }

    /**
     *
     * @return optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public Optional<Duration> getDeliveryDelay() {
        return deliveryDelay;
    }

    /**
     *
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    /**
     *
     * @param deliveryDelay optional: how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public void setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = Optional.ofNullable(deliveryDelay);
    }

    @Override
    public String toString() {
        return "QueueMessages{" +
                "queueName=" + queueName +
                ", payloads=" + payloads +
                '}';
    }

    public void validate() {
        requireNonNull(queueName, "You must provide a queueName");
        requireNonNull(payloads, "You must provide a payloads list");
        requireNonNull(deliveryDelay, "You must provide a deliveryDelay option");
    }
}
