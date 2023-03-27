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

/**
 * Builder for {@link GetQueuedMessages}
 */
public class GetQueuedMessagesBuilder {
    private QueueName queueName;
    private DurableQueues.QueueingSortOrder queueingSortOrder = DurableQueues.QueueingSortOrder.ASC;
    private long                            startIndex;
    private long                            pageSize = 20;

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