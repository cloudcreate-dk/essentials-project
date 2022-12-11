package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Get the total number of messages queued (i.e. not including Dead Letter Messages) for the given queue<br>
 * Operation also matched {@link DurableQueuesInterceptor#intercept(GetTotalMessagesQueuedFor, InterceptorChain)}
 */
public class GetTotalMessagesQueuedFor {
    public final QueueName           queueName;

    /**
     * Get the total number of messages queued (i.e. not including Dead Letter Messages) for the given queue
     *
     * @param queueName the name of the Queue where we will query for queued messages
     */
    public GetTotalMessagesQueuedFor(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
    }

    public QueueName getQueueName() {
        return queueName;
    }

    @Override
    public String toString() {
        return "GetTotalMessagesQueuedFor{" +
                "queueName=" + queueName +
                '}';
    }
}
