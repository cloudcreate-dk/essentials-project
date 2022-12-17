package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueEntryId;

/**
 * Builder for {@link MarkAsDeadLetterMessage}
 */
public class MarkAsDeadLetterMessageBuilder {
    private QueueEntryId queueEntryId;
    private Exception causeForBeingMarkedAsDeadLetter;

    /**
     *
     * @param queueEntryId the unique id of the message that must be marked as a Dead Letter Message
     * @return this builder instance
     */
    public MarkAsDeadLetterMessageBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    /**
     *
     * @param causeForBeingMarkedAsDeadLetter the reason for the message being marked as a Dead Letter Message
     * @return this builder instance
     */
    public MarkAsDeadLetterMessageBuilder setCauseForBeingMarkedAsDeadLetter(Exception causeForBeingMarkedAsDeadLetter) {
        this.causeForBeingMarkedAsDeadLetter = causeForBeingMarkedAsDeadLetter;
        return this;
    }

    public MarkAsDeadLetterMessage build() {
        return new MarkAsDeadLetterMessage(queueEntryId, causeForBeingMarkedAsDeadLetter);
    }
}