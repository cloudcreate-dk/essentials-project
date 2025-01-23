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
 * The {@link MessageTemplate} concept supports structured messages with typed parameters.<br>
 * Each {@link MessageTemplate} instance has a unique {@link #getKey()} that clearly identifies the {@link MessageTemplate}.<br>
 * {@link MessageTemplate} keys can be nested, to support message hierarchies:
 * <pre>{@code
 * // Has key: "ESSENTIALS"
 * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
 *
 * // Has key: "ESSENTIALS.VALIDATION"
 * MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");
 *
 * // Has key: "ESSENTIALS.VALIDATION.AMOUNT_TOO_HIGH"
 * MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = VALIDATION.key2("AMOUNT_TOO_HIGH",
 *                                                                          "Amount {0} is higher than {1}");
 *
 * // Has key: "ESSENTIALS.VALIDATION.AMOUNT_TOO_LOW"
 * MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_LOW = VALIDATION.key2("AMOUNT_TOO_LOW",
 *                                                                         "Amount {0} is lower than {1}");
 * }</pre>
 * There are multiple {@link MessageTemplate} subclasses that support a different number of parameters:
 * <ul>
 *  <li>{@link MessageTemplate0} which supports 0 parameters (mostly used for {@link MessageTemplates#root(String)} or nested keys)</li>
 *  <li>{@link MessageTemplate1} which supports 1 parameter</li>
 *  <li>{@link MessageTemplate2} which supports 2 parameters</li>
 *  <li>{@link MessageTemplate3} which supports 3 parameters</li>
 *  <li>{@link MessageTemplate4} which supports 4 parameters</li>
 * </ul>
 * <p>
 * Example defining a {@link MessageTemplate4}'s:
 * <pre>{@code
 * // Has key: "ESSENTIALS"
 * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
 *
 * // Has key: "ESSENTIALS.ACCOUNT_OVERDRAWN"
 * MessageTemplate4<String, BigDecimal, BigDecimal, LocalDate> ACCOUNT_OVERDRAWN = ROOT.key4("ACCOUNT_OVERDRAWN",
 *                                                                                           "Account {0} is overdrawn by ${1}. A fee of ${2} will be debited on the {3}");
 * }</pre>
 * From a {@link MessageTemplate} we can create {@link Message} instances, which are useful for e.g. error reporting.
 * A {@link Message} is an instance of a {@link MessageTemplate} with parameters bound to it.<br>
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
 * @see MessageTemplates
 * @see Message
 */
public interface MessageTemplate {
    /**
     * The message key for this {@link MessageTemplate}. Message key's can be used for multiple purposes:
     * <ul>
     *     <li>A Message key is the identifier for a message or error (e.g. INVENTORY_ITEM.OUT_OF_STOCK)</li>
     *     <li>A Message key can be used to lookup translations (e.g. in a Resource bundle)</li>
     * </ul>
     *
     * @return the message key for this {@link MessageTemplate}
     */
    String getKey();

    /**
     * The default Message for the given message key<br>
     * E.g. say the {@link #getKey()} is <code>SALES.INVENTORY_ITEM.OUT_OF_STOCK</code> and the concrete {@link MessageTemplate} is a {@link MessageTemplate1}
     * then a default english message for this key could be: <code>Inventory item {0} is out of Stock</code>, which would be defined as:
     * <pre>{@code
     * MessageTemplate1<ProductName> INVENTORY_OUT_OF_STOCK = MessageTemplates.key1("INVENTORY_ITEM.OUT_OF_STOCK", "Inventory item {0} is out of Stock");
     * }</pre>
     *
     * @return the default message for the given message key
     */
    String getDefaultMessage();
}
