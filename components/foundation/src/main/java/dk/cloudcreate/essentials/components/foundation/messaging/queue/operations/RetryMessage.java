package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Schedule the message for redelivery after the specified <code>deliveryDelay</code> (called by the {@link DurableQueueConsumer})<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(RetryMessage, InterceptorChain)}
 */
public class RetryMessage {
    public final QueueEntryId queueEntryId;
    private      Exception    causeForRetry;
    private      Duration     deliveryDelay;

    /**
     * Schedule the message for redelivery after the specified <code>deliveryDelay</code> (called by the {@link DurableQueueConsumer})<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId  the unique id of the message that must we will retry the delivery of
     * @param causeForRetry the reason why the message delivery has to be retried
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     */
    public RetryMessage(QueueEntryId queueEntryId, Exception causeForRetry, Duration deliveryDelay) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
        this.causeForRetry = causeForRetry;
        this.deliveryDelay = deliveryDelay;
    }

    public QueueEntryId getQueueEntryId() {
        return queueEntryId;
    }

    public Exception getCauseForRetry() {
        return causeForRetry;
    }

    public void setCauseForRetry(Exception causeForRetry) {
        this.causeForRetry = causeForRetry;
    }

    public Duration getDeliveryDelay() {
        return deliveryDelay;
    }

    public void setDeliveryDelay(Duration deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
    }

    @Override
    public String toString() {
        return "RetryMessage{" +
                "queueEntryId=" + queueEntryId +
                ", causeForRetry=" + causeForRetry +
                ", deliveryDelay=" + deliveryDelay +
                '}';
    }
}
