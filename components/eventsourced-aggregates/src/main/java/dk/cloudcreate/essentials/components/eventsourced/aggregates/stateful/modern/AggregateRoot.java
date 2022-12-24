/*
 * Copyright 2021-2022 the original author or authors.
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

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.Event;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateEventStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.reflection.Reflector;
import dk.cloudcreate.essentials.shared.reflection.invocation.*;
import dk.cloudcreate.essentials.shared.types.GenericType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * A modern opinionated interpretation of the classic {@link dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.AggregateRoot} design, where the {@link Event}'s are mutable.<br>
 * The modern interpretation doesn't specify any requirement on the design of the Events, they can be Java 17+ <b>records</b> or simple POJO's.<br>
 * <br>
 * <blockquote>
 * <strong>Note: The {@link AggregateRoot} works best in combination with the {@link StatefulAggregateRepository} that's configured to use the {@link StatefulAggregateInstanceFactory#reflectionBasedAggregateRootFactory()},
 * because the {@link AggregateRoot} needs to be provided its aggregated id through the {@link AggregateRoot#AggregateRoot(Object)} constructor!</strong><br>
 * </blockquote>
 * <br>
 * The modern {@link AggregateRoot} supports keeping the state projection and {@link EventHandler} annotated methods within the {@link AggregateRoot} instance or within an {@link AggregateState} instance.<br>
 * If you wish to keep the state projection and {@link EventHandler} annotated methods within an {@link AggregateState} instance, then you only need to implement the {@link WithState} interface:
 * <pre>{@code
 * public class Order extends AggregateRoot<OrderId, OrderEvent, Order> implements WithState<OrderId, OrderEvent, Order, OrderState> {
 * public Order(OrderId orderId,
 *                  CustomerId orderingCustomerId,
 *                  int orderNumber) {
 *         super(orderId);
 *         requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");
 *
 *         apply(new OrderEvent.OrderAdded(orderId,
 *                                         orderingCustomerId,
 *                                         orderNumber));
 *     }
 *
 *     public void addProduct(ProductId productId, int quantity) {
 *         requireNonNull(productId, "You must provide a productId");
 *         if (state(OrderState.class).accepted) {
 *             throw new IllegalStateException("Order is already accepted");
 *         }
 *         apply(new OrderEvent.ProductAddedToOrder(aggregateId(),
 *                                                  productId,
 *                                                  quantity));
 *     }
 *
 *     public void accept() {
 *         if (state(OrderState.class).accepted) {
 *             return;
 *         }
 *         apply(eventOrder -> new OrderEvent.OrderAccepted(aggregateId(),
 *                                                          eventOrder));
 *     }
 * }
 * }</pre>
 * And state class:
 * <pre>{@code
 * public class OrderState extends AggregateState<OrderId, OrderEvent, Order> {
 *     Map<ProductId, Integer> productAndQuantity;
 *     boolean                 accepted;
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
 * @param <AGGREGATE_TYPE> the aggregate type
 */
public abstract class AggregateRoot<ID, EVENT_TYPE, AGGREGATE_TYPE extends AggregateRoot<ID, EVENT_TYPE, AGGREGATE_TYPE>> implements StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_TYPE> {
    private PatternMatchingMethodInvoker<Object>           invoker;
    private ID                                             aggregateId;
    private EventOrder                                     eventOrderOfLastAppliedEvent = EventOrder.NO_EVENTS_PERSISTED;
    private List<EVENT_TYPE>                               uncommittedEvents;
    private EventOrder                                     eventOrderOfLastRehydratedEvent;
    private boolean                                        hasBeenRehydrated;
    private boolean                                        isRehydrating;
    private AggregateState<ID, EVENT_TYPE, AGGREGATE_TYPE> state;

    /**
     * Used for rehydration
     * @param aggregateId  the id of the aggregate being initialized during the rehydration flow
     */
    public AggregateRoot(ID aggregateId) {
        this.aggregateId = aggregateId;
        initialize();
    }

    @Override
    public ID aggregateId() {
        return aggregateId;
    }

    @Override
    public EventsToPersist<ID, EVENT_TYPE> getUncommittedChanges() {
        return new EventsToPersist<>(aggregateId,
                                     eventOrderOfLastRehydratedEvent,
                                     uncommittedEvents);
    }

