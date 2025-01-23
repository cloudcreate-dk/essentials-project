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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.Exceptions;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import java.time.Duration;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Mark an already Queued Message as a Dead Letter Message (or Poison Message).<br>
 * Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer} (called by the {@link DurableQueueConsumer})<br>
 * To deliver a Dead Letter Message you must first resurrect the message using {@link DurableQueues#resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(MarkAsDeadLetterMessage, InterceptorChain)}
 */
public final class MarkAsDeadLetterMessage {
    public final QueueEntryId queueEntryId;
    private      String       causeForBeingMarkedAsDeadLetter;

    /**
     * Create a new builder that produces a new {@link MarkAsDeadLetterMessage} instance
     *
     * @return a new {@link MarkAsDeadLetterMessageBuilder} instance
     */
    public static MarkAsDeadLetterMessageBuilder builder() {
        return new MarkAsDeadLetterMessageBuilder();
    }

    /**
     * Mark a Message as a Dead Letter Message (or Poison Message).  Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer} (called by the {@link DurableQueueConsumer})<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link DurableQueues#resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId                    the unique id of the message that must be marked as a Dead Letter Message
     */
    public MarkAsDeadLetterMessage(QueueEntryId queueEntryId) {
        this(queueEntryId, (String) null);
    }

    /**
     * Mark a Message as a Dead Letter Message (or Poison Message).  Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer} (called by the {@link DurableQueueConsumer})<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link DurableQueues#resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId                    the unique id of the message that must be marked as a Dead Letter Message
     * @param causeForBeingMarkedAsDeadLetter the optional reason for the message being marked as a Dead Letter Message
     */
    public MarkAsDeadLetterMessage(QueueEntryId queueEntryId, Throwable causeForBeingMarkedAsDeadLetter) {
        this(queueEntryId, causeForBeingMarkedAsDeadLetter != null ? Exceptions.getStackTrace(causeForBeingMarkedAsDeadLetter) : null);
    }

    /**
     * Mark a Message as a Dead Letter Message (or Poison Message).  Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer} (called by the {@link DurableQueueConsumer})<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link DurableQueues#resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId                    the unique id of the message that must be marked as a Dead Letter Message
     * @param causeForBeingMarkedAsDeadLetter the optional reason for the message being marked as a Dead Letter Message
     */
    public MarkAsDeadLetterMessage(QueueEntryId queueEntryId, String causeForBeingMarkedAsDeadLetter) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
        this.causeForBeingMarkedAsDeadLetter = causeForBeingMarkedAsDeadLetter;
    }

    /**
     * @return the unique id of the message that must be marked as a Dead Letter Message
     */
    public QueueEntryId getQueueEntryId() {
        return queueEntryId;
    }

    /**
     * @return the reason for the message being marked as a Dead Letter Message
     */
    public String getCauseForBeingMarkedAsDeadLetter() {
        return causeForBeingMarkedAsDeadLetter;
    }

    /**
     * @param causeForBeingMarkedAsDeadLetter the reason for the message being marked as a Dead Letter Message
     */
    public void setCauseForBeingMarkedAsDeadLetter(Throwable causeForBeingMarkedAsDeadLetter) {
        this.causeForBeingMarkedAsDeadLetter = causeForBeingMarkedAsDeadLetter != null ? Exceptions.getStackTrace(causeForBeingMarkedAsDeadLetter) : null;
    }

    /**
     * @param causeForBeingMarkedAsDeadLetter the reason for the message being marked as a Dead Letter Message
     */
    public void setCauseForBeingMarkedAsDeadLetter(String causeForBeingMarkedAsDeadLetter) {
        this.causeForBeingMarkedAsDeadLetter = causeForBeingMarkedAsDeadLetter;
    }

    @Override
    public String toString() {
        return "MarkAsDeadLetterMessage{" +
                "queueEntryId=" + queueEntryId +
                ", causeForBeingMarkedAsDeadLetter=" + causeForBeingMarkedAsDeadLetter +
                '}';
    }

    public void validate() {
        requireNonNull(queueEntryId, "You must provide a queueEntryId");
    }
}
