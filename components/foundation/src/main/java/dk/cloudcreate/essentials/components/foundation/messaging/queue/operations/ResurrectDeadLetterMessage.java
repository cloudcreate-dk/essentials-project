package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Resurrect a Dead Letter Message for redelivery after the specified <code>deliveryDelay</code><br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(ResurrectDeadLetterMessage, InterceptorChain)}
 */
public class ResurrectDeadLetterMessage {
    public final QueueEntryId queueEntryId;
    private      Duration     deliveryDelay;

    /**
     * Resurrect a Dead Letter Message for redelivery after the specified <code>deliveryDelay</code><br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId  the unique id of the Dead Letter Message that must we will retry the delivery of
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     */
    public ResurrectDeadLetterMessage(QueueEntryId queueEntryId, Duration deliveryDelay) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
        this.deliveryDelay = deliveryDelay;
    }

    public QueueEntryId getQueueEntryId() {
        return queueEntryId;
    }

    public Duration getDeliveryDelay() {
        return deliveryDelay;
    }

    public void setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
    }

    @Override
    public String toString() {
        return "ResurrectDeadLetterMessage{" +
                "queueEntryId=" + queueEntryId +
                ", deliveryDelay=" + deliveryDelay +
                '}';
    }
}
