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
import dk.trustworks.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory;
import dk.trustworks.essentials.shared.security.EssentialsSecurityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.trustworks.essentials.shared.security.EssentialsSecurityRoles.*;
import static dk.trustworks.essentials.shared.security.EssentialsSecurityValidator.validateHasAnyEssentialsSecurityRoles;

/**
 * Default implementation of the {@code PostgresqlEventStoreStatisticsApi} interface
 * for retrieving PostgreSQL event store statistics. This implementation provides methods
 * to fetch information about table sizes, table activity, and cache hit ratios for tables
 * associated with the event store, with role-based security enforced.
 */
public class DefaultPostgresqlEventStoreStatisticsApi implements PostgresqlEventStoreStatisticsApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultPostgresqlEventStoreStatisticsApi.class);

    private final EssentialsSecurityProvider                                    securityProvider;
    private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
    private final Set<String>                                                   aggregateTableNames;

    public DefaultPostgresqlEventStoreStatisticsApi(EssentialsSecurityProvider securityProvider,
                                                    HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                                    Set<String> aggregateTableNames) {
        this.securityProvider = securityProvider;
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.aggregateTableNames = aggregateTableNames;
    }

    private void validateRoles(Object principal) {
        validateHasAnyEssentialsSecurityRoles(securityProvider,
                principal,
                POSTGRESQL_STATS_READER,
                ESSENTIALS_ADMIN);
    }

    @Override
    public Map<String, ApiTableSizeStatistics> fetchTableSizeStatistics(Object principal) {
        if (aggregateTableNames.isEmpty()) {
            return Map.of();
        }
        validateRoles(principal);
        String sql = """
                SELECT relname AS table_name,
                  pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
                  pg_size_pretty(pg_relation_size(relid)) AS table_size,
                  pg_size_pretty(pg_total_relation_size(relid) - pg_relation_size(relid)) AS index_size
                FROM pg_catalog.pg_statio_user_tables
                WHERE relname IN (<tables>)
                """;
        return unitOfWorkFactory.withUnitOfWork(uow -> {
            return uow.handle().createQuery(sql)
                    .bindList("tables", aggregateTableNames)
                    .map((rs, ctx) -> Map.entry(
                            rs.getString("table_name"),
                            new ApiTableSizeStatistics(
                                    rs.getString("total_size"),
                                    rs.getString("table_size"),
                                    rs.getString("index_size")
                            )
                    ))
                    .list()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
    }

    @Override
    public Map<String, ApiTableActivityStatistics> fetchTableActivityStatistics(Object principal) {
        if (aggregateTableNames.isEmpty()) {
            return Map.of();
        }
        validateRoles(principal);
        String sql = """
                SELECT
                    relname AS table_name,
                    seq_scan,
                    seq_tup_read,
                    idx_scan,
                    idx_tup_fetch,
                    n_tup_ins,
                    n_tup_upd,
                    n_tup_del
                FROM pg_stat_user_tables
                WHERE relname IN (<tables>);
                """;
        return unitOfWorkFactory.withUnitOfWork(uow -> {
            return uow.handle().createQuery(sql)
                    .bindList("tables", aggregateTableNames)
                    .map((rs, ctx) -> Map.entry(
                            rs.getString("table_name"),
                            new ApiTableActivityStatistics(
                                    rs.getLong("seq_scan"),
                                    rs.getLong("seq_tup_read"),
                                    rs.getLong("idx_scan"),
                                    rs.getLong("idx_tup_fetch"),
                                    rs.getLong("n_tup_ins"),
                                    rs.getLong("n_tup_upd"),
                                    rs.getLong("n_tup_del")
                            )
                    ))
                    .list()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
    }

    @Override
    public Map<String, ApiTableCacheHitRatio> fetchTableCacheHitRatio(Object principal) {
        if (aggregateTableNames.isEmpty()) {
            return Map.of();
        }
        validateRoles(principal);
        String sql = """
                SELECT
                    relname AS table_name,
                    ((heap_blks_hit + idx_blks_hit)::float /
                    nullif((heap_blks_hit + idx_blks_hit + heap_blks_read + idx_blks_read), 0))
                    AS cache_hit_ratio
                FROM pg_statio_user_tables
                WHERE relname IN (<tables>);
                """;
        return unitOfWorkFactory.withUnitOfWork(uow -> {
            return uow.handle().createQuery(sql)
                    .bindList("tables", aggregateTableNames)
                    .map((rs, ctx) -> Map.entry(
                            rs.getString("table_name"),
                            new ApiTableCacheHitRatio(
                                    rs.getLong("cache_hit_ratio")
                            )
                    ))
                    .list()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
    }

}
