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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.Event;

public final class NoDefaultConstructorOrderEvents {
    // ------------------------------------------------------------------ Events -------------------------------------------------------------------------------
    // Note: These Events assume the ObjectMapper is configured with Objenesis Jackson instantiation (EssentialsImmutableJacksonModule from immutable-jackson)
    //       as well as the EssentialTypesJacksonModule from types-jackson)
    // ------------------------------------------------------------------- Events -------------------------------------------------------------------------------
    public static class OrderAdded extends Event<OrderId> {
        public final CustomerId orderingCustomerId;
        public final long       orderNumber;

        public OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
            // MUST be set manually for the FIRST/INITIAL - after this the AggregateRoot ensures
            // that the aggregateId will be set on the other events automatically
            aggregateId(orderId);
            this.orderingCustomerId = orderingCustomerId;
            this.orderNumber = orderNumber;
        }
    }

    public static class ProductAddedToOrder extends Event<OrderId> {
        public final ProductId productId;
        public final int       quantity;

        public ProductAddedToOrder(ProductId productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    public static class ProductOrderQuantityAdjusted extends Event<OrderId> {
        public final ProductId productId;
        public final int       newQuantity;

        public ProductOrderQuantityAdjusted(ProductId productId, int newQuantity) {
            this.productId = productId;
            this.newQuantity = newQuantity;
        }
    }

    public static class ProductRemovedFromOrder extends Event<OrderId> {
        public final ProductId productId;

        public ProductRemovedFromOrder(ProductId productId) {
            this.productId = productId;
        }
    }

    public static class OrderAccepted extends Event<OrderId> {
    }
}
