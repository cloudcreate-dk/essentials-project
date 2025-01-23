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

package dk.cloudcreate.essentials.shared.messages;

/**
 * Represents a {@link MessageTemplate} accepting 3 parameters<br>
 * Example defining a {@link MessageTemplate3}'s:
 * <pre>{@code
 * // Has key: "ESSENTIALS"
 * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
 *
 * // Has key: "ESSENTIALS.ACCOUNT_OVERDRAWN"
 * MessageTemplate3<String, BigDecimal, BigDecimal> ACCOUNT_OVERDRAWN = MessageTemplates.key3("ACCOUNT_OVERDRAWN",
 *                                                                                           "Account {0} is overdrawn by {1}. Fee {2}");
 * }</pre>
 * <p>
 * Example creating a {@link Message} from a {@link MessageTemplate3}:
 * <pre>{@code
 * MessageTemplate2<String, BigDecimal, BigDecimal> ACCOUNT_OVERDRAWN = MessageTemplates.key3("ACCOUNT_OVERDRAWN",
 *                                                                                            "Account {0} is overdrawn by {1}. Fee {2}");
 *
 * String accountId = ...;
 * BigDecimal requestedAmount = ...;
 * BigDecimal maximumAmountAllowed = ...;
 * Message msg = ACCOUNT_OVERDRAWN.create(accountId,
 *                                        requestedAmount,
 *                                        maximumAmountAllowed);
 * }</pre>
 */
public final class MessageTemplate3<PARAM_1, PARAM_2, PARAM_3> extends AbstractMessageTemplate {
    public MessageTemplate3(String messageKey, String defaultMessage) {
        super(messageKey, defaultMessage);
    }

    /**
     * Create a {@link Message}, with the provided parameter, based on this {@link MessageTemplate}<br>
     * Example creating a {@link Message} from a {@link MessageTemplate3}:
     * <pre>{@code
     * MessageTemplate2<String, BigDecimal, BigDecimal> ACCOUNT_OVERDRAWN = MessageTemplates.key3("ACCOUNT_OVERDRAWN",
     *                                                                                            "Account {0} is overdrawn by {1}. Fee {2}");
     *
     * String accountId = ...;
     * BigDecimal requestedAmount = ...;
     * BigDecimal maximumAmountAllowed = ...;
     * Message msg = ACCOUNT_OVERDRAWN.create(accountId,
     *                                        requestedAmount,
     *                                        maximumAmountAllowed);
     * }</pre>
     *
     * @param param1 the parameter with index 0 in the generated {@link Message#getMessage()}
     * @param param2 the parameter with index 1 in the generated {@link Message#getMessage()}
     * @param param3 the parameter with index 2 in the generated {@link Message#getMessage()}
     * @return the new {@link Message} with the parameter applied
     */
    public Message create(PARAM_1 param1, PARAM_2 param2, PARAM_3 param3) {
        return new Message(this,
                           param1,
                           param2,
                           param3);
    }
}
