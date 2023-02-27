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
 * Instance of a {@link MessageTemplate}
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
     * applied using {@link MessageFormat#format(String, Object...)}
     *
     * @return The {@link MessageTemplate#getDefaultMessage()} with the {@link #getParameters()}
     * applied using {@link MessageFormat#format(String, Object...)}
     */
    public String getMessage() {
        return message;
    }
}
