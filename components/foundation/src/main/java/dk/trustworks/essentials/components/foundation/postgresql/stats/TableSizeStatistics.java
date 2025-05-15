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
 * Represents statistics about the size of a database table, including total size, table size,
 * and index size. These size values are typically reported as human-readable strings (e.g.,
 * "10 MB", "5 GB").
 *
 * This record is intended for use in PostgreSQL database systems or any database system
 * where table and index size statistics might be queried and reported.
 *</pre>
 */
public record TableSizeStatistics(
        String totalSize,
        String tableSize,
        String indexSize
) {}
