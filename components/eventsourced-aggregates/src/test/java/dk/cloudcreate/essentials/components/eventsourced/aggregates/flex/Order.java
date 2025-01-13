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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.flex;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class Order extends FlexAggregate<OrderId, Order> {
    public Map<ProductId, Integer> productAndQuantity;
    public boolean                 accepted;

    // ---------------- Business methods ------------------

    public static EventsToPersist<OrderId, Object> createNewOrder(OrderId orderId,
                                                          CustomerId orderingCustomerId,
                                                          int orderNumber) {
        // Normally you will ensure that orderId is never NULL, but to perform certain tests we need to option to allow this to be null
        //FailFast.requireNonNull(orderId, "You must provide an orderId");
        requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");
        return newAggregateEvents(orderId, new OrderAdded(orderId, orderingCustomerId, orderNumber));
    }

    public EventsToPersist<OrderId, Object> addProduct(ProductId productId, int quantity) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        return events(new ProductAddedToOrder(aggregateId(), productId, quantity));
    }

    public EventsToPersist<OrderId, Object> adjustProductQuantity(ProductId productId, int newQuantity) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (productAndQuantity.containsKey(productId)) {
            return events(new ProductOrderQuantityAdjusted(aggregateId(), productId, newQuantity));
        }
        return noEvents();
    }

    public EventsToPersist<OrderId, Object> removeProduct(ProductId productId) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (productAndQuantity.containsKey(productId)) {
            return events(new ProductRemovedFromOrder(aggregateId(), productId));
        }
        return noEvents();
    }

    public EventsToPersist<OrderId, Object> accept() {
        if (accepted) {
            return noEvents();
        }
        return events(new OrderAccepted(aggregateId()));
    }

    // ---------------  Apply previous events to restore aggregate state from history ---------------
//    Java 17 example
//    @Override
//    protected void applyHistoricEventToTheAggregate(Object event) {
//        if (event instanceof OrderAdded e) {
//            // You don't need to store all properties from an Event inside the Aggregate.
//            // Only do this IF it actually is needed for business logic and in this cases none of them are needed
//            // for further command processing
//
//            // To support instantiation using e.g. Objenesis we initialize productAndQuantity here
//            productAndQuantity = new HashMap<>();
//        } else if (event instanceof ProductAddedToOrder e) {
//            var existingQuantity = productAndQuantity.get(e.productId);
//            productAndQuantity.put(e.productId, e.quantity + existingQuantity);
//        } else if (event instanceof ProductOrderQuantityAdjusted e) {
//            productAndQuantity.put(e.productId, e.newQuantity);
//        } else if (event instanceof ProductRemovedFromOrder e) {
//            productAndQuantity.remove(e.productId);
//        } else if (event instanceof OrderAccepted) {
//            accepted = true;
//        }
//    }

    @EventHandler
    private void on(OrderAdded e) {
        productAndQuantity = new HashMap<>();
    }

    @EventHandler
    private void on(ProductAddedToOrder e) {
        var existingQuantity = productAndQuantity.get(e.productId);
        productAndQuantity.put(e.productId, e.quantity + (existingQuantity != null ? existingQuantity : 0));
    }

    @EventHandler
    private void on(ProductOrderQuantityAdjusted e) {
        productAndQuantity.put(e.productId, e.newQuantity);
    }

    @EventHandler
    private void on(ProductRemovedFromOrder e) {
        productAndQuantity.remove(e.productId);
    }

    @EventHandler
    private void on(OrderAccepted e) {
        accepted = true;
    }

    public boolean isAccepted() {
        return accepted;
    }

    // ---------------------------------------------------------------------------------
    // Events (could e.g. be Java 17 record based)
    // ---------------------------------------------------------------------------------
    public static class OrderAdded {
        public final OrderId    orderId;
        public final CustomerId orderingCustomerId;
        public final long       orderNumber;

        public OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
            this.orderId = orderId;
            this.orderingCustomerId = orderingCustomerId;
            this.orderNumber = orderNumber;
        }
    }

    public static class OrderAccepted {
        public final OrderId orderId;

        public OrderAccepted(OrderId orderId) {
            this.orderId = orderId;
        }
    }

    public static class ProductAddedToOrder {
        public final OrderId   orderId;
        public final ProductId productId;
        public final int       quantity;

        public ProductAddedToOrder(OrderId orderId, ProductId productId, int quantity) {
            this.orderId = orderId;
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    public static class ProductOrderQuantityAdjusted {
        public final OrderId   orderId;
        public final ProductId productId;
        public final int       newQuantity;

        public ProductOrderQuantityAdjusted(OrderId orderId, ProductId productId, int newQuantity) {
            this.orderId = orderId;
            this.productId = productId;
            this.newQuantity = newQuantity;
        }
    }

    public static class ProductRemovedFromOrder {
        public final OrderId   orderId;
        public final ProductId productId;

        public ProductRemovedFromOrder(OrderId orderId, ProductId productId) {
            this.orderId = orderId;
            this.productId = productId;
        }
    }

}
