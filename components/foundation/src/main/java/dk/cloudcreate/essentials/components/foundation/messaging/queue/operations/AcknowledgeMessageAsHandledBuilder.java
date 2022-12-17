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

    /**
     * Builder an {@link AcknowledgeMessageAsHandled} instance from the builder properties
     * @return the {@link AcknowledgeMessageAsHandled} instance
     */
    public AcknowledgeMessageAsHandled build() {
        return new AcknowledgeMessageAsHandled(queueEntryId);
    }
}