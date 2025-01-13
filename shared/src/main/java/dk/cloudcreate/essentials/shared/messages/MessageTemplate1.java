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
 * Represents a {@link MessageTemplate} accepting 1 parameter<br>
 * Example defining a {@link MessageTemplate1}'s:
 * <pre>{@code
 * // Has key: "ESSENTIALS"
 * MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");
 *
 * // Has key: "ESSENTIALS.ACCOUNT_NOT_FOUND"
 * MessageTemplate1<String> ACCOUNT_NOT_FOUND = ROOT.key1("ACCOUNT_NOT_FOUND",
 *                                                        "Account {0} not found");
 * }</pre>
 * <p>
 * Example creating a {@link Message} from a {@link MessageTemplate1}:
 * <pre>{@code
 * MessageTemplate1<String> ACCOUNT_NOT_FOUND = ROOT.key1("ACCOUNT_NOT_FOUND",
 *                                                        "Account {0} not found");
 * Message msg = ACCOUNT_NOT_FOUND.create(accountId);
 * }</pre>
 */
public final class MessageTemplate1<PARAM_1> extends AbstractMessageTemplate {

    public MessageTemplate1(String messageKey, String defaultMessage) {
        super(messageKey, defaultMessage);
    }

    /**
     * Create a {@link Message}, with the provided parameter, based on this {@link MessageTemplate}<br>
     * Example creating a {@link Message} from a {@link MessageTemplate1}:
     * <pre>{@code
     * MessageTemplate1<String> ACCOUNT_NOT_FOUND = ROOT.key1("ACCOUNT_NOT_FOUND",
     *                                                        "Account {0} not found");
     * Message msg = ACCOUNT_NOT_FOUND.create(accountId);
     * }</pre>
     *
     * @param param1 the parameter with index 0 in the generated {@link Message#getMessage()}
     * @return the new {@link Message} with the parameter applied
     */
    public Message create(PARAM_1 param1) {
        return new Message(this, param1);
    }

}
