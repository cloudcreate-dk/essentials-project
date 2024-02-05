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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.shared.reflection.Classes;
import dk.cloudcreate.essentials.types.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Encapsulates the <b>Java</b> Type Fully Qualified Class Name (FQCN) of an Event as opposed to the {@link EventName}<br>
 * The Event Name is used if only a JSON String payload representing a "CustomerRegistered" event is persisted (in {@link PersistedEvent#event()})
 * as opposed as an actual typed Event (i.e. a custom class such as a com.company.product.events.CustomerRegistered class)<br>
 * <br>
 * The {@link #toString()} methods returns the Serialized version of the {@link EventType}, which is prefixed with {@link #FQCN_PREFIX}.<br>
 * The {@link #isSerializedEventType(CharSequence)} can be used to check if a value has been produced by {@link EventType#toString()} as opposed to
 * {@link EventName#toString()}
 *
 * @see EventTypeOrName
 */
public class EventType extends CharSequenceType<EventType> implements Identifier {
    /**
     * The prefix that pre-appended the actual Fully Qualified Class Name (FQCN) during serialization.<br>
     * This allows the {@link EventStore} to distinguish between a {@link EventType} and {@link EventName} by
     * calling {@link #isSerializedEventType(CharSequence)}
     */
    public static final String FQCN_PREFIX = "FQCN:";

    public EventType(CharSequence value) {
        super(isSerializedEventType(value) ? value : FQCN_PREFIX + value);
    }

    public static EventType of(CharSequence value) {
        return new EventType(value);
    }

    public static EventType of(Class<?> type) {
        return new EventType(requireNonNull(type, "No type provided").getName());
    }

    /**
     * Get a class instance corresponding to the Fully Qualified Class Name (FQCN) stored in this instance<br>
     * To only get the Fully Qualified Class Name (FQCN) stored in this instance use {@link #getJavaTypeName()}
     *
     * @param <T> the java type
     * @return a class instance corresponding to the Fully Qualified Class Name (FQCN) stored in this instance
     */
    @SuppressWarnings("unchecked")
    public <T> Class<T> toJavaClass() {
        return (Class<T>) Classes.forName(getJavaTypeName());
    }

    /**
     * Get the Fully Qualified Class Name (FQCN) stored in this instance (removes the {@link #FQCN_PREFIX})<br>
     * Use {@link #toJavaClass()} to get the corresponding {@link Class} instance
     *
     * @return the Fully Qualified Class Name (FQCN) stored in this instance
     */
    public String getJavaTypeName() {
        var value = value();
        return value.subSequence(FQCN_PREFIX.length(), value().length()).toString();
    }

    /**
     * This can be used to check if a value has been produced by {@link EventType#toString()} (as opposed to
     * {@link EventName#toString()}). This method check if the <code>value</code> starts with {@link #FQCN_PREFIX}
     *
     * @param value the value to check
     * @return true if the value starts with {@link #FQCN_PREFIX}
     */
    public static boolean isSerializedEventType(CharSequence value) {
        requireNonNull(value, "No value provided");
        if (value.length() <= FQCN_PREFIX.length()) {
            return false;
        }
        return value.charAt(0) == FQCN_PREFIX.charAt(0) &&  // F
                value.charAt(1) == FQCN_PREFIX.charAt(1) && // Q
                value.charAt(2) == FQCN_PREFIX.charAt(2) && // C
                value.charAt(3) == FQCN_PREFIX.charAt(3) && // N
                value.charAt(4) == FQCN_PREFIX.charAt(4);   // :
    }


}
