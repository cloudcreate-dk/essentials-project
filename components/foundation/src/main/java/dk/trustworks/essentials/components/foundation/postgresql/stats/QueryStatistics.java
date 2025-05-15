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

package dk.trustworks.essentials.components.foundation.postgresql.stats;

/**
 * <pre>
 * Represents statistics for specific database queries.
 *
 * This record includes details about individual database queries, such as:
 * - The query string itself.
 * - The total execution time for the query.
 * - The number of times the query was called.
 * - The mean time per execution of the query.
 *
 * This structure is intended for use in monitoring and analyzing query
 * performance in a PostgreSQL database, particularly in scenarios
 * where `pg_stat_statements` or other query tracking tools are utilized.
 * </pre>
 */
public record QueryStatistics(
        String query,
        double totalTime,
        long calls,
        double meanTime
) {

}

