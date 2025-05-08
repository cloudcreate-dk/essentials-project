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

package dk.trustworks.essentials.components.eventsourced.aggregates.flex;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.trustworks.essentials.components.eventsourced.aggregates.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.trustworks.essentials.components.foundation.types.EventId;
import dk.trustworks.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.trustworks.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.trustworks.essentials.types.LongRange;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class FlexAggregateTest {
    @Test
    void createAggregateWithNullHistoricPersistedEvents() {
        Assertions.assertThatThrownBy(() -> new Order().rehydrate(OrderId.random(), (List<Object>) null))
                  .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAggregateWithNullId() {
        Assertions.assertThatThrownBy(() -> new Order().rehydrate(null, List.of()))
                  .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAggregateWithIdAndNullHistoricEvents() {
        Assertions.assertThatThrownBy(() -> new Order().rehydrate(OrderId.random(), (Stream<Object>) null))
                  .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAggregateWithNullEventsToPersist() {
        Assertions.assertThatThrownBy(() -> new Order().rehydrate((EventsToPersist<OrderId, Object>) null))
                  .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAggregateWithNullAggregateEventStream() {
        Assertions.assertThatThrownBy(() -> new Order().rehydrate((AggregateEventStream<OrderId>) null))
                  .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAggregateExplicitAggregateIdAndHistoricEvents() {
        // Given
        var orderId       = OrderId.random();
        var orderAdded    = new Order.OrderAdded(orderId, CustomerId.random(), 1234);
        var orderAccepted = new Order.OrderAccepted(orderId);

        // When
        var aggregate = new Order().rehydrate(orderId, List.of(orderAdded, orderAccepted));

        // Then
        assertThat(aggregate.aggregateId().toString()).isEqualTo(orderId.toString());
        assertThat(aggregate.eventOrderOfLastRehydratedEvent()).isEqualTo(EventOrder.of(1));
        assertThat(aggregate.hasBeenRehydrated()).isEqualTo(true);
        assertThat(aggregate.isAccepted()).isEqualTo(true);
    }

    @Test
    void createAggregateExplicitAggregateIdAndNoHistoricEvents() {
        // Given
        var orderId = OrderId.random();

        // When
        var aggregate = new Order().rehydrate(orderId, List.of());

        // Then
        assertThat(aggregate.aggregateId().toString()).isEqualTo(orderId.toString());
        assertThat(aggregate.eventOrderOfLastRehydratedEvent()).isEqualTo(EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED);
        assertThat(aggregate.hasBeenRehydrated()).isEqualTo(false);
        assertThat(aggregate.isAccepted()).isEqualTo(false);
    }

    @Test
    void createAggregateWithHistoricPersistedEvents() {
        // Given
        var orderId       = OrderId.random();
        var eventMetaData = EventMetaData.of("Hello", "World", "TrackingId", "1234");

        var orderAdded    = new Order.OrderAdded(orderId, CustomerId.random(), 1234);
        var orderAccepted = new Order.OrderAccepted(orderId);

        var orderAddedPersistedEvent = typedEvent(orderId,
                                                  orderAdded,
                                                  0);
        var orderAcceptedPersistedEvent = typedEvent(orderId,
                                                     orderAccepted,
                                                     1);

        // When
        var aggregate = new Order().rehydrate(
                AggregateEventStream.of(Mockito.mock(AggregateEventStreamConfiguration.class),
                                        orderId,
                                        LongRange.from(0),
                                        Stream.of(orderAddedPersistedEvent,
                                                  orderAcceptedPersistedEvent)));

        // Then
        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.eventOrderOfLastRehydratedEvent()).isEqualTo(EventOrder.of(1));
        assertThat(aggregate.hasBeenRehydrated()).isEqualTo(true);
        assertThat(aggregate.isAccepted()).isEqualTo(true);
    }

    // -------------------------- Supporting methods -------------------------------------

    private static ObjectMapper        objectMapper   = createObjectMapper();
    private static JSONEventSerializer jsonSerializer = new JacksonJSONEventSerializer(objectMapper);

    private static PersistedEvent typedEvent(Object aggregateId, Object event, long eventOrder) {
        return PersistedEvent.from(EventId.random(),
                                   AggregateType.of("Orders"),
                                   aggregateId,
                                   jsonSerializer.serializeEvent(event),
                                   EventOrder.of(eventOrder),
                                   EventRevision.of(1),
                                   GlobalEventOrder.of(eventOrder),
                                   new EventMetaDataJSON(jsonSerializer, EventMetaData.class.getName(), "{}"),
                                   OffsetDateTime.now(),
                                   Optional.empty(),
                                   Optional.empty(),
                                   Optional.empty());
    }

    private static ObjectMapper createObjectMapper() {
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
        return objectMapper;
    }
}
