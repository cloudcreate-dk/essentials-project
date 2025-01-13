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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.with_state;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.OrderEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregateRootWithStateTest {
    @Test
    void verify_markChangesAsCommitted_resets_uncomittedChanges() {
        // Given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;

        var aggregate          = new Order(orderId, orderingCustomerId, orderNumber);
        var uncommittedChanges = aggregate.getUncommittedChanges();
        assertThat((CharSequence) uncommittedChanges.aggregateId).isEqualTo(orderId);
        assertThat(uncommittedChanges.eventOrderOfLastRehydratedEvent).isEqualTo(EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED);
        assertThat(uncommittedChanges.events.size()).isEqualTo(1);
        assertThat(uncommittedChanges.events.get(0)).isInstanceOf(OrderEvent.OrderAdded.class);
        assertThat((CharSequence) ((OrderEvent.OrderAdded) uncommittedChanges.events.get(0)).orderId).isEqualTo(orderId);
        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(0));

        // When
        aggregate.markChangesAsCommitted();

        // Then
        assertThat(aggregate.getUncommittedChanges().isEmpty()).isTrue();
    }

    @Test
    void test_rehydrating_aggregate() {
        // given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;
        var productId          = ProductId.random();

        var aggregate = new Order(orderId, orderingCustomerId, orderNumber);
        assertThat(aggregate.state().productAndQuantity.get(productId)).isNull();

        // And
        aggregate.addProduct(productId, 10);

        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.state().productAndQuantity.get(productId)).isEqualTo(10);
        var uncommittedChanges = aggregate.getUncommittedChanges();
        assertThat(uncommittedChanges.events.size()).isEqualTo(2);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(1));

        assertThat(uncommittedChanges.events.get(1)).isInstanceOf(OrderEvent.ProductAddedToOrder.class);
        var productAddedEvent = (OrderEvent.ProductAddedToOrder) uncommittedChanges.events.get(1);
        assertThat((CharSequence) productAddedEvent.orderId).isEqualTo(orderId);
        assertThat((CharSequence) productAddedEvent.productId).isEqualTo(productId);
        assertThat(productAddedEvent.quantity).isEqualTo(10);

        // when
        var rehydratedAggregate = new Order(orderId).rehydrate(uncommittedChanges.stream());

        // then
        assertThat((CharSequence) rehydratedAggregate.aggregateId()).isEqualTo(orderId);
        assertThat(rehydratedAggregate.state().productAndQuantity.get(productId)).isEqualTo(10);
        uncommittedChanges = rehydratedAggregate.getUncommittedChanges();
        assertThat(uncommittedChanges.isEmpty()).isTrue();
        assertThat(uncommittedChanges.eventOrderOfLastRehydratedEvent).isEqualTo(EventOrder.of(1));
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
        assertThat(aggregate.state()
                           .productAndQuantity
                           .get(productId))
                .isNull();

        // And
        aggregate.addProduct(productId, 10);

        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.state().productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(aggregate.getUncommittedChanges().events.size()).isEqualTo(2);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(1));

        // when
        var rehydratedAggregate = new Order(orderId).rehydrate(aggregate.getUncommittedChanges().stream());
        assertThat(rehydratedAggregate.state().accepted).isFalse();
        assertThat(aggregate.state().productAndQuantity.get(productId)).isEqualTo(10);
        rehydratedAggregate.accept();

        // then
        var uncommittedChanges = rehydratedAggregate.getUncommittedChanges();
        assertThat(uncommittedChanges.events.size()).isEqualTo(1);
        assertThat(rehydratedAggregate.eventOrderOfLastAppliedEvent()).isEqualTo(EventOrder.of(2));
        assertThat(uncommittedChanges.events.get(0)).isInstanceOf(OrderEvent.OrderAccepted.class);

        var orderAccepted = (OrderEvent.OrderAccepted) uncommittedChanges.events.get(0);
        assertThat((CharSequence) orderAccepted.orderId).isEqualTo(orderId);
        assertThat(orderAccepted.eventOrder).isEqualTo(EventOrder.of(2));

        assertThat((CharSequence) rehydratedAggregate.aggregateId()).isEqualTo(orderId);
        assertThat(rehydratedAggregate.state().accepted).isTrue();
    }
}
