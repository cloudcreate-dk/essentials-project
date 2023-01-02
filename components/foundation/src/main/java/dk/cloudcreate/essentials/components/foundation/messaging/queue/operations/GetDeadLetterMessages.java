/*
 * Copyright 2021-2023 the original author or authors.
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
     * Create a new builder that produces a new {@link GetDeadLetterMessages} instance
     *
     * @return a new {@link GetDeadLetterMessagesBuilder} instance
     */
    public static GetDeadLetterMessagesBuilder builder() {
        return new GetDeadLetterMessagesBuilder();
    }

    /**
     * Query Dead Letter Messages (i.e. not normal Queued Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for Dead letter messages
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#getId()}
     * @param startIndex        the index of the first message to include in the result (used for pagination)
     * @param pageSize          how many messages to include in the result (used for pagination)
     */
    public GetDeadLetterMessages(QueueName queueName, DurableQueues.QueueingSortOrder queueingSortOrder, long startIndex, long pageSize) {
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.queueingSortOrder = requireNonNull(queueingSortOrder, "No queueingSortOrder provided");;
        this.startIndex = startIndex;
        this.pageSize = pageSize;
    }

    /**
     *
     * @return the name of the Queue where we will query for Dead letter messages
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
        return "GetDeadLetterMessages{" +
                "queueName=" + queueName +
                ", queueingSortOrder=" + queueingSortOrder +
                ", startIndex=" + startIndex +
                ", pageSize=" + pageSize +
                '}';
    }
}
