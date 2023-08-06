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

package dk.cloudcreate.essentials.components.eventsourced.aggregates;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateEventStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

/**
 * Common interface that all concrete (classical) {@link Aggregate}'s must implement. Most concrete implementations choose to extend the {@link AggregateRoot} class.
 *
 * @param <ID> the aggregate id (or stream-id) type
 * @see AggregateRoot
 */
public interface Aggregate<ID, AGGREGATE_TYPE extends Aggregate<ID, AGGREGATE_TYPE>> {
    /**
     * The id of the aggregate (aka. the stream-id)
     */
    ID aggregateId();

    /**
     * Has the aggregate been initialized using previously recorded/persisted events (aka. historic events) using the {@link #rehydrate(AggregateEventStream)} method
     */
    boolean hasBeenRehydrated();

    /**
     * Effectively performs a leftFold over all the previously persisted events related to this aggregate instance
     *
     * @param persistedEvents the previous persisted events related to this aggregate instance, aka. the aggregates history
     * @return the same aggregate instance (self)
     */
    @SuppressWarnings("unchecked")
    AGGREGATE_TYPE rehydrate(AggregateEventStream<ID> persistedEvents);

    /**
     * Get the eventOrder of the last event during aggregate hydration (using the {@link #rehydrate(AggregateEventStream)} method)
     *
     * @return the event order of the last applied {@link Event} or {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED} in case no
     * events has ever been applied to the aggregate
     */
    EventOrder eventOrderOfLastRehydratedEvent();
}
