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

package dk.trustworks.essentials.components.foundation.postgresql;

import com.fasterxml.jackson.databind.*;
import org.postgresql.core.Notification;

import java.util.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Manages a chain of {@link NotificationDuplicationFilter} instances and uses them
 * to extract unique keys from the {@link Notification#getParameter()} JSON content.<br>
 * The key extracted from {@link NotificationDuplicationFilter#extractDuplicationKey(JsonNode)}
 * will be used inside {@link MultiTableChangeListener} for duplication checks across all {@link Notification}'s returned in one poll.<br>
 * If an empty {@link Optional} is returned then the given notification won't be deduplicated.<br>
 * If two or more {@link Notification}'s in a given poll batch share the same duplication key, then only one of them will be published to the listeners registered with the {@link MultiTableChangeListener}
 */
public class NotificationFilterChain {
    private final List<NotificationDuplicationFilter> filters = new ArrayList<>();
    private final ObjectMapper objectMapper;

    public NotificationFilterChain(ObjectMapper objectMapper) {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Adds a custom {@link NotificationDuplicationFilter} to the filter chain as the very first (highest priority).<br>
     * Note: Only a single instance of a particular {@link NotificationDuplicationFilter},
     * determined by calling {@link Object#equals(Object)} on the filters, can be added
     *
     * @param filter the filter to be added to the chain
     * @return true if the filter was added as the first, otherwise false (e.g. the filter was already added)
     */
    public boolean addFilterAsFirst(NotificationDuplicationFilter filter) {
        if (!filters.contains(filter)) {
            filters.add(0, filter);
            return true;
        }
        return false;
    }

    /**
     * Adds a custom {@link NotificationDuplicationFilter} to the filter chain as the very last (lowest priority).<br>
     * Note: Only a single instance of a particular {@link NotificationDuplicationFilter},
     * determined by calling {@link Object#equals(Object)} on the filters, can be added
     *
     * @param filter the filter to be added to the chain
     * @return true if the filter was added as the last, otherwise false (e.g. the filter was already added)
     */
    public boolean addFilterAsLast(NotificationDuplicationFilter filter) {
        if (!filters.contains(filter)) {
            return filters.add(filter);
        }
        return false;
    }

    /**
     * Removes a custom {@link NotificationDuplicationFilter} from the filter chain.
     *
     * @param filter the filter to be added to the chain
     */
    public void removeFilter(NotificationDuplicationFilter filter) {
        filters.remove(filter);
    }

    /**
     * Extracts the duplication key from the given JSON parameter string using the registered {@link NotificationDuplicationFilter}'s in sequence.
     * The first {@link NotificationDuplicationFilter} to return a non-empty {@link Optional} determines the key.<br>
     * If an empty {@link Optional} is returned then the given notification won't be deduplicated.
     *
     * @param parameterJson the JSON string representation of the  {@link Notification#getParameter()} JSON content.
     * @return an {@link Optional} containing the unique key if extraction was successful, otherwise an empty {@link Optional}.
     * @throws IllegalArgumentException if the JSON is invalid or no unique key can be extracted
     */
    public Optional<String> extractDuplicationKey(String parameterJson) {
        requireNonNull(parameterJson, "parameterJson must not be null");
        try {
            JsonNode jsonNode = objectMapper.readTree(parameterJson);

            // Apply registered filters
            for (NotificationDuplicationFilter filter : filters) {
                Optional<String> key = filter.extractDuplicationKey(jsonNode);
                if (key.isPresent()) {
                    return key;
                }
            }

            // No filtering
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + parameterJson, e);
        }
    }

    /**
     * Are there any {@link NotificationDuplicationFilter} added to the chain?
     * @return if there are any {@link NotificationDuplicationFilter} added to the chain?
     */
    public boolean hasDuplicationFilters() {
        return !filters.isEmpty();
    }
}
