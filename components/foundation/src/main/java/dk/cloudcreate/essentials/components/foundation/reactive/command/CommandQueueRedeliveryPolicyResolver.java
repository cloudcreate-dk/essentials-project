package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Strategy that allows the {@link DurableLocalCommandBus} to vary the {@link RedeliveryPolicy}
 * per {@link QueueName}
 */
public interface CommandQueueRedeliveryPolicyResolver {
    /**
     * Resolve the {@link RedeliveryPolicy} to use for the given <code>queueName</code>
     *
     * @param queueName the name of the Queue that we're trying to resolve the {@link RedeliveryPolicy} for
     * @return the {@link RedeliveryPolicy} to use for the given Command Queue
     */
    RedeliveryPolicy resolveRedeliveryPolicyFor(QueueName queueName);

    /**
     * Apply the same {@link RedeliveryPolicy} independent on the {@link QueueName}
     *
     * @param redeliveryPolicy the {@link RedeliveryPolicy} used for all queues
     * @return resolve that uses the same {@link RedeliveryPolicy} independent on the {@link QueueName}
     */
    static CommandQueueRedeliveryPolicyResolver sameReliveryPolicyForAllCommandQueues(RedeliveryPolicy redeliveryPolicy) {
        requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
        return queueName -> redeliveryPolicy;
    }
}
