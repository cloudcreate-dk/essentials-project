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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypeTest {
    @Test
    void test_creating_a_EventType_instance_from_a_String_value() {
        // Given
        var fqcn = EventJSON.class.getName();
        // When
        var eventJavaType = EventType.of(fqcn);
        // Then
        assertThat(eventJavaType.toString()).isEqualTo(EventType.FQCN_PREFIX + fqcn);
        assertThat(eventJavaType.getJavaTypeName()).isEqualTo(fqcn);
        assertThat(eventJavaType.toJavaClass()).isEqualTo(EventJSON.class);
    }

    @Test
    void test_creating_a_EventType_instance_from_a_Class_value() {
        // Given
        var type = EventJSON.class;
        var fqcn = type.getName();
        // When
        var eventJavaType = EventType.of(type);
        // Then
        assertThat(eventJavaType.toString()).isEqualTo(EventType.FQCN_PREFIX + fqcn);
        assertThat(eventJavaType.getJavaTypeName()).isEqualTo(fqcn);
        assertThat(eventJavaType.toJavaClass()).isEqualTo(type);
    }

    @Test
    void test_creating_a_EventType_instance_from_a_serialized_String_value_that_starts_with_fqcn_prefix() {
        // Given
        var fqcn = EventJSON.class.getName();
        var eventJavaType = EventType.of(fqcn);
        assertThat(eventJavaType.toString()).isEqualTo(EventType.FQCN_PREFIX + fqcn);
        // When
        var eventJavaTypeFromSerializedValue = EventType.of(eventJavaType.toString());
        // Then
        assertThat(eventJavaTypeFromSerializedValue.toString()).isEqualTo(EventType.FQCN_PREFIX + fqcn);
        assertThat(eventJavaTypeFromSerializedValue.getJavaTypeName()).isEqualTo(fqcn);
        assertThat(eventJavaTypeFromSerializedValue.toJavaClass()).isEqualTo(EventJSON.class);
        assertThat(eventJavaTypeFromSerializedValue.equals(eventJavaType)).isTrue();
        assertThat((CharSequence) eventJavaTypeFromSerializedValue).isEqualTo(eventJavaType);
    }

    @Test
    void test_two_different_EventTypes_are_not_equal() {
        // Given
        var eventJavaType = EventType.of(EventJSON.class);
        // When
        var otherEventJavaType = EventType.of(EventMetaDataJSON.class);
        // Then
        assertThat(eventJavaType.getJavaTypeName()).isNotEqualTo(otherEventJavaType.getJavaTypeName());
        assertThat(eventJavaType.toJavaClass()).isNotEqualTo(otherEventJavaType.toJavaClass());
        assertThat(eventJavaType.toString()).isNotEqualTo(otherEventJavaType.toString());
        assertThat(eventJavaType.equals(otherEventJavaType)).isFalse();
        assertThat((CharSequence) eventJavaType).isNotEqualTo(otherEventJavaType);
    }

    @Test
    void test_isSerializedEventType() {
        // Given
        var fqcn = EventJSON.class.getName();
        var eventJavaType = EventType.of(fqcn);
        // Then
        assertThat(EventType.isSerializedEventType(eventJavaType.toString())).isTrue();
        assertThat(EventType.isSerializedEventType(fqcn)).isFalse();
        assertThat(EventType.isSerializedEventType(eventJavaType.getJavaTypeName())).isFalse();
    }
}