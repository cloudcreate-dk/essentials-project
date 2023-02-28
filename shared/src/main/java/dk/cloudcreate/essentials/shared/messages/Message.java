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

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Instance of a {@link MessageTemplate}<br>
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
public class Message implements Serializable {
    private MessageTemplate template;
    private List<Object>    parameters;
    private String          message;

    /**
     * Create a concrete instance of a {@link MessageTemplate}
     *
     * @param template   the message template
     * @param parameters the parameters for the message template
     */
    public Message(MessageTemplate template, Object... parameters) {
        this.template = requireNonNull(template, "No template provided");
        this.parameters = List.of(requireNonNull(parameters, "No parameters provided"));
        this.message = MessageFormat.format(template.getDefaultMessage(),
                                            parameters);
    }

    /**
     * The {@link MessageTemplate#getKey()}
     *
     * @return The {@link MessageTemplate#getKey()}
     */
    public String getKey() {
        return template.getKey();
    }

    /**
     * The message template that this message is based on
     *
     * @return the message template that this message is based on
     */
    public MessageTemplate getTemplate() {
        return template;
    }

    /**
     * The concrete parameters for the message
     *
     * @return the concrete parameters for the message
     */
    public List<Object> getParameters() {
        return parameters;
    }

    /**
     * The {@link MessageTemplate#getDefaultMessage()} with the {@link #getParameters()}
     * applied using {@link MessageFormat#format(String, Object...)}<br>
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
     * @return The {@link MessageTemplate#getDefaultMessage()} with the {@link #getParameters()}
     * applied using {@link MessageFormat#format(String, Object...)}
     */
    public String getMessage() {
        return message;
    }
}
