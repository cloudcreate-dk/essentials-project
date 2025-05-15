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

package dk.trustworks.essentials.components.foundation.messaging.queue.stats;

import dk.trustworks.essentials.components.foundation.messaging.queue.QueueEntryId;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueName;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.QueuedStatisticsMessage.QueueStatistics;

import java.util.Optional;

/**
 * <pre>
 * Provides methods to retrieve statistical information for durable queues and their messages.
 * This interface is designed to query queue-level and message-level statistics, enabling insights
 * into the operation and performance of queues and their contents.
 * </pre>
 */
public interface DurableQueuesStatistics {

    /**
     * Retrieves statistical information for the given queue.
     *
     * @param queueName the name of the queue for which statistics are requested
     * @return an {@link Optional} containing {@link QueueStatistics} if the statistics are available, or an empty {@link Optional} if no statistics exist for the provided queue
     */
    Optional<QueueStatistics> getQueueStatistics(QueueName queueName);

    /**
     * Retrieves statistical information associated with a specific message in a queue, identified by its unique
     * QueueEntryId. This method provides insights into the message's metadata and activity within the queue.
     *
     * @param id the unique identifier of the queue message for which statistical information is requested
     * @return an {@link Optional} containing {@link QueuedStatisticsMessage} if statistical data is available
     *         for the given QueueEntryId, or an empty {@link Optional} if no such data exists
     */
    Optional<QueuedStatisticsMessage> getQueueStatisticsMessage(QueueEntryId id);
}
