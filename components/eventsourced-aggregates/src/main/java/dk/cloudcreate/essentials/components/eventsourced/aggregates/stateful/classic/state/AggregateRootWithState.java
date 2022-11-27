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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.state;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.reflection.Reflector;
import dk.cloudcreate.essentials.shared.types.GenericType;

import java.util.stream.Stream;

/**
 * Variant of the {@link AggregateRoot} pattern where the aggregate's state and all {@link EventHandler} annotated methods
 * are placed within the concrete {@link AggregateState} object.<br>
 * When the {@link AggregateRootWithState} is combined with {@link AggregateState}, then the {@link AggregateRootWithState}
 * will contain the command methods and the {@link AggregateState} contains the state fields and the
 * {@link EventHandler} annotated methods.
 *
 * @param <ID>             the aggregate id type
 * @param <EVENT_TYPE>     the type of event
 * @param <AGGREGATE_TYPE> the aggregate self type (i.e. your concrete aggregate type)
 * @param <STATE>          the aggregate state type (i.e. your concrete aggregate state)
 */
public abstract class AggregateRootWithState<ID, EVENT_TYPE extends Event<ID>, STATE extends AggregateState<ID, EVENT_TYPE>,
        AGGREGATE_TYPE extends AggregateRootWithState<ID, EVENT_TYPE, STATE, AGGREGATE_TYPE>> extends AggregateRoot<ID, EVENT_TYPE, AGGREGATE_TYPE> {
    protected STATE state;

    public AggregateRootWithState() {
    }

    /**
     * Override this method to initialize the {@link #state} variable in case
     * the {@link #resolveStateImplementationClass()} doesn't fit the requirements
     */
    @Override
    protected void initialize() {
        var stateType = resolveStateImplementationClass();
        state = Reflector.reflectOn(stateType).newInstance();
    }

    @SuppressWarnings("unchecked")
    @Override
    public AGGREGATE_TYPE rehydrate(Stream<EVENT_TYPE> previousEvents) {
        if (state == null) {
            // Instance was created by Objenesis
            initialize();
        }
        state.rehydrate(previousEvents);
        return (AGGREGATE_TYPE) this;
    }

    @Override
    public EventOrder eventOrderOfLastRehydratedEvent() {
        return state.getEventOrderOfLastRehydratedEvent();
    }

    @Override
    protected void apply(EVENT_TYPE event) {
        state.apply(event);
    }

    @Override
    public ID aggregateId() {
        return state.aggregateId();
    }

    @Override
    public EventOrder eventOrderOfLastAppliedEvent() {
        return state.eventOrderOfLastAppliedEvent();
    }

    @Override
    public EventsToPersist<ID, EVENT_TYPE> getUncommittedChanges() {
        return new EventsToPersist<>(aggregateId(),
                                     eventOrderOfLastRehydratedEvent(),
                                     state.uncommittedChanges());
    }

    @Override
    public void markChangesAsCommitted() {
        state.markChangesAsCommitted();
    }

    /**
     * Override this method to provide a non reflection based look up of the Type Argument
     * provided to the {@link AggregateRootWithState} class
     *
     * @return the {@link AggregateState} implementation to use for the {@link #state} instance
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Class<AggregateState> resolveStateImplementationClass() {
        return (Class<AggregateState>) GenericType.resolveGenericTypeOnSuperClass(this.getClass(), 2);
    }
}
