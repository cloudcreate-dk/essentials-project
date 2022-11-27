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

import java.util.*;

/**
 * An Event id provides a unique identifier for an Event
 */
public class EventId extends CharSequenceType<EventId> implements Identifier {
    public EventId(CharSequence value) {
        super(value);
    }

    public static EventId of(CharSequence value) {
        return new EventId(value);
    }

    /**
     * Converts a non-null <code>value</code> to an {@link Optional#of(Object)} with argument {@link EventId},
     * otherwise it returns an {@link Optional#empty()}
     *
     * @param value the optional value
     * @return an {@link Optional} with an {@link EventId} depending on whether <code>value</code> is non-null, otherwise an
     * {@link Optional#empty()} is returned
     */
    public static Optional<EventId> optionalFrom(CharSequence value) {
        if (value == null) {
            return Optional.empty();
        } else {
            return Optional.of(EventId.of(value));
        }
    }

    public static EventId random() {
        return new EventId(UUID.randomUUID().toString());
    }
}
