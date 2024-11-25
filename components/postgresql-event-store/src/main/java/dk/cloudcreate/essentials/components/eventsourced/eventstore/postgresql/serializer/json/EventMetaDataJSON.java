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
import dk.cloudcreate.essentials.components.foundation.json.JSONDeserializationException;
import dk.cloudcreate.essentials.shared.reflection.Classes;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * JSON Serialized payload, used to Serialize {@link PersistedEvent}'s {@link PersistedEvent#metaData()} payload
 */
public final class EventMetaDataJSON {
    private transient JSONEventSerializer jsonSerializer;
    /**
     * Cache of the {@link #json} deserialized back to its {@link #javaType} form
     */
    private transient Object jsonDeserialized;
    private final String javaType;
    private final String json;

    public EventMetaDataJSON(JSONEventSerializer jsonSerializer, Object jsonDeserialized, String javaType, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.javaType = requireNonNull(javaType, "No Java type provided");
        this.json = requireNonNull(json, "No JSON provided");
        this.jsonDeserialized = requireNonNull(jsonDeserialized, "No payload provided");
    }

    public EventMetaDataJSON(JSONEventSerializer jsonSerializer, String javaType, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.javaType = javaType;
        this.json = requireNonNull(json, "No JSON provided");
    }

    /**
     * The {@link #getJson()} deserialized to the corresponding {@link #getJavaType()} that
     * was serialized into the {@link #getJson()}<br>
     * It's optional to specify a corresponding Java type, in which case {@link #getJsonDeserialized()}
     * will return {@link Optional#empty()}
     *
     * @return The {@link #getJson()} deserialized to the corresponding {@link #getJavaType()}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getJsonDeserialized() {
        if (jsonDeserialized == null && jsonSerializer != null) {
            if (javaType != null) {
                jsonDeserialized = jsonSerializer.deserialize(json, Classes.forName(javaType, jsonSerializer.getClassLoader()));
            }
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
        return (T) getJsonDeserialized().orElseThrow(() -> new JSONDeserializationException("Couldn't deserialize since no JavaType specified"));
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
    public Optional<String> getJavaType() {
        return Optional.ofNullable(javaType);
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventMetaDataJSON that)) return false;
        return javaType.equals(that.javaType) && Objects.equals(json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaType, json);
    }

    @Override
    public String toString() {
        return "EventMetaDataJSON{" +
                "javaType='" + javaType + "', " +
                "json='" + json + '\'' +
                '}';
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
