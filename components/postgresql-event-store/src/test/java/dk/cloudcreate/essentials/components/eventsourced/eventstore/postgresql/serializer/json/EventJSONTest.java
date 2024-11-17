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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.json.JSONDeserializationException;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

public class EventJSONTest {

    private       JSONEventSerializer jsonSerializer;
    private final String              jsonPayload        = "{\"testField\": \"testFieldValue\"}";
    private final EventType           eventType          = new EventType(this.getClass().getName() + "$TestEvent");
    private final EventName           eventName          = new EventName("ExampleEvent");
    private final TestEvent           deserializedObject = new TestEvent("testFieldValue");

    @BeforeEach
    void setUp() {
        jsonSerializer = createSerializer();
    }

    @Test
    void testConstructorWithEventTypeAndJsonDeserialized() {
        var eventJSON = new EventJSON(jsonSerializer, deserializedObject, eventType, jsonPayload);

        assertThat(eventJSON.getEventType()).contains(eventType);
        assertThat(eventJSON.getJsonDeserialized()).isPresent().contains(deserializedObject);
        assertThat(eventJSON.getJson()).isEqualTo(jsonPayload);
    }

    @Test
    void testConstructorWithEventType() {
        var eventJSON = new EventJSON(jsonSerializer, eventType, jsonPayload);

        assertThat(eventJSON.getEventType()).contains(eventType);
        assertThat(eventJSON.getJson()).isEqualTo(jsonPayload);
    }

    @Test
    void testConstructorWithEventNameAndJsonDeserialized() {
        var eventJSON = new EventJSON(jsonSerializer, deserializedObject, eventName, jsonPayload);

        assertThat(eventJSON.getEventName()).contains(eventName);
        assertThat(eventJSON.getJsonDeserialized()).isPresent().contains(deserializedObject);
        assertThat(eventJSON.getJson()).isEqualTo(jsonPayload);
    }

    @Test
    void testConstructorWithEventName() {
        var eventJSON = new EventJSON(jsonSerializer, eventName, jsonPayload);

        assertThat(eventJSON).isNotNull();
        assertThat(eventJSON.getEventName()).contains(eventName);
        assertThat(eventJSON.getJson()).isEqualTo(jsonPayload);
    }

    @Test
    void testGetJsonDeserialized_DeserializeOnDemand() {
        var eventJSON = new EventJSON(jsonSerializer, eventType, jsonPayload);

        Optional<TestEvent> deserialized = eventJSON.getJsonDeserialized();
        assertThat(deserialized).isPresent();
        assertThat(deserialized.get()).isEqualTo(deserializedObject);
        assertThat(deserialized.get().getTestField()).isEqualTo("testFieldValue");
    }

    @Test
    void testDeserialize_ThrowsExceptionWhenNoSerializerProvided() {
        var eventJSON = new EventJSON(jsonSerializer, eventType, jsonPayload);
        eventJSON = jsonSerializer.deserialize(jsonSerializer.serialize(eventJSON), EventJSON.class);
        assertThatThrownBy(eventJSON::deserialize)
                .isInstanceOf(JSONDeserializationException.class)
                .hasMessageContaining("No JSONSerializer specified");
    }

    @Test
    void testDeserialize_ThrowsExceptionWhenNoEventTypeProvided() {
        var eventJSON = new EventJSON(jsonSerializer, eventName, jsonPayload);
        assertThatThrownBy(eventJSON::deserialize)
                .isInstanceOf(JSONDeserializationException.class)
                .hasMessageContaining("No EventJavaType specified");
    }

    @Test
    void testGetEventTypeOrNamePersistenceValue_WithEventType() {
        var eventJSON = new EventJSON(jsonSerializer, eventType, jsonPayload);
        assertThat(eventJSON.getEventTypeOrNamePersistenceValue()).isEqualTo(eventType.toString());
    }

    @Test
    void testGetEventTypeOrNamePersistenceValue_WithEventName() {
        var eventJSON = new EventJSON(jsonSerializer, eventName, jsonPayload);
        assertThat(eventJSON.getEventTypeOrNamePersistenceValue()).isEqualTo(eventName.toString());
    }

    @Test
    void testEqualsAndHashCode() {
        var eventJSON1 = new EventJSON(jsonSerializer, eventType, jsonPayload);
        var eventJSON2 = new EventJSON(jsonSerializer, eventType, jsonPayload);

        assertThat(eventJSON1).isEqualTo(eventJSON2);
        assertThat(eventJSON1.hashCode()).isEqualTo(eventJSON2.hashCode());
    }

    @Test
    void testNotEquals() {
        var eventJSON1 = new EventJSON(jsonSerializer, eventType, jsonPayload);
        var eventJSON2 = new EventJSON(jsonSerializer, eventName, jsonPayload);

        assertThat(eventJSON1).isNotEqualTo(eventJSON2);
    }

    @Test
    void testToString() {
        var    eventJSON = new EventJSON(jsonSerializer, eventType, jsonPayload);
        String expected  = "EventJSON{eventTypeOrName=" + EventTypeOrName.with(eventType) + "}";
        assertThat(eventJSON.toString()).isEqualTo(expected);
    }

    @Test
    void testGetEventTypeAsJavaClass() {
        var eventJSON = new EventJSON(jsonSerializer, eventType, jsonPayload);

        assertThat(eventJSON.getEventTypeAsJavaClass()).isPresent();
        assertThat(eventJSON.getEventTypeAsJavaClass().get()).isEqualTo(TestEvent.class);
    }

    public static class TestEvent {
        private String testField;

        public TestEvent(String testField) {
            this.testField = testField;
        }

        public String getTestField() {
            return testField;
        }

        public void setTestField(String testField) {
            this.testField = testField;
        }
    }

    public static JacksonJSONEventSerializer createSerializer() {
        var objectMapper = JsonMapper.builder()
                                     .disable(MapperFeature.AUTO_DETECT_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_SETTERS)
                                     .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                                     .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                     .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                     .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                     .enable(MapperFeature.AUTO_DETECT_CREATORS)
                                     .enable(MapperFeature.AUTO_DETECT_FIELDS)
                                     .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                                     .addModule(new Jdk8Module())
                                     .addModule(new JavaTimeModule())
                                     .addModule(new EssentialTypesJacksonModule())
                                     .addModule(new EssentialsImmutableJacksonModule())
                                     .build();

        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return new JacksonJSONEventSerializer(objectMapper);
    }
}