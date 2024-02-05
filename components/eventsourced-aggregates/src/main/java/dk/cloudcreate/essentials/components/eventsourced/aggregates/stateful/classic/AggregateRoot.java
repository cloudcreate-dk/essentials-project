/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.state.AggregateRootWithState;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateEventStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.reflection.invocation.*;
import dk.cloudcreate.essentials.shared.types.GenericType;

import java.util.*;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * A specialized and opinionated mutable {@link Aggregate} design<br>
 * This {@link AggregateRoot} is designed to work with Class based Event's that inherit from {@link Event}.<br>
 * This design is deliberate and will manage a lot of things for you as a developer at the cost of some flexibility.<p>
 * Specifically you only have to supply the Aggregate ID, through {@link Event#aggregateId()}, on the FIRST/initial {@link Event}
 * that's being applied to the {@link AggregateRoot} using the {@link #apply(Event)} method.<br>
 * Every consecutive {@link Event} applied will automatically have its {@link Event#aggregateId(Object)} method called IF it doesn't already have a value.<br>
 * <b>I.e. you can be lazy and skip setting the aggregate id on the Event if you don't want to.</b><br>
 * The {@link AggregateRoot} also automatically keeps track of the {@link Event#eventOrder()} value and will set it for you and ensure that it's consecutively growing.
 * <p>
 * Note: <strong>The {@link AggregateRoot} works best in combination with the {@link StatefulAggregateRepository}</strong>
 * <p>
 * You don't have to use the {@link AggregateRoot} IF it doesn't fit your purpose. It's always possible to build your own {@link Aggregate} concept.<br>
 *
 * @param <ID>             the aggregate id type
 * @param <EVENT_TYPE>     the type of event
 * @param <AGGREGATE_TYPE> the aggregate self type (i.e. your concrete aggregate type)
 * @see AggregateRootWithState
 */
public abstract class AggregateRoot<ID, EVENT_TYPE extends Event<ID>, AGGREGATE_TYPE extends AggregateRoot<ID, EVENT_TYPE, AGGREGATE_TYPE>> implements StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_TYPE> {
    private transient PatternMatchingMethodInvoker<Event<ID>> invoker;
    private           ID                                      aggregateId;
    private           List<EVENT_TYPE>                        uncommittedChanges;
    /**
     * Zero based event order
     */
    private           EventOrder                              eventOrderOfLastAppliedEvent = EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED;
    private           boolean                                 hasBeenRehydrated;
    private           boolean                                 isRehydrating;
    private           EventOrder                              eventOrderOfLastRehydratedEvent = EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED;

    public AggregateRoot() {
        initialize();
    }

    /**
     * Initialize the aggregate, e.g. setting up state objects, {@link PatternMatchingMethodInvoker}, etc.
     */
    protected void initialize() {
        invoker = new PatternMatchingMethodInvoker<>(this,
                                                     new SingleArgumentAnnotatedMethodPatternMatcher<>(EventHandler.class,
                                                                                                       new GenericType<>() {
                                                                                                       }),
                                                     InvocationStrategy.InvokeMostSpecificTypeMatched);
    }

    /**
     * Effectively performs a leftFold over all the previously persisted events related to this aggregate instance
     *
     * @param persistedEvents the previous persisted events related to this aggregate instance, aka. the aggregates history
     * @return the same aggregate instance (self)
     */
    @SuppressWarnings("unchecked")
    @Override
    public AGGREGATE_TYPE rehydrate(AggregateEventStream<ID> persistedEvents) {
        requireNonNull(persistedEvents, "You must provide a persistedEvents stream");
        return rehydrate(persistedEvents.map(persistedEvent -> persistedEvent.event().deserialize()));
    }

    /**
     * Effectively performs a leftFold over all the previous events related to this aggregate instance
     *
     * @param previousEvents the previous events related to this aggregate instance, aka. the aggregates history
     * @return the same aggregate instance (self)
     */
    @SuppressWarnings("unchecked")
    public AGGREGATE_TYPE rehydrate(Stream<EVENT_TYPE> previousEvents) {
        requireNonNull(previousEvents, "You must provide a previousEvents stream");
        isRehydrating = true;
        previousEvents.forEach(event -> {
            if (aggregateId == null) {
                // The aggregate doesn't know its aggregate id, hence the FIRST historic event being applied MUST know it
                aggregateId = event.aggregateId();
                requireNonNull(aggregateId, msg("The first previous/historic Event '{}' applied to Aggregate '{}' didn't contain an aggregateId",
                                                event.getClass().getName(),
                                                this.getClass().getName()));
            }
            applyEventToTheAggregateState(event);
            eventOrderOfLastAppliedEvent = event.eventOrder();
        });
        eventOrderOfLastRehydratedEvent = eventOrderOfLastAppliedEvent;
        isRehydrating = false;
        hasBeenRehydrated = true;
        return (AGGREGATE_TYPE) this;
    }

    @Override
    public EventOrder eventOrderOfLastRehydratedEvent() {
        return eventOrderOfLastRehydratedEvent;
    }

    /**
     * Apply a new non persisted/uncommitted Event to this aggregate instance.<br>
     * If it is the very FIRST {@link Event} that is being applied then {@link Event#aggregateId()} MUST return the ID of the aggregate the event relates to<br>
     * Every consecutive {@link Event} applied will have its {@link Event#aggregateId(Object)} method called IF it doesn't already have a value. I.e. you can be lazy and skip setting the aggregate id
     * on the Event if you don't want to.<p>
     * The {@link AggregateRoot} automatically keeps track of the {@link Event#eventOrder()} value and will set it for you and ensure that it's growing consecutively.
     *
     * @param event the event to apply
     */
    protected void apply(EVENT_TYPE event) {
        requireNonNull(event, "You must supply an event");
        ID eventAggregateId = event.aggregateId();
        if (this.aggregateId == null) {
            // The aggregate doesn't know its aggregate id, hence the FIRST event being applied MUST know it
            this.aggregateId = eventAggregateId;
            if (this.aggregateId == null) {
                throw new InitialEventIsMissingAggregateIdException(msg("The first Event '{}' applied to Aggregate '{}' didn't contain an aggregateId",
                                                                        event.getClass().getName(),
                                                                        this.getClass().getName()));
            }
        }
        if (eventAggregateId == null) {
            event.aggregateId(aggregateId());
        } else {
            requireTrue(Objects.equals(eventAggregateId, this.aggregateId), msg("Aggregate Id's do not match! Cannot apply Event '{}' with aggregateId '{}' to Aggregate '{}' with aggregateId '{}'",
                                                                                event.getClass().getName(),
                                                                                eventAggregateId,
                                                                                this.getClass().getName(),
                                                                                this.aggregateId));
        }
        var nextEventOrderToBeApplied = eventOrderOfLastAppliedEvent().increment();
        event.eventOrder(nextEventOrderToBeApplied);
        applyEventToTheAggregateState(event);
        eventOrderOfLastAppliedEvent = nextEventOrderToBeApplied;
        _uncommittedChanges().add(event);
    }

    @Override
    public ID aggregateId() {
        requireNonNull(aggregateId, "The aggregate id has not been set on the AggregateRoot and not supplied using one of the Event applied to it. At least the first event MUST supply it");
        return aggregateId;
    }

    /**
     * Has {@link #rehydrate(Stream)}  or {@link #rehydrate(AggregateEventStream)} been used
     */
    public boolean hasBeenRehydrated() {
        return hasBeenRehydrated;
    }

    /**
     * Is the event being supplied to {@link #applyEventToTheAggregateState(Event)} a previously persisted (i.e. a historic event)
     */
    protected final boolean isRehydrating() {
        return isRehydrating;
    }

    /**
     * Apply the event to the aggregate instance to reflect the event as a state change to the aggregate<br/>
     * The default implementation will automatically call any (private) methods annotated with
     * {@link EventHandler}
     *
     * @param event the event to apply to the aggregate
     * @see #isRehydrating()
     */
    protected void applyEventToTheAggregateState(Event<ID> event) {
        if (invoker == null) {
            // Instance was created by Objenesis
            initialize();
        }
        invoker.invoke(event, unmatchedEvent -> {
            // Ignore unmatched events as Aggregates don't necessarily need handle every event
        });
    }

    /**
     * Get the {@link Event#eventOrder() of the last {}@link Event} that was applied to the {@link AggregateRoot}
     * (either using {@link #rehydrate(AggregateEventStream)}/{@link #rehydrate(Stream)} or using {@link #apply(Event)}
     *
     * @return the event order of the last applied {@link Event} or {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED} in case no
     * events has ever been applied to the aggregate
     */
    public EventOrder eventOrderOfLastAppliedEvent() {
        if (eventOrderOfLastAppliedEvent == null) {
            // Since the aggregate instance MAY have been created using Objenesis (which doesn't
            // initialize fields nor call a constructor) we have to be defensive and lazy way initialize
            // the eventOrderOfLastAppliedEvent
            eventOrderOfLastAppliedEvent = EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED;
        }
        return eventOrderOfLastAppliedEvent;
    }

    /**
     * The the events that have been applied to this aggregate instance but not yet persisted to
     * the underlying {@link EventStore}
     */
    @Override
    public EventsToPersist<ID, EVENT_TYPE> getUncommittedChanges() {
        return new EventsToPersist<>(aggregateId,
                                     eventOrderOfLastRehydratedEvent,
                                     _uncommittedChanges());

    }

    /**
     * Resets the {@link #getUncommittedChanges()} - effectively marking them as having been persisted
     * and committed to the underlying {@link EventStore}
     */
    @Override
    public void markChangesAsCommitted() {
        uncommittedChanges = new ArrayList<>();
        eventOrderOfLastRehydratedEvent = null;
    }

    /**
     * Since the aggregate instance MAY have been created using Objenesis (which doesn't
     * initialize fields nor call a constructor) we have to be defensive and lazy way to initialize the list
     *
     * @return the initialized uncommitted changes
     */
    private List<EVENT_TYPE> _uncommittedChanges() {
        if (uncommittedChanges == null) {
            uncommittedChanges = new ArrayList<>();
        }
        return uncommittedChanges;
    }

}
