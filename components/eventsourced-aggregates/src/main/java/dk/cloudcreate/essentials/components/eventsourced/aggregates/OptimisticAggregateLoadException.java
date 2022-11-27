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

package dk.cloudcreate.essentials.components.eventsourced.aggregates;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreException;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class OptimisticAggregateLoadException extends EventStoreException {
    public final Object                           aggregateId;
    public final Class<? extends Aggregate<?, ?>> aggregateType;
    public final EventOrder                       expectedLatestEventOrder;
    public final EventOrder                       actualLatestEventOrder;

    public OptimisticAggregateLoadException(Object aggregateId, Class<? extends Aggregate<?, ?>> aggregateType, EventOrder expectedLatestEventOrder, EventOrder actualLatestEventOrder) {
        super(msg("Expected expectedLatestEventOrder '{}' for '{}' with id '{}' but found '{}' (actualLatestEventOrder) in the EventStore",
                  expectedLatestEventOrder,
                  aggregateType.getName(),
                  aggregateId,
                  actualLatestEventOrder));
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.expectedLatestEventOrder = expectedLatestEventOrder;
        this.actualLatestEventOrder = actualLatestEventOrder;
    }
}
