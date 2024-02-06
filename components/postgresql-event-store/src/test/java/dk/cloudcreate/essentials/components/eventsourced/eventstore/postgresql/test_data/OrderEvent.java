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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.Revision;

@Revision(2)
public class OrderEvent {
    public final OrderId orderId;

    public OrderEvent(OrderId orderId) {
        this.orderId = orderId;
    }

    @Revision(3)
    public static class OrderAdded extends OrderEvent {
        public final CustomerId orderingCustomerId;
        public final long       orderNumber;

        public OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
            super(orderId);
            this.orderingCustomerId = orderingCustomerId;
            this.orderNumber = orderNumber;
        }
    }

    public static class ProductAddedToOrder extends OrderEvent {
        public final ProductId productId;
        public final int       quantity;

        public ProductAddedToOrder(OrderId orderId, ProductId productId, int quantity) {
            super(orderId);
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    public static class ProductOrderQuantityAdjusted extends OrderEvent {
        public final ProductId productId;
        public final int       newQuantity;

        public ProductOrderQuantityAdjusted(OrderId orderId, ProductId productId, int newQuantity) {
            super(orderId);
            this.productId = productId;
            this.newQuantity = newQuantity;
        }
    }

    public static class ProductRemovedFromOrder extends OrderEvent {
        public final ProductId productId;

        public ProductRemovedFromOrder(OrderId orderId, ProductId productId) {
            super(orderId);
            this.productId = productId;
        }
    }

    public static class OrderAccepted extends OrderEvent {
        public OrderAccepted(OrderId orderId) {
            super(orderId);
        }
    }
}
