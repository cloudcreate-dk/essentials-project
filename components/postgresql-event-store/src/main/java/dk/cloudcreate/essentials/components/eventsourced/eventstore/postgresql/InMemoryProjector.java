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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;

import java.util.Optional;

/**
 * Responsible for performing an in memory projection of a given aggregate instances event stream.<br>
 * Note: Unless the {@link InMemoryProjector} explicitly states otherwise, an
 * in memory projection is not automatically associated with a {@link UnitOfWork} and any changes to the projection
 * won't automatically be persisted.
 */
public interface InMemoryProjector {
    /**
     * Check if this {@link InMemoryProjector} supports the given <code>projectionType</code>
     *
     * @param projectionType the projection result type
     * @return true if this projector supports the given <code>projectionType</code>, otherwise false
     */
    boolean supports(Class<?> projectionType);

    /**
     * Perform an in memory projection of a given aggregate instances event stream.<br>
     * Note: Unless the {@link InMemoryProjector} explicitly states otherwise, an
     * in memory projection is not automatically associated with a {@link UnitOfWork} and any changes to the projection
     * won't automatically be persisted.<br>
     *
     * @param aggregateType  the name of the aggregate' event stream
     * @param aggregateId    the identifier for the aggregate instance
     * @param projectionType the type of projection result
     * @param <ID>           the aggregate id type
     * @param <PROJECTION>   the projection type
     * @return an {@link Optional} with the underlying {@link AggregateEventStream} projected using a matching {@link InMemoryProjector}
     * or {@link Optional#empty()} in case there the {@link AggregateEventStream} doesn't exist
     */
    <ID, PROJECTION> Optional<PROJECTION> projectEvents(AggregateType aggregateType, ID aggregateId, Class<PROJECTION> projectionType, EventStore eventStore);
}
