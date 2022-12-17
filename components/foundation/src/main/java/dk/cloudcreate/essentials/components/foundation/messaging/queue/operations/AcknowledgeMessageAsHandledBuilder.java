package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueEntryId;

/**
 * Builder for {@link AcknowledgeMessageAsHandled}
 */
public class AcknowledgeMessageAsHandledBuilder {
    private QueueEntryId queueEntryId;

    /**
     * @param queueEntryId the unique id of the Message to acknowledge
     * @return this builder instance
     */
    public AcknowledgeMessageAsHandledBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    public AcknowledgeMessageAsHandled build() {
        return new AcknowledgeMessageAsHandled(queueEntryId);
    }
}