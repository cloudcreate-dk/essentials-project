package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

/**
 * Builder for {@link GetDeadLetterMessages}
 */
public class GetDeadLetterMessagesBuilder {
    private QueueName queueName;
    private DurableQueues.QueueingSortOrder queueingSortOrder;
    private long                            startIndex;
    private long                            pageSize;

    /**
     *
     * @param queueName the name of the Queue where we will query for Dead letter messages
     * @return this builder instance
     */
    public GetDeadLetterMessagesBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     *
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#getId()}
     * @return this builder instance
     */
    public GetDeadLetterMessagesBuilder setQueueingSortOrder(DurableQueues.QueueingSortOrder queueingSortOrder) {
        this.queueingSortOrder = queueingSortOrder;
        return this;
    }

    /**
     *
     * @param startIndex the index of the first message to include in the result (used for pagination)
     * @return this builder instance
     */
    public GetDeadLetterMessagesBuilder setStartIndex(long startIndex) {
        this.startIndex = startIndex;
        return this;
    }

    /**
     *
     * @param pageSize how many messages to include in the result (used for pagination)
     * @return this builder instance
     */
    public GetDeadLetterMessagesBuilder setPageSize(long pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public GetDeadLetterMessages build() {
        return new GetDeadLetterMessages(queueName, queueingSortOrder, startIndex, pageSize);
    }
}