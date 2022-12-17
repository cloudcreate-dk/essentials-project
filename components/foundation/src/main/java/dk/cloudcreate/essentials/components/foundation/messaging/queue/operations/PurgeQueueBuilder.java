package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;

/**
 * Builder for {@link PurgeQueue}
 */
public class PurgeQueueBuilder {
    private QueueName queueName;

    /**
     *
     * @param queueName the name of the Queue where all the messages will be deleted
     * @return this builder instance
     */
    public PurgeQueueBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    public PurgeQueue build() {
        return new PurgeQueue(queueName);
    }
}