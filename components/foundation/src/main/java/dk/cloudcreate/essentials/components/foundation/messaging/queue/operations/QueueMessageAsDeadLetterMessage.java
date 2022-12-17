package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Queue the message directly as a Dead Letter Message. Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer}<br>
 * To deliver a Dead Letter Message you must first resurrect the message using {@link DurableQueues#resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(QueueMessageAsDeadLetterMessage, InterceptorChain)}
 */
public class QueueMessageAsDeadLetterMessage {
    public final QueueName queueName;
    private      Object    payload;
    private      Exception causeOfError;

    /**
     * Create a new builder that produces a new {@link QueueMessageAsDeadLetterMessage} instance
     *
     * @return a new {@link QueueMessageAsDeadLetterMessageBuilder} instance
     */
    public static QueueMessageAsDeadLetterMessageBuilder builder() {
        return new QueueMessageAsDeadLetterMessageBuilder();
    }

    /**
     * Queue the message directly as a Dead Letter Message. Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer}<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link DurableQueues#resurrectDeadLetterMessage(QueueEntryId, Duration)}
     *
     * @param queueName    the name of the Queue the message is added to
     * @param payload      the message payload
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     */
    public QueueMessageAsDeadLetterMessage(QueueName queueName, Object payload, Exception causeOfError) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.payload = requireNonNull(payload, "No payload provided");
        this.causeOfError = causeOfError;
    }

    /**
     *
     * @return the name of the Queue the message is added to
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     *
     * @return the message payload
     */
    public Object getPayload() {
        return payload;
    }

    /**
     *
     * @param payload the message payload
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     *
     * @return the reason for the message being queued directly as a Dead Letter Message
     */
    public Exception getCauseOfError() {
        return causeOfError;
    }

    /**
     *
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     */
    public void setCauseOfError(Exception causeOfError) {
        this.causeOfError = causeOfError;
    }

    @Override
    public String toString() {
        return "QueueMessageAsDeadLetterMessage{" +
                "queueName=" + queueName +
                ", payload=" + payload +
                ", causeOfError=" + causeOfError +
                '}';
    }
}
