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
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.shared.interceptor.InterceptorChain;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Mark the message as acknowledged - this operation also deletes the messages from the Queue<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(AcknowledgeMessageAsHandled, InterceptorChain)}
 */
public final class AcknowledgeMessageAsHandled {
    public final QueueEntryId queueEntryId;

    /**
     * Create a new builder that produces a new {@link AcknowledgeMessageAsHandled} instance
     *
     * @return a new {@link AcknowledgeMessageAsHandledBuilder} instance
     */
    public static AcknowledgeMessageAsHandledBuilder builder() {
        return new AcknowledgeMessageAsHandledBuilder();
    }

    /**
     * Mark the message as acknowledged - this operation deleted the messages from the Queue<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId the unique id of the Message to acknowledge
     */
    public AcknowledgeMessageAsHandled(QueueEntryId queueEntryId) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
    }

    /**
     *
     * @return the unique id of the Message to acknowledge
     */
    public QueueEntryId getQueueEntryId() {
        return queueEntryId;
    }

    @Override
    public String toString() {
        return "AcknowledgeMessageAsHandled{" +
                "queueEntryId=" + queueEntryId +
                '}';
    }
}
