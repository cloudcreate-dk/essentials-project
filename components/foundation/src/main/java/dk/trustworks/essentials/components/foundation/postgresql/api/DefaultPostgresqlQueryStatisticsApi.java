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

import dk.trustworks.essentials.components.foundation.postgresql.PostgresqlUtil;
import dk.trustworks.essentials.components.foundation.postgresql.stats.QueryStatistics;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWorkException;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory;
import dk.trustworks.essentials.shared.security.EssentialsSecurityProvider;
import dk.trustworks.essentials.shared.security.EssentialsSecurityRoles;
import dk.trustworks.essentials.shared.security.EssentialsSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static dk.trustworks.essentials.shared.security.EssentialsSecurityRoles.ESSENTIALS_ADMIN;
import static dk.trustworks.essentials.shared.security.EssentialsSecurityRoles.POSTGRESQL_STATS_READER;
import static dk.trustworks.essentials.shared.security.EssentialsSecurityValidator.hasAnyEssentialsSecurityRoles;

/**
 * <pre>
 * Default implementation of the {@link PostgresqlQueryStatisticsApi} interface for retrieving PostgreSQL query statistics.
 * This class provides functionality to fetch performance data of SQL queries executed in a PostgreSQL database,
 * including support for the `pg_stat_statements` extension.
 *
 * The implementation attempts to initialize the `pg_stat_statements` extension and determine
 * its availability on the target database during construction.
 * </pre>
 */
public class DefaultPostgresqlQueryStatisticsApi implements PostgresqlQueryStatisticsApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultPostgresqlQueryStatisticsApi.class);

    private final EssentialsSecurityProvider securityProvider;
    private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
    private boolean                                                             pgStatementsAvailable;

    public DefaultPostgresqlQueryStatisticsApi(EssentialsSecurityProvider securityProvider,
                                               HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
        this.securityProvider = securityProvider;
        this.unitOfWorkFactory = unitOfWorkFactory;

        tryAndCreatePgStatementsExtension();
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            this.pgStatementsAvailable = PostgresqlUtil.isPGExtensionAvailable(uow.handle(), "pg_stat_statements");
        });
        log.info("pg_statements extension is '{}' available", pgStatementsAvailable);
    }

    private void tryAndCreatePgStatementsExtension() {
        try {
            unitOfWorkFactory.usingUnitOfWork(uow -> {
                uow.handle().execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements;");
            });
        } catch (UnitOfWorkException e) {
            log.warn("Failed to initialize pg_statements extension, query statistics will not be available", e);
        }
    }

    private void validateRoles(Object principal) {
        hasAnyEssentialsSecurityRoles(securityProvider, principal, POSTGRESQL_STATS_READER, ESSENTIALS_ADMIN);
    }

    public List<ApiQueryStatistics> getTopTenSlowestQueries(Object principal) {
        validateRoles(principal);
        return getTopTenSlowestQueries(10).stream()
                .map(ApiQueryStatistics::from)
                .toList();
    }

    private List<QueryStatistics> getTopTenSlowestQueries(int limit) {
        if(!pgStatementsAvailable) {
            return List.of();
        }
        try {
            return unitOfWorkFactory.withUnitOfWork(uow -> {
                var sql = """
                            SELECT
                              query,
                              calls,
                              total_plan_time + total_exec_time AS total_time,
                              mean_plan_time + mean_exec_time  AS mean_time
                            FROM pg_stat_statements
                            ORDER BY total_time DESC
                            LIMIT :limit;
                        """;
                return uow.handle().createQuery(sql)
                        .bind("limit", limit)
                        .map((rs, ctx) -> {
                            return new QueryStatistics(rs.getString("query"),
                                    rs.getDouble("total_time"),
                                    rs.getLong("calls"),
                                    rs.getDouble("mean_time"));
                        })
                        .list();
            });
        } catch (Exception e) {
            if (PostgresqlUtil.isPGExtensionNotLoadedException(e)) {
                log.debug("pg_stat_statements extension is not loaded, query statistics will not be available");
                return List.of();
            }
            throw e;
        }
    }

}