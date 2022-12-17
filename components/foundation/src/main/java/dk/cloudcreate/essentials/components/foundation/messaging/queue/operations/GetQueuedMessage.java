package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Get a queued message that is NOT marked as a {@link QueuedMessage#isDeadLetterMessage}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(GetQueuedMessage, InterceptorChain)}
 */
public class GetQueuedMessage {
    /**
     * the messages unique queue entry id
     */
    public final QueueEntryId queueEntryId;

    public static GetQueuedMessageBuilder builder() {
        return new GetQueuedMessageBuilder();
    }

    /**
     * Get a queued message that is NOT marked as a {@link QueuedMessage#isDeadLetterMessage}<br>
     *
     * @param queueEntryId the messages unique queue entry id
     */
    public GetQueuedMessage(QueueEntryId queueEntryId) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
    }

    /**
     *
     * @return the messages unique queue entry id
     */
    public QueueEntryId getQueueEntryId() {
        return queueEntryId;
    }

    @Override
    public String toString() {
        return "GetQueuedMessage{" +
                "queueEntryId=" + queueEntryId +
                '}';
    }
}
