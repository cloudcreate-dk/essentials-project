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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;

/**
 * Helper Factory for creating {@link SeparateTablePerAggregateEventStreamConfiguration}
 * with a shared/common configuration that's compatible wth the chosen {@link AggregateEventStreamPersistenceStrategy}
 */
public interface AggregateEventStreamConfigurationFactory<CONFIG extends AggregateEventStreamConfiguration> {
    /**
     * Create concrete {@link AggregateEventStreamConfiguration} for a given {@link AggregateType} based on the base configuration of
     * this {@link AggregateEventStreamConfigurationFactory}
     *
     * @param aggregateType         the aggregate type for which we're creating an {@link AggregateEventStreamConfiguration}
     * @param aggregateIdSerializer the {@link AggregateIdSerializer} to use for the given {@link AggregateType}
     * @return the concrete {@link AggregateEventStreamConfiguration} for a given {@link AggregateType}
     */
    CONFIG createEventStreamConfigurationFor(AggregateType aggregateType,
                                             AggregateIdSerializer aggregateIdSerializer);

    /**
     * Create concrete {@link AggregateEventStreamConfiguration} for a given {@link AggregateType} based on the base configuration of
     * this {@link AggregateEventStreamConfigurationFactory}
     *
     * @param aggregateType   the aggregate type for which we're creating an {@link AggregateEventStreamConfiguration}
     * @param aggregateIdType the Aggregate Id type used by the provided {@link AggregateType} - calls {@link AggregateIdSerializer#serializerFor(Class)}
     * @return the concrete {@link AggregateEventStreamConfiguration} for a given {@link AggregateType}
     */
    default CONFIG createEventStreamConfigurationFor(AggregateType aggregateType,
                                                     Class<?> aggregateIdType) {
        return createEventStreamConfigurationFor(aggregateType,
                                                 AggregateIdSerializer.serializerFor(aggregateIdType));
    }
}
