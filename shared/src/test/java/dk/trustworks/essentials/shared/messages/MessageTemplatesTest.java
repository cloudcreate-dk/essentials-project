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

package dk.trustworks.essentials.shared.messages;

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
    void test_MessageTemplate() {
        var rootKey = MessageTemplates.root("ESSENTIALS");
        assertThat(rootKey.getKey()).isEqualTo("ESSENTIALS");
        assertThat(rootKey.getDefaultMessage()).isNull();

        // Create MessageTemplate
        MessageTemplate1<Long> NUMBER_TOO_HIGH = rootKey.key1("NUMBER_TOO_HIGH",
                                                              "The provided number {0} is too high");
        assertThat(NUMBER_TOO_HIGH.getKey()).isEqualTo("ESSENTIALS.NUMBER_TOO_HIGH");
        assertThat(NUMBER_TOO_HIGH.getDefaultMessage()).isEqualTo("The provided number {0} is too high");


        // Create MessageTemplate
        MessageTemplate2<String, BigDecimal> ACCOUNT_BALANCE = rootKey.key2("ACCOUNT_BALANCE",
                                                                            "Account {0} has balance {1}");
        assertThat(ACCOUNT_BALANCE.getKey()).isEqualTo("ESSENTIALS.ACCOUNT_BALANCE");
        assertThat(ACCOUNT_BALANCE.getDefaultMessage()).isEqualTo("Account {0} has balance {1}");

        // Create a Message from a MessageTemplate
        var message = ACCOUNT_BALANCE.create("12345", new BigDecimal("1000.5"));
        assertThat(message.getTemplate()).isEqualTo(ACCOUNT_BALANCE);
        assertThat(message.getKey()).isEqualTo("ESSENTIALS.ACCOUNT_BALANCE");
        assertThat(message.getParameters()).isEqualTo(List.of("12345", new BigDecimal("1000.5")));
        assertThat(message.getMessage()).isEqualTo("Account 12345 has balance 1,000.5");
    }

    @Test
    void test_MessageTemplates_skipTemplateWithNoDefaultMessage() {
        var messageTemplates = MessageTemplates.getMessageTemplates(MyMessageTemplates.class,
                                                                    true);
        assertThat(messageTemplates).hasSize(3);
        assertThat(messageTemplates.stream()
                           .map(MessageTemplate::getKey)).containsAll(List.of("ESSENTIALS.VALIDATION.AMOUNT_TOO_HIGH",
                                                                              "ESSENTIALS.VALIDATION.AMOUNT_TOO_LOW",
                                                                              "ESSENTIALS.BUSINESS_RULES.ACCOUNT_NOT_ACTIVATED"));
    }

    @Test
    void test_MessageTemplates_includeTemplateWithNoDefaultMessage() {
        var messageTemplates = MessageTemplates.getMessageTemplates(MyMessageTemplates.class,
                                                                    false);
        assertThat(messageTemplates).hasSize(6);
        assertThat(messageTemplates.stream()
                                   .map(MessageTemplate::getKey)).containsAll(List.of("ESSENTIALS",
                                                                                      "ESSENTIALS.VALIDATION",
                                                                                      "ESSENTIALS.BUSINESS_RULES",
                                                                                      "ESSENTIALS.VALIDATION.AMOUNT_TOO_HIGH",
                                                                                      "ESSENTIALS.VALIDATION.AMOUNT_TOO_LOW",
                                                                                      "ESSENTIALS.BUSINESS_RULES.ACCOUNT_NOT_ACTIVATED"));
    }

}