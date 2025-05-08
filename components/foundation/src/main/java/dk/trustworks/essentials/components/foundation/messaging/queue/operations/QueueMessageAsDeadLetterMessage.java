/*
 * Copyright 2021-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.trustworks.essentials.components.foundation.messaging.queue.operations;

import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Queue the message directly as a Dead Letter Message. Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer}<br>
 * To deliver a Dead Letter Message you must first resurrect the message using {@link DurableQueues#resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(QueueMessageAsDeadLetterMessage, InterceptorChain)}
 */
public final class QueueMessageAsDeadLetterMessage {
    public final QueueName queueName;
    private      Message   message;
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
     * @param message      the message being enqueued directly as a Dead Letter Message  ({@link Message}/{@link OrderedMessage})
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     */
    public QueueMessageAsDeadLetterMessage(QueueName queueName, Message message, Exception causeOfError) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.message = requireNonNull(message, "No message provided");
        this.causeOfError = causeOfError;
    }

    /**
     * @return the name of the Queue the message is added to
     */
    public QueueName getQueueName() {
        return queueName;
    }

    /**
     * @return the message payload
     */
    public Object getPayload() {
        return getMessage().getPayload();
    }

    /**
     * Get the dead letter message
     *
     * @return the dead letter message  ({@link Message}/{@link OrderedMessage})
     */
    public Message getMessage() {
        return message;
    }

    /**
     *
     * @param message set the dead letter message  ({@link Message}/{@link OrderedMessage})
     */
    public void setMessage(Message message) {
        this.message = requireNonNull(message, "No message provided");
    }

    /**
     * @return the reason for the message being queued directly as a Dead Letter Message
     */
    public Exception getCauseOfError() {
        return causeOfError;
    }

    /**
     * @return metadata metadata related to the message/payload
     */
    public MessageMetaData getMetaData() {
        return getMessage().getMetaData();
    }

    /**
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     */
    public void setCauseOfError(Exception causeOfError) {
        this.causeOfError = causeOfError;
    }

    @Override
    public String toString() {
        return "QueueMessageAsDeadLetterMessage{" +
                "queueName=" + queueName +
                ", message=" + message +
                ", causeOfError=" + causeOfError +
                '}';
    }
}
