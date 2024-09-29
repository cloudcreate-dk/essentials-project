/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql;

import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.test.fencedlock.DBFencedLockManagerIT;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.JdbiUnitOfWorkFactory;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.Optional;

@Testcontainers
class PostgresqlFencedLockManagerIT extends DBFencedLockManagerIT<PostgresqlFencedLockManager> {
    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("lock-db")
            .withUsername("test-user")
            .withPassword("secret-password");

    @Override
    protected PostgresqlFencedLockManager createLockManagerNode2() {
        var jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl() + "?connectTimeout=1&socketTimeout=1",
                                postgreSQLContainer.getUsername(),
                                postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());
        return new PostgresqlFencedLockManager(jdbi,
                                               new JdbiUnitOfWorkFactory(jdbi),
                                               Optional.of("node2"),
                                               Duration.ofSeconds(3),
                                               Duration.ofSeconds(1));
    }

    @Override
    protected PostgresqlFencedLockManager createLockManagerNode1() {
        var jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl() + "?connectTimeout=1&socketTimeout=1",
                                postgreSQLContainer.getUsername(),
                                postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());
        return new PostgresqlFencedLockManager(jdbi,
                                               new JdbiUnitOfWorkFactory(jdbi),
                                               Optional.of("node1"),
                                               Duration.ofSeconds(3),
                                               Duration.ofSeconds(1));
    }

    @Override
    protected void disruptDatabaseConnection() {
        var dockerClient = postgreSQLContainer.getDockerClient();
        dockerClient.pauseContainerCmd(postgreSQLContainer.getContainerId()).exec();
    }

    @Override
    protected void restoreDatabaseConnection() {
        var dockerClient = postgreSQLContainer.getDockerClient();
        dockerClient.unpauseContainerCmd(postgreSQLContainer.getContainerId()).exec();
    }
}