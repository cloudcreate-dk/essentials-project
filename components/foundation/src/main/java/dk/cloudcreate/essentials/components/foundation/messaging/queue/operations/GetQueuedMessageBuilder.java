package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueEntryId;

public class GetQueuedMessageBuilder {
    private QueueEntryId queueEntryId;

    /**
     * @param queueEntryId the messages unique queue entry id
     * @return this builder instance
     */
    public GetQueuedMessageBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    public GetQueuedMessage build() {
        return new GetQueuedMessage(queueEntryId);
    }
}