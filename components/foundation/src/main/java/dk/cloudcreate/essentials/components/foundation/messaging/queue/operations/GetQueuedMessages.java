package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Query Queued Messages (i.e. not including Dead Letter Messages) for the given Queue<br>
 * Operation also matched {@link DurableQueuesInterceptor#intercept(GetQueuedMessages, InterceptorChain)}
 */
public class GetQueuedMessages {
    public final QueueName           queueName;
    private DurableQueues.QueueingSortOrder queueingSortOrder;
    private long startIndex;
    private long pageSize;

    public static GetQueuedMessagesBuilder builder() {
        return new GetQueuedMessagesBuilder();
    }

    /**
     * Query Queued Messages (i.e. not including any Dead Letter Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for queued messages
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#getId()}
     * @param startIndex        the index of the first message to include in the result (used for pagination)
     * @param pageSize          how many messages to include in the result (used for pagination)
     */
    public GetQueuedMessages(QueueName queueName, DurableQueues.QueueingSortOrder queueingSortOrder, long startIndex, long pageSize) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.queueingSortOrder = requireNonNull(queueingSortOrder, "No queueingSortOrder provided");;
        this.startIndex = startIndex;
        this.pageSize = pageSize;
    }

    /**
     *
     * @return the name of the Queue where we will query for queued messages
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     *
     * @return the sort order for the {@link QueuedMessage#getId()}
     */
    public DurableQueues.QueueingSortOrder getQueueingSortOrder() {
        return queueingSortOrder;
    }

    /**
     *
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#getId()}
     */
    public void setQueueingSortOrder(DurableQueues.QueueingSortOrder queueingSortOrder) {
        this.queueingSortOrder = requireNonNull(queueingSortOrder, "No queueingSortOrder provided");;
    }

    /**
     *
     * @return the index of the first message to include in the result (used for pagination)
     */
    public long getStartIndex() {
        return startIndex;
    }

    /**
     *
     * @param startIndex the index of the first message to include in the result (used for pagination)
     */
    public void setStartIndex(long startIndex) {
        this.startIndex = startIndex;
    }

    /**
     *
     * @return how many messages to include in the result (used for pagination)
     */
    public long getPageSize() {
        return pageSize;
    }

    /**
     *
     * @param pageSize how many messages to include in the result (used for pagination)
     */
    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public String toString() {
        return "GetQueuedMessages{" +
                "queueName=" + queueName +
                ", queueingSortOrder=" + queueingSortOrder +
                ", startIndex=" + startIndex +
                ", pageSize=" + pageSize +
                '}';
    }
}
