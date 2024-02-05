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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSON;
import dk.cloudcreate.essentials.shared.functional.tuple.*;

import java.util.Optional;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Represents an {@link Either} variant that represent a choice between having either an {@link EventType} or an {@link EventName} element value
 * (i.e. only one element can have a value at a time) for a given {@link EventJSON} payload (as used by {@link PersistedEvent})<br>
 * <br>
 * Use {@link #with(EventType)}/{@link #with(EventName)} (or {@link #EventTypeOrName(EventType, EventName)}) to create
 * a new {@link EventTypeOrName} instance<br>
 * <br>
 * Use {@link #hasEventType()} or {@link #hasEventName()} to check which value is non-null
 * and {@link #eventType()} or {@link #eventName()} to get the value of the element.<br>
 * <br>
 * Conditional logic can be applied using {@link #ifHasEventType(Consumer)} or {@link #ifHasEventName(Consumer)}
 */
public class EventTypeOrName extends Either<EventType, EventName> {
    public EventTypeOrName(EventType eventType, EventName eventName) {
        super(eventType, eventName);
    }

    /**
     * Construct a new {@link Tuple} with {@link #_1()}/#eventJavaType() element having a value
     *
     * @param eventType the {@link #eventType()} element ({@link #eventName()} will have value null)
     */
    public static EventTypeOrName with(EventType eventType) {
        return new EventTypeOrName(requireNonNull(eventType, "No eventType value provided"), null);
    }

    /**
     * Construct a new {@link Tuple} with {@link #_1()}/#eventJavaType() element having a value
     *
     * @param eventType the {@link #eventType()} element ({@link #eventName()} will have value null)
     */
    public static EventTypeOrName with(Class<?> eventType) {
        return new EventTypeOrName(EventType.of(eventType), null);
    }

    /**
     * Construct a new {@link Tuple} with {@link #_2()}/{@link #eventName()} element having a value
     *
     * @param eventName the {@link #eventName()} element ({@link #eventType()} will have value null)
     */
    public static EventTypeOrName with(EventName eventName) {
        return new EventTypeOrName(null, requireNonNull(eventName, "No eventName value provided"));
    }

    /**
     * The first element (aka {@link EventType}) in this tuple (can be null)
     *
     * @return The first element in this tuple (can be null)
     * @see #getEventType()
     * @see #hasEventType()
     * @see #ifHasEventType(Consumer)
     */
    public EventType eventType() {
        return _1;
    }

    /**
     * The first element (aka {@link EventType}) in this tuple wrapped as an {@link Optional}
     *
     * @return The first element in this tuple wrapped as an {@link Optional}
     * @see #eventType()
     * @see #hasEventType()
     * @see #ifHasEventType(Consumer)
     */
    public Optional<EventType> getEventType() {
        return Optional.ofNullable(_1);
    }

    /**
     * Does first element (aka- {@link EventType}) in this tuple have a non-null value
     *
     * @return true if the {@link EventType} element in this tuple has a non-null value
     * @see #ifHasEventType(Consumer)
     * @see #getEventType()
     */
    public boolean hasEventType() {
        return _1 != null;
    }

    /**
     * If {@link #is_1()}/{@link #hasEventType()} returns true, then provide the value of {@link #_1()}/{@link #eventType()}
     * to the supplied <code>consumer</code>
     *
     * @param consumer the consumer that will be provided the value of the {@link #eventType()}
     *                 if {@link #hasEventType()} returns true
     * @see #getEventType()
     */
    public void ifHasEventType(Consumer<EventType> consumer) {
        requireNonNull(consumer, "No consumer provided");
        if (hasEventType()) {
            consumer.accept(_1);
        }
    }

    /**
     * The second element (aka. {@link EventName}) in this tuple (can be null)
     *
     * @return The {@link EventName} element in this tuple (can be null)
     * @see #getEventName()
     * @see #hasEventName() ()
     * @see #ifHasEventName(Consumer)
     */
    public EventName eventName() {
        return _2;
    }

    /**
     * The second element (aka {@link EventName}) in this tuple wrapped as an {@link Optional}
     *
     * @return The second element in this tuple wrapped as an {@link Optional}
     * @see #eventName()
     * @see #hasEventName()
     * @see #ifHasEventName(Consumer)
     */
    public Optional<EventName> getEventName() {
        return Optional.ofNullable(_2);
    }

    /**
     * Does second element (aka- {@link EventName}) in this tuple have a non-null value
     *
     * @return true if the {@link EventName} element in this tuple has a non-null value
     * @see #ifHasEventName(Consumer)
     * @see #getEventName()
     */
    public boolean hasEventName() {
        return _2 != null;
    }

    /**
     * If {@link #is_2()}/{@link #hasEventName()} returns true, then provide the value of {@link #_2()}/{@link #eventName()}
     * to the supplied <code>consumer</code>
     *
     * @param consumer the consumer that will be provided the value of the {@link #eventName()}
     *                 if {@link #hasEventName()} returns true
     * @see #getEventName()
     */
    public void ifHasEventName(Consumer<EventName> consumer) {
        requireNonNull(consumer, "No consumer provided");
        if (hasEventName()) {
            consumer.accept(_2);
        }
    }

    /**
     * If {@link #hasEventName()} then the {@link EventName}'s String value is returned<br>
     * If {@link #hasEventType()} then the {@link EventType#getJavaTypeName()} is returned
     *
     * @return the value of the {@link EventName} or {@link EventType} stored in this instance
     */
    public String getValue() {
        if (_1 != null) {
            return _1.getJavaTypeName();
        } else {
            return _2.toString();
        }
    }

    @Override
    public String toString() {
        return "(EventType: '" + _1 + "', EventName: '" + _2 + "')";
    }
}
