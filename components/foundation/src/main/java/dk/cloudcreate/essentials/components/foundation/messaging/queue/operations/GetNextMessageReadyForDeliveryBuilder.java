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

import java.util.*;

/**
 * Builder for {@link GetNextMessageReadyForDelivery}
 */
public final class GetNextMessageReadyForDeliveryBuilder {
    private QueueName          queueName;
    private Collection<String> excludeOrderedMessagesWithKey = List.of();

    /**
     * @param queueName the name of the Queue where we will query for the next message ready for delivery
     * @return this builder instance
     */
    public GetNextMessageReadyForDeliveryBuilder setQueueName(QueueName queueName) {
        this.queueName = queueName;
        return this;
    }

    /**
     * @param excludeOrderedMessagesWithKey collection of {@link OrderedMessage#getKey()}'s to exclude in the search for the next message
     * @return this builder instance
     */
    public GetNextMessageReadyForDeliveryBuilder setExcludeOrderedMessagesWithKey(Collection<String> excludeOrderedMessagesWithKey) {
        this.excludeOrderedMessagesWithKey = excludeOrderedMessagesWithKey;
        return this;
    }

    /**
     * Builder an {@link GetNextMessageReadyForDelivery} instance from the builder properties
     *
     * @return the {@link GetNextMessageReadyForDelivery} instance
     */
    public GetNextMessageReadyForDelivery build() {
        return new GetNextMessageReadyForDelivery(queueName,
                                                  excludeOrderedMessagesWithKey);
    }
}