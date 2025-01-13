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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.EventStoreInterceptor;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link ConfigurableEventStore} interface expands the {@link EventStore} with the capabilities to:
 * <ul>
 *     <li>Add {@link AggregateEventStreamConfiguration}'s - i.e. through the AggregateEventStreamConfiguration instruct the associated {@link AggregateEventStreamPersistenceStrategy}
 *     about how each {@link AggregateType} should be persisted</li>
 *     <li>Add {@link InMemoryProjector}'s</li>
 * </ul>
 *
 * @param <CONFIG> the {@link AggregateType} event stream configuration
 * @see PostgresqlEventStore
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface ConfigurableEventStore<CONFIG extends AggregateEventStreamConfiguration> extends EventStore {

    /**
     * Add Event stream configuration related to a specific {@link AggregateType} as indicated by {@link AggregateEventStreamConfiguration#aggregateType }
     *
     * @param eventStreamConfiguration the event stream configuration
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> addAggregateEventStreamConfiguration(CONFIG eventStreamConfiguration);

    /**
     * Add Event stream configuration related to a specific {@link AggregateType} and {@link AggregateIdSerializer}
     * using the default configuration provided by the encapsulated {@link AggregateEventStreamConfigurationFactory}'s
     * {@link AggregateEventStreamConfigurationFactory#createEventStreamConfigurationFor(AggregateType, AggregateIdSerializer)}
     *
     * @param aggregateType         the aggregate type for which we're creating an {@link AggregateEventStreamConfiguration}
     * @param aggregateIdSerializer the {@link AggregateIdSerializer} to use for the given {@link AggregateType}
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> addAggregateEventStreamConfiguration(AggregateType aggregateType,
                                                                        AggregateIdSerializer aggregateIdSerializer);

    /**
     * Add Event stream configuration related to a specific {@link AggregateType} and {@link AggregateIdSerializer}
     * using the default configuration provided by the encapsulated {@link AggregateEventStreamConfigurationFactory}'s
     * {@link AggregateEventStreamConfigurationFactory#createEventStreamConfigurationFor(AggregateType, Class)}
     *
     * @param aggregateType   the aggregate type for which we're creating an {@link AggregateEventStreamConfiguration}
     * @param aggregateIdType the Aggregate Id type used by the provided {@link AggregateType} - calls {@link AggregateIdSerializer#serializerFor(Class)}
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> addAggregateEventStreamConfiguration(AggregateType aggregateType,
                                                                        Class<?> aggregateIdType);

    /**
     * Get the {@link AggregateEventStreamConfiguration} for this specific AggregateType
     *
     * @param aggregateType the aggregate type to get the configuration for
     * @return the aggregate type configuration
     * @throws EventStoreException if the
     */
    CONFIG getAggregateEventStreamConfiguration(AggregateType aggregateType);

    /**
     * Lookup the optional {@link AggregateEventStreamConfiguration} for the given aggregate type
     *
     * @param aggregateType the aggregate type
     * @return the {@link AggregateEventStreamConfiguration} wrapped in an {@link Optional}
     */
    Optional<CONFIG> findAggregateEventStreamConfiguration(AggregateType aggregateType);

    /**
     * Add a generic {@link InMemoryProjector}. When {@link #inMemoryProjection(AggregateType, Object, Class)} is called
     * it will first check for any {@link InMemoryProjector}'s registered using {@link #addSpecificInMemoryProjector(Class, InMemoryProjector)}<br>
     * After that it will try and find the first {@link InMemoryProjector#supports(Class)}, registered using this method, which reports true for the given projection type
     *
     * @param inMemoryProjector the in memory projector
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> addGenericInMemoryProjector(InMemoryProjector inMemoryProjector);

    /**
     * Remove a generic {@link InMemoryProjector
     *
     * @param inMemoryProjector the in memory projector
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> removeGenericInMemoryProjector(InMemoryProjector inMemoryProjector);

    /**
     * Add a projection-type specific {@link InMemoryProjector}. When {@link #inMemoryProjection(AggregateType, Object, Class)} is called
     * it will first check for any {@link InMemoryProjector}'s registered using {@link #addSpecificInMemoryProjector(Class, InMemoryProjector)}<br>
     * After that it will try and find the first {@link InMemoryProjector#supports(Class)}, registered using {@link #addGenericInMemoryProjector(InMemoryProjector)}, which reports true for the given projection type
     *
     * @param projectionType    the projection type
     * @param inMemoryProjector the in memory projector
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> addSpecificInMemoryProjector(Class<?> projectionType, InMemoryProjector inMemoryProjector);

    /**
     * Remove a projection-type specific {@link InMemoryProjector}.
     *
     * @param projectionType the projection type
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> removeSpecificInMemoryProjector(Class<?> projectionType);

    /**
     * Add an {@link EventStoreInterceptor} to the {@link ConfigurableEventStore}.<br>
     * {@link EventStoreInterceptor}'s added will be ordered according to they {@link dk.cloudcreate.essentials.shared.interceptor.InterceptorOrder}
     *
     * @param eventStoreInterceptor the interceptor to add
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> addEventStoreInterceptor(EventStoreInterceptor eventStoreInterceptor);

    /**
     * Add {@link EventStoreInterceptor}'s to the {@link ConfigurableEventStore}
     *
     * @param eventStoreInterceptors the interceptors to add
     * @return this event store instance
     */
    default ConfigurableEventStore<CONFIG> addEventStoreInterceptors(List<EventStoreInterceptor> eventStoreInterceptors) {
        requireNonNull(eventStoreInterceptors, "No eventStoreInterceptors list provided");
        eventStoreInterceptors.forEach(this::addEventStoreInterceptor);
        return this;
    }

    /**
     * Remove an {@link EventStoreInterceptor} from the {@link ConfigurableEventStore}
     *
     * @param eventStoreInterceptor the interceptor to add
     * @return this event store instance
     */
    ConfigurableEventStore<CONFIG> removeEventStoreInterceptor(EventStoreInterceptor eventStoreInterceptor);
}
