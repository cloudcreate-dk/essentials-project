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

import java.math.BigDecimal;

public interface MyMessageTemplates extends MessageTemplates {
    MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");

    /**
     * Has {@link MessageTemplate#getKey()}: "ESSENTIALS.VALIDATION"
     */
    MessageTemplate0                         VALIDATION      = ROOT.subKey("VALIDATION");
    /**
     * Has {@link MessageTemplate#getKey()}: "ESSENTIALS.VALIDATION.AMOUNT_TOO_HIGH"
     */
    MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = VALIDATION.key2("AMOUNT_TOO_HIGH",
                                                                               "Amount {0} is higher than {1}");

    /**
     * Has {@link MessageTemplate#getKey()}: "ESSENTIALS.VALIDATION.AMOUNT_TOO_LOW"
     */
    MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_LOW = VALIDATION.key2("AMOUNT_TOO_LOW",
                                                                              "Amount {0} is lower than {1}");

    /**
     * Has {@link MessageTemplate#getKey()}: "ESSENTIALS.BUSINESS_RULES"
     */
    MessageTemplate0         BUSINESS_RULES        = ROOT.subKey("BUSINESS_RULES");
    /**
     * Has {@link MessageTemplate#getKey()}: "ESSENTIALS.BUSINESS_RULES.ACCOUNT_NOT_ACTIVATED"
     */
    MessageTemplate1<String> ACCOUNT_NOT_ACTIVATED = BUSINESS_RULES.key1("ACCOUNT_NOT_ACTIVATED",
                                                                         "Account {0} is not activated");
}
