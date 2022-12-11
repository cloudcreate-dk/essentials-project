package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Query Dead Letter Messages (i.e. not normal Queued Messages) for the given Queue<br>
 * Operation also matched {@link DurableQueuesInterceptor#intercept(GetDeadLetterMessages, InterceptorChain)}
 */
public class GetDeadLetterMessages {
    public final QueueName           queueName;
    private DurableQueues.QueueingSortOrder queueingSortOrder;
    private long startIndex;
    private long pageSize;

    /**
     * Query Dead Letter Messages (i.e. not normal Queued Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for queued messages
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#getId()}
     * @param startIndex        the index of the first message to include (used for pagination)
     * @param pageSize          how many messages to include (used for pagination)
     */
    public GetDeadLetterMessages(QueueName queueName, DurableQueues.QueueingSortOrder queueingSortOrder, long startIndex, long pageSize) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.queueingSortOrder = requireNonNull(queueingSortOrder, "No queueingSortOrder provided");;
        this.startIndex = startIndex;
        this.pageSize = pageSize;
    }

    public QueueName getQueueName() {
        return queueName;
    }

    public DurableQueues.QueueingSortOrder getQueueingSortOrder() {
        return queueingSortOrder;
    }

    public void setQueueingSortOrder(DurableQueues.QueueingSortOrder queueingSortOrder) {
        this.queueingSortOrder = requireNonNull(queueingSortOrder, "No queueingSortOrder provided");;
    }

    public long getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(long startIndex) {
        this.startIndex = startIndex;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public String toString() {
        return "GetDeadLetterMessages{" +
                "queueName=" + queueName +
                ", queueingSortOrder=" + queueingSortOrder +
                ", startIndex=" + startIndex +
                ", pageSize=" + pageSize +
                '}';
    }
}
