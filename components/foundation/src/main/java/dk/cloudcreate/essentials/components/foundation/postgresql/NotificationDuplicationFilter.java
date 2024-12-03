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

package dk.cloudcreate.essentials.components.foundation.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import org.postgresql.core.Notification;

import java.util.Optional;

/**
 * Interface for extracting a unique key from the JSON representation
 * of a {@link Notification#getParameter()} JSON to determine if the notification should be duplicate.<br>
 * The key extracted from {@link NotificationDuplicationFilter#extractDuplicationKey(JsonNode)}
 * will be used inside {@link MultiTableChangeListener} for duplication checks <b>across all {@link Notification}'s returned in a single poll</b>.<br>
 * If an empty {@link Optional} is returned then the given notification won't be deduplicated.<br>
 * If two or more {@link Notification}'s in a given pull batch share the same duplication key, then only one of them will be published to the listeners registered with the {@link MultiTableChangeListener}<br>
 */
public interface NotificationDuplicationFilter {
    /**
     * Extracts the duplication key from the given parameter JSON.
     *
     * @param notificationParameterJson a JsonNode representing the JSON structure of the {@link Notification#getParameter()} JSON content
     * @return an {@link Optional} containing the unique key if extraction was successful, otherwise an empty {@link Optional} if this filter doesn't support the given parameter JSON or no filtering is required
     * This key will be used inside {@link MultiTableChangeListener} for duplication checks across all {@link Notification}'s returned in one pull
     */
    Optional<String> extractDuplicationKey(JsonNode notificationParameterJson);

    /**
     * Default filter to extract the "table_name" property from a JSON parameter.
     */
    class TableNameDuplicationFilter implements NotificationDuplicationFilter {

        /**
         * Extracts the "table_name" field from the given JSON parameter.
         * Example {@link Notification#getParameter()} content:
         * <pre>{@code
         * {
         *   "table_name" : "table1",
         *   "sql_operation" : "INSERT",
         *   "id" : 2,
         *   "column1" : "Column1Value-2",
         *   "column2" : "Column2Value-2"
         * }
         * }</pre>
         *
         * @param parameterJson a JsonNode representing the JSON structure of the notification parameter
         * @return an Optional containing the table name if it exists, otherwise an empty Optional
         */
        @Override
        public Optional<String> extractDuplicationKey(JsonNode parameterJson) {
            return Optional.ofNullable(parameterJson.has("table_name") ? parameterJson.get("table_name").asText() : null);
        }
    }
}