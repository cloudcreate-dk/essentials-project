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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * JSON Serialized payload, used to Serialize {@link PersistedEvent}'s {@link PersistedEvent#metaData()} payload
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class EventMetaDataJSON {
    private transient JSONSerializer   jsonSerializer;
    /**
     * Cache or the {@link #json} deserialized back to its {@link #javaType} form
     */
    private transient Optional<Object> jsonDeserialized;
    private final     Optional<String> javaType;
    private final     String           json;

    public EventMetaDataJSON(JSONSerializer jsonSerializer, Object jsonDeserialized, String javaType, String json) {
        this(jsonSerializer, javaType, json);
        this.jsonDeserialized = Optional.of(requireNonNull(jsonDeserialized, "No payload provided"));
    }

    public EventMetaDataJSON(JSONSerializer jsonSerializer, String javaType, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.javaType = Optional.ofNullable(javaType);
        this.json = json;
    }

    /**
     * The {@link #getJson()} deserialized to the corresponding {@link #getJavaType()} that
     * was serialized into the {@link #getJson()}<br>
     * It's optional to specify a corresponding Java type, in which case {@link #getJsonDeserialized()}
     * will return {@link Optional#empty()}
     *
     * @return The {@link #getJson()} deserialized to the corresponding {@link #getJavaType()}
     */
    @SuppressWarnings({"OptionalAssignedToNull", "unchecked"})
    public <T> Optional<T> getJsonDeserialized() {
        if (jsonDeserialized == null && jsonSerializer != null) {
            javaType.ifPresent(s -> jsonDeserialized = Optional.of(jsonSerializer.deserialize(json, s)));
        }
        return (Optional<T>) jsonDeserialized;
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
        return (T) getJsonDeserialized().orElseThrow(() -> new JSONDeserializationException(msg("Couldn't deserialize '{}' due to: {}",
                                                                                                javaType.orElse("N/A"),
                                                                                                javaType.isPresent() ? "No JSONSerializer specified" : "No JavaType specified")));
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
        return javaType;
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
        if (!(o instanceof EventMetaDataJSON)) return false;
        EventMetaDataJSON that = (EventMetaDataJSON) o;
        return javaType.equals(that.javaType) && Objects.equals(json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaType, json);
    }

    @Override
    public String toString() {
        return "EventMetaDataJSON{" +
                "'" + json + '\'' +
                '}';
    }
}
