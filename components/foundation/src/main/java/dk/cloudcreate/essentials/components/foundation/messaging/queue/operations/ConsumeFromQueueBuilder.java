package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

public class ConsumeFromQueueBuilder {
    private QueueName            queueName;
    private RedeliveryPolicy     redeliveryPolicy;
    private int                  parallelConsumers;
    private QueuedMessageHandler queueMessageHandler;

    /**
     * @param queueName the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }


    /**
     * @param redeliveryPolicy the redelivery policy in case the handling of a message fails
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
        return this;
    }

    /**
     * @param parallelConsumers the number of parallel consumers (if number > 1 then you will effectively have competing consumers on the current node)
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setParallelConsumers(int parallelConsumers) {
        this.parallelConsumers = parallelConsumers;
        return this;
    }

    /**
     * @param queueMessageHandler the message handler that will receive {@link QueuedMessage}'s
     * @return this builder instance
     */
    public ConsumeFromQueueBuilder setQueueMessageHandler(QueuedMessageHandler queueMessageHandler) {
        this.queueMessageHandler = queueMessageHandler;
        return this;
    }

    public ConsumeFromQueue build() {
        return new ConsumeFromQueue(queueName, redeliveryPolicy, parallelConsumers, queueMessageHandler);
    }
}