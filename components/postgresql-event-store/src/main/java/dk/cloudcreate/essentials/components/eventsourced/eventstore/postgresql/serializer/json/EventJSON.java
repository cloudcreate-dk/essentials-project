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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.types.CharSequenceType;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * JSON Serialized payload, used to Serialize {@link PersistedEvent} {@link PersistedEvent#metaData()} and the {@link PersistedEvent#event()} payload
 */
public final class EventJSON {
    private transient JSONEventSerializer jsonSerializer;
    /**
     * Cache of the {@link #json} deserialized back to its {@link #eventTypeOrName} form
     */
    private transient Object              jsonDeserialized;
    private final     EventTypeOrName     eventTypeOrName;
    private final     String              json;

    public EventJSON(JSONEventSerializer jsonSerializer, Object jsonDeserialized, EventType eventType, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.eventTypeOrName = requireNonNull(EventTypeOrName.with(eventType), "No EventTypeOrName provided");
        this.json = requireNonNull(json, "No JSON provided");
        this.jsonDeserialized = requireNonNull(jsonDeserialized, "No payload provided");
    }

    public EventJSON(JSONEventSerializer jsonSerializer, EventType eventType, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.eventTypeOrName = requireNonNull(EventTypeOrName.with(eventType), "No EventTypeOrName provided");
        this.json = requireNonNull(json, "No JSON provided");
    }

    public EventJSON(JSONEventSerializer jsonSerializer, Object jsonDeserialized, EventName eventName, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.eventTypeOrName = requireNonNull(EventTypeOrName.with(eventName), "No EventTypeOrName provided");
        this.json = requireNonNull(json, "No JSON provided");
        this.jsonDeserialized = requireNonNull(jsonDeserialized, "No payload provided");
    }

    public EventJSON(JSONEventSerializer jsonSerializer, EventName eventName, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.eventTypeOrName = requireNonNull(EventTypeOrName.with(eventName), "No EventTypeOrName provided");
        this.json = requireNonNull(json, "No JSON provided");
    }

    /**
     * The {@link #getJson()} deserialized to the corresponding {@link #getEventType()} that
     * was serialized into the {@link #getJson()}<br>
     * It's optional to specify a corresponding Java type, in which case {@link #getJsonDeserialized()}
     * will return {@link Optional#empty()}
     *
     * @return The {@link #getJson()} deserialized to the corresponding {@link #getEventType()}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getJsonDeserialized() {
        if (jsonDeserialized == null && jsonSerializer != null) {
            eventTypeOrName.ifHasEventType(eventJavaType -> {
                jsonDeserialized = jsonSerializer.deserialize(json, eventJavaType.toJavaClass(jsonSerializer.getClassLoader()));
            });
        }
        return Optional.ofNullable((T) jsonDeserialized);
    }

    /**
     * Variant of {@link #getJsonDeserialized()} that will throw an {@link JSONDeserializationException} in case the
     * event payload cannot be serialized
     *
     * @param <T> the java type the event payload will be cast to
     * @return the deserialized event payload
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize() {
        if (jsonSerializer == null) {
            throw new JSONDeserializationException("No JSONSerializer specified for deserialization");
        }
        return (T) getJsonDeserialized().orElseThrow(() -> new JSONDeserializationException(msg("Couldn't deserialize '{}' due to: {}",
                                                                                                eventTypeOrName,
                                                                                                "No EventJavaType specified")));
    }

    /**
     * The corresponding Java type (i.e. fully qualified class name) that
     * was serialized into the {@link #getJson()}<br>
     * It's optional to specify a corresponding Java type, in which case {@link #getJsonDeserialized()}
     * will return {@link Optional#empty()}
     *
     * @return The corresponding Java type (i.e. fully qualified class name) that
     * was serialized into the {@link #getJson()}
     */
    public String getEventTypeOrNamePersistenceValue() {
        try {
            return eventTypeOrName.getEventType().map(CharSequenceType::toString)
                                  .orElseGet(() -> eventTypeOrName.getEventName().get().toString());
        } catch (Exception e) {
            throw new IllegalStateException(msg("Failed to resolve eventTypeOrNamePersistenceValue for {}", eventTypeOrName), e);
        }
    }

    public Optional<EventType> getEventType() {
        return eventTypeOrName.getEventType();
    }

    public Optional<EventName> getEventName() {
        return eventTypeOrName.getEventName();
    }

    public EventTypeOrName getEventTypeOrName() {
        return eventTypeOrName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventJSON that)) return false;
        return Objects.equals(eventTypeOrName, that.eventTypeOrName) &&
                Objects.equals(json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTypeOrName, json);
    }

    /**
     * The raw serialized JSON
     *
     * @return The raw serialized JSON
     */
    public String getJson() {
        return json;
    }

    @Override
    public String toString() {
        return "EventJSON{" +
                "eventTypeOrName=" + eventTypeOrName +
                '}';
    }

    /**
     * If {@link #getEventType()} is non-empty then the event's corresponding Java class is returned.
     * If the {@link EventJSON} contains a reference to the associated {@link JSONSerializer} then
     * {@link EventType#toJavaClass(ClassLoader)} (which is compatible with SpringBoot DevTools) will be used to convert,
     * otherwise {@link EventType#toJavaClass()} is used
     *
     * @return the optional {@link EventType}'s Java class
     */
    public Optional<Class<?>> getEventTypeAsJavaClass() {
        return getEventType()
                .map(eventType -> jsonSerializer != null ?
                                  eventType.toJavaClass(jsonSerializer.getClassLoader()) :
                                  eventType.toJavaClass());
    }

    /**
     * Sets the JSON serializer for this instance. This is useful when the jsonSerializer needs to be reinitialized after deserialization.
     *
     * @param jsonSerializer the {@link JSONEventSerializer} to set
     */
    public void setJsonSerializer(JSONEventSerializer jsonSerializer) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "JSON serializer must not be null");
    }
}
