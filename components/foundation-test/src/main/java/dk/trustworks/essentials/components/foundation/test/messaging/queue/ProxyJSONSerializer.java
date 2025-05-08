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

package dk.trustworks.essentials.components.foundation.test.messaging.queue;

import dk.trustworks.essentials.components.foundation.json.JSONSerializer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Proxy implementation of {@link JSONSerializer} that can be configured to corrupt the JSON during
 * deserialization for specific test scenarios without affecting other tests.
 */
public class ProxyJSONSerializer implements JSONSerializer {

    private final JSONSerializer delegate;
    private boolean  corruptJSONDuringDeserializationEnabled = false;
    private Class<?> onlyCorruptForPayloadType;

    public ProxyJSONSerializer(JSONSerializer delegate) {
        this.delegate = delegate;
    }

    /**
     * Enable corrupting of JSON during deserialization for a specific payload type
     * @param payloadType The fully qualified class name that should trigger deserialization failure
     */
    public void enableJSONCorruptionDuringDeserialization(Class<?> payloadType) {
        this.corruptJSONDuringDeserializationEnabled = true;
        this.onlyCorruptForPayloadType = payloadType;
    }

    /**
     * Disable corrupting of JSON during deserialization, returning to normal behavior
     */
    public void disableJSONCorruptionDuringDeserialization() {
        this.corruptJSONDuringDeserializationEnabled = false;
        this.onlyCorruptForPayloadType = null;
    }

    @Override
    public String serialize(Object obj) {
        return delegate.serialize(obj);
    }

    @Override
    public byte[] serializeAsBytes(Object obj) {
        return delegate.serializeAsBytes(obj);
    }

    @Override
    public <T> T deserialize(String json, String javaType) {
        if (corruptJSONDuringDeserializationEnabled &&
                javaType.equals(onlyCorruptForPayloadType.getName())) {
            // Corrupt the JSON by removing a closing brace - this will cause parse errors
            json = json.substring(0, json.length() - 1);
            disableJSONCorruptionDuringDeserialization();
        }

        return delegate.deserialize(json, javaType);
    }

    @Override
    public <T> T deserialize(String json, Class<T> javaType) {
        if (corruptJSONDuringDeserializationEnabled &&
                javaType.getName().equals(onlyCorruptForPayloadType.getName())) {
            // Corrupt the JSON by removing a closing brace - this will cause parse errors
            json = json.substring(0, json.length() - 1);
            disableJSONCorruptionDuringDeserialization();
        }

        return delegate.deserialize(json, javaType);
    }

    @Override
    public <T> T deserialize(byte[] json, String javaType) {
        if (corruptJSONDuringDeserializationEnabled &&
                javaType.equals(onlyCorruptForPayloadType.getName())) {
            String jsonStr = new String(json, UTF_8);
            // Corrupt the JSON by removing a closing brace - this will cause parse errors
            jsonStr = jsonStr.substring(0, jsonStr.length() - 1);
            json = jsonStr.getBytes(UTF_8);
            disableJSONCorruptionDuringDeserialization();
        }

        return delegate.deserialize(json, javaType);
    }

    @Override
    public <T> T deserialize(byte[] json, Class<T> javaType) {
        if (corruptJSONDuringDeserializationEnabled &&
                javaType.getName().equals(onlyCorruptForPayloadType.getName())) {
            String jsonStr = new String(json, UTF_8);
            // Corrupt the JSON by removing a closing brace - this will cause parse errors
            jsonStr = jsonStr.substring(0, jsonStr.length() - 1);
            json = jsonStr.getBytes(UTF_8);
            disableJSONCorruptionDuringDeserialization();
        }

        return delegate.deserialize(json, javaType);
    }

    @Override
    public ClassLoader getClassLoader() {
        return delegate.getClassLoader();
    }

    @Override
    public void setClassLoader(ClassLoader classLoader) {
        delegate.setClassLoader(classLoader);
    }
}