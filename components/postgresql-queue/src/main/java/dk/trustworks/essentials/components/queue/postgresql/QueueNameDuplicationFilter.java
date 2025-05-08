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

package dk.trustworks.essentials.components.queue.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueName;
import dk.trustworks.essentials.components.foundation.postgresql.*;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import org.postgresql.core.Notification;

import java.util.Optional;

/**
 * Custom filter to extract the "queue_name" property from the Postgresql {@link Notification#getParameter()} JSON content.<br>
 * This filter is useful when dealing with multiple {@link org.postgresql.core.Notification}'s involving the same queue.<br>
 * Example: if 100 messages are queued for the same {@link QueueName} in the same {@link UnitOfWork}, then the {@link MultiTableChangeListener} will notify
 * the {@link PostgresqlDurableQueues} with 100 {@link TableChangeNotification}'s. Using the {@link QueueNameDuplicationFilter} the {@link PostgresqlDurableQueues}
 * will only receive a single {@link TableChangeNotification} for that specific {@link QueueName}. Filtering is only performed on Notifications
 * returned in a single database notification poll
 * @see QueueNameDuplicationFilter
 */
public class QueueNameDuplicationFilter implements NotificationDuplicationFilter {

    /**
     * Extracts the "queue_name" field from the given JSON parameter, if it exists.<br>
     * Example {@link Notification#getParameter()} content:
     * <pre>{@code
     * {
     *   "table_name" : "durable_queues",
     *   "sql_operation" : "INSERT",
     *   "id" : "d897f4eb-ed90-4b10-9479-971bf9b26fd3",
     *   "queue_name" : "TestQueue",
     *   "added_ts" : "2024-11-26T17:09:41.279749+01:00",
     *   "next_delivery_ts" : "2024-11-26T17:09:41.279749+01:00",
     *   "delivery_ts" : null,
     *   "is_dead_letter_message" : false,
     *   "is_being_delivered" : false
     * }
     * }</pre>
     *
     * @param parameterJson a JsonNode representing the JSON structure of the notification parameter
     * @return an Optional containing the queue name if it exists, otherwise an empty Optional
     */
    @Override
    public Optional<String> extractDuplicationKey(JsonNode parameterJson) {
        return Optional.ofNullable(parameterJson.has("queue_name") ? parameterJson.get("queue_name").asText() : null);
    }
}

