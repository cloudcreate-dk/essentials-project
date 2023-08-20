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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.state;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.EventHandler;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.reflection.invocation.*;
import dk.cloudcreate.essentials.shared.types.GenericType;

import java.util.*;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Base class for the state object associated with {@link AggregateRootWithState}.<br>
 * When this is combined with the {@link AggregateRootWithState} when the {@link AggregateRootWithState}
 * will contain the command methods and the {@link AggregateState} contains the state fields and the
 * {@link EventHandler} annotated methods.
 *
 * @param <ID>         the aggregate id type
 * @param <EVENT_TYPE> the type of event
 */
public abstract class AggregateState<ID, EVENT_TYPE extends Event<ID>> {
    private PatternMatchingMethodInvoker<Event<ID>> invoker;
    private ID                                      aggregateId;
    private List<EVENT_TYPE>                        uncommittedChanges;
    /**
     * Zero based event order
     */
    private EventOrder                              eventOrderOfLastAppliedEvent;
    private boolean                                 hasBeenRehydrated;
    private boolean                                 isRehydrating;
    private EventOrder                              eventOrderOfLastRehydratedEvent;

    public AggregateState() {
        invoker = new PatternMatchingMethodInvoker<>(this,
                                                     new SingleArgumentAnnotatedMethodPatternMatcher<>(EventHandler.class,
                                                                                                       new GenericType<>() {
                                                                                                       }),
                                                     InvocationStrategy.InvokeMostSpecificTypeMatched);

    }

    public void rehydrate(Stream<EVENT_TYPE> previousEvents) {
        requireNonNull(previousEvents, "You must provide a previousEvents stream");
        isRehydrating = true;
        previousEvents.forEach(event -> {
            if (aggregateId == null) {
                // The aggregate doesn't know its aggregate id, hence the FIRST historic event being applied MUST know it
                aggregateId = event.aggregateId();
                requireNonNull(aggregateId, msg("The first previous/historic Event '{}' applied to Aggregate {}'s state didn't contain an aggregateId",
                                                event.getClass().getName(),
                                                this.getClass().getName()));
            }
            applyEventToTheAggregate(event);
            eventOrderOfLastAppliedEvent = event.eventOrder();
        });
        eventOrderOfLastRehydratedEvent = eventOrderOfLastAppliedEvent;
        isRehydrating = false;
        hasBeenRehydrated = true;
    }

    public EventOrder getEventOrderOfLastAppliedEvent() {
        return eventOrderOfLastAppliedEvent;
    }

    public EventOrder getEventOrderOfLastRehydratedEvent() {
        return eventOrderOfLastRehydratedEvent;
    }

    /**
     * Apply a new non persisted/uncommitted Event to this aggregate instance.<br>
     * If it is the very FIRST {@link Event} that is being applied then {@link Event#aggregateId()} MUST return the ID of the aggregate the event relates to<br>
     * Every consecutive {@link Event} applied will have its {@link Event#aggregateId(Object)}  method called IF it doesn't already have a value. I.e. you can be lazy and skip setting the aggregate id
     * on the Event if you don't want to.<p>
     * The {@link AggregateRoot} automatically keeps track of the {@link Event#eventOrder()} value and will set it for you and ensure that it's growing consecutively.
     *
     * @param event the event to apply
     */
    protected final void apply(EVENT_TYPE event) {
        requireNonNull(event, "You must supply an event");
        var eventAggregateId = event.aggregateId();
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
        applyEventToTheAggregate(event);
        eventOrderOfLastAppliedEvent = nextEventOrderToBeApplied;
        _uncommittedChanges().add(event);
    }

    public ID aggregateId() {
        requireNonNull(aggregateId, "The aggregate id has not been set on the AggregateRoot and not supplied using one of the Event applied to it. At least the first event MUST supply it");
        return aggregateId;
    }

    /**
     * Has {@link #rehydrate(Stream)} been used
     */
    public boolean hasBeenRehydrated() {
        return hasBeenRehydrated;
    }

    /**
     * Is the event being supplied to {@link #applyEventToTheAggregate(Event)} a historic event
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
    protected void applyEventToTheAggregate(Event<ID> event) {
        invoker.invoke(event, unmatchedEvent -> {
            // Ignore unmatched events as Aggregates don't necessarily need handle every event
        });
    }

    /**
     * Get the {@link Event#eventOrder() of the last {}@link Event} that was applied to the {@link AggregateRoot}
     * (either using {@link #rehydrate(Stream)} or using {@link #apply(Event)}
     *
     * @return the event order of the last applied {@link Event} or {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED} in case no
     * events has ever been applied to the aggregate
     */
    public final EventOrder eventOrderOfLastAppliedEvent() {
        if (eventOrderOfLastAppliedEvent == null) {
            // Since the aggregate instance MAY have been created using Objenesis (which doesn't
            // initialize fields nor calls a constructor) we have to be defensive and lazy way initialize
            // the eventOrderOfLastAppliedEvent
            eventOrderOfLastAppliedEvent = EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED;
        }
        return eventOrderOfLastAppliedEvent;
    }

    /**
     * The the events that have been applied to this aggregate instance but not yet persisted to
     * the underlying {@link EventStore}
     */
    public List<EVENT_TYPE> uncommittedChanges() {
        return _uncommittedChanges();
    }

    /**
     * Resets the {@link #uncommittedChanges()} - effectively marking them as having been persisted
     * and committed to the underlying {@link EventStore}
     */
    public void markChangesAsCommitted() {
        uncommittedChanges = new ArrayList<>();
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
