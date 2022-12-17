package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

/**
 * Builder for {@link GetQueuedMessages}
 */
public class GetQueuedMessagesBuilder {
    private QueueName queueName;
    private DurableQueues.QueueingSortOrder queueingSortOrder;
    private long                            startIndex;
    private long                            pageSize;

    /**
     *
     * @param queueName the name of the Queue where we will query for queued messages
     * @return this builder instance
     */
    public GetQueuedMessagesBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     *
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#getId()}
     * @return this builder instance
     */

    public GetQueuedMessagesBuilder setQueueingSortOrder(DurableQueues.QueueingSortOrder queueingSortOrder) {
        this.queueingSortOrder = queueingSortOrder;
        return this;
    }

    /**
     *
     * @param startIndex the index of the first message to include in the result (used for pagination)
     * @return this builder instance
     */
    public GetQueuedMessagesBuilder setStartIndex(long startIndex) {
        this.startIndex = startIndex;
        return this;
    }

    /**
     *
     * @param pageSize how many messages to include in the result (used for pagination)
     * @return this builder instance
     */
    public GetQueuedMessagesBuilder setPageSize(long pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    /**
     * Builder an {@link GetQueuedMessages} instance from the builder properties
     * @return the {@link GetQueuedMessages} instance
     */
    public GetQueuedMessages build() {
        return new GetQueuedMessages(queueName, queueingSortOrder, startIndex, pageSize);
    }
}