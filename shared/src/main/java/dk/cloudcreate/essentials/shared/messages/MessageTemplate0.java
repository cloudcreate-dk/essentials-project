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
 * Represents a {@link MessageTemplate} accepting 0 parameters
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

    public Message create() {
        return new Message(this);
    }

    // ---------------------- Factory methods ----------------------
    <PARAM_1> MessageTemplate1<PARAM_1> key1(String messageKey,
                                             String defaultMessage) {
        return new MessageTemplate1<>(this.messageKey + "." + messageKey, defaultMessage);
    }

    <PARAM_1, PARAM_2> MessageTemplate2<PARAM_1, PARAM_2> key2(String messageKey,
                                                               String defaultMessage) {
        return new MessageTemplate2<>(this.messageKey + "." + messageKey, defaultMessage);
    }

    <PARAM_1, PARAM_2, PARAM_3> MessageTemplate3<PARAM_1, PARAM_2, PARAM_3> key3(String messageKey,
                                                                                 String defaultMessage) {
        return new MessageTemplate3<>(this.messageKey + "." + messageKey, defaultMessage);
    }

    <PARAM_1, PARAM_2, PARAM_3, PARAM_4> MessageTemplate4<PARAM_1, PARAM_2, PARAM_3, PARAM_4> key4(String messageKey,
                                                                                                   String defaultMessage) {
        return new MessageTemplate4<>(this.messageKey + "." + messageKey, defaultMessage);
    }
}
