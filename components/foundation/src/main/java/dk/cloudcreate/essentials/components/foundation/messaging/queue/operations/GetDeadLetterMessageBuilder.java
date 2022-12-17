package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueEntryId;

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

    public GetDeadLetterMessage build() {
        return new GetDeadLetterMessage(queueEntryId);
    }
}