package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Get the total number of messages queued (i.e. not including Dead Letter Messages) for the given queue<br>
 * Operation also matched {@link DurableQueuesInterceptor#intercept(GetTotalMessagesQueuedFor, InterceptorChain)}
 */
public class GetTotalMessagesQueuedFor {
    /**
     * the name of the Queue where we will query for the number of queued messages
     */
    public final QueueName           queueName;

    /**
     * Create a new builder that produces a new {@link GetTotalMessagesQueuedFor} instance
     *
     * @return a new {@link GetTotalMessagesQueuedForBuilder} instance
     */
    public static GetTotalMessagesQueuedForBuilder builder() {
        return new GetTotalMessagesQueuedForBuilder();
    }

    /**
     * Get the total number of messages queued (i.e. not including Dead Letter Messages) for the given queue
     *
     * @param queueName the name of the Queue where we will query for the number of queued messages
     */
    public GetTotalMessagesQueuedFor(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
    }

    /**
     *
     * @return the name of the Queue where we will query for the number of queued messages
     */
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
