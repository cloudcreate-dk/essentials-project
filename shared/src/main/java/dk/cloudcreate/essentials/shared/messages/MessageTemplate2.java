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
 * Represents a {@link MessageTemplate} accepting 2 parameters<br>
 * Example defining a {@link MessageTemplate2}'s:
 * <pre>{@code
 * // Has key: "ESSENTIALS"
 * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
 *
 * // Has key: "ESSENTIALS.ACCOUNT_NOT_FOUND"
 * MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = ROOT.key2("AMOUNT_TOO_HIGH",
 *                                                                      "Amount {0} is higher than {1}");
 * }</pre>
 *
 * Example creating a {@link Message} from a {@link MessageTemplate2}:
 * <pre>{@code
 * MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = ROOT.key2("AMOUNT_TOO_HIGH",
 *                                                                      "Amount {0} is higher than {1}");
 *
 * BigDecimal requestedAmount = ...;
 * BigDecimal maximumAmountAllowed = ...;
 * Message msg = AMOUNT_TOO_HIGH.create(requestedAmount,
 *                                      maximumAmountAllowed);
 * }</pre>
 */
public final class MessageTemplate2<PARAM_1, PARAM_2> extends AbstractMessageTemplate {
    public MessageTemplate2(String messageKey, String defaultMessage) {
        super(messageKey, defaultMessage);
    }

    /**
     * Create a {@link Message}, with the provided parameter, based on this {@link MessageTemplate}<br>
     * Example creating a {@link Message} from a {@link MessageTemplate2}:
     * <pre>{@code
     * MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = ROOT.key2("AMOUNT_TOO_HIGH",
     *                                                                      "Amount {0} is higher than {1}");
     *
     * BigDecimal requestedAmount = ...;
     * BigDecimal maximumAmountAllowed = ...;
     * Message msg = AMOUNT_TOO_HIGH.create(requestedAmount,
     *                                      maximumAmountAllowed);
     * }</pre>
     * @param param1 the parameter with index 0 in the generated {@link Message#getMessage()}
     * @param param2 the parameter with index 1 in the generated {@link Message#getMessage()}
     * @return the new {@link Message} with the parameter applied
     */
    public Message create(PARAM_1 param1, PARAM_2 param2) {
        return new Message(this,
                           param1,
                           param2);
    }
}
