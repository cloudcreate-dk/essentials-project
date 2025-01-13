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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.GetNextMessageReadyForDelivery;

/**
 * Notification interface that {@link DurableQueueConsumer} can implement
 * if they want to be notified about messages being added to the queue they're consuming from<br>
 * This is required for {@link DurableQueueConsumer}'s that use a {@link QueuePollingOptimizer}
 */
public interface DurableQueueConsumerNotifications {
    /**
     * Notification from the {@link DurableQueues} implementation
     * that a new message has been added to the Queue that the given {@link DurableQueueConsumer}
     * is consuming messages from<br>
     * Note: A consumer still needs to use {@link DurableQueues#getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)}
     * to ensure proper message locking of messages that they are processing
     *
     * @param queuedMessage the message added
     */
    void messageAdded(QueuedMessage queuedMessage);
}
