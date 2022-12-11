package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Delete all messages (Queued or Dead letter Messages) in the given queue<br>
 * Operation matching {@link DurableQueuesInterceptor#intercept(PurgeQueue, InterceptorChain)}
 */
public class PurgeQueue {
    public final QueueName queueName;

    /**
     * Delete all messages (Queued or Dead letter Messages) in the given queue
     *
     * @param queueName     the name of the Queue the messages will be added to
     */
    public PurgeQueue(QueueName queueName) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
    }

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
