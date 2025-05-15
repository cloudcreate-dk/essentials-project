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

package dk.trustworks.essentials.components.foundation.messaging.queue.api;

import dk.trustworks.essentials.components.foundation.messaging.queue.DurableQueues.QueueingSortOrder;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueEntryId;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueName;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The DurableQueuesApi provides an interface for interacting with durable queues, enabling
 * message management, retrieval, and queue-specific operations. It offers methods to handle
 * messages, retrieve queue statistics, and manage dead-letter queues.
 */
public interface DurableQueuesApi {

    Set<QueueName> getQueueNames(Object principal);

    Optional<QueueName> getQueueNameFor(Object principal, QueueEntryId queueEntryId);

    Optional<ApiQueuedMessage> getQueuedMessage(Object principal, QueueEntryId queueEntryId);

    Optional<ApiQueuedMessage> resurrectDeadLetterMessage(Object principal, QueueEntryId queueEntryId,
                                                       Duration deliveryDelay);

    Optional<ApiQueuedMessage> markAsDeadLetterMessage(Object principal, QueueEntryId queueEntryId);

    boolean deleteMessage(Object principal, QueueEntryId queueEntryId);

    long getTotalMessagesQueuedFor(Object principal, QueueName queueName);

    long getTotalDeadLetterMessagesQueuedFor(Object principal, QueueName queueName);

    List<ApiQueuedMessage> getQueuedMessages(Object principal,
                                          QueueName queueName,
                                          QueueingSortOrder queueingSortOrder,
                                          long startIndex,
                                          long pageSize);

    List<ApiQueuedMessage> getDeadLetterMessages(Object principal,
                                              QueueName queueName,
                                              QueueingSortOrder queueingSortOrder,
                                              long startIndex,
                                              long pageSize);

    int purgeQueue(Object principal, QueueName queueName);

    Optional<ApiQueuedStatistics> getQueuedStatistics(Object principal, QueueName queueName);
}
