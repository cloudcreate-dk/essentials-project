/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.foundation.types;

import dk.cloudcreate.essentials.types.*;

import java.util.UUID;

/**
 * A message id provides a unique identifier for a Message in a distributed system
 */
public class MessageId extends CharSequenceType<MessageId> implements Identifier {
    public MessageId(CharSequence value) {
        super(value);
    }

    public static MessageId of(CharSequence value) {
        return new MessageId(value);
    }

    public static MessageId random() {
        return new MessageId(UUID.randomUUID().toString());
    }
}