    /**
     * Resets the {@link #getUncommittedChanges()} - effectively marking them as having been persisted
     * and committed to the underlying {@link EventStore}
     */
    @Override
    public void markChangesAsCommitted() {
        uncommittedEvents = new ArrayList<>();
    }

    @Override
    public boolean hasBeenRehydrated() {
        return hasBeenRehydrated;
    }

    /**
     * Variant of the {@link #state()} where the concrete State type is specified in the argument<br>
     * If the aggregate implements {@link WithState} then you can call this method to access the state object
     *
     * @param <STATE> the type of state object
     * @return the state object
     * @throws AggregateException if the aggregate doesn't implement {@link WithState}
     */
    @SuppressWarnings("unchecked")
    protected <STATE extends AggregateState<ID, EVENT_TYPE, AGGREGATE_TYPE>> STATE state(Class<STATE> stateClass) {
        return state();
    }

    /**
     * If the aggregate implements {@link WithState} then you can call this method to access the state object<br>
     * This method requires that the return type information is available at compile time:
     * <pre>OrderState state = state();</pre>
     * If this isn't the case, then you can use {@link #state(Class)}:
     * <pre>{@code
     *     public void accept() {
     *         if (state(OrderState.class).accepted) {
     *             return;
     *         }
     *         apply(eventOrder -> new OrderEvent.OrderAccepted(aggregateId(),
     *                                                          eventOrder));
     *     }}</pre>
     * <br>
     * Alternatively you can override the {@link #state()} within your concrete Aggregate class and use Java's feature of overriding methods
     * being allowed to define a covariant return type:
     * <pre>{@code
     * public class Order extends AggregateRoot<OrderId, OrderEvent, Order> implements WithState<OrderId, OrderEvent, Order, OrderState> {
     *     protected OrderState state() {
     *         return super.state();
     *     }
     * }
     * }</pre>
     *
     * @param <STATE> the type of state object
     * @return the state object
     * @throws AggregateException if the aggregate doesn't implement {@link WithState}
     */
    @SuppressWarnings("unchecked")
    protected <STATE extends AggregateState<ID, EVENT_TYPE, AGGREGATE_TYPE>> STATE state() {
        if (!WithState.class.isAssignableFrom(this.getClass())) {
            throw new AggregateException(msg("Aggregate {} doesn't implement {}",
                                             this.getClass().getName(),
                                             WithState.class.getSimpleName()));
        }
        if (invoker == null) {
            initialize();
        }
        return (STATE) state;
    }

    /**
     * Initialize the aggregate, e.g. setting up state objects, {@link PatternMatchingMethodInvoker}, etc.
     */
    @SuppressWarnings("unchecked")
    protected void initialize() {
        uncommittedEvents = new ArrayList<>();
        if (WithState.class.isAssignableFrom(this.getClass())) {
            // Create a state object unless we aren't working on snapshot version of the Aggregate in which case the state object will have a value
            if (state == null) {
                // With state object -  so EventHandler annotated methods are placed on the aggregate-state class
                Class<?> aggregateStateType = resolveStateImplementationClass();
                state = Reflector.reflectOn(aggregateStateType).newInstance();
            }
            state.setAggregate((AGGREGATE_TYPE) this);
            invoker = new PatternMatchingMethodInvoker<>(state,
                                                         new SingleArgumentAnnotatedMethodPatternMatcher<>(EventHandler.class,
                                                                                                           new GenericType<>() {
                                                                                                           }),
                                                         InvocationStrategy.InvokeMostSpecificTypeMatched);
        } else {
            // Without separate state object - so EventHandler annotated methods are placed on the aggregate class
            invoker = new PatternMatchingMethodInvoker<>(this,
                                                         new SingleArgumentAnnotatedMethodPatternMatcher<>(EventHandler.class,
                                                                                                           new GenericType<>() {
                                                                                                           }),
                                                         InvocationStrategy.InvokeMostSpecificTypeMatched);
        }
    }

    /**
     * Override this method to provide a non reflection based look up of the Type Argument
     * provided to the {@link AggregateRoot} class
     *
     * @return the {@link AggregateState} implementation to use for the {@link #state} instance
     */
    protected Class<?> resolveStateImplementationClass() {
        var aggregateStateType = GenericType.resolveGenericTypeForInterface(this.getClass(),
                                                                            WithState.class,
                                                                            3);
        return aggregateStateType;
    }

