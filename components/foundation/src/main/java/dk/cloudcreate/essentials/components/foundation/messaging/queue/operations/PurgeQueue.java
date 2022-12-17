package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Delete all messages (Queued or Dead letter Messages) in the given queue<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(PurgeQueue, InterceptorChain)}
 */
public class PurgeQueue {
    public final QueueName queueName;

    /**
     * Create a new builder that produces a new {@link PurgeQueue} instance
     *
     * @return a new {@link PurgeQueueBuilder} instance
     */
    public static PurgeQueueBuilder builder() {
        return new PurgeQueueBuilder();
    }

    /**
     * Delete all messages (Queued or Dead letter Messages) in the given queue
     *
     * @param queueName the name of the Queue where all the messages will be deleted
     */
    public PurgeQueue(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
    }

    /**
     * @return the name of the Queue where all the messages will be deleted
     */
    public QueueName getQueueName() {
        return queueName;
    }

    @Override
    public String toString() {
        return "PurgeQueue{" +
                "queueName=" + queueName +
                '}';
    }
}
