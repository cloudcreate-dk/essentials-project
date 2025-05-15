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

import dk.trustworks.essentials.components.foundation.postgresql.stats.TableCacheHitRatio;

/**
 * Represents the cache hit ratio for a table in an API context.
 *
 * This record encapsulates the cache hit ratio as a metric indicating the
 * efficiency of a table's caching mechanism. A higher cache hit ratio
 * suggests that more queries are resolved using cached data rather than
 * requiring retrieval from the underlying storage system, which improves
 * performance.
 *
 * The {@link #from(TableCacheHitRatio)} method allows conversion from a
 * {@code TableCacheHitRatio} object, serving as a bridge between domain-specific
 * statistics and the API layer representation.
 *
 * @param cacheHitRatio The cache hit ratio expressed as a long value.
 */
public record ApiTableCacheHitRatio(long cacheHitRatio) {

    public static ApiTableCacheHitRatio from(TableCacheHitRatio cacheHitRatio) {
        return new ApiTableCacheHitRatio(cacheHitRatio.cacheHitRatio());
    }
}
