/*
 * Copyright 2021-2024 the original author or authors.
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
 * A subscriber id is used to uniquely identify an event subscriber
 */
public final class SubscriberId extends CharSequenceType<SubscriberId> implements Identifier {
    public SubscriberId(CharSequence value) {
        super(value);
    }

    public static SubscriberId of(CharSequence value) {
        return new SubscriberId(value);
    }

    public static SubscriberId random() {
        return new SubscriberId(UUID.randomUUID().toString());
    }
}
