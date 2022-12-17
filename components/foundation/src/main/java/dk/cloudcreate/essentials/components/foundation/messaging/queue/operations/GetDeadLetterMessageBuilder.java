package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueEntryId;

/**
 * Builder for {@link GetDeadLetterMessage}
 */
public class GetDeadLetterMessageBuilder {
    private QueueEntryId queueEntryId;

    /**
     * @param queueEntryId the messages unique queue entry id
     * @return this builder instance
     */
    public GetDeadLetterMessageBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    /**
     * Builder an {@link GetDeadLetterMessage} instance from the builder properties
     * @return the {@link GetDeadLetterMessage} instance
     */
    public GetDeadLetterMessage build() {
        return new GetDeadLetterMessage(queueEntryId);
    }
}