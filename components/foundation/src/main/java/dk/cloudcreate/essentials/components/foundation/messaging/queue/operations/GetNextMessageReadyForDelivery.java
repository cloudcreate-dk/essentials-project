package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matched {@link DurableQueuesInterceptor#intercept(GetNextMessageReadyForDelivery, InterceptorChain)}
 */
public class GetNextMessageReadyForDelivery {
    /**
     * the name of the Queue where we will query for the next message ready for delivery
     */
    public final QueueName           queueName;

    /**
     * Create a new builder that produces a new {@link GetNextMessageReadyForDelivery} instance
     *
     * @return a new {@link GetNextMessageReadyForDeliveryBuilder} instance
     */
    public static GetNextMessageReadyForDeliveryBuilder builder() {
        return new GetNextMessageReadyForDeliveryBuilder();
    }

    /**
     * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueName           the name of the Queue where we will query for the next message ready for delivery
     */
    public GetNextMessageReadyForDelivery(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
    }

    /**
     *
     * @return the name of the Queue where we will query for the next message ready for delivery
     */
    public QueueName getQueueName() {
        return queueName;
    }

    @Override
    public String toString() {
        return "GetNextMessageReadyForDelivery{" +
                "queueName=" + queueName +
                '}';
    }
}