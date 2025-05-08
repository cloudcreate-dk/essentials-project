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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.trustworks.essentials.components.foundation.types.*;
import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;
import java.util.Optional;

import static dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSONTest.createSerializer;
import static org.assertj.core.api.Assertions.assertThat;

class PersistedEventTest {
    public static final  String META_DATA_JSON = "{ \"metaData\": \"someValue\" }";
    private static final String EVENT_JSON     = "{\"testField\": \"testFieldValue\"}";

    private       JSONEventSerializer     jsonSerializer;
    private final EventType               eventType          = new EventType(this.getClass().getName() + "$TestEvent");
    private final EventName               eventName          = new EventName("ExampleEvent");
    private final EventJSONTest.TestEvent deserializedObject = new EventJSONTest.TestEvent("testFieldValue");


    @BeforeEach
    void setUp() {
        jsonSerializer = createSerializer();
    }


    @Test
    void valueEquals_should_return_true_for_equal_properties() {
        // Given
        var eventId          = EventId.random();
        var aggregateType    = AggregateType.of("TestAggregate");
        var aggregateId      = "aggregate-id-123";
        var event            = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder       = EventOrder.of(1);
        var eventRevision    = EventRevision.of(1);
        var globalEventOrder = GlobalEventOrder.of(1);
        var metaData         = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp        = OffsetDateTime.now();
        var causedByEventId  = Optional.of(EventId.random());
        var correlationId    = Optional.of(CorrelationId.random());
        var tenant           = Optional.of(TenantId.of("tenant-1"));

        var event1 = PersistedEvent.from(eventId, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);
        var event2 = PersistedEvent.from(eventId, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);

        // When & Then
        assertThat(event1.valueEquals(event2)).isTrue();
    }

    @Test
    void valueEquals_should_return_true_for_equal_properties_with_optional_empty() {
        // Given
        var eventId          = EventId.random();
        var aggregateType    = AggregateType.of("TestAggregate");
        var aggregateId      = "aggregate-id-123";
        var event            = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder       = EventOrder.of(1);
        var eventRevision    = EventRevision.of(1);
        var globalEventOrder = GlobalEventOrder.of(1);
        var metaData         = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp        = OffsetDateTime.now();
        var causedByEventId  = Optional.<EventId>empty();
        var correlationId    = Optional.<CorrelationId>empty();
        var tenant           = Optional.<Tenant>empty();

        var event1 = PersistedEvent.from(eventId, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);
        var event2 = PersistedEvent.from(eventId, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);

        // When & Then
        assertThat(event1.valueEquals(event2)).isTrue();
    }

    @Test
    void valueEquals_should_return_false_for_different_properties() {
        // Given
        var eventId1         = EventId.random();
        var eventId2         = EventId.random();
        var aggregateType    = AggregateType.of("TestAggregate");
        var aggregateId      = "aggregate-id-123";
        var event            = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder       = EventOrder.of(1);
        var eventRevision    = EventRevision.of(1);
        var globalEventOrder = GlobalEventOrder.of(1);
        var metaData         = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp        = OffsetDateTime.now();
        var causedByEventId  = Optional.of(EventId.random());
        var correlationId    = Optional.of(CorrelationId.random());
        var tenant           = Optional.of(TenantId.of("tenant-1"));

        var event1 = PersistedEvent.from(eventId1, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);
        var event2 = PersistedEvent.from(eventId2, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);

        // When & Then
        assertThat(event1.valueEquals(event2)).isFalse();
    }

    @Test
    void equals_should_return_true_for_same_eventId_even_if_other_properties_are_different() {
        // Given
        var eventId         = EventId.random();
        var aggregateType   = AggregateType.of("TestAggregate");
        var event           = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder      = EventOrder.of(1);
        var eventRevision   = EventRevision.of(1);
        var metaData        = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp       = OffsetDateTime.now();
        var causedByEventId = Optional.of(EventId.random());
        var correlationId   = Optional.of(CorrelationId.random());
        var tenant          = Optional.of(TenantId.of("tenant-1"));

        var event1 = PersistedEvent.from(eventId, aggregateType, "aggregate-id-123", event, eventOrder,
                                         eventRevision, GlobalEventOrder.of(1), metaData, timestamp, causedByEventId, correlationId, tenant);
        var event2 = PersistedEvent.from(eventId, aggregateType, "aggregate-id-456", event, eventOrder,
                                         eventRevision, GlobalEventOrder.of(2), metaData, timestamp, causedByEventId, correlationId, tenant);

        // When & Then
        assertThat(event1).isEqualTo(event2);
    }

