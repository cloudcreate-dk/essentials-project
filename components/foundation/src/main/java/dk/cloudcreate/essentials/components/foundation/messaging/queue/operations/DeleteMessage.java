/*
 * Copyright 2021-2024 the original author or authors.
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
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Delete a message (Queued or Dead Letter Message)<br>
 * Note this method MUST be called within an existing {@link UnitOfWork} IF
 * using {@link TransactionalMode#FullyTransactional}<br>
 * Operation also matches {@link DurableQueuesInterceptor#intercept(DeleteMessage, InterceptorChain)}
 */
public final class DeleteMessage {
    public final QueueEntryId queueEntryId;

    /**
     * Create a new builder that produces a new {@link DeleteMessage} instance
     *
     * @return a new {@link DeleteMessageBuilder} instance
     */
    public static DeleteMessageBuilder builder() {
        return new DeleteMessageBuilder();
    }

    /**
     * Delete a message (Queued or Dead Letter Message)<br>
     * Note this method MUST be called within an existing {@link UnitOfWork} IF
     * using {@link TransactionalMode#FullyTransactional}
     *
     * @param queueEntryId the unique id of the Message to delete
     */
    public DeleteMessage(QueueEntryId queueEntryId) {
        this.queueEntryId = requireNonNull(queueEntryId, "No queueEntryId provided");
    }

    /**
     *
     * @return the unique id of the Message to delete
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
