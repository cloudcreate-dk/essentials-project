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

package dk.trustworks.essentials.components.eventsourced.aggregates.classic;

import dk.trustworks.essentials.components.eventsourced.aggregates.*;
import dk.trustworks.essentials.components.eventsourced.aggregates.classic.OrderEvents.*;
import dk.trustworks.essentials.components.eventsourced.aggregates.stateful.classic.InitialEventIsMissingAggregateIdException;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderAggregateRootTest {

    @Test
    void verify_that_an_initial_event_with_null_aggregateid_causes_failure() {
        assertThatThrownBy(() -> new Order(null, CustomerId.random(), 123))
                .isExactlyInstanceOf(InitialEventIsMissingAggregateIdException.class);
    }

    @Test
    void verify_the_aggregates_id_is_the_same_as_the_initial_events_aggregateid() {
        // Given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;

        // When
        var order = new Order(orderId,
                              orderingCustomerId,
                              orderNumber);

        // Then
        var uncommittedChanges = order.getUncommittedChanges();
        assertThat(uncommittedChanges.size()).isEqualTo(1);
        assertThat(uncommittedChanges.get(0)).isInstanceOf(OrderAdded.class);

        var orderAddedEvent = (OrderAdded) uncommittedChanges.get(0);
        assertThat((CharSequence) orderAddedEvent.aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) orderAddedEvent.getOrderingCustomerId()).isEqualTo(orderingCustomerId);
        assertThat(orderAddedEvent.getOrderNumber()).isEqualTo(orderNumber);
        assertThat(orderAddedEvent.eventOrder()).isEqualTo(EventOrder.of(0));

        assertThat((CharSequence) order.aggregateId()).isEqualTo(orderId);
        assertThat(order.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(0));
    }

    @Test
    void verify_markChangesAsCommitted_resets_uncomittedChanges() {
        // Given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;

        var aggregate = new Order(orderId, orderingCustomerId, orderNumber);
        assertThat(aggregate.getUncommittedChanges().size()).isEqualTo(1);
        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(0));

        // When
        aggregate.markChangesAsCommitted();

        // Then
        assertThat(aggregate.getUncommittedChanges().size()).isEqualTo(0);
    }

    @Test
    void test_rehydrating_aggregate() {
        // given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;
        var productId          = ProductId.random();

        var aggregate = new Order(orderId, orderingCustomerId, orderNumber);
        assertThat(aggregate.productAndQuantity.get(productId)).isNull();

        // And
        aggregate.addProduct(productId, 10);

        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.productAndQuantity.get(productId)).isEqualTo(10);
        var uncommittedChanges = aggregate.getUncommittedChanges();
        assertThat(uncommittedChanges.size()).isEqualTo(2);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(1));

        assertThat(uncommittedChanges.get(1)).isInstanceOf(ProductAddedToOrder.class);
        var productAddedEvent = (ProductAddedToOrder) uncommittedChanges.get(1);
        assertThat((CharSequence) productAddedEvent.aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) productAddedEvent.getProductId()).isEqualTo(productId);
        assertThat(productAddedEvent.getQuantity()).isEqualTo(10);
        assertThat(productAddedEvent.eventOrder()).isEqualTo(EventOrder.of(1));

        // when
        var rehydratedAggregate = new Order().rehydrate(uncommittedChanges.stream());

        // then
        assertThat((CharSequence) rehydratedAggregate.aggregateId()).isEqualTo(orderId);
        assertThat(rehydratedAggregate.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(rehydratedAggregate.getUncommittedChanges().size()).isEqualTo(0);
        assertThat(rehydratedAggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(1));
    }

    @Test
    void test_rehydrating_aggregate_and_then_modifying_the_aggregate_state() {
        // given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;
        var productId          = ProductId.random();

        var aggregate = new Order(orderId, orderingCustomerId, orderNumber);
        assertThat(aggregate.productAndQuantity.get(productId)).isNull();

        // And
        aggregate.addProduct(productId, 10);

        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(aggregate.getUncommittedChanges().size()).isEqualTo(2);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(1));

        // when
        var rehydratedAggregate = new Order().rehydrate(aggregate.getUncommittedChanges().stream());
        var newProductId = ProductId.random();
        rehydratedAggregate.addProduct(newProductId, 3);

        // then
        assertThat(rehydratedAggregate.getUncommittedChanges().size()).isEqualTo(1);
        assertThat(rehydratedAggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(2));
        assertThat(rehydratedAggregate.getUncommittedChanges().get(0)).isInstanceOf(ProductAddedToOrder.class);

        var newProductAddedEvent = (ProductAddedToOrder) rehydratedAggregate.getUncommittedChanges().get(0);
        assertThat((CharSequence) newProductAddedEvent.aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) newProductAddedEvent.getProductId()).isEqualTo(newProductId);
        assertThat(newProductAddedEvent.getQuantity()).isEqualTo(3);
        assertThat(newProductAddedEvent.eventOrder()).isEqualTo(EventOrder.of(2));

        assertThat((CharSequence) rehydratedAggregate.aggregateId()).isEqualTo(orderId);
        assertThat(rehydratedAggregate.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(rehydratedAggregate.productAndQuantity.get(newProductId)).isEqualTo(3);
    }
}