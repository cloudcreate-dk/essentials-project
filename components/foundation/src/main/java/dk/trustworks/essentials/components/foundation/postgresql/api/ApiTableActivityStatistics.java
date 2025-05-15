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

import dk.trustworks.essentials.components.foundation.postgresql.stats.TableActivityStatistics;

/**
 * <pre>
 * Represents activity statistics for a database table in an API context.
 *
 * This record encapsulates various metrics related to the activity of a database
 * table, including sequential scans, index scans, and tuple (row) operations
 * such as inserts, updates, and deletes.
 *
 * The {@link #from(TableActivityStatistics)} method enables conversion from
 * a {@code TableActivityStatistics} object, providing a bridge between the
 * domain-specific representation of table activity statistics and the API layer.
 * </pre>
 */
public record ApiTableActivityStatistics(
        long seq_scan,
        long seq_tup_read,
        long idx_scan,
        long idx_tup_fetch,
        long n_tup_ins,
        long n_tup_upd,
        long n_tup_del
) {

    public static ApiTableActivityStatistics from(TableActivityStatistics tableActivityStatistics) {
        return new ApiTableActivityStatistics(
                tableActivityStatistics.seq_scan(),
                tableActivityStatistics.seq_tup_read(),
                tableActivityStatistics.idx_scan(),
                tableActivityStatistics.idx_tup_fetch(),
                tableActivityStatistics.n_tup_ins(),
                tableActivityStatistics.n_tup_upd(),
                tableActivityStatistics.n_tup_del()
        );
    }
}
