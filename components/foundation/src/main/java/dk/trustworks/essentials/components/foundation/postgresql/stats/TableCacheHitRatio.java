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
 * Represents the cache hit ratio for a database table.
 *
 * The cache hit ratio is a measurement indicating how often database queries
 * are resolved using cached data rather than needing to retrieve data from
 * the underlying storage system. Higher values suggest better cache efficiency.
 *
 * This record is useful in database performance monitoring or analysis
 * to evaluate the effectiveness of caching mechanisms in a PostgreSQL system.
 * </pre>
 */
public record TableCacheHitRatio(long cacheHitRatio) {}
