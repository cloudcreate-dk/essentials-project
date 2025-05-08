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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData;
import dk.trustworks.essentials.components.foundation.json.JSONDeserializationException;
import org.junit.jupiter.api.*;

import static dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSONTest.createSerializer;
import static org.assertj.core.api.Assertions.*;

class EventMetaDataJSONTest {

    private              JSONEventSerializer jsonEventSerializer;
    private static final String              JAVA_TYPE = EventMetaData.class.getName();
    private              String              json;
    private              EventMetaData       jsonDeserialized;

    @BeforeEach
    void setUp() {
        jsonEventSerializer = createSerializer();
        jsonDeserialized = EventMetaData.of("key", "value");
        json = jsonEventSerializer.serialize(jsonDeserialized);
    }

    @Test
    void givenEventMetaDataJSON_whenCallingGetJsonDeserialized_thenReturnsCorrectObject() {
        // Given
        var eventMetaDataJSON = new EventMetaDataJSON(jsonEventSerializer, jsonDeserialized, JAVA_TYPE, json);

        // When
        var deserialized = eventMetaDataJSON.getJsonDeserialized();

        // Then
        assertThat(deserialized).isPresent().containsSame(jsonDeserialized);
    }

    @Test
    void givenEventMetaDataJSONWithoutDeserializedObject_whenCallingGetJsonDeserialized_thenDeserializeObject() {
        // Given
        var eventMetaDataJSON = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);

        // When
        var deserialized = eventMetaDataJSON.getJsonDeserialized();

        // Then
        assertThat(deserialized).isPresent().contains(jsonDeserialized);
    }

    @Test
    void givenEventMetaDataJSON_whenCallingDeserialize_thenReturnsCorrectObject() {
        // Given
        var eventMetaDataJSON = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);

        // When
        var deserialized = eventMetaDataJSON.deserialize();

        // Then
        assertThat(deserialized).isEqualTo(jsonDeserialized);
    }

    @Test
    void givenEventMetaDataJSONWithoutSerializer_whenCallingDeserialize_thenThrowsException() {
        // Given
        var eventMetaDataJSON = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);

        // When
        eventMetaDataJSON = jsonEventSerializer.deserialize(jsonEventSerializer.serialize(eventMetaDataJSON), EventMetaDataJSON.class);

        // When
        assertThatThrownBy(eventMetaDataJSON::deserialize)
                .isInstanceOf(JSONDeserializationException.class)
                .hasMessageContaining("No JSONSerializer specified for deserialization");
    }

    @Test
    void givenTwoEqualEventMetaDataJSONInstances_whenCallingEquals_thenTheyAreEqual() {
        // Given
        var eventMetaDataJSON1 = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);
        var eventMetaDataJSON2 = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);

        // When/Then
        assertThat(eventMetaDataJSON1).isEqualTo(eventMetaDataJSON2);
    }

    @Test
    void givenTwoDifferentEventMetaDataJSONInstances_whenCallingEquals_thenTheyAreNotEqual() {
        // Given
        var eventMetaDataJSON1 = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);
        var eventMetaDataJSON2 = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, "{\"differentKey\": \"differentValue\"}");

        // When/Then
        assertThat(eventMetaDataJSON1).isNotEqualTo(eventMetaDataJSON2);
    }

    @Test
    void givenEventMetaDataJSON_whenCallingHashCode_thenReturnsCorrectHashCode() {
        // Given
        var eventMetaDataJSON1 = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);
        var eventMetaDataJSON2 = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);

        // When/Then
        assertThat(eventMetaDataJSON1.hashCode()).isEqualTo(eventMetaDataJSON2.hashCode());
    }

    @Test
    void givenEventMetaDataJSON_whenCallingToString_thenReturnsExpectedString() {
        // Given
        var eventMetaDataJSON = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);

        // When
        var toStringResult = eventMetaDataJSON.toString();

        // Then
        assertThat(toStringResult).isEqualTo("EventMetaDataJSON{javaType='" + JAVA_TYPE + "', json='" + json + "'}");
    }

    @Test
    void givenEventMetaDataJSONWithJavaType_whenCallingGetProperties_thenReturnsJavaTypeAndJson() {
        // Given
        var eventMetaDataJSON = new EventMetaDataJSON(jsonEventSerializer, JAVA_TYPE, json);

        // When & Then
        assertThat(eventMetaDataJSON.getJavaType()).isPresent().contains(JAVA_TYPE);
        assertThat(eventMetaDataJSON.getJson()).isEqualTo(json);
    }

    @Test
    void givenEventMetaDataJSONWithoutJavaType_whenCallingGetProperties_thenReturnsOnlyJson() {
        // Given
        var eventMetaDataJSON = new EventMetaDataJSON(jsonEventSerializer, null, json);

        // When & Then
        assertThat(eventMetaDataJSON.getJavaType()).isNotPresent();
        assertThat(eventMetaDataJSON.getJson()).isEqualTo(json);
    }
}