    @Test
    void equals_should_return_false_for_different_eventId() {
        // Given
        var eventId1         = EventId.random();
        var eventId2         = EventId.random();
        var aggregateType    = AggregateType.of("TestAggregate");
        var aggregateId      = "aggregate-id-123";
        var event            = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder       = EventOrder.of(1);
        var eventRevision    = EventRevision.of(1);
        var globalEventOrder = GlobalEventOrder.of(1);
        var metaData         = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp        = OffsetDateTime.now();
        var causedByEventId  = Optional.of(EventId.random());
        var correlationId    = Optional.of(CorrelationId.random());
        var tenant           = Optional.of(TenantId.of("tenant-1"));

        var event1 = PersistedEvent.from(eventId1, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);
        var event2 = PersistedEvent.from(eventId2, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);

        // When & Then
        assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    void should_return_correct_property_values() {
        // Given
        var eventId          = EventId.random();
        var aggregateType    = AggregateType.of("TestAggregate");
        var aggregateId      = "aggregate-id-123";
        var eventJson        = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder       = EventOrder.of(1);
        var eventRevision    = EventRevision.of(1);
        var globalEventOrder = GlobalEventOrder.of(1);
        var metaData         = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp        = OffsetDateTime.now();
        var causedByEventId  = Optional.of(EventId.random());
        var correlationId    = Optional.of(CorrelationId.random());
        var tenant           = Optional.of(TenantId.of("tenant-1"));

        var event = PersistedEvent.from(eventId, aggregateType, aggregateId, eventJson, eventOrder,
                                        eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);

        // When & Then
        assertThat((CharSequence) event.eventId()).isEqualTo(eventId);
        assertThat((CharSequence) event.aggregateType()).isEqualTo(aggregateType);
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.event()).isEqualTo(eventJson);
        assertThat(event.eventOrder()).isEqualTo(eventOrder);
        assertThat(event.eventRevision()).isEqualTo(eventRevision);
        assertThat(event.globalEventOrder()).isEqualTo(globalEventOrder);
        assertThat(event.metaData()).isEqualTo(metaData);
        assertThat(event.timestamp()).isEqualTo(timestamp);
        assertThat(event.causedByEventId()).isEqualTo(causedByEventId);
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.tenant()).isEqualTo(tenant);
    }

    @Test
    void hashCode_should_be_equal_for_same_eventId_even_if_other_properties_are_different() {
        // Given
        var eventId         = EventId.random();
        var aggregateType   = AggregateType.of("TestAggregate");
        var event           = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder      = EventOrder.of(1);
        var eventRevision   = EventRevision.of(1);
        var metaData        = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp       = OffsetDateTime.now();
        var causedByEventId = Optional.of(EventId.random());
        var correlationId   = Optional.of(CorrelationId.random());
        var tenant          = Optional.of(TenantId.of("tenant-1"));

        var event1 = PersistedEvent.from(eventId, aggregateType, "aggregate-id-123", event, eventOrder,
                                         eventRevision, GlobalEventOrder.of(1), metaData, timestamp, causedByEventId, correlationId, tenant);
        var event2 = PersistedEvent.from(eventId, aggregateType, "aggregate-id-456", event, eventOrder,
                                         eventRevision, GlobalEventOrder.of(2), metaData, timestamp, causedByEventId, correlationId, tenant);

        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    void hashCode_should_return_false_for_different_eventId() {
        // Given
        var eventId1         = EventId.random();
        var eventId2         = EventId.random();
        var aggregateType    = AggregateType.of("TestAggregate");
        var aggregateId      = "aggregate-id-123";
        var event            = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder       = EventOrder.of(1);
        var eventRevision    = EventRevision.of(1);
        var globalEventOrder = GlobalEventOrder.of(1);
        var metaData         = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp        = OffsetDateTime.now();
        var causedByEventId  = Optional.of(EventId.random());
        var correlationId    = Optional.of(CorrelationId.random());
        var tenant           = Optional.of(TenantId.of("tenant-1"));

        var event1 = PersistedEvent.from(eventId1, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);
        var event2 = PersistedEvent.from(eventId2, aggregateType, aggregateId, event, eventOrder,
                                         eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);

        // When & Then
        assertThat(event1.hashCode()).isNotEqualTo(event2.hashCode());
    }

    @Test
    void toString_should_return_string_without_event_json_payload() {
        // Given
        var eventId          = EventId.random();
        var aggregateType    = AggregateType.of("TestAggregate");
        var aggregateId      = "aggregate-id-123";
        var eventJson        = new EventJSON(jsonSerializer, deserializedObject, eventType, EVENT_JSON);
        var eventOrder       = EventOrder.of(1);
        var eventRevision    = EventRevision.of(1);
        var globalEventOrder = GlobalEventOrder.of(1);
        var metaData         = new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), META_DATA_JSON);
        var timestamp        = OffsetDateTime.now();
        var causedByEventId  = Optional.of(EventId.random());
        var correlationId    = Optional.of(CorrelationId.random());
        var tenant           = Optional.of(TenantId.of("tenant-1"));

        var event = PersistedEvent.from(eventId, aggregateType, aggregateId, eventJson, eventOrder,
                                        eventRevision, globalEventOrder, metaData, timestamp, causedByEventId, correlationId, tenant);

        // When
        var toStringResult = event.toString();

        // Then
        assertThat(toStringResult).isNotEmpty();
        assertThat(toStringResult).doesNotContain(EVENT_JSON);
        assertThat(toStringResult).contains(META_DATA_JSON);
    }
}