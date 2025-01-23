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

import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public abstract class AbstractMessageTemplate implements MessageTemplate {
    protected final String key;
    protected final String defaultMessage;

    public AbstractMessageTemplate(String key, String defaultMessage) {
        this.key = requireNonNull(key, "No key provided");
        this.defaultMessage = requireNonNull(defaultMessage, "No defaultMessage provided");
    }

    public AbstractMessageTemplate(String key) {
        this.key = requireNonNull(key, "No key provided");
        this.defaultMessage = null;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageTemplate)) return false;
        MessageTemplate that = (MessageTemplate) o;
        return Objects.equals(key, that.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "MessageTemplate{" +
                "key='" + key + '\'' +
                '}';
    }
}
