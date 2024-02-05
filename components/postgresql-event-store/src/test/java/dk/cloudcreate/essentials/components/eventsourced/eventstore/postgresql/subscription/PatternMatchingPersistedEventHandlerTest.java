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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreSubscription;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.types.EventId;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class PatternMatchingPersistedEventHandlerTest {
    @Test
    void test_eventhandler_method_matching() throws JsonProcessingException {
        var eventHandler = new TestPatternMatchingPersistedEventHandler();
        var orderId      = OrderId.random();

        // public method with one argument
        var orderAdded = new OrderEvent.OrderAdded(orderId,
                                                   CustomerId.random(),
                                                   1234);
        eventHandler.handle(typedEvent(orderId,
                                       orderAdded,
                                       0));
        assertThat(eventHandler.orderAdded).isEqualTo(orderAdded);

        // private method with one argument
        var productAddedToOrder = new OrderEvent.ProductAddedToOrder(orderId,
                                                                     ProductId.random(),
                                                                     2);
        eventHandler.handle(typedEvent(orderId,
                                       productAddedToOrder,
                                       1));
        assertThat(eventHandler.productAddedToOrder).isEqualTo(productAddedToOrder);

        // Private method with a second argument that's not a PersistedEvent, which causes the method not to match and hence we get an exception
        var productOrderQuantityAdjusted = new OrderEvent.ProductOrderQuantityAdjusted(orderId,
                                                                                       ProductId.random(),
                                                                                       10);
        assertThatThrownBy(() -> eventHandler.handle(typedEvent(orderId,
                                                                productOrderQuantityAdjusted,
                                                                2)))
                .isExactlyInstanceOf(IllegalArgumentException.class);

        // Private method with 2 arguments, 2nd argument is of type PersistedEvent
        var productRemovedFromOrder = new OrderEvent.ProductRemovedFromOrder(orderId,
                                                                             ProductId.random());
        var typedProductRemoveFromOrderPersistedEvent = typedEvent(orderId,
                                                                   productRemovedFromOrder,
                                                                   3);
        eventHandler.handle(typedProductRemoveFromOrderPersistedEvent);
        assertThat(eventHandler.productRemovedFromOrder).isEqualTo(productRemovedFromOrder);
        assertThat(eventHandler.productRemovedFromOrderPersistedEvent).isEqualTo(typedProductRemoveFromOrderPersistedEvent);

        // Private method that will match a Named event
        var productAddedToOrder2 = new OrderEvent.ProductAddedToOrder(orderId,
                                                                      ProductId.random(),
                                                                      20);
        var namedProductAddedToOrderPersistedEvent = namedEvent(orderId,
                                                                productAddedToOrder2,
                                                                4);
        eventHandler.handle(namedProductAddedToOrderPersistedEvent);
        assertThat(eventHandler.json).isEqualTo(objectMapper.writeValueAsString(productAddedToOrder2));
        assertThat(eventHandler.jsonPersistedEvent).isEqualTo(namedProductAddedToOrderPersistedEvent);

        // Private method WITHOUT SubscriptionEventHandler annotation, hence it doesn't match
        var orderAccepted = new OrderEvent.OrderAccepted(orderId);
        assertThatThrownBy(() -> eventHandler.handle(typedEvent(orderId,
                                                                orderAccepted,
                                                                5)))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    private static class TestPatternMatchingPersistedEventHandler extends PatternMatchingPersistedEventHandler {
        private OrderEvent.OrderAdded                   orderAdded;
        private OrderEvent.ProductAddedToOrder          productAddedToOrder;
        private OrderEvent.ProductOrderQuantityAdjusted productOrderQuantityAdjusted;
        private PersistedEvent                          productRemovedFromOrderPersistedEvent;
        private OrderEvent.ProductRemovedFromOrder      productRemovedFromOrder;
        private OrderEvent.OrderAccepted                orderAccepted;
        private String                                  json;
        private PersistedEvent                          jsonPersistedEvent;

        @Override
        public void onResetFrom(EventStoreSubscription eventStoreSubscription, GlobalEventOrder globalEventOrder) {

        }

        @SubscriptionEventHandler
        public void handle(OrderEvent.OrderAdded orderAdded) {
            if (this.orderAdded != null) throw new IllegalStateException("orderAdded field already set");
            this.orderAdded = orderAdded;
        }

        @SubscriptionEventHandler
        private void handle(OrderEvent.ProductAddedToOrder productAddedToOrder) {
            if (this.productAddedToOrder != null) throw new IllegalStateException("productAddedToOrder field already set");
            this.productAddedToOrder = productAddedToOrder;
        }

        @SubscriptionEventHandler
        private void handle(OrderEvent.ProductOrderQuantityAdjusted productOrderQuantityAdjusted, Object unused) {
            if (this.productOrderQuantityAdjusted != null) throw new IllegalStateException("productOrderQuantityAdjusted field already set");
            this.productOrderQuantityAdjusted = productOrderQuantityAdjusted;
        }

        @SubscriptionEventHandler
        private void handle(OrderEvent.ProductRemovedFromOrder productRemovedFromOrder, PersistedEvent productRemovedFromOrderPersistedEvent) {
            if (this.productRemovedFromOrder != null) throw new IllegalStateException("productRemovedFromOrder field already set");
            this.productRemovedFromOrderPersistedEvent = productRemovedFromOrderPersistedEvent;
            this.productRemovedFromOrder = productRemovedFromOrder;
        }

        @SubscriptionEventHandler
        private void handle(String json, PersistedEvent jsonPersistedEvent) {
            if (this.json != null) throw new IllegalStateException("json field already set");
            this.json = json;
            this.jsonPersistedEvent = jsonPersistedEvent;
        }

        private void handle(OrderEvent.OrderAccepted orderAccepted) {
            if (this.orderAccepted != null) throw new IllegalStateException("orderAccepted field already set");
            this.orderAccepted = orderAccepted;
        }
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

    private static PersistedEvent namedEvent(Object aggregateId, Object event, long eventOrder) throws JsonProcessingException {
        return PersistedEvent.from(EventId.random(),
                                   AggregateType.of("Orders"),
                                   aggregateId,
                                   new EventJSON(jsonSerializer,
                                                 EventName.of(event.getClass().getSimpleName()),
                                                 objectMapper.writeValueAsString(event)),
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