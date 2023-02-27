/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.shared.messages;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplatesTest {
    private Locale locale;

    @BeforeEach
    void setup() {
        locale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterEach
    void cleanup() {
        Locale.setDefault(locale);
    }

    @Test
    void test() {
        var rootKey = MessageTemplates.key("ESSENTIALS");
        MessageTemplate1<Long> NUMBER_TOO_HIGH = rootKey.key1("NUMBER_TOO_HIGH",
                                                              "The provided number {0} is too high");
        MessageTemplate2<String, BigDecimal> ACCOUNT_BALANCE = rootKey.key2("ACCOUNT_BALANCE",
                                                                            "Account {0} has balance {1}");

        assertThat(rootKey.getKey()).isEqualTo("ESSENTIALS");
        assertThat(rootKey.getDefaultMessage()).isNull();

        assertThat(NUMBER_TOO_HIGH.getKey()).isEqualTo("ESSENTIALS.NUMBER_TOO_HIGH");
        assertThat(NUMBER_TOO_HIGH.getDefaultMessage()).isEqualTo("The provided number {0} is too high");

        assertThat(ACCOUNT_BALANCE.getKey()).isEqualTo("ESSENTIALS.ACCOUNT_BALANCE");
        assertThat(ACCOUNT_BALANCE.getDefaultMessage()).isEqualTo("Account {0} has balance {1}");

        var message = ACCOUNT_BALANCE.create("12345", new BigDecimal("1000.5"));
        assertThat(message.getTemplate()).isEqualTo(ACCOUNT_BALANCE);
        assertThat(message.getKey()).isEqualTo("ESSENTIALS.ACCOUNT_BALANCE");
        assertThat(message.getParameters()).isEqualTo(List.of("12345", new BigDecimal("1000.5")));
        assertThat(message.getMessage()).isEqualTo("Account 12345 has balance 1,000.5");
    }
}