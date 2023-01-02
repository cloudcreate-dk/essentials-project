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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreUnitOfWork;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.*;
import java.util.stream.Stream;

/**
 * Represents the strategy that the {@link PostgresqlEventStore} will use to persist and load events related to a named Event Stream.<br>
 * The {@link AggregateEventStreamPersistenceStrategy} is managed by the {@link PostgresqlEventStore} and is <b>shared</b>
 * between all the {@link AggregateEventStream}'s it manages.
 *
 * @param <CONFIG> the {@link AggregateType} configuration type
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface AggregateEventStreamPersistenceStrategy<CONFIG extends AggregateEventStreamConfiguration> {
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
     * @param aggregateType the aggregate type
     * @return the {@link AggregateEventStreamConfiguration} wrapped in an {@link Optional}
     */
    Optional<CONFIG> findAggregateEventStreamConfiguration(AggregateType aggregateType);

    /**
     * Add Event stream configuration related to a specific {@link AggregateType} as indicated by {@link AggregateEventStreamConfiguration#aggregateType }
     *
     * @param eventStreamConfiguration the event stream configuration
     * @return this strategy instance
     */
    AggregateEventStreamPersistenceStrategy<CONFIG> addAggregateEventStreamConfiguration(CONFIG eventStreamConfiguration);

    /**
     * Add Event stream configuration related to a specific {@link AggregateType} and {@link AggregateIdSerializer}
     * using the default configuration provided by the encapsulated {@link AggregateEventStreamConfigurationFactory}'s
     * {@link AggregateEventStreamConfigurationFactory#createEventStreamConfigurationFor(AggregateType, AggregateIdSerializer)}
     *
     * @param aggregateType         the aggregate type for which we're creating an {@link AggregateEventStreamConfiguration}
     * @param aggregateIdSerializer the {@link AggregateIdSerializer} to use for the given {@link AggregateType}
     * @return this strategy instance
     */
    AggregateEventStreamPersistenceStrategy<CONFIG> addAggregateEventStreamConfiguration(AggregateType aggregateType,
                                                                                         AggregateIdSerializer aggregateIdSerializer);

    /**
     * Add Event stream configuration related to a specific {@link AggregateType} and {@link AggregateIdSerializer}
     * using the default configuration provided by the encapsulated {@link AggregateEventStreamConfigurationFactory}'s
     * {@link AggregateEventStreamConfigurationFactory#createEventStreamConfigurationFor(AggregateType, Class)}
     *
     * @param aggregateType   the aggregate type for which we're creating an {@link AggregateEventStreamConfiguration}
     * @param aggregateIdType the Aggregate Id type used by the provided {@link AggregateType} - calls {@link AggregateIdSerializer#serializerFor(Class)}
     * @return this strategy instance
     */
    AggregateEventStreamPersistenceStrategy<CONFIG> addAggregateEventStreamConfiguration(AggregateType aggregateType,
                                                                                         Class<?> aggregateIdType);


    /**
     * Persist a List of persistable events
     *
     * @param unitOfWork                  the current unitOfWork
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 The id of the aggregate identifier (aka the stream id) that the <code>persistableEvents</code> are related to
     * @param appendEventsAfterEventOrder the {@link PersistedEvent#eventOrder()} of the last event persisted related to the given <code>aggregateId</code>.
     *                                    This means that events in <code>persistableEvents</code> will receive {@link PersistedEvent#eventOrder()} starting from and including <code>appendEventsAfterEventOrder + 1</code>
     * @param persistableEvents           the list of persistable events (i.e. events that haven't yet been persisted)
     * @return the {@link PersistedEvent}'s - each one corresponds 1-1 and IN-ORDER with the <code>persistableEvents</code>
     */
    <STREAM_ID> AggregateEventStream<STREAM_ID> persist(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, STREAM_ID aggregateId, Optional<Long> appendEventsAfterEventOrder, List<?> persistableEvents);

    /**
     * Load the last {@link PersistedEvent} in relation to the specified <code>configuration</code> and <code>aggregateId</code>
     *
     * @param aggregateId   the identifier of the aggregate we want to find the last {@link PersistedEvent}
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param <STREAM_ID>   the id type for the aggregate
     * @return an {@link Optional} with the last {@link PersistedEvent} related to the <code>aggregateId</code> instance or
     * {@link Optional#empty()} if no events have been persisted before
     */
    <STREAM_ID> Optional<PersistedEvent> loadLastPersistedEventRelatedTo(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, STREAM_ID aggregateId);

    /**
     * Load all events related to the given <code>configuration</code>, sharing the same <code>aggregateId</code> and having a {@link PersistedEvent#eventOrder()} within the <code>eventOrderRange</code>
     *
     * @param unitOfWork                            the current unit of work
     * @param aggregateType                         the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                           The id of the aggregate identifier (aka the stream id) that the events is related to
     * @param eventOrderRange                       the range of {@link PersistedEvent#globalEventOrder()}'s we want Events for
     * @param onlyIncludeEventsIfTheyBelongToTenant Matching events will only be returned if the {@link PersistedEvent#tenant()} matches
     *                                              the given tenant specified in this parameter OR if the Event doesn't specify ANY tenant
     * @return the {@link PersistedEvent} related to the event stream's
     */
    <STREAM_ID> Optional<AggregateEventStream<STREAM_ID>> loadAggregateEvents(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, STREAM_ID aggregateId, LongRange eventOrderRange, Optional<Tenant> onlyIncludeEventsIfTheyBelongToTenant);

    /**
     * Load all events related to the given <code>configuration</code> and having a {@link PersistedEvent#globalEventOrder()} within the <code>globalOrderRange</code>
     *
     * @param unitOfWork                            the current unit of work
     * @param aggregateType                         the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param globalOrderRange                      the range of {@link PersistedEvent#globalEventOrder()}'s we want Events for
     * @param includeAdditionalGlobalOrders         a list of additional global orders (typically outside the <code>globalOrderRange</code>) that you want to include additionally<br>
     *                                              May be null or empty if no additional events should be loaded outside the <code>globalOrderRange</code>
     * @param onlyIncludeEventsIfTheyBelongToTenant Matching events will only be returned if the {@link PersistedEvent#tenant()} matches
     *                                              the given tenant specified in this parameter OR if the Event doesn't specify ANY tenant
     * @return the {@link PersistedEvent}'s
     */
    Stream<PersistedEvent> loadEventsByGlobalOrder(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, LongRange globalOrderRange, List<GlobalEventOrder> includeAdditionalGlobalOrders, Optional<Tenant> onlyIncludeEventsIfTheyBelongToTenant);

    /**
     * Load the event belonging to the given <code>configuration</code> and having the specified <code>eventId</code>
     *
     * @param unitOfWork    the current unit of work
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param eventId       the identifier of the {@link PersistedEvent}
     * @return an {@link Optional} with the {@link PersistedEvent} or {@link Optional#empty()} if the event couldn't be found
     */
    Optional<PersistedEvent> loadEvent(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, EventId eventId);

    /**
     * Find the highest {@link GlobalEventOrder} persisted in relation to the given aggregateType
     *
     * @param unitOfWork    the current unit of work
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @return an {@link Optional} with the {@link GlobalEventOrder} persisted or {@link Optional#empty()} if no events have been persisted
     */
    Optional<GlobalEventOrder> findHighestGlobalEventOrderPersisted(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType);
}
