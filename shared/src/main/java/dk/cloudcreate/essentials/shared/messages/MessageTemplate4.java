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

package dk.cloudcreate.essentials.shared.messages;

/**
 * Represents a {@link MessageTemplate} accepting 4 parameters<br>
 * Example defining a {@link MessageTemplate4}'s:
 * <pre>{@code
 * // Has key: "ESSENTIALS"
 * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
 *
 * // Has key: "ESSENTIALS.ACCOUNT_OVERDRAWN"
 * MessageTemplate4<String, BigDecimal, BigDecimal, LocalDate> ACCOUNT_OVERDRAWN = ROOT.key4("ACCOUNT_OVERDRAWN",
 *                                                                                           "Account {0} is overdrawn by ${1}. A fee of ${2} will be debited on the {3}");
 * }</pre>
 * <p>
 * Example creating a {@link Message} from a {@link MessageTemplate4}:
 * <pre>{@code
 * MessageTemplate4<String, BigDecimal, BigDecimal, LocalDate> ACCOUNT_OVERDRAWN = ROOT.key4("ACCOUNT_OVERDRAWN",
 *                                                                                           "Account {0} is overdrawn by ${1}. A fee of ${2} will be debited on the {3}");
 *
 * String accountId = "Account1";
 * BigDecimal overdrawnAmount = new BigDecimal("125");
 * BigDecimal feeAmount = new BigDecimal("10");
 * LocalDate  feeDebitDate =  LocalDate.of(2023, 2, 25);
 * Message msg = ACCOUNT_OVERDRAWN.create(accountId,
 *                                        overdrawnAmount,
 *                                        feeAmount,
 *                                        feeDebitDate);
 *
 * }</pre>
 * will create a {@link Message} with {@link Message#getMessage()}:
 * <code>"Account Account1 is overdrawn by $125. A fee of $10 will be debited on the 2023-2-25"</code> (date formatting is dependent on the {@link java.util.Locale})
 */
public final class MessageTemplate4<PARAM_1, PARAM_2, PARAM_3, PARAM_4> extends AbstractMessageTemplate {
    public MessageTemplate4(String messageKey, String defaultMessage) {
        super(messageKey, defaultMessage);
    }

    /**
     * Create a {@link Message}, with the provided parameter, based on this {@link MessageTemplate}<br>
     * Example creating a {@link Message} from a {@link MessageTemplate4}:
     * <pre>{@code
     * MessageTemplate4<String, BigDecimal, BigDecimal, LocalDate> ACCOUNT_OVERDRAWN = ROOT.key4("ACCOUNT_OVERDRAWN",
     *                                                                                           "Account {0} is overdrawn by ${1}. A fee of ${2} will be debited on the {3}");
     *
     * String accountId = "Account1";
     * BigDecimal overdrawnAmount = new BigDecimal("125");
     * BigDecimal feeAmount = new BigDecimal("10");
     * LocalDate  feeDebitDate =  LocalDate.of(2023, 2, 25);
     * Message msg = ACCOUNT_OVERDRAWN.create(accountId,
     *                                        overdrawnAmount,
     *                                        feeAmount,
     *                                        feeDebitDate);
     *
     * }</pre>
     * will create a {@link Message} with {@link Message#getMessage()}:
     * <code>"Account Account1 is overdrawn by $125. A fee of $10 will be debited on the 2023-2-25"</code> (date formatting is dependent on the {@link java.util.Locale})
     *
     * @param param1 the parameter with index 0 in the generated {@link Message#getMessage()}
     * @param param2 the parameter with index 1 in the generated {@link Message#getMessage()}
     * @param param3 the parameter with index 2 in the generated {@link Message#getMessage()}
     * @param param3 the parameter with index 3 in the generated {@link Message#getMessage()}
     * @return the new {@link Message} with the parameter applied
     */
    public Message create(PARAM_1 param1, PARAM_2 param2, PARAM_3 param3, PARAM_4 param4) {
        return new Message(this,
                           param1,
                           param2,
                           param3,
                           param4);
    }
}
