package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;

/**
 * Builder for {@link GetTotalMessagesQueuedFor}
 */
public class GetTotalMessagesQueuedForBuilder {
    private QueueName queueName;

    /**
     *
     * @param queueName the name of the Queue where we will query for the number of queued messages
     * @return this builder instance
     */
    public GetTotalMessagesQueuedForBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     * Builder an {@link GetTotalMessagesQueuedFor} instance from the builder properties
     * @return the {@link GetTotalMessagesQueuedFor} instance
     */
    public GetTotalMessagesQueuedFor build() {
        return new GetTotalMessagesQueuedFor(queueName);
    }
}