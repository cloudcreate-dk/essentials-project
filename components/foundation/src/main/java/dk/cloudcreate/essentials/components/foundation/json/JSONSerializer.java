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

/**
 * JSON serializer and deserializer
 */
public interface JSONSerializer {
    /**
     * Serialize the object to JSON
     *
     * @param obj the object to serialize
     * @return the serialized json payload as a String
     * @throws JSONSerializationException in case the json couldn't be deserialized to the specified java type
     */
    String serialize(Object obj);

    /**
     * Serialize the object to JSON
     *
     * @param obj the object to serialize
     * @return the serialized json payload as a byte[]
     * @throws JSONSerializationException in case the json couldn't be deserialized to the specified java type
     */
    byte[] serializeAsBytes(Object obj);

    /**
     * Deserialize the payload in the <code>json</code> parameter into the Java type specified by the Fully Qualified Class Name contained
     * in the <code>javaType</code> parameter
     *
     * @param json     the json payload
     * @param javaType the Fully Qualified Class Name for the Java type that the json payload should be deserialized into
     * @param <T>      the corresponding Java type
     * @return the deserialized json payload
     * @throws JSONDeserializationException in case the json couldn't be deserialized to the specified java type
     */
    <T> T deserialize(String json, String javaType);

    /**
     * Deserialize the payload in the <code>json</code> parameter into the Java type specified by the <code>javaType</code> parameter
     *
     * @param json     the json payload
     * @param javaType the Java type that the json payload should be deserialized into
     * @param <T>      the corresponding Java type
     * @return the deserialized json payload
     * @throws JSONDeserializationException in case the json couldn't be deserialized to the specified java type
     */
    <T> T deserialize(String json, Class<T> javaType);

    /**
     * Deserialize the payload in the <code>json</code> parameter into the Java type specified by the Fully Qualified Class Name contained
     * in the <code>javaType</code> parameter
     *
     * @param json     the json payload
     * @param javaType the Fully Qualified Class Name for the Java type that the json payload should be deserialized into
     * @param <T>      the corresponding Java type
     * @return the deserialized json payload
     * @throws JSONDeserializationException in case the json couldn't be deserialized to the specified java type
     */
    <T> T deserialize(byte[] json, String javaType);

    /**
     * Deserialize the payload in the <code>json</code> parameter into the Java type specified by the <code>javaType</code> parameter
     *
     * @param json     the json payload
     * @param javaType the Java type that the json payload should be deserialized into
     * @param <T>      the corresponding Java type
     * @return the deserialized json payload
     * @throws JSONDeserializationException in case the json couldn't be deserialized to the specified java type
     */
    <T> T deserialize(byte[] json, Class<T> javaType);

    /**
     * Get the {@link ClassLoader} used for serializing to JSON and deserializing from JSON<br>
     * <b>Note: MUST not be null</b>
     * @return the {@link ClassLoader} used for serializing to JSON and deserializing from JSON
     */
    ClassLoader getClassLoader();

    /**
     * Set the {@link ClassLoader} used for serializing to JSON and deserializing from JSON.
     * This is e.g. required to support SpringBoot DevTools
     * @param classLoader the {@link ClassLoader} used for serializing to JSON and deserializing from JSON
     */
    void setClassLoader(ClassLoader classLoader);
}
