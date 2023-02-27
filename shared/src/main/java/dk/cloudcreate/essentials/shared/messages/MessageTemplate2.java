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

/**
 * Represents a {@link MessageTemplate} accepting 2 parameters
 */
public class MessageTemplate2<PARAM_1, PARAM_2> extends MessageTemplate0 {
    public MessageTemplate2(String messageKey, String defaultMessage) {
        super(messageKey, defaultMessage);
    }

    public Message create(PARAM_1 param1, PARAM_2 param2) {
        return new Message(this,
                           param1,
                           param2);
    }
}
