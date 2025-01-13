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
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.Event;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateEventStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.reflection.invocation.*;

import java.util.List;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Simple, easy and opinionated aggregate that allows you to apply events that don't have any requirements
 * with regards to inheritance (i.e. you can use Records) in combination with an {@link FlexAggregateRepository}<br/>
 * <strong>Events</strong><br/>
 * <pre>{@code
 *     public record OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
 *     }
 *
 *     public static class OrderAccepted {
 *         public final OrderId orderId;
 *
 *         public OrderAccepted(OrderId orderId) {
 *             this.orderId = orderId;
 *         }
 *     }
 * }</pre>
 * <strong>New Aggregate instance</strong><br/>
 * The method that creates a new aggregate instance may be a static method that returns a {@link EventsToPersist}
 * instance:
 * <pre>{@code
 * unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
 *      var eventsToPersist = Order.createNewOrder(orderId, CustomerId.random(), 123);
 *      repository.persist(eventsToPersist);
 *  });
 * }</pre>
 * Where the createNewOrder static methods looks like:
 * <pre>{@code
 *     public static EventsToPersist<OrderId> createNewOrder(OrderId orderId,
 *                                                           CustomerId orderingCustomerId,
 *                                                           int orderNumber) {
 *         FailFast.requireNonNull(orderId, "You must provide an orderId");
 *         FailFast.requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");
 *         return initialAggregateEvents(orderId, new OrderAdded(orderId, orderingCustomerId, orderNumber));
 *     }
 * }
 * </pre>
 * <strong>Aggregate command methods</strong><br/>
 * Each individual <b>command</b> method MUST also return a {@link EventsToPersist} instance
 * that can will usually be use in in a call to {@link FlexAggregateRepository#persist(EventsToPersist)}
 * <pre>{@code
 * FlexAggregateRepository<OrderId, Order> repository =
 *                   FlexAggregateRepository.from(
 *                        eventStores,
 *                        standardSingleTenantConfiguration(
 *                             AggregateType.of("Orders"),
 *                             new JacksonJSONEventSerializer(createObjectMapper()),
 *                             AggregateIdSerializer.serializerFor(OrderId.class),
 *                             IdentifierColumnType.UUID,
 *                             JSONColumnType.JSONB),
 *                         unitOfWorkFactory,
 *                         OrderId.class,
 *                         Order.class
 *                        );
 *
 * unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
 *      var order = repository.load(orderId);
 *      var eventsToPersist = order.accept();
 *      repository.persist(eventsToPersist);
 *  });
 * }
 * </pre>
 * And where the accept() command method looks like this:
 * <pre>{@code
 *     public EventsToPersist<OrderId> accept() {
 *         if (accepted) {
 *             return noEvents();
 *         }
 *         return events(new OrderAccepted(aggregateId()));
 *     }
 * }</pre>
 * <br/>
 * <strong>Command method chaining</strong><br/>
 * Command methods can be <b>chained</b> like this:
 * <pre>{@code
 *         unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
 *             var eventsToPersist = Order.createNewOrder(orderId, CustomerId.random(), 123);
 *             eventsToPersist = eventsToPersist.append(new Order().rehydrate(eventsToPersist).addProduct(productId, 10));
 *             repository.persist(eventsToPersist);
 *         });
 * }</pre>
 * <strong>Aggregate event handling methods</strong>
 * <p>
 * When an aggregate is loaded from the {@link EventStore} using the {@link FlexAggregateRepository},
 * e.g. using {@link FlexAggregateRepository#load(Object)} or {@link FlexAggregateRepository#tryLoad(Object)},
 * the repository will return an {@link FlexAggregate} instance that has had all events returned from the {@link EventStore} <b>applied</b> to it.
 * <br/>
 * What happens internally is that the {@link FlexAggregateRepository#load(Object)} method will call {@link #rehydrate(AggregateEventStream)},
 * which in-order will call {@link FlexAggregate#applyRehydratedEventToTheAggregate(Object)} for each
 * event returned from the EventStore.
 * <br/>
 * You can either choose to implement the event matching using instanceof:
 * <pre>{@code
 *     @Override
 *     protected void applyHistoricEventToTheAggregate(Object event) {
 *         if (event instanceof OrderAdded e) {
 *             // You don't need to store all properties from an Event inside the Aggregate.
 *             // Only do this IF it actually is needed for business logic and in this cases none of them are needed
 *             // for further command processing
 *
 *             // To support instantiation using e.g. Objenesis we initialize productAndQuantity here
 *             productAndQuantity = HashMap.empty();
 *         } else if (event instanceof ProductAddedToOrder e) {
 *             Option<Integer> existingQuantity = productAndQuantity.get(e.productId);
 *             productAndQuantity = productAndQuantity.put(e.productId, e.quantity + existingQuantity.getOrElse(0));
 *         } else if (event instanceof ProductOrderQuantityAdjusted e) {
 *             productAndQuantity = productAndQuantity.put(e.productId, e.newQuantity);
 *         } else if (event instanceof ProductRemovedFromOrder e) {
 *             productAndQuantity = productAndQuantity.remove(e.productId);
 *         } else if (event instanceof OrderAccepted) {
 *             accepted = true;
 *         }
 *     }
 * }
 * </pre>
 * or use {@link EventHandler} annotated (private) methods:
 * <pre>{@code
 *     @EventHandler
 *     private void on(OrderAdded e) {
 *         productAndQuantity = HashMap.empty();
 *     }
 *
 *     @EventHandler
 *     private void on(ProductAddedToOrder e) {
 *         Option<Integer> existingQuantity = productAndQuantity.get(e.productId);
 *         productAndQuantity = productAndQuantity.put(e.productId, e.quantity + existingQuantity.getOrElse(0));
 *     }
 *
 *     @EventHandler
 *     private void on(ProductOrderQuantityAdjusted e) {
 *         productAndQuantity = productAndQuantity.put(e.productId, e.newQuantity);
 *     }
 *
 *     @EventHandler
 *     private void on(ProductRemovedFromOrder e) {
 *         productAndQuantity = productAndQuantity.remove(e.productId);
 *     }
 *
 *     @EventHandler
 *     private void on(OrderAccepted e) {
 *         accepted = true;
 *     }
 * }</pre>
 *
 * @param <ID>             The id type for the aggregate id
 * @param <AGGREGATE_TYPE> the type of the aggregate
 * @see FlexAggregateRepository
 */
@SuppressWarnings("unchecked")
public abstract class FlexAggregate<ID, AGGREGATE_TYPE extends FlexAggregate<ID, AGGREGATE_TYPE>> implements Aggregate<ID, AGGREGATE_TYPE> {
    private transient PatternMatchingMethodInvoker<Object> invoker;
    private           ID                                   aggregateId;

    /**
     * Zero based event order
     */
    private EventOrder eventOrderOfLastRehydratedEvent;
    private boolean    hasBeenRehydrated;

    public FlexAggregate() {
        initialize();
    }

    protected void initialize() {
        invoker = new PatternMatchingMethodInvoker<>(this,
                                                     new SingleArgumentAnnotatedMethodPatternMatcher<>(EventHandler.class,
                                                                                                       Object.class),
                                                     InvocationStrategy.InvokeMostSpecificTypeMatched);
    }

    /**
     * Initialize an aggregate instance from historic events.<br>
     * Effectively performs a leftFold over all the previous persisted events related to this aggregate instance
     *
     * @param persistedEvents the previous recorded/persisted events related to this aggregate instance, aka. the aggregates history
     * @return the aggregate instance
     */
    @SuppressWarnings("unchecked")
    public AGGREGATE_TYPE rehydrate(AggregateEventStream<ID> persistedEvents) {
        requireNonNull(persistedEvents, "You must provide an EventStream");
        var firstEvent = persistedEvents.eventList().get(0);
        return rehydrate((ID) firstEvent.aggregateId(),
                         persistedEvents.map(persistedEvent -> persistedEvent.event().deserialize()));
    }

    /**
     * Initialize an aggregate instance from {@link EventsToPersist}<br>
     * Effectively performs a leftFold over all the previous events related to this aggregate instance
     *
     * @param eventsToPersist
     * @return the aggregate instance
     */
    public AGGREGATE_TYPE rehydrate(EventsToPersist<ID, Object> eventsToPersist) {
        requireNonNull(eventsToPersist, "You must supply an EventsToPersist instance");
        return rehydrate(eventsToPersist.aggregateId,
                         eventsToPersist.events);
    }

    public AGGREGATE_TYPE rehydrate(ID aggregateId, List<Object> historicEvents) {
        requireNonNull(historicEvents, "You must supply historicEvents");
        return rehydrate(aggregateId, historicEvents.stream());
    }

    /**
     * Effectively performs a leftFold over all the previous events related to this aggregate instance
     *
     * @param persistedEvents the previous recorded/persisted events related to this aggregate instance, aka. the aggregates history
     * @return the aggregate instance
     */
    public AGGREGATE_TYPE rehydrate(ID aggregateId, Stream<Object> persistedEvents) {
        this.aggregateId = requireNonNull(aggregateId, "You must provide an aggregateId when initializing a FlexAggregate from a list of previous events");
        requireNonNull(persistedEvents, "You must provide a list of persisted events");

        // Must be initialized here to ensure any eventOrderOfLastAppliedHistoricEvent++ will set the correct event order
        eventOrderOfLastRehydratedEvent = EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED;
        persistedEvents.forEach(this::rehydrateEvent);

        return (AGGREGATE_TYPE) this;
    }

    /**
     * Wrap the events that should be persisted for this new aggregate
     *
     * @param aggregateId     the aggregate id this relates to
     * @param eventsToPersist the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     *                        May be empty if the command method invocation didn't result in any events (e.g. due to idempotency checks)
     * @param <ID>            the aggregate id type
     */
    public static <ID> EventsToPersist<ID, Object> newAggregateEvents(ID aggregateId,
                                                                      Object... eventsToPersist) {
        return EventsToPersist.initialAggregateEvents(aggregateId,
                                                      eventsToPersist);
    }

    /**
     * Wrap the events that should be persisted as a side effect of a command method
     *
     * @param eventsToPersist the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     *                        May be empty if the command method invocation didn't result in any events (e.g. due to idempotency checks)
     * @see EventsToPersist#events(FlexAggregate, Object...)
     */
    protected EventsToPersist<ID, Object> events(Object... eventsToPersist) {
        return EventsToPersist.events(this, eventsToPersist);
    }

    /**
     * Creates an empty {@link EventsToPersist} which is handy for a commanded method didn't have a side-effect (e.g. due to idempotent handling)
     *
     * @see EventsToPersist#noEvents(FlexAggregate)
     */
    protected EventsToPersist<ID, Object> noEvents() {
        return EventsToPersist.noEvents(this);
    }

    private void rehydrateEvent(Object event) {
        requireNonNull(event, "You must supply an event");
        applyRehydratedEventToTheAggregate(event);
        hasBeenRehydrated = true;
        eventOrderOfLastRehydratedEvent = eventOrderOfLastRehydratedEvent.increment();
    }

    /**
     * Apply the previously recorded/persisted event (aka historic event)
     * to the aggregate instance to reflect the event as a state change to the aggregate<br/>
     * The default implementation will automatically call any (private) methods annotated with
     * {@link EventHandler}
     *
     * @param event the event to apply to the aggregate
     */
    protected void applyRehydratedEventToTheAggregate(Object event) {
        if (invoker == null) {
            // Instance was created by Objenesis
            initialize();
        }
        invoker.invoke(event, unmatchedEvent -> {
            // Ignore unmatched events as Aggregates don't necessarily need handle every event
        });
    }

    @Override
    public ID aggregateId() {
        requireNonNull(aggregateId, "The aggregate id has not been set on " +
                "the AggregateRoot and not supplied using one of the Event applied to it. " +
                "At least the first event MUST supply it");
        return aggregateId;
    }

    /**
     * Has the aggregate been initialized using previously recorded/persisted events?
     *
     * @see #rehydrate(AggregateEventStream)
     * @see #rehydrate(EventsToPersist)
     * @see #rehydrate(Object, List)
     * @see #rehydrate(Object, Stream)
     */
    public boolean hasBeenRehydrated() {
        return hasBeenRehydrated;
    }

    /**
     * Get the eventOrder
     * of the last event that was applied to the {@link FlexAggregate}
     * Using:
     * <ul>
     * <li>{@link #rehydrate(AggregateEventStream)}</li>
     * <li>{@link #rehydrate(EventsToPersist)}</li>
     * <li>{@link #rehydrate(Object, Stream)}</li>
     * <li>{@link #rehydrate(Object, List)}</li>
     * </ul>
     *
     * @return the event order of the last applied {@link Event} or {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED} in case no
     * events has ever been applied to the aggregate
     */
    public final EventOrder eventOrderOfLastRehydratedEvent() {
        if (eventOrderOfLastRehydratedEvent == null) {
            // This constructor is needed to work well with Objenesis where constructors and fields are not called when an object instance is created
            eventOrderOfLastRehydratedEvent = EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED;
        }
        return eventOrderOfLastRehydratedEvent;
    }

}
