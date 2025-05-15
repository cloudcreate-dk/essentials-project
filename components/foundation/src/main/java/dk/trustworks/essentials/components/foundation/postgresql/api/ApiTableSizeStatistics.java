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

import dk.trustworks.essentials.components.foundation.postgresql.stats.TableSizeStatistics;

/**
 * <pre>
 * Represents statistics about the size of a database table in an API context.
 *
 * This record provides the total size, table size, and index size as human-readable
 * strings, encapsulating critical storage statistics for a database table. The size
 * values are typically expressed in units such as "MB" or "GB", making them
 * comprehensible for display and reporting purposes.
 *
 * The {@link #from(TableSizeStatistics)} method enables conversion from a
 * {@code TableSizeStatistics} object, facilitating the mapping from a domain-specific
 * representation of table size statistics to this API-focused record.
 * </pre>
 */
public record ApiTableSizeStatistics(
        String totalSize,
        String tableSize,
        String indexSize
) {

    public static ApiTableSizeStatistics from(TableSizeStatistics tableSizeStatistics) {
        return new ApiTableSizeStatistics(
                tableSizeStatistics.totalSize(),
                tableSizeStatistics.tableSize(),
                tableSizeStatistics.indexSize()
        );
    }
}
