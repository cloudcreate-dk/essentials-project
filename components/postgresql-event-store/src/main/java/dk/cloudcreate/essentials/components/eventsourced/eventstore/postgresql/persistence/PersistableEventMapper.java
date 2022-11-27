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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;

import java.util.*;

/**
 * This mapper is used by the {@link AggregateEventStreamPersistenceStrategy} to convert from any type of Event to a {@link PersistableEvent}
 * which is the type of Event the {@link AggregateEventStreamPersistenceStrategy} understands how to persist.<br>
 * This mapper is also responsible for enriching the {@link PersistableEvent} with additional metadata, such as {@link CorrelationId}, {@link PersistableEvent#causedByEventId()}, {@link Tenant} and
 * {@link EventMetaData}
 */
public interface PersistableEventMapper {
    /**
     * Convert a Java object Event into a PersistableEvent.
     *
     * @param aggregateId                the aggregate id (as provided to {@link EventStore#appendToStream(AggregateType, Object, Optional, List)}/{@link AggregateEventStreamPersistenceStrategy#persist(UnitOfWork, AggregateEventStreamConfiguration, Object, Optional, List)}
     * @param aggregateEventStreamConfiguration the configuration for the {@link AggregateEventStream} the events related to this aggregate instance will be appended to
     * @param event                      the raw Java event
     * @param eventOrder                 the order of the event
     * @return the {@link PersistableEvent} which is the precursor to the {@link PersistedEvent}.<br>
     * A {@link PersistableEvent} contains additional metadata, such as {@link CorrelationId}, {@link PersistableEvent#causedByEventId()}, {@link Tenant},
     * and {@link EventMetaData}
     */
    PersistableEvent map(Object aggregateId,
                         AggregateEventStreamConfiguration aggregateEventStreamConfiguration,
                         Object event,
                         EventOrder eventOrder);
}
