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

import static org.assertj.core.api.Assertions.*;

class PostgresqlUtilTest {
    @Test
    void testValidName() {
        assertThatCode(() -> PostgresqlUtil.checkIsValidTableOrColumnName("ValidTableName")).doesNotThrowAnyException();
        assertThatCode(() -> PostgresqlUtil.checkIsValidTableOrColumnName("valid_column_name")).doesNotThrowAnyException();
    }

    @Test
    void testWithReservedKeyword() {
        assertThatThrownBy(() -> PostgresqlUtil.checkIsValidTableOrColumnName("SELECT"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("is a reserved keyword");
    }

    @Test
    void testWithInvalidCharacters() {
        assertThatThrownBy(() -> PostgresqlUtil.checkIsValidTableOrColumnName("Invalid-Name"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testStartsWithDigit() {
        assertThatThrownBy(() -> PostgresqlUtil.checkIsValidTableOrColumnName("123InvalidName"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }

    @Test
    void testEmptyString() {
        assertThatThrownBy(() -> PostgresqlUtil.checkIsValidTableOrColumnName(""))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void testNullInput() {
        assertThatThrownBy(() -> PostgresqlUtil.checkIsValidTableOrColumnName(null))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void testSimpleSqlInjection() {
        assertThatThrownBy(() -> PostgresqlUtil.checkIsValidTableOrColumnName("users; DROP TABLE users;--"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
        assertThatThrownBy(() -> PostgresqlUtil.checkIsValidTableOrColumnName("1 OR 1=1"))
                .isInstanceOf(InvalidTableOrColumnNameException.class)
                .hasMessageContaining("Invalid table or column name");
    }
}