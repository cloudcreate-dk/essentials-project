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

package dk.trustworks.essentials.components.foundation.postgresql.api;

import dk.trustworks.essentials.components.foundation.postgresql.stats.QueryStatistics;

/**
 * <pre>
 * Represents statistics for a specific database query in an API context.
 *
 * This record encapsulates details about a query, including:
 * - The query string itself.
 * - The total execution time spent on the query.
 * - The number of times the query was executed.
 * - The average execution time per query call.
 *
 * The {@link #from(QueryStatistics)} method allows conversion from a
 * {@code QueryStatistics} object, providing a bridge between a domain-specific
 * representation of query statistics and the API layer.
 * </pre>
 */
public record ApiQueryStatistics(
        String query,
        double totalTime,
        long calls,
        double meanTime
) {

    public static ApiQueryStatistics from(QueryStatistics queryStats) {
        return new ApiQueryStatistics(queryStats.query(),
                queryStats.totalTime(),
                queryStats.calls(),
                queryStats.meanTime());
    }
}
