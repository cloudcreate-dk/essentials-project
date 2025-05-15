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

import java.util.Optional;

/**
 * <pre>
 * A no-op implementation of the {@link DurableQueuesStatistics} interface.
 * This class provides stub methods that do not perform any actual operations or retrieve
 * any statistics. All methods return empty {@link Optional} instances.
 *
 * This implementation can be used as a placeholder or default implementation where queue
 * statistics are not required or applicable.
 * </pre>
 */
public class NoOpDurableQueuesStatistics implements DurableQueuesStatistics {

    @Override
    public Optional<QueuedStatisticsMessage.QueueStatistics> getQueueStatistics(QueueName queueName) {
        return Optional.empty();
    }

    @Override
    public Optional<QueuedStatisticsMessage> getQueueStatisticsMessage(QueueEntryId id) {
        return Optional.empty();
    }
}
