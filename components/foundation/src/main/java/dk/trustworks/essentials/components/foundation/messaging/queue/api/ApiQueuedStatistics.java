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

import dk.trustworks.essentials.components.foundation.messaging.queue.QueueName;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.QueuedStatisticsMessage.QueueStatistics;

import java.time.OffsetDateTime;

/**
 * Represents statistics for messages queued in a specific durable queue.
 * This record provides details such as the name of the queue, the time range
 * of the statistics, the total number of messages delivered, average latency,
 * and the timestamps of the first and last deliveries.
 */
public record ApiQueuedStatistics(
        QueueName queueName,
        OffsetDateTime fromTimestamp,
        long totalMessagesDelivered,
        int avgDeliveryLatencyMs,
        OffsetDateTime firstDelivery,
        OffsetDateTime lastDelivery
) {

    public static ApiQueuedStatistics from(QueueStatistics queueStatistics) {
        return new ApiQueuedStatistics(
                queueStatistics.queueName(),
                queueStatistics.fromTimestamp(),
                queueStatistics.totalMessagesDelivered(),
                queueStatistics.avgDeliveryLatencyMs(),
                queueStatistics.firstDelivery(),
                queueStatistics.lastDelivery()
        );
    }
}
