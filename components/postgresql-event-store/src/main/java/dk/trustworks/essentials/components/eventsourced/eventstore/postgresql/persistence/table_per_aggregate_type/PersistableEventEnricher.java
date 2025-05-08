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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreUnitOfWork;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

import java.util.*;

/**
 * Enricher of {@link PersistableEvent}'s - can e.g. adjust {@link PersistableEvent#metaData()} entries such as tracing id's and other contextual information
 */
public interface PersistableEventEnricher {
    /**
     * Is called by the {@link SeparateTablePerAggregateTypePersistenceStrategy#persist(EventStoreUnitOfWork, AggregateType, Object, Optional, List)} after
     * {@link PersistableEventMapper#map(Object, AggregateEventStreamConfiguration, Object, EventOrder)}
     * has been called
     *
     * @param event the {@link PersistableEvent} produced by {@link PersistableEventMapper#map(Object, AggregateEventStreamConfiguration, Object, EventOrder)}
     * @return the enriched event (can be the same instance or a new instance)
     */
    PersistableEvent enrich(PersistableEvent event);
}
