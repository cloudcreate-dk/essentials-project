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

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Represents a {@link MessageTemplate} accepting 0 parameters.<br>
 * Example defining a {@link MessageTemplate0}'s:
 * <pre>{@code
 * // Has key: "ESSENTIALS"
 * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
 *
 * // Has key: "ESSENTIALS.VALIDATION"
 * MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");
 * }</pre>
 *
 * Example creating a {@link Message} from a {@link MessageTemplate0}:
 * <pre>{@code
 * MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");
 * Message msg = VALIDATION.create();
 * }</pre>
 */
public class MessageTemplate0 implements MessageTemplate {
    private       String messageKey;
    private final String defaultMessage;

    public MessageTemplate0(String messageKey, String defaultMessage) {
        this.messageKey = requireNonNull(messageKey, "No messageKey provided");
        this.defaultMessage = requireNonNull(defaultMessage, "No defaultMessage provided");
    }

    public MessageTemplate0(String messageKey) {
        this.messageKey = requireNonNull(messageKey, "No messageKey provided");
        this.defaultMessage = null;
    }

    @Override
    public String getKey() {
        return messageKey;
    }

    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * Create a {@link Message} based on this {@link MessageTemplate}<br>
     * Example creating a {@link Message} from a {@link MessageTemplate0}:
     * <pre>{@code
     * MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");
     * Message msg = VALIDATION.create();
     * }</pre>
     * @return the new {@link Message}
     */
    public Message create() {
        return new Message(this);
    }

    // ---------------------- Factory methods ----------------------

    /**
     * Create a sub key which concatenates this {@link MessageTemplate}'s
     * {@link #getKey()} with the provided <code>messageKey</code><br>
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
     *
     * // SubKey has key: "ESSENTIALS.VALIDATION"
     * MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");
     * }</pre>
     *
     * @param messageKey the message key
     * @return a new {@link MessageTemplate} with {@link #getKey()}: this.getKey() + "." + messageKey
     */
    MessageTemplate0 subKey(String messageKey) {
        return new MessageTemplate0(this.messageKey + "." + messageKey);
    }

    /**
     * Create a {@link MessageTemplate} with a key that is a concatenation of this {@link MessageTemplate}'s
     * {@link #getKey()} and the provided <code>messageKey</code><br>
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
     *
     * // SubKey has key: "ESSENTIALS.VALIDATION"
     * MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @return a new {@link MessageTemplate} with {@link #getKey()}: this.getKey() + "." + messageKey
     * and the provided defaultMessage
     */
    MessageTemplate0 subKey(String messageKey, String defaultMessage) {
        return new MessageTemplate0(this.messageKey + "." + messageKey,
                                    defaultMessage);
    }

    /**
     * Create a {@link MessageTemplates} with 1 parameter
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
     * // Has key: "ESSENTIALS.ACCOUNT_NOT_ACTIVATED"
     * MessageTemplate1<String> ACCOUNT_NOT_ACTIVATED = ROOT.key1("ACCOUNT_NOT_ACTIVATED",
     *                                                            "Account {0} is not activated");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @param <PARAM_1>      the type for the parameter with index 0
     * @return a new {@link MessageTemplate} with {@link #getKey()}: this.getKey() + "." + messageKey
     * and the provided defaultMessage
     */
    <PARAM_1> MessageTemplate1<PARAM_1> key1(String messageKey,
                                             String defaultMessage) {
        return new MessageTemplate1<>(this.messageKey + "." + messageKey, defaultMessage);
    }

    /**
     * Create a {@link MessageTemplates} with 2 parameters
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
     * // Has key: "ESSENTIALS.AMOUNT_TOO_HIGH"
     * MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = ROOT.key2("AMOUNT_TOO_HIGH",
     *                                                                      "Amount {0} is higher than {1}");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @param <PARAM_1>      the type for the parameter with index 0
     * @param <PARAM_2>      the type for the parameter with index 1
     * @return a new {@link MessageTemplate} with {@link #getKey()}: this.getKey() + "." + messageKey
     * and the provided defaultMessage
     */
    <PARAM_1, PARAM_2> MessageTemplate2<PARAM_1, PARAM_2> key2(String messageKey,
                                                               String defaultMessage) {
        return new MessageTemplate2<>(this.messageKey + "." + messageKey, defaultMessage);
    }

    /**
     * Create a {@link MessageTemplates} with 3 parameters
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
     * // Has key: "ESSENTIALS.ACCOUNT_OVERDRAWN"
     * MessageTemplate3<String, BigDecimal, BigDecimal> ACCOUNT_OVERDRAWN = ROOT.key3("ACCOUNT_OVERDRAWN",
     *                                                                                "Account {0} is overdrawn by {1}. Fee {2}");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @param <PARAM_1>      the type for the parameter with index 0
     * @param <PARAM_2>      the type for the parameter with index 1
     * @param <PARAM_3>      the type for the parameter with index 2
     * @return a new {@link MessageTemplate} with {@link #getKey()}: this.getKey() + "." + messageKey
     * and the provided defaultMessage
     */
    <PARAM_1, PARAM_2, PARAM_3> MessageTemplate3<PARAM_1, PARAM_2, PARAM_3> key3(String messageKey,
                                                                                 String defaultMessage) {
        return new MessageTemplate3<>(this.messageKey + "." + messageKey, defaultMessage);
    }

    /**
     * Create a {@link MessageTemplates} with 4 parameters
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
     * // Has key: "ESSENTIALS.ACCOUNT_OVERDRAWN"
     * MessageTemplate4<String, BigDecimal, BigDecimal, LocalDate> ACCOUNT_OVERDRAWN = ROOT.key4("ACCOUNT_OVERDRAWN",
     *                                                                                           "Account {0} is overdrawn by {1}. Fee of {2} will be debited on the {3}");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @param <PARAM_1>      the type for the parameter with index 0
     * @param <PARAM_2>      the type for the parameter with index 1
     * @param <PARAM_3>      the type for the parameter with index 2
     * @param <PARAM_3>      the type for the parameter with index 3
     * @return a new {@link MessageTemplate} with {@link #getKey()}: this.getKey() + "." + messageKey
     * and the provided defaultMessage
     */
    <PARAM_1, PARAM_2, PARAM_3, PARAM_4> MessageTemplate4<PARAM_1, PARAM_2, PARAM_3, PARAM_4> key4(String messageKey,
                                                                                                   String defaultMessage) {
        return new MessageTemplate4<>(this.messageKey + "." + messageKey, defaultMessage);
    }
}
