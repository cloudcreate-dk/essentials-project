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

package dk.cloudcreate.essentials.components.foundation.postgresql;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ListenNotifyPostgresql12IT extends ListenNotifyIT {

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:12")
            .withDatabaseName("listen-notify-db")
            .withUsername("test-user")
            .withPassword("secret-password");


    protected PostgreSQLContainer<?> getPostgreSQLContainer() {
        return postgreSQLContainer;
    }

    @Test
    void verify_using_version_12() {
        jdbi.useTransaction(handle -> {
            assertThat(PostgresqlUtil.getServiceMajorVersion(handle)).isEqualTo(12);
        });
    }
}