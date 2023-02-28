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

import dk.cloudcreate.essentials.shared.reflection.Reflector;

import java.util.List;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Marker interface for classes or interfaces that contain {@link MessageTemplate} fields, which can be queried using
 * {@link MessageTemplates#getMessageTemplates(Class, boolean)}<br>
 * Example of a concrete {@link MessageTemplates} subclass:
 * <pre>{@code
 * public interface MyMessageTemplates extends MessageTemplates {
 *     // Has key: "ESSENTIALS"
 *     MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
 *
 *     // Has key: "ESSENTIALS.VALIDATION"
 *     MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");
 *
 *     // Has key: "ESSENTIALS.VALIDATION.AMOUNT_TOO_HIGH"
 *     MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = VALIDATION.key2("AMOUNT_TOO_HIGH",
 *                                                                              "Amount {0} is higher than {1}");
 *
 *     // Has key: "ESSENTIALS.VALIDATION.AMOUNT_TOO_LOW"
 *     MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_LOW = VALIDATION.key2("AMOUNT_TOO_LOW",
 *                                                                             "Amount {0} is lower than {1}");
 *
 *     // Has key: "ESSENTIALS.BUSINESS_RULES"
 *     MessageTemplate0 BUSINESS_RULES = ROOT.subKey("BUSINESS_RULES");
 *
 *     // Has key: "ESSENTIALS.BUSINESS_RULES.ACCOUNT_NOT_ACTIVATED"
 *     MessageTemplate1<String> ACCOUNT_NOT_ACTIVATED = BUSINESS_RULES.key1("ACCOUNT_NOT_ACTIVATED",
 *                                                                          "Account {0} is not activated");
 * }
 * }</pre>
 * @see MessageTemplate
 * @see Message
 */
public interface MessageTemplates {
    /**
     * Extract all {@link MessageTemplate}'s defined as fields in {@link MessageTemplates} sub-type (interface or class)
     *
     * @param messageTemplatesType                    the {@link MessageTemplates} sub-type (interface or class)
     * @param skipMessageTemplateWithNoDefaultMessage skip {@link MessageTemplate} where {@link MessageTemplate#getDefaultMessage()} is null
     * @return the {@link MessageTemplate}'s that match the criteria
     */
    static List<MessageTemplate> getMessageTemplates(Class<? extends MessageTemplates> messageTemplatesType,
                                                     boolean skipMessageTemplateWithNoDefaultMessage) {
        requireNonNull(messageTemplatesType, "No messageTemplatesType provided");
        var reflector = Reflector.reflectOn(messageTemplatesType);
        return reflector
                .staticFields()
                .filter(field -> !field.getDeclaringClass().equals(MessageTemplates.class))
                .filter(field -> MessageTemplate.class.isAssignableFrom(field.getType()))
                .map(field -> (MessageTemplate) reflector.getStatic(field))
                .filter(messageTemplate -> {
                    if (!skipMessageTemplateWithNoDefaultMessage) return true;
                    return messageTemplate.getDefaultMessage() != null;
                })
                .collect(Collectors.toList());
    }

    /**
     * Create a message key (same as {@link #root(String)}) with no parameters.<br>
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.key("MY_SERVICE", "My Service ...");
     * }</pre>
     *
     * @param messageKey the message key
     * @param defaultMessage The default message
     * @return the {@link MessageTemplate}
     */
    static MessageTemplate0 key(String messageKey,
                                String defaultMessage) {
        return new MessageTemplate0(messageKey, defaultMessage);
    }


    /**
     * Create a message key (same as {@link #root(String)}) with no parameters.<br>
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.key("MY_SERVICE");
     * }</pre>
     *
     * @param messageKey the message key
     * @return the {@link MessageTemplate} (with no default message)
     */
    static MessageTemplate0 key(String messageKey) {
        return new MessageTemplate0(messageKey);
    }

    /**
     * Create a root message key (same as {@link #key(String)}).<br>
     * This is usually used for the root key, to provide context for message template.<br>
     * Example:
     * <pre>{@code
     * MessageTemplate0 ROOT = MessageTemplates.root("MY_SERVICE");
     * }</pre>
     *
     * @param messageKey the root message key
     * @return the {@link MessageTemplate} (with no default message)
     */
    static MessageTemplate0 root(String messageKey) {
        return new MessageTemplate0(messageKey);
    }

    /**
     * Create a {@link MessageTemplates} with 1 parameter
     * Example:
     * <pre>{@code
     * // Has key: "ACCOUNT_NOT_ACTIVATED"
     * MessageTemplate1<String> ACCOUNT_NOT_ACTIVATED = MessageTemplates.key1("ACCOUNT_NOT_ACTIVATED",
     *                                                                        "Account {0} is not activated");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @param <PARAM_1>      the type for the parameter with index 0
     * @return a new {@link MessageTemplate} with {@link MessageTemplate#getKey()}: messageKey
     * and the provided defaultMessage
     */
    static <PARAM_1> MessageTemplate1<PARAM_1> key1(String messageKey,
                                                    String defaultMessage) {
        return new MessageTemplate1<>(messageKey, defaultMessage);
    }

    /**
     * Create a {@link MessageTemplates} with 2 parameters
     * Example:
     * <pre>{@code
     * // Has key: "AMOUNT_TOO_HIGH"
     * MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = MessageTemplates.key2("AMOUNT_TOO_HIGH",
     *                                                                      "Amount {0} is higher than {1}");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @param <PARAM_1>      the type for the parameter with index 0
     * @param <PARAM_2>      the type for the parameter with index 1
     * @return a new {@link MessageTemplate} with {@link MessageTemplate#getKey()}: messageKey
     * and the provided defaultMessage
     */
    static <PARAM_1, PARAM_2> MessageTemplate2<PARAM_1, PARAM_2> key2(String messageKey,
                                                                      String defaultMessage) {
        return new MessageTemplate2<>(messageKey, defaultMessage);
    }

    /**
     * Create a {@link MessageTemplates} with 3 parameters
     * Example:
     * <pre>{@code
     * // Has key: "ACCOUNT_OVERDRAWN"
     * MessageTemplate3<String, BigDecimal, BigDecimal> ACCOUNT_OVERDRAWN = MessageTemplates.key3("ACCOUNT_OVERDRAWN",
     *                                                                                "Account {0} is overdrawn by {1}. Fee {2}");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @param <PARAM_1>      the type for the parameter with index 0
     * @param <PARAM_2>      the type for the parameter with index 1
     * @param <PARAM_3>      the type for the parameter with index 2
     * @return a new {@link MessageTemplate} with {@link MessageTemplate#getKey()}: messageKey
     * and the provided defaultMessage
     */
    static <PARAM_1, PARAM_2, PARAM_3> MessageTemplate3<PARAM_1, PARAM_2, PARAM_3> key3(String messageKey,
                                                                                        String defaultMessage) {
        return new MessageTemplate3<>(messageKey, defaultMessage);
    }

    /**
     * Create a {@link MessageTemplates} with 4 parameters
     * Example:
     * <pre>{@code
     * // Has key: "ACCOUNT_OVERDRAWN"
     * MessageTemplate4<String, BigDecimal, BigDecimal, LocalDate> ACCOUNT_OVERDRAWN = MessageTemplates.key4("ACCOUNT_OVERDRAWN",
     *                                                                                           "Account {0} is overdrawn by {1}. Fee of {2} will be debited on the {3}");
     * }</pre>
     *
     * @param messageKey     the message key
     * @param defaultMessage the default message
     * @param <PARAM_1>      the type for the parameter with index 0
     * @param <PARAM_2>      the type for the parameter with index 1
     * @param <PARAM_3>      the type for the parameter with index 2
     * @param <PARAM_3>      the type for the parameter with index 3
     * @return a new {@link MessageTemplate} with {@link MessageTemplate#getKey()}: messageKey
     * and the provided defaultMessage
     */
    static <PARAM_1, PARAM_2, PARAM_3, PARAM_4> MessageTemplate4<PARAM_1, PARAM_2, PARAM_3, PARAM_4> key4(String messageKey,
                                                                                                          String defaultMessage) {
        return new MessageTemplate4<>(messageKey, defaultMessage);
    }
}
