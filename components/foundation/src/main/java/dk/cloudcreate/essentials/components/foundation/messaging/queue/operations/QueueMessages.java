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
 * Operation matching {@link DurableQueuesInterceptor#intercept(QueueMessages, InterceptorChain)}
 */
public class QueueMessages {
    public final QueueName queueName;
    public final List<?>   payloads;
    private Optional<Duration> deliveryDelay;

    /**
     * Queue multiple messages to the same queue. All the messages will receive the same {@link QueuedMessage#getNextDeliveryTimestamp()}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName     the name of the Queue the messages will be added to
     * @param payloads      the message payloads
     * @param deliveryDelay how long will the queue wait until it delivers the messages to the {@link DurableQueueConsumer}
     */
    public QueueMessages(QueueName queueName, List<?> payloads, Optional<Duration> deliveryDelay) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.payloads = requireNonNull(payloads, "No payloads provided");
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    public QueueName getQueueName() {
        return queueName;
    }

    public List<?> getPayloads() {
        return payloads;
    }

    public Optional<Duration> getDeliveryDelay() {
        return deliveryDelay;
    }

    public void setDeliveryDelay(Optional<Duration> deliveryDelay) {
        this.deliveryDelay = requireNonNull(deliveryDelay, "No deliveryDelay provided");
    }

    @Override
    public String toString() {
        return "QueueMessages{" +
                "queueName=" + queueName +
                ", payloads=" + payloads +
                '}';
    }
}
