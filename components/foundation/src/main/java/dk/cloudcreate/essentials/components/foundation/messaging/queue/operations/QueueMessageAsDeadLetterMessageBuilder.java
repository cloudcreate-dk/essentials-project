package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;

/**
 * Builder for {@link QueueMessageAsDeadLetterMessage}
 */
public class QueueMessageAsDeadLetterMessageBuilder {
    private QueueName queueName;
    private Object    payload;
    private Exception causeOfError;

    /**
     *
     * @param queueName the name of the Queue the message is added to
     * @return this builder instance
     */
    public QueueMessageAsDeadLetterMessageBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     *
     * @param payload the message payload
     * @return this builder instance
     */
    public QueueMessageAsDeadLetterMessageBuilder setPayload(Object payload) {
        this.payload = payload;
        return this;
    }

    /**
     *
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     * @return this builder instance
     */
    public QueueMessageAsDeadLetterMessageBuilder setCauseOfError(Exception causeOfError) {
        this.causeOfError = causeOfError;
        return this;
    }

    /**
     * Builder an {@link QueueMessageAsDeadLetterMessage} instance from the builder properties
     * @return the {@link QueueMessageAsDeadLetterMessage} instance
     */
    public QueueMessageAsDeadLetterMessage build() {
        return new QueueMessageAsDeadLetterMessage(queueName, payload, causeOfError);
    }
}