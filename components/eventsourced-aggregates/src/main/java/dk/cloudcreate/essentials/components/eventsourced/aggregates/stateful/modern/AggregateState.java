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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.modern;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Aggregate state object associated with a given {@link AggregateRoot} instance (see {@link #getAggregate()})<br>
 * Example:
 * <pre>{@code
 * public class OrderState extends AggregateState<OrderId, OrderEvent, Order> {
 *     private Map<ProductId, Integer> productAndQuantity;
 *     private boolean                 accepted;
 *
 *     @EventHandler
 *     private void on(OrderEvent.OrderAdded e) {
 *         productAndQuantity = new HashMap<>();
 *     }
 *
 *     @EventHandler
 *     private void on(OrderEvent.ProductAddedToOrder e) {
 *         var existingQuantity = productAndQuantity.get(e.productId);
 *         productAndQuantity.put(e.productId, e.quantity + (existingQuantity != null ? existingQuantity : 0));
 *     }
 *
 *     @EventHandler
 *     private void on(OrderEvent.ProductOrderQuantityAdjusted e) {
 *         productAndQuantity.put(e.productId, e.newQuantity);
 *     }
 *
 *     @EventHandler
 *     private void on(OrderEvent.ProductRemovedFromOrder e) {
 *         productAndQuantity.remove(e.productId);
 *     }
 *
 *     @EventHandler
 *     private void on(OrderEvent.OrderAccepted e) {
 *         accepted = true;
 *     }
 * }
 * }</pre>
 *
 * @param <ID>             the type of id
 * @param <EVENT_TYPE>     the type of event
 * @param <AGGREGATE_TYPE> the aggregate root type
 */
public abstract class AggregateState<ID, EVENT_TYPE, AGGREGATE_TYPE extends AggregateRoot<ID, EVENT_TYPE, AGGREGATE_TYPE>> {
    @JsonIgnore
    private transient AGGREGATE_TYPE aggregate;

    /**
     * After the aggregate state is initialized (or rehydrated in case of loading from a snapshot) we
     * need to be able to set the transient aggregate instance this state object is associated with
     *
     * @param aggregate the aggregate instance this state object is associated with
     * @return this state object
     */
    AggregateState<ID, EVENT_TYPE, AGGREGATE_TYPE> setAggregate(AGGREGATE_TYPE aggregate) {
        this.aggregate = requireNonNull(aggregate, "No aggregate instance provided");
        return this;
    }

    /**
     * Get access to the aggregate instance this state object is associated with
     *
     * @return the aggregate instance this state object is associated with
     */
    protected final AGGREGATE_TYPE getAggregate() {
        return aggregate;
    }
}
