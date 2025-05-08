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

package dk.trustworks.essentials.components.eventsourced.aggregates.modern;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import dk.trustworks.essentials.components.eventsourced.aggregates.*;
import dk.trustworks.essentials.components.eventsourced.aggregates.stateful.modern.AggregateRoot;

import java.util.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

public class Order extends AggregateRoot<OrderId, OrderEvent, Order> {
    // NOTE: Fields are public for framework tests performed - this isn't a pattern to replicate in a business application

    // Annotated with @JsonDeserialize to support AggregateSnapshot JSON serialization
    @JsonDeserialize(keyUsing = ProductIdKeyDeserializer.class)
    public Map<ProductId, Integer> productAndQuantity;
    public boolean                 accepted;

    protected Order() {
    }

    /**
     * Used for rehydration
     */
    public Order(OrderId orderId) {
        super(orderId);
    }

    public Order(OrderId orderId,
                 CustomerId orderingCustomerId,
                 int orderNumber) {
        this(orderId);
        // Normally you will ensure that orderId is never NULL, but to perform certain tests we need to option to allow this to be null
        requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");

        apply(new OrderEvent.OrderAdded(orderId,
                                        orderingCustomerId,
                                        orderNumber));
    }

    public void addProduct(ProductId productId, int quantity) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        apply(new OrderEvent.ProductAddedToOrder(aggregateId(),
                                                 productId,
                                                 quantity));
    }

    public void adjustProductQuantity(ProductId productId, int newQuantity) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (productAndQuantity.containsKey(productId)) {
            apply(new OrderEvent.ProductOrderQuantityAdjusted(aggregateId(),
                                                              productId,
                                                              newQuantity));
        }
    }

    public void removeProduct(ProductId productId) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (productAndQuantity.containsKey(productId)) {
            apply(new OrderEvent.ProductRemovedFromOrder(aggregateId(),
                                                         productId));
        }
    }

    public void accept() {
        if (accepted) {
            return;
        }
        apply(eventOrder -> new OrderEvent.OrderAccepted(aggregateId(),
                                                         eventOrder));
    }

    @EventHandler
    private void on(OrderEvent.OrderAdded e) {
        productAndQuantity = new HashMap<>();
    }

    @EventHandler
    private void on(OrderEvent.ProductAddedToOrder e) {
        var existingQuantity = productAndQuantity.get(e.productId);
        productAndQuantity.put(e.productId, e.quantity + (existingQuantity != null ? existingQuantity : 0));
    }

    @EventHandler
    private void on(OrderEvent.ProductOrderQuantityAdjusted e) {
        productAndQuantity.put(e.productId, e.newQuantity);
    }

    @EventHandler
    private void on(OrderEvent.ProductRemovedFromOrder e) {
        productAndQuantity.remove(e.productId);
    }

    @EventHandler
    private void on(OrderEvent.OrderAccepted e) {
        accepted = true;
    }
}
