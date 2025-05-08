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

package dk.trustworks.essentials.components.foundation.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import dk.trustworks.essentials.shared.reflection.Classes;

import static dk.trustworks.essentials.shared.Exceptions.rethrowIfCriticalError;
import static dk.trustworks.essentials.shared.FailFast.requireNonNull;
import static dk.trustworks.essentials.shared.MessageFormatter.msg;

/**
 * Jackson {@link ObjectMapper} based {@link JSONSerializer}
 */
public class JacksonJSONSerializer implements JSONSerializer {
    protected final ObjectMapper objectMapper;

    public JacksonJSONSerializer(ObjectMapper objectMapper) {
        this.objectMapper = requireNonNull(objectMapper, "No object mapper instance provided");
        // Ensure that we have defined a default ClassLoader to avoid NullPointerException's as all class lookups use a #getClassLoader()
        setClassLoader(this.getClass().getClassLoader());
    }

    @Override
    public String serialize(Object obj) {
        requireNonNull(obj, "you must provide a non-null object");
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Throwable e) {
            rethrowIfCriticalError(e);
            throw new JSONSerializationException(msg("Failed to serialize {} to JSON", obj.getClass().getName()),
                                                 e);
        }
    }

    @Override
    public byte[] serializeAsBytes(Object obj) {
        requireNonNull(obj, "you must provide a non-null object");
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Throwable e) {
            rethrowIfCriticalError(e);
            throw new JSONSerializationException(msg("Failed to serialize {} to JSON", obj.getClass().getName()),
                                                 e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(String json, String javaType) {
        return deserialize(json,
                           (Class<T>) Classes.forName(requireNonNull(javaType, "No javaType provided"), getClassLoader()));
    }

    @Override
    public <T> T deserialize(String json, Class<T> javaType) {
        requireNonNull(json, "No json provided");
        requireNonNull(javaType, "No javaType provided");
        try {
            return objectMapper.readValue(json, javaType);
        } catch (Throwable e) {
            rethrowIfCriticalError(e);
            throw new JSONDeserializationException(msg("Failed to deserialize JSON to {}", javaType.getName()),
                                                   e);
        }
    }

    @Override
    public <T> T deserialize(byte[] json, String javaType) {
        return deserialize(json,
                           (Class<T>) Classes.forName(requireNonNull(javaType, "No javaType provided"), getClassLoader()));
    }

    @Override
    public <T> T deserialize(byte[] json, Class<T> javaType) {
        requireNonNull(json, "No json provided");
        requireNonNull(javaType, "No javaType provided");
        try {
            return objectMapper.readValue(json, javaType);
        } catch (Throwable e) {
            rethrowIfCriticalError(e);
            throw new JSONDeserializationException(msg("Failed to deserialize JSON to {}", javaType.getName()),
                                                   e);
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        return objectMapper.getTypeFactory().getClassLoader();
    }

    @Override
    public void setClassLoader(ClassLoader classLoader) {
        requireNonNull(classLoader, "No ClassLoader provided");
        objectMapper.setTypeFactory(TypeFactory.defaultInstance().withClassLoader(classLoader));
    }

    /**
     * Direct access to the internal {@link ObjectMapper} used by the {@link JacksonJSONSerializer} (or subclasses)
     *
     * @return the internal {@link ObjectMapper}
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
