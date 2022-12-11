package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Start an asynchronous message consumer.<br>
 * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
 * Operation also matches {@link DurableQueuesInterceptor#intercept(ConsumeFromQueue, InterceptorChain)}
 */
public class ConsumeFromQueue {
    public final QueueName            queueName;
    private      RedeliveryPolicy     redeliveryPolicy;
    public final int                  parallelConsumers;
    public final QueuedMessageHandler queueMessageHandler;

    /**
     * Start an asynchronous message consumer.<br>
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName} per {@link DurableQueues} instance
     *
     * @param queueName           the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @param redeliveryPolicy    the redelivery policy in case the handling of a message fails
     * @param parallelConsumers   the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @param queueMessageHandler the message handler that will receive {@link QueuedMessage}'s
     */
    public ConsumeFromQueue(QueueName queueName, RedeliveryPolicy redeliveryPolicy, int parallelConsumers, QueuedMessageHandler queueMessageHandler) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
        this.parallelConsumers = parallelConsumers;
        this.queueMessageHandler = requireNonNull(queueMessageHandler, "No queueMessageHandler provided");
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
    }

    public QueueName getQueueName() {
        return queueName;
    }

    public int getParallelConsumers() {
        return parallelConsumers;
    }

    public QueuedMessageHandler getQueueMessageHandler() {
        return queueMessageHandler;
    }

    @Override
    public String toString() {
        return "ConsumeFromQueue{" +
                "queueName=" + queueName +
                ", redeliveryPolicy=" + redeliveryPolicy +
                ", parallelConsumers=" + parallelConsumers +
                ", queueMessageHandler=" + queueMessageHandler +
                '}';
    }
}
