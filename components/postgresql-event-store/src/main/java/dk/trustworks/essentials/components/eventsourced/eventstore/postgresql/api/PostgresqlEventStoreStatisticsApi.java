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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.api;

import dk.trustworks.essentials.components.foundation.postgresql.api.ApiTableActivityStatistics;
import dk.trustworks.essentials.components.foundation.postgresql.api.ApiTableCacheHitRatio;
import dk.trustworks.essentials.components.foundation.postgresql.api.ApiTableSizeStatistics;

import java.util.Map;

/**
 * Interface for fetching various statistics related to the PostgreSQL event store.
 * This API provides methods to retrieve insights into table size, activity, and caching efficiency
 * for tables used in the event store.
 */
public interface PostgresqlEventStoreStatisticsApi {

    /**
     * Fetches table size statistics for all tables in the event store.
     * The statistics include the total size, table size, and index size
     * for each table, represented as a map where the key is the table name
     * and the value is an {@code ApiTableSizeStatistics} object.
     *
     * @param principal the principal or identity requesting the statistics,
     *                  typically representing the authenticated user or system performing the action
     * @return a map where the key is the table name and the value is an {@code ApiTableSizeStatistics}
     *         object containing size details for each table
     */
    Map<String, ApiTableSizeStatistics> fetchTableSizeStatistics(Object principal);

    /**
     * Fetches activity statistics for all tables in the event store.
     * The statistics include metrics such as sequential scans, index scans, and tuple operations
     * (inserts, updates, and deletes) for each table, represented as a map where the key is the
     * table name and the value is an {@code ApiTableActivityStatistics} object.
     *
     * @param principal the principal or identity requesting the statistics,
     *                  typically representing the authenticated user or system performing the action
     * @return a map where the key is the table name and the value is an {@code ApiTableActivityStatistics}
     *         object containing activity details for each table
     */
    Map<String, ApiTableActivityStatistics> fetchTableActivityStatistics(Object principal);

    /**
     * Fetches the cache hit ratio statistics for all tables in the event store.
     * The statistics are represented as a map where the key is the table name
     * and the value is an {@code ApiTableCacheHitRatio} object, which contains
     * the cache hit ratio for each table.
     *
     * @param principal the principal or identity requesting the statistics,
     *                  typically representing the authenticated user or system
     *                  performing the action
     * @return a map where the key is the table name and the value is an
     *         {@code ApiTableCacheHitRatio} object containing cache hit ratio
     *         details for each table
     */
    Map<String, ApiTableCacheHitRatio> fetchTableCacheHitRatio(Object principal);

}