    /**
     * Effectively performs a leftFold over all the previously persisted events related to this aggregate instance
     *
     * @param persistedEvents the previous persisted events related to this aggregate instance, aka. the aggregates history
     * @return the same aggregate instance (self)
     */
    @SuppressWarnings("unchecked")
    public AGGREGATE_TYPE rehydrate(AggregateEventStream<ID> persistedEvents) {
        requireNonNull(persistedEvents, "You must provide a persistedEvents stream");
        return rehydrate(persistedEvents.map(persistedEvent -> persistedEvent.event().deserialize()));
    }

    @Override
    public EventOrder eventOrderOfLastRehydratedEvent() {
        return eventOrderOfLastRehydratedEvent;
    }

    /**
     * Effectively performs a leftFold over all the previous events related to this aggregate instance
     *
     * @param persistedEvents the previous events related to this aggregate instance, aka. the aggregates history
     * @return the same aggregate instance (self)
     */
    @SuppressWarnings("unchecked")
    public AGGREGATE_TYPE rehydrate(Stream<EVENT_TYPE> persistedEvents) {
        requireNonNull(aggregateId, "You must provide an aggregateId with the aggregate constructor");
        requireNonNull(persistedEvents, "You must provide a persistedEvents stream");
        isRehydrating = true;
        persistedEvents.forEach(event -> {
            applyEventToTheAggregateState(event);
            eventOrderOfLastAppliedEvent = eventOrderOfLastAppliedEvent().increment();
        });
        eventOrderOfLastRehydratedEvent = eventOrderOfLastAppliedEvent;
        isRehydrating = false;
        hasBeenRehydrated = true;
        return (AGGREGATE_TYPE) this;
    }

    /**
     * Apply a new non persisted/uncommitted Event to this aggregate instance. If you don't need to store the
     * {@link EventOrder} for the event being applied, then you can just call the {@link #apply(Object)} function instead.
     *
     * @param eventSupplier function that as argument/input receives the {@link EventOrder} for the next event to be applied (in case you want to
     *                      store the {@link EventOrder} with the event being produced by the <code>eventSupplier</code><br>
     *                      The result of the event supplier will be forwarded to the {@link #apply(Object)} method.
     */
    protected void apply(Function<EventOrder, EVENT_TYPE> eventSupplier) {
        requireNonNull(eventSupplier, "No eventSupplier provided");
        var event = eventSupplier.apply(eventOrderOfLastAppliedEvent().increment());
        requireNonNull(event, "No event was returned from the eventSupplier");
        apply(event);
    }

    public EventOrder eventOrderOfLastAppliedEvent() {
        if (eventOrderOfLastAppliedEvent == null) {
            // Since the aggregate instance MAY have been created using Objenesis (which doesn't
            // initialize fields nor calls a constructor) we have to be defensive and lazy way initialize
            // the eventOrderOfLastAppliedEvent
            eventOrderOfLastAppliedEvent = EventOrder.NO_EVENTS_PERSISTED;
        }
        return eventOrderOfLastAppliedEvent;
    }

    /**
     * Apply a new non persisted/uncommitted Event to this aggregate instance.<br>
     *
     * @param event the event to apply to the state of the aggregate
     */
    protected void apply(EVENT_TYPE event) {
        requireNonNull(event, "You must supply an event");
        applyEventToTheAggregateState(event);
        uncommittedEvents.add(event);
        eventOrderOfLastAppliedEvent = eventOrderOfLastAppliedEvent().increment();
    }

    /**
     * Apply the event to the aggregate instance to reflect the event as a state change to the aggregate<br/>
     * The default implementation will automatically call any (private) methods annotated with
     * {@link EventHandler}
     *
     * @param event the event to apply to the aggregate
     * @see #isRehydrating()
     */
    protected void applyEventToTheAggregateState(Object event) {
        if (invoker == null) {
            // Instance was created by Objenesis
            initialize();
        }
        invoker.invoke(event, unmatchedEvent -> {
            // Ignore unmatched events as Aggregates don't necessarily need handle every event
        });
    }

    /**
     * Is the event being supplied to {@link #applyEventToTheAggregateState(Object)} a previously persisted event (i.e. a historic event)
     */
    protected final boolean isRehydrating() {
        return isRehydrating;
    }

}
