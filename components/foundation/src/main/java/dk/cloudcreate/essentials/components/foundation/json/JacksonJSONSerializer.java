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

package dk.cloudcreate.essentials.components.foundation.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.cloudcreate.essentials.shared.reflection.Classes;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Jackson {@link ObjectMapper} based {@link JSONSerializer}
 */
public class JacksonJSONSerializer implements JSONSerializer {
    protected final ObjectMapper objectMapper;

    public JacksonJSONSerializer(ObjectMapper objectMapper) {
        this.objectMapper = requireNonNull(objectMapper, "No object mapper instance provided");
    }

    @Override
    public String serialize(Object obj) {
        requireNonNull(obj, "you must provide a non-null object");
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Throwable e) {
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
            throw new JSONSerializationException(msg("Failed to serialize {} to JSON", obj.getClass().getName()),
                                                 e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(String json, String javaType) {
        return deserialize(json,
                           (Class<T>) Classes.forName(requireNonNull(javaType, "No javaType provided")));
    }

    @Override
    public <T> T deserialize(String json, Class<T> javaType) {
        requireNonNull(json, "No json provided");
        requireNonNull(javaType, "No javaType provided");
        try {
            return objectMapper.readValue(json, javaType);
        } catch (Throwable e) {
            throw new JSONDeserializationException(msg("Failed to deserialize JSON to {}", javaType.getName()),
                                                   e);
        }
    }

    @Override
    public <T> T deserialize(byte[] json, String javaType) {
        return deserialize(json,
                           (Class<T>) Classes.forName(requireNonNull(javaType, "No javaType provided")));
    }

    @Override
    public <T> T deserialize(byte[] json, Class<T> javaType) {
        requireNonNull(json, "No json provided");
        requireNonNull(javaType, "No javaType provided");
        try {
            return objectMapper.readValue(json, javaType);
        } catch (Throwable e) {
            throw new JSONDeserializationException(msg("Failed to deserialize JSON to {}", javaType.getName()),
                                                   e);
        }
    }
}
