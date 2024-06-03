/*
 * Copyright 2021-2024 the original author or authors.
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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.EventStoreInterceptor;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.types.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder.FIRST_EVENT_ORDER;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link EventStore} interface providing capabilities to persist and load events, such as {@link #appendToStream(AggregateType, Object, Optional, List)}/
 * {@link #fetchStream(AggregateType, Object, LongRange, Optional)}/{@link #loadEvent(AggregateType, EventId)}/
 * {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
 *
 * @see ConfigurableEventStore
 * @see PostgresqlEventStore
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface EventStore {
    /**
     * Default value for {@link AggregateEventStreamConfiguration#queryFetchSize}
     */
    int DEFAULT_QUERY_BATCH_SIZE              = 100;
    /**
     * Default Polling Interface for {@link #pollEvents(AggregateType, long, Optional, Optional, Optional, Optional)}/{@link #pollEvents(AggregateType, GlobalEventOrder, Optional, Optional, Optional, Optional)}
     */
    int DEFAULT_POLLING_INTERVAL_MILLISECONDS = 500;

    /**
     * Get the {@link UnitOfWorkFactory} associated with the {@link EventStore}
     *
     * @return the {@link UnitOfWorkFactory} associated with the {@link EventStore}
     */
    EventStoreUnitOfWorkFactory<EventStoreUnitOfWork> getUnitOfWorkFactory();

    /**
     * Get the {@link LocalEventBus}, which can be used for transactional listening for {@link EventStore} changes
     *
     * @return the local event bus
     */
    EventBus localEventBus();

    /**
     * Get an immutable list of all registered {@link EventStoreInterceptor}'s ordered according to their {@link dk.cloudcreate.essentials.shared.interceptor.InterceptorOrder}
     * @return an immutable list of all registered {@link EventStoreInterceptor}'s ordered according to their {@link dk.cloudcreate.essentials.shared.interceptor.InterceptorOrder}
     * @see ConfigurableEventStore#addEventStoreInterceptor(EventStoreInterceptor)
     * @see ConfigurableEventStore#removeEventStoreInterceptor(EventStoreInterceptor)
     */
    List<EventStoreInterceptor> getEventStoreInterceptors();

    /**
     * Append the <code>eventsToAppend</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>. <br>
     * This method will call {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
     * to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.<br>
     * If you know the {@link EventOrder} of the last persisted event, then please use either {@link #appendToStream(AggregateType, Object, Optional, List)}
     * or {@link #appendToStream(AggregateType, Object, EventOrder, List)} as this involves one less event store query
     *
     * @param aggregateType  the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId    the identifier of the aggregate we want to persist eventsToAppend related to
     * @param eventsToAppend the eventsToAppend to persist
     * @param <ID>           the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         List<?> eventsToAppend) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.empty(),
                              eventsToAppend);
    }

    /**
     * Append the <code>eventsToAppend</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>. <br>
     * This method will call {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
     * to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.<br>
     * If you know the {@link EventOrder} of the last persisted event, then please use either {@link #appendToStream(AggregateType, Object, Optional, List)}
     * or {@link #appendToStream(AggregateType, Object, EventOrder, List)} as this involves one less event store query
     *
     * @param aggregateType  the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId    the identifier of the aggregate we want to persist eventsToAppend related to
     * @param eventsToAppend the eventsToAppend to persist
     * @param <ID>           the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         Object... eventsToAppend) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.empty(),
                              List.of(eventsToAppend));
    }

    /**
     * Start a new {@link AggregateEventStream}, related to the aggregate with id <code>aggregateId</code> and associated with the <code>aggregateType</code>,
     * and append the <code>eventsToAppend</code> to it.<br>
     * <br>
     * This is a shorthand to calling {@link #appendToStream(AggregateType, Object, EventOrder, List)} using {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}
     *
     * @param aggregateType  the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId    the identifier of the aggregate we want to persist eventsToAppend related to
     * @param eventsToAppend the eventsToAppend to persist
     * @param <ID>           the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> startStream(AggregateType aggregateType,
                                                      ID aggregateId,
                                                      List<?> eventsToAppend) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.empty(),
                              eventsToAppend);
    }

    /**
     * Start a new {@link AggregateEventStream}, related to the aggregate with id <code>aggregateId</code> and associated with the <code>aggregateType</code>,
     * and append the <code>eventsToAppend</code> to it.<br>
     * <br>
     * This is a shorthand to calling {@link #appendToStream(AggregateType, Object, EventOrder, List)} using {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}
     *
     * @param aggregateType  the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId    the identifier of the aggregate we want to persist eventsToAppend related to
     * @param eventsToAppend the eventsToAppend to persist
     * @param <ID>           the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> startStream(AggregateType aggregateType,
                                                      ID aggregateId,
                                                      Object... eventsToAppend) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.empty(),
                              List.of(eventsToAppend));
    }

    /**
     * Append the <code>eventsToAppend</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 the identifier of the aggregate we want to persist eventsToAppend related to
     * @param appendEventsAfterEventOrder append the <code>eventsToAppend</code> after this event order, i.e. the first event in the <code>eventsToAppend</code> list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}
     * @param eventsToAppend              the eventsToAppend to persist
     * @param <ID>                        the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         EventOrder appendEventsAfterEventOrder,
                                                         List<?> eventsToAppend) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.ofNullable(appendEventsAfterEventOrder).map(NumberType::longValue),
                              eventsToAppend);
    }

    /**
     * Append the <code>eventsToAppend</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 the identifier of the aggregate we want to persist eventsToAppend related to
     * @param appendEventsAfterEventOrder append the <code>eventsToAppend</code> after this event order, i.e. the first event in the <code>eventsToAppend</code> list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED}
     * @param eventsToAppend              the eventsToAppend to persist
     * @param <ID>                        the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         EventOrder appendEventsAfterEventOrder,
                                                         Object... eventsToAppend) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.ofNullable(appendEventsAfterEventOrder).map(NumberType::longValue),
                              List.of(eventsToAppend));
    }

    /**
     * Append the <code>eventsToAppend</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 the identifier of the aggregate we want to persist eventsToAppend related to
     * @param appendEventsAfterEventOrder append the <code>eventsToAppend</code> after this event order, i.e. the first event in the <code>eventsToAppend</code> list
     *                                    will receive {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    Use the value of {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED} in case no eventsToAppend have been persisted for this aggregate instance yet<br>
     *                                    Note: If the <code>appendEventsAfterEventOrder</code> is null then the implementation will call
     *                                    {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @param eventsToAppend              the eventsToAppend to persist
     * @param <ID>                        the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         Long appendEventsAfterEventOrder,
                                                         List<?> eventsToAppend) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.ofNullable(appendEventsAfterEventOrder),
                              eventsToAppend);
    }

    /**
     * Append the <code>eventsToAppend</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 the identifier of the aggregate we want to persist eventsToAppend related to
     * @param appendEventsAfterEventOrder append the <code>eventsToAppend</code> after this event order, i.e. the first event in the <code>eventsToAppend</code> list
     *                                    will receive {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    Use the value of {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED} in case no eventsToAppend have been persisted for this aggregate instance yet<br>
     *                                    Note: If the <code>appendEventsAfterEventOrder</code> is {@link Optional#empty()} then the implementation will call
     *                                    {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @param eventsToAppend              the eventsToAppend to persist
     * @param <ID>                        the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         Optional<Long> appendEventsAfterEventOrder,
                                                         List<?> eventsToAppend) {
        return appendToStream(new AppendToStream<>(aggregateType,
                                                   aggregateId,
                                                   appendEventsAfterEventOrder,
                                                   eventsToAppend));
    }

    /**
     * Append the <code>eventsToAppend</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 the identifier of the aggregate we want to persist eventsToAppend related to
     * @param appendEventsAfterEventOrder append the <code>eventsToAppend</code> after this event order, i.e. the first event in the <code>eventsToAppend</code> list
     *                                    will receive {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    Use the value of {@link EventOrder#NO_EVENTS_PREVIOUSLY_PERSISTED} in case no eventsToAppend have been persisted for this aggregate instance yet<br>
     *                                    Note: If the <code>appendEventsAfterEventOrder</code> is {@link Optional#empty()} then the implementation will call
     *                                    {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @param eventsToAppend              the eventsToAppend to persist
     * @param <ID>                        the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         Optional<Long> appendEventsAfterEventOrder,
                                                         Object... eventsToAppend) {
        return appendToStream(new AppendToStream<>(aggregateType,
                                                   aggregateId,
                                                   appendEventsAfterEventOrder,
                                                   eventsToAppend));
    }

    /**
     * Append the <code>eventsToAppend</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param operation the {@link AppendToStream} operation
     * @param <ID>      the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>eventsToAppend</code>
     */
    <ID> AggregateEventStream<ID> appendToStream(AppendToStream<ID> operation);


    /**
     * Load the last {@link PersistedEvent} in relation to the specified <code>aggregateType</code> and <code>aggregateId</code>
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to find the last {@link PersistedEvent}
     * @param <ID>          the id type for the aggregate
     * @return an {@link Optional} with the last {@link PersistedEvent} related to the <code>aggregateId</code> instance or
     * {@link Optional#empty()} if no events have been persisted before
     */
    default <ID> Optional<PersistedEvent> loadLastPersistedEventRelatedTo(AggregateType aggregateType,
                                                                          ID aggregateId) {
        return loadLastPersistedEventRelatedTo(new LoadLastPersistedEventRelatedTo<>(aggregateType, aggregateId));
    }

    /**
     * Load the last {@link PersistedEvent} in relation to the specified <code>aggregateType</code> and <code>aggregateId</code>
     *
     * @param operation the {@link LoadLastPersistedEventRelatedTo} operation
     * @param <ID>      the id type for the aggregate
     * @return an {@link Optional} with the last {@link PersistedEvent} related to the <code>aggregateId</code> instance or
     * {@link Optional#empty()} if no events have been persisted before
     */
    <ID> Optional<PersistedEvent> loadLastPersistedEventRelatedTo(LoadLastPersistedEventRelatedTo<ID> operation);

    /**
     * Load the event belonging to <code>aggregateType</code> and having the specified <code>eventId</code>
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream}, which should contain the {@link PersistedEvent} with the given <code>eventId</code>, is associated with
     * @param eventId       the identifier of the {@link PersistedEvent}
     * @return an {@link Optional} with the {@link PersistedEvent} or {@link Optional#empty()} if the event couldn't be found
     */
    default Optional<PersistedEvent> loadEvent(AggregateType aggregateType,
                                               EventId eventId) {
        return loadEvent(new LoadEvent(aggregateType, eventId));
    }

    /**
     * Load the event belonging to <code>aggregateType</code> and having the specified <code>eventId</code>
     *
     * @param operation the {@link LoadEvent} operation
     * @return an {@link Optional} with the {@link PersistedEvent} or {@link Optional#empty()} if the event couldn't be found
     */
    Optional<PersistedEvent> loadEvent(LoadEvent operation);

    /**
     * Load the events belonging to <code>aggregateType</code> which have the specified <code>eventId</code>'s
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream}, for which we want to load the events specified by the <code>eventIds</code> parameter
     * @param eventIds       the list of identifiers of the {@link PersistedEvent}'s we want to load
     * @return list of the matching events
     */
    default List<PersistedEvent> loadEvents(AggregateType aggregateType,
                                                List<EventId> eventIds) {
        return loadEvents(new LoadEvents(aggregateType, eventIds));
    }


    /**
     * Load the events belonging to <code>aggregateType</code> which have the specified <code>eventId</code>'s
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream}, for which we want to load the events specified by the <code>eventIds</code> parameter
     * @param eventIds       the identifiers of the {@link PersistedEvent}'s we want to load
     * @return list of the matching events
     */
    default List<PersistedEvent> loadEvents(AggregateType aggregateType,
                                               EventId... eventIds) {
        return loadEvents(new LoadEvents(aggregateType, List.of(eventIds)));
    }

    /**
     * Load the events belonging to <code>aggregateType</code> and having the specified <code>eventId</code>'s
     *
     * @param operation the {@link LoadEvents} operation
     * @return list of the matching events
     */
    List<PersistedEvent> loadEvents(LoadEvents operation);


    /**
     * Check if the {@link EventStore} has an {@link AggregateEventStream} for an Aggregate with type <code>aggregateType</code> and id <code>aggregateId</code><br>
     * Uses {@link #fetchStream(FetchStream)} internally
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param <ID>          the id type for the aggregate
     * @return true if the {@link EventStore} has an {@link AggregateEventStream} for an Aggregate with type <code>aggregateType</code> and id <code>aggregateId</code>,
     * otherwise false
     */
    default <ID> boolean hasEventStream(AggregateType aggregateType,
                                        ID aggregateId) {
        requireNonNull(aggregateType, "No aggregateType is provided");
        requireNonNull(aggregateId, "No aggregateId is provided");

        return fetchStream(FetchStream.builder()
                                      .setAggregateType(aggregateType)
                                      .setAggregateId(aggregateId)
                                      .setEventOrderRange(LongRange.only(EventOrder.FIRST_EVENT_ORDER.value()))
                                      .build())
                .isPresent();
    }

    /**
     * Check if the {@link EventStore} has an {@link AggregateEventStream} for an Aggregate with type <code>aggregateType</code> and id <code>aggregateId</code><br>
     * Uses {@link #fetchStream(FetchStream)} internally
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param tenant        the tenant that the  {@link AggregateEventStream} is belongs to
     * @param <ID>          the id type for the aggregate
     * @return true if the {@link EventStore} has an {@link AggregateEventStream} for an Aggregate with type <code>aggregateType</code> and id <code>aggregateId</code>,
     * otherwise false
     */
    default <ID> boolean hasEventStream(AggregateType aggregateType,
                                        ID aggregateId,
                                        Tenant tenant) {
        requireNonNull(aggregateType, "No aggregateType is provided");
        requireNonNull(aggregateId, "No aggregateId is provided");
        requireNonNull(tenant, "No tenant is provided");

        return fetchStream(FetchStream.builder()
                                      .setAggregateType(aggregateType)
                                      .setAggregateId(aggregateId)
                                      .setEventOrderRange(LongRange.only(EventOrder.FIRST_EVENT_ORDER.value()))
                                      .setTenant(tenant)
                                      .build())
                .isPresent();
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from {@link EventOrder#FIRST_EVENT_ORDER}
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param <ID>          the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId) {
        return fetchStream(aggregateType,
                           aggregateId,
                           LongRange.from(FIRST_EVENT_ORDER.longValue()));
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from the specified <code>eventOrderRange</code>
     *
     * @param aggregateType   the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId     the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     * @param <ID>            the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId,
                                                                LongRange eventOrderRange) {
        return fetchStream(aggregateType,
                           aggregateId,
                           eventOrderRange,
                           Optional.empty());
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * Only {@link PersistedEvent}'s belonging to the specified <code>tenant</code> will be returned<br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from {@link EventOrder#FIRST_EVENT_ORDER}
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param tenant        only return events belonging to the specified tenant
     * @param <ID>          the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId,
                                                                Tenant tenant) {
        return fetchStream(aggregateType,
                           aggregateId,
                           LongRange.from(FIRST_EVENT_ORDER.longValue()),
                           Optional.of(tenant));
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from the specified <code>eventOrderRange</code><br>
     * If the <code>tenant</code> arguments is {@link Optional#isPresent()}, then only {@link PersistedEvent}'s belonging to the specified <code>tenant</code> and matching the specified <code>eventOrderRange</code> will be returned<br>
     * Otherwise all {@link PersistedEvent}'s matching the specified <code>eventOrderRange</code> will be returned
     *
     * @param aggregateType   the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId     the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     * @param tenant          only return events belonging to the specified tenant (if different from null)
     * @param <ID>            the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId,
                                                                LongRange eventOrderRange,
                                                                Tenant tenant) {
        return fetchStream(aggregateType,
                           aggregateId,
                           eventOrderRange,
                           Optional.ofNullable(tenant));
    }


    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from the specified <code>eventOrderRange</code><br>
     * If the <code>tenant</code> arguments is {@link Optional#isPresent()}, then only {@link PersistedEvent}'s belonging to the specified <code>tenant</code> and matching the specified <code>eventOrderRange</code> will be returned<br>
     * Otherwise all {@link PersistedEvent}'s matching the specified <code>eventOrderRange</code> will be returned
     *
     * @param aggregateType   the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId     the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     * @param tenant          only return events belonging to the specified tenant (if {@link Optional#isPresent()})
     * @param <ID>            the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId,
                                                                LongRange eventOrderRange,
                                                                Optional<Tenant> tenant) {
        return fetchStream(new FetchStream<>(aggregateType, aggregateId, eventOrderRange, tenant));
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from the specified <code>eventOrderRange</code><br>
     * If the <code>tenant</code> arguments is {@link Optional#isPresent()}, then only {@link PersistedEvent}'s belonging to the specified <code>tenant</code> and matching the specified <code>eventOrderRange</code> will be returned<br>
     * Otherwise all {@link PersistedEvent}'s matching the specified <code>eventOrderRange</code> will be returned
     *
     * @param operation the {@link FetchStream} operation
     * @param <ID>      the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    <ID> Optional<AggregateEventStream<ID>> fetchStream(FetchStream<ID> operation);

    /**
     * Perform an in memory projection of a given aggregate instances event stream.<br>
     * Note: Unless the {@link InMemoryProjector} explicitly states otherwise, an
     * in memory projection is not automatically associated with a {@link UnitOfWork} and any changes to the projection
     * won't automatically be persisted.<br>
     * A matching {@link InMemoryProjector} must be registered in advance, either using {@link ConfigurableEventStore#addSpecificInMemoryProjector(Class, InMemoryProjector)}
     * or {@link ConfigurableEventStore#addGenericInMemoryProjector(InMemoryProjector)}
     *
     * @param aggregateType  the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId    the identifier for the aggregate instance
     * @param projectionType the type of projection result
     * @param <ID>           the aggregate id type
     * @param <PROJECTION>   the projection type
     * @return an {@link Optional} with the underlying {@link AggregateEventStream} projected using a matching {@link InMemoryProjector}
     * or {@link Optional#empty()} in case there the {@link AggregateEventStream} doesn't exist
     */
    <ID, PROJECTION> Optional<PROJECTION> inMemoryProjection(AggregateType aggregateType,
                                                             ID aggregateId,
                                                             Class<PROJECTION> projectionType);

    /**
     * Perform an in memory projection of a given aggregate instances event stream.<br>
     * Note: Unless the {@link InMemoryProjector} explicitly states otherwise, an
     * in memory projection is not automatically associated with a {@link UnitOfWork} and any changes to the projection
     * won't automatically be persisted.<br>
     *
     * @param aggregateType     the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId       the identifier for the aggregate instance
     * @param projectionType    the type of projection result
     * @param inMemoryProjector the projector to use
     * @param <ID>              the aggregate id type
     * @param <PROJECTION>      the projection type
     * @return an {@link Optional} with the underlying {@link AggregateEventStream} projected using the supplied {@link InMemoryProjector}
     * or {@link Optional#empty()} in case there the {@link AggregateEventStream} doesn't exist
     */
    <ID, PROJECTION> Optional<PROJECTION> inMemoryProjection(AggregateType aggregateType,
                                                             ID aggregateId,
                                                             Class<PROJECTION> projectionType,
                                                             InMemoryProjector inMemoryProjector);

    /**
     * Load all events relating to the provided <code>aggregateType</code> by their {@link PersistedEvent#globalEventOrder()}
     *
     * @param aggregateType         the aggregate type that the underlying events are associated with
     * @param globalEventOrderRange the range of {@link GlobalEventOrder}'s to include in the stream
     * @return a stream of all {@link PersistedEvent}'s matching the <code>aggregateType</code> and the <code>globalEventOrderRange</code>
     */
    default Stream<PersistedEvent> loadEventsByGlobalOrder(AggregateType aggregateType,
                                                           LongRange globalEventOrderRange) {
        return loadEventsByGlobalOrder(aggregateType,
                                       globalEventOrderRange,
                                       null,
                                       Optional.empty());
    }

    /**
     * Load all events, belonging to the specified <code>onlyIncludeEventIfItBelongsToTenant</code> option, and which are relating to the provided <code>aggregateType</code> by their {@link PersistedEvent#globalEventOrder()}
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param globalEventOrderRange               the range of {@link GlobalEventOrder}'s to include in the stream
     * @param includeAdditionalGlobalOrders       a list of additional global orders (typically outside the <code>globalOrderRange</code>) that you want to include additionally<br>
     *                                            May be null or empty if no additional events should be loaded outside the <code>globalOrderRange</code>
     * @param onlyIncludeEventIfItBelongsToTenant if non-null then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @return a stream of all {@link PersistedEvent}'s matching the <code>aggregateType</code> and the <code>globalEventOrderRange</code>
     */
    default Stream<PersistedEvent> loadEventsByGlobalOrder(AggregateType aggregateType,
                                                           LongRange globalEventOrderRange,
                                                           List<GlobalEventOrder> includeAdditionalGlobalOrders,
                                                           Tenant onlyIncludeEventIfItBelongsToTenant) {
        return loadEventsByGlobalOrder(aggregateType,
                                       globalEventOrderRange,
                                       includeAdditionalGlobalOrders,
                                       Optional.ofNullable(onlyIncludeEventIfItBelongsToTenant));
    }

    /**
     * Load all events which are relating to the provided <code>aggregateType</code> by their {@link PersistedEvent#globalEventOrder()}
     *
     * @param aggregateType                 the aggregate type that the underlying events are associated with
     * @param globalEventOrderRange         the range of {@link GlobalEventOrder}'s to include in the stream
     * @param includeAdditionalGlobalOrders a list of additional global orders (typically outside the <code>globalOrderRange</code>) that you want to include additionally<br>
     *                                      May be null or empty if no additional events should be loaded outside the <code>globalOrderRange</code>
     * @return a stream of all {@link PersistedEvent}'s matching the <code>aggregateType</code> and the <code>globalEventOrderRange</code>
     */
    default Stream<PersistedEvent> loadEventsByGlobalOrder(AggregateType aggregateType,
                                                           LongRange globalEventOrderRange,
                                                           List<GlobalEventOrder> includeAdditionalGlobalOrders) {
        return loadEventsByGlobalOrder(aggregateType, globalEventOrderRange, includeAdditionalGlobalOrders, Optional.empty());
    }

    /**
     * Load all events, belonging to the specified <code>onlyIncludeEventIfItBelongsToTenant</code> option, and which are relating to the provided <code>aggregateType</code> by their {@link PersistedEvent#globalEventOrder()}
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param globalEventOrderRange               the range of {@link GlobalEventOrder}'s to include in the stream
     * @param includeAdditionalGlobalOrders       a list of additional global orders (typically outside the <code>globalOrderRange</code>) that you want to include additionally<br>
     *                                            May be null or empty if no additional events should be loaded outside the <code>globalOrderRange</code>
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @return a stream of all {@link PersistedEvent}'s matching the <code>aggregateType</code> and the <code>globalEventOrderRange</code>
     */
    default Stream<PersistedEvent> loadEventsByGlobalOrder(AggregateType aggregateType,
                                                           LongRange globalEventOrderRange,
                                                           List<GlobalEventOrder> includeAdditionalGlobalOrders,
                                                           Optional<Tenant> onlyIncludeEventIfItBelongsToTenant) {
        return loadEventsByGlobalOrder(new LoadEventsByGlobalOrder(aggregateType, globalEventOrderRange, includeAdditionalGlobalOrders, onlyIncludeEventIfItBelongsToTenant));
    }

    /**
     * Load all events, belonging to the specified <code>onlyIncludeEventIfItBelongsToTenant</code> option, and which are relating to the provided <code>aggregateType</code> by their {@link PersistedEvent#globalEventOrder()}
     *
     * @param operation the {@link LoadEventsByGlobalOrder} operation
     * @return a stream of all {@link PersistedEvent}'s matching the <code>aggregateType</code> and the <code>globalEventOrderRange</code>
     */
    Stream<PersistedEvent> loadEventsByGlobalOrder(LoadEventsByGlobalOrder operation);

    /**
     * Asynchronously poll for new events related to the given <code>aggregateType</code><br>
     * The returned Flux support backpressure, e.g. by using {@link Flux#limitRate(int)}
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param fromInclusiveGlobalOrder            the first {@link GlobalEventOrder}'s to include in the returned {@link Flux}
     * @param loadEventsByGlobalOrderBatchSize    how many events should we maximum return from every call to {@link #loadEventsByGlobalOrder(AggregateType, LongRange, List, Optional)}
     *                                            Default value is {@link AggregateEventStreamConfiguration#queryFetchSize} or {@link EventStore#DEFAULT_QUERY_BATCH_SIZE}
     * @param pollingInterval                     how often should the {@link EventStore} be polled for new events. Default value is {@link #DEFAULT_POLLING_INTERVAL_MILLISECONDS}
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param subscriptionId                      unique subscriber id which is used for creating a unique logger name. If {@link Optional#empty()} then a UUID value is generated and used
     * @return a {@link Flux} that asynchronously will publish events associated with the provided <code>aggregateType</code>
     */
    default Flux<PersistedEvent> pollEvents(AggregateType aggregateType,
                                            GlobalEventOrder fromInclusiveGlobalOrder,
                                            Optional<Integer> loadEventsByGlobalOrderBatchSize,
                                            Optional<Duration> pollingInterval,
                                            Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                            Optional<SubscriberId> subscriptionId) {
        requireNonNull(fromInclusiveGlobalOrder, "No fromInclusiveGlobalOrder value provided");
        return pollEvents(aggregateType,
                          fromInclusiveGlobalOrder.longValue(),
                          loadEventsByGlobalOrderBatchSize,
                          pollingInterval,
                          onlyIncludeEventIfItBelongsToTenant,
                          subscriptionId);
    }

    /**
     * Asynchronously poll for new events related to the given <code>aggregateType</code> using default values - see {@link #pollEvents(AggregateType, GlobalEventOrder, Optional, Optional, Optional, Optional)}<br>
     * The returned Flux support backpressure, e.g. by using {@link Flux#limitRate(int)}
     *
     * @param aggregateType            the aggregate type that the underlying events are associated with
     * @param fromInclusiveGlobalOrder the first {@link GlobalEventOrder}'s to include in the returned {@link Flux}
     * @return a {@link Flux} that asynchronously will publish events associated with the provided <code>aggregateType</code>
     */
    default Flux<PersistedEvent> pollEvents(AggregateType aggregateType,
                                            GlobalEventOrder fromInclusiveGlobalOrder) {
        requireNonNull(fromInclusiveGlobalOrder, "No fromInclusiveGlobalOrder value provided");
        return pollEvents(aggregateType,
                          fromInclusiveGlobalOrder.longValue(),
                          Optional.empty(),
                          Optional.empty(),
                          Optional.empty(),
                          Optional.empty());
    }

    /**
     * Asynchronously poll for new events related to the given <code>aggregateType</code><br>
     * The returned Flux support backpressure, e.g. by using {@link Flux#limitRate(int)}
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param fromInclusiveGlobalOrder            the first {@link GlobalEventOrder}'s to include in the returned {@link Flux}
     * @param loadEventsByGlobalOrderBatchSize    how many events should we maximum return from every call to {@link #loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}
     *                                            Default value is {@link AggregateEventStreamConfiguration#queryFetchSize} or {@link EventStore#DEFAULT_QUERY_BATCH_SIZE}
     * @param pollingInterval                     how often should the {@link EventStore} be polled for new events. Default value is {@link #DEFAULT_POLLING_INTERVAL_MILLISECONDS}
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param subscriptionId                      unique subscriber id which is used for creating a unique logger name. If {@link Optional#empty()} then a UUID value is generated and used
     * @return a {@link Flux} that asynchronously will publish events associated with the provided <code>aggregateType</code>
     */
    Flux<PersistedEvent> pollEvents(AggregateType aggregateType,
                                    long fromInclusiveGlobalOrder,
                                    Optional<Integer> loadEventsByGlobalOrderBatchSize,
                                    Optional<Duration> pollingInterval,
                                    Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                    Optional<SubscriberId> subscriptionId);

    /**
     * Asynchronously poll for new events related to the given <code>aggregateType</code><br>
     * The returned Flux does NOT support backpressure
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param fromInclusiveGlobalOrder            the first {@link GlobalEventOrder}'s to include in the returned {@link Flux}
     * @param loadEventsByGlobalOrderBatchSize    how many events should we maximum return from every call to {@link #loadEventsByGlobalOrder(AggregateType, LongRange, List, Optional)}
     *                                            Default value is {@link AggregateEventStreamConfiguration#queryFetchSize} or {@link EventStore#DEFAULT_QUERY_BATCH_SIZE}
     * @param pollingInterval                     how often should the {@link EventStore} be polled for new events. Default value is {@link #DEFAULT_POLLING_INTERVAL_MILLISECONDS}
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param subscriptionId                      unique subscriber id which is used for creating a unique logger name. If {@link Optional#empty()} then a UUID value is generated and used
     * @return a {@link Flux} that asynchronously will publish events associated with the provided <code>aggregateType</code>
     */
    default Flux<PersistedEvent> unboundedPollForEvents(AggregateType aggregateType,
                                                        GlobalEventOrder fromInclusiveGlobalOrder,
                                                        Optional<Integer> loadEventsByGlobalOrderBatchSize,
                                                        Optional<Duration> pollingInterval,
                                                        Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                                        Optional<SubscriberId> subscriptionId) {
        requireNonNull(fromInclusiveGlobalOrder, "No fromInclusiveGlobalOrder value provided");
        return unboundedPollForEvents(aggregateType,
                                      fromInclusiveGlobalOrder.longValue(),
                                      loadEventsByGlobalOrderBatchSize,
                                      pollingInterval,
                                      onlyIncludeEventIfItBelongsToTenant,
                                      subscriptionId);
    }

    /**
     * Asynchronously poll for new events related to the given <code>aggregateType</code><br>
     * The returned Flux does NOT support backpressure
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param fromInclusiveGlobalOrder            the first {@link GlobalEventOrder}'s to include in the returned {@link Flux}
     * @param loadEventsByGlobalOrderBatchSize    how many events should we maximum return from every call to {@link #loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}
     *                                            Default value is {@link AggregateEventStreamConfiguration#queryFetchSize} or {@link EventStore#DEFAULT_QUERY_BATCH_SIZE}
     * @param pollingInterval                     how often should the {@link EventStore} be polled for new events. Default value is {@link #DEFAULT_POLLING_INTERVAL_MILLISECONDS}
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param subscriptionId                      unique subscriber id which is used for creating a unique logger name. If {@link Optional#empty()} then a UUID value is generated and used
     * @return a {@link Flux} that asynchronously will publish events associated with the provided <code>aggregateType</code>
     */
    Flux<PersistedEvent> unboundedPollForEvents(AggregateType aggregateType,
                                                long fromInclusiveGlobalOrder,
                                                Optional<Integer> loadEventsByGlobalOrderBatchSize,
                                                Optional<Duration> pollingInterval,
                                                Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                                Optional<SubscriberId> subscriptionId);

    /**
     * Find the highest {@link GlobalEventOrder} persisted in relation to the given aggregateType
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @return an {@link Optional} with the {@link GlobalEventOrder} persisted or {@link Optional#empty()} if no events have been persisted
     */
    Optional<GlobalEventOrder> findHighestGlobalEventOrderPersisted(AggregateType aggregateType);
}
