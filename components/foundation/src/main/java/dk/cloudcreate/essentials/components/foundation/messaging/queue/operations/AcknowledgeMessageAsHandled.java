package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Mark the message as acknowledged - this operation also deletes the messages from the Queue<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(AcknowledgeMessageAsHandled, InterceptorChain)}
 */
public class AcknowledgeMessageAsHandled {
    public final QueueEntryId queueEntryId;

    /**
     * Create a new builder that produces a new {@link AcknowledgeMessageAsHandled} instance
     *
     * @return a new {@link AcknowledgeMessageAsHandledBuilder} instance
     */
    public static AcknowledgeMessageAsHandledBuilder builder() {
        return new AcknowledgeMessageAsHandledBuilder();
    }

    /**
     * Mark the message as acknowledged - this operation deleted the messages from the Queue<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId the unique id of the Message to acknowledge
     */
    public AcknowledgeMessageAsHandled(QueueEntryId queueEntryId) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
    }

    /**
     *
     * @return the unique id of the Message to acknowledge
     */
    public QueueEntryId getQueueEntryId() {
        return queueEntryId;
    }

    @Override
    public String toString() {
        return "AcknowledgeMessageAsHandled{" +
                "queueEntryId=" + queueEntryId +
                '}';
    }
}
