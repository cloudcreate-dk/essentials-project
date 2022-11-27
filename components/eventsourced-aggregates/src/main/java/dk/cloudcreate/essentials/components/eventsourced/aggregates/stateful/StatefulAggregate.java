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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.flex.FlexAggregate;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;

/**
 * A stateful {@link Aggregate} is the most common form of Aggregate design in Object Oriented languages.<br>
 * What makes an {@link Aggregate} stateful is the fact that any changes, i.e. Events applied as the result of calling command methods on the aggregate instance, are stored
 * within the {@link StatefulAggregate} prior to persisting the aggregate, and the events associated with any changes can be queried using {@link #getUncommittedChanges()} and is
 * reset (e.g. after a transaction/{@link UnitOfWork} has completed) using {@link #markChangesAsCommitted()}<br>
 * <br>
 * See {@link FlexAggregate} for an immutable {@link Aggregate} design
 *
 * @param <ID>             the type of id
 * @param <EVENT_TYPE>     the type of event
 * @param <AGGREGATE_TYPE> the aggregate type
 */
public interface StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_TYPE extends StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_TYPE>> extends Aggregate<ID, AGGREGATE_TYPE> {
    /**
     * Query any changes to the Aggregate,  i.e. Events applied as the result of calling command methods on the aggregate instance,
     *
     * @return the changes to the aggregate
     */
    EventsToPersist<ID, EVENT_TYPE> getUncommittedChanges();

    /**
     * Resets the {@link #getUncommittedChanges()} - effectively marking them as having been persisted
     * and committed to the underlying {@link EventStore}
     */
    void markChangesAsCommitted();
}
