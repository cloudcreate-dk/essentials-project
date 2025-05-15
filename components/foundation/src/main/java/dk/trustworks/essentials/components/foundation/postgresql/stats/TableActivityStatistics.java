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
 * Represents activity statistics for a database table.
 *
 * These statistics typically include various metrics related
 * to sequential scans, index scans, tuple operations (inserts, updates, deletes),
 * and data fetching within a PostgreSQL table.
 *
 * This record is intended for use in monitoring or analyzing
 * database table activity.
 * </pre>
 */
public record TableActivityStatistics(
        long seq_scan,
        long seq_tup_read,
        long idx_scan,
        long idx_tup_fetch,
        long n_tup_ins,
        long n_tup_upd,
        long n_tup_del
) {}
