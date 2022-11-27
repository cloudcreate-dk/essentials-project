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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.EventStoreInterceptor;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.reactive.LocalEventBus;
import dk.cloudcreate.essentials.types.LongRange;
import org.jdbi.v3.core.ConnectionException;
import org.slf4j.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import java.util.stream.*;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.EventStoreInterceptorChain.newInterceptorChainForOperation;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Postgresql specific {@link EventStore} implementation
 *
 * @param <CONFIG> The concrete {@link AggregateEventStreamConfiguration
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class PostgresqlEventStore<CONFIG extends AggregateEventStreamConfiguration> implements ConfigurableEventStore<CONFIG> {
    private static final Logger       log              = LoggerFactory.getLogger(PostgresqlEventStore.class);
    private static final SubscriberId NO_SUBSCRIBER_ID = SubscriberId.of("NoSubscriberId");

    private final EventStoreUnitOfWorkFactory<EventStoreUnitOfWork> unitOfWorkFactory;
    private final AggregateEventStreamPersistenceStrategy<CONFIG>   persistenceStrategy;


    /**
     * Cache of specific a {@link InMemoryProjector} instance that support rehydrating/projecting a specific projection/aggregate type<br>
     * Key: Projection/Aggregate type<br>
     * Value: The specific {@link InMemoryProjector} that supports the given projection type (if provided to {@link #addSpecificInMemoryProjector(Class, InMemoryProjector)})
     * or the first {@link InMemoryProjector#supports(Class)} that reports true for the given projection type
     */
    private final ConcurrentMap<Class<?>, InMemoryProjector> inMemoryProjectorPerProjectionType;
    private final HashSet<InMemoryProjector>                 inMemoryProjectors;
    private final List<EventStoreInterceptor>                eventStoreInterceptors;
    private final EventStoreLocalEventBus                    eventStoreLocalEventBus;
    private final EventStreamGapHandler<CONFIG>              eventStreamGapHandler;

    /**
     * Create a {@link PostgresqlEventStore} without EventStreamGapHandler (specifically with {@link NoEventStreamGapHandler}) as a backwards compatible configuration
     *
     * @param unitOfWorkFactory                       the unit of work factory
     * @param aggregateEventStreamPersistenceStrategy the persistence strategy
     * @param <STRATEGY>                              the persistence strategy type
     */
    public <STRATEGY extends AggregateEventStreamPersistenceStrategy<CONFIG>> PostgresqlEventStore(EventStoreUnitOfWorkFactory unitOfWorkFactory,
                                                                                                   STRATEGY aggregateEventStreamPersistenceStrategy) {
        this(unitOfWorkFactory,
             aggregateEventStreamPersistenceStrategy,
             Optional.empty(),
             eventStore -> new NoEventStreamGapHandler<>());
    }


    /**
     * Create a {@link PostgresqlEventStore} with EventStreamGapHandler (specifically with {@link PostgresqlEventStreamGapHandler})
     *
     * @param unitOfWorkFactory                       the unit of work factory
     * @param aggregateEventStreamPersistenceStrategy the persistence strategy
     * @param eventStoreLocalEventBusOption           option that contains {@link EventStoreLocalEventBus} to use. If empty a new {@link EventStoreLocalEventBus} instance will be used
     * @param eventStreamGapHandlerFactory            the {@link EventStreamGapHandler} to use for tracking event stream gaps
     * @param <STRATEGY>                              the persistence strategy type
     */
    public <STRATEGY extends AggregateEventStreamPersistenceStrategy<CONFIG>> PostgresqlEventStore(EventStoreUnitOfWorkFactory unitOfWorkFactory,
                                                                                                   STRATEGY aggregateEventStreamPersistenceStrategy,
                                                                                                   Optional<EventStoreLocalEventBus> eventStoreLocalEventBusOption,
                                                                                                   Function<PostgresqlEventStore<CONFIG>, EventStreamGapHandler<CONFIG>> eventStreamGapHandlerFactory) {
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory provided");
        this.persistenceStrategy = requireNonNull(aggregateEventStreamPersistenceStrategy, "No eventStreamPersistenceStrategy provided");
        requireNonNull(eventStoreLocalEventBusOption, "No eventStoreLocalEventBus option provided");
        requireNonNull(eventStreamGapHandlerFactory, "No eventStreamGapHandlerFactory provided");
        this.eventStoreLocalEventBus = eventStoreLocalEventBusOption.orElseGet(() -> new EventStoreLocalEventBus(unitOfWorkFactory));
        this.eventStreamGapHandler = eventStreamGapHandlerFactory.apply(this);

        eventStoreInterceptors = new ArrayList<>();
        inMemoryProjectors = new HashSet<>();
        inMemoryProjectorPerProjectionType = new ConcurrentHashMap<>();
    }

    /**
     * Create a {@link PostgresqlEventStore} without EventStreamGapHandler (specifically with {@link NoEventStreamGapHandler})<br>
     * Same as calling {@link #PostgresqlEventStore(EventStoreUnitOfWorkFactory, AggregateEventStreamPersistenceStrategy)}
     *
     * @param unitOfWorkFactory                       the unit of work factory
     * @param aggregateEventStreamPersistenceStrategy the persistence strategy
     * @param <CONFIG>                                The concrete {@link AggregateEventStreamConfiguration
     * @param <STRATEGY>                              the persistence strategy type
     * @return new {@link PostgresqlEventStore} instance
     */
    public static <CONFIG extends AggregateEventStreamConfiguration, STRATEGY extends AggregateEventStreamPersistenceStrategy<CONFIG>> PostgresqlEventStore withoutGapHandling(EventStoreUnitOfWorkFactory unitOfWorkFactory,
                                                                                                                                                                               STRATEGY aggregateEventStreamPersistenceStrategy) {
        return new PostgresqlEventStore<>(unitOfWorkFactory,
                                          aggregateEventStreamPersistenceStrategy,
                                          Optional.empty(),
                                          eventStore -> new NoEventStreamGapHandler<>());
    }

    /**
     * Create a {@link PostgresqlEventStore} with EventStreamGapHandler (specifically with {@link PostgresqlEventStreamGapHandler})<br>
     * Same as calling {@link #PostgresqlEventStore(EventStoreUnitOfWorkFactory, AggregateEventStreamPersistenceStrategy, Optional, Function)} with an empty {@link EventStoreLocalEventBus} {@link Optional}
     *
     * @param unitOfWorkFactory                       the unit of work factory
     * @param aggregateEventStreamPersistenceStrategy the persistence strategy
     * @param <CONFIG>                                The concrete {@link AggregateEventStreamConfiguration
     * @param <STRATEGY>                              the persistence strategy type
     * @return new {@link PostgresqlEventStore} instance
     */
    public static <CONFIG extends AggregateEventStreamConfiguration, STRATEGY extends AggregateEventStreamPersistenceStrategy<CONFIG>> PostgresqlEventStore withGapHandling(EventStoreUnitOfWorkFactory unitOfWorkFactory,
                                                                                                                                                                            STRATEGY aggregateEventStreamPersistenceStrategy) {
        return new PostgresqlEventStore<>(unitOfWorkFactory,
                                          aggregateEventStreamPersistenceStrategy,
                                          Optional.empty(),
                                          eventStore -> new PostgresqlEventStreamGapHandler<>(eventStore, unitOfWorkFactory));
    }

    public AggregateEventStreamPersistenceStrategy<CONFIG> getPersistenceStrategy() {
        return persistenceStrategy;
    }

    public EventStreamGapHandler<CONFIG> getEventStreamGapHandler() {
        return eventStreamGapHandler;
    }

    @Override
    public LocalEventBus<PersistedEvents> localEventBus() {
        return eventStoreLocalEventBus.localEventBus();
    }

    @Override
    public ConfigurableEventStore<CONFIG> addGenericInMemoryProjector(InMemoryProjector inMemoryProjector) {
        inMemoryProjectors.add(requireNonNull(inMemoryProjector, "No inMemoryProjection"));
        return this;
    }


    @Override
    public ConfigurableEventStore<CONFIG> addSpecificInMemoryProjector(Class<?> projectionType,
                                                                       InMemoryProjector inMemoryProjector) {
        inMemoryProjectorPerProjectionType.put(requireNonNull(projectionType, "No projectionType provided"),
                                               requireNonNull(inMemoryProjector, "No inMemoryProjection"));
        return this;
    }

    @Override
    public ConfigurableEventStore<CONFIG> addEventStoreInterceptor(EventStoreInterceptor eventStoreInterceptor) {
        this.eventStoreInterceptors.add(requireNonNull(eventStoreInterceptor, "No eventStoreInterceptor provided"));
        return this;
    }

    @Override
    public <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                        ID aggregateId,
                                                        Optional<Long> appendEventsAfterEventOrder,
                                                        List<?> events) {
        var unitOfWork = unitOfWorkFactory.getRequiredUnitOfWork();

        var operation = new EventStoreInterceptor.AppendToStream<>(aggregateType,
                                                                   aggregateId,
                                                                   appendEventsAfterEventOrder,
                                                                   events);
        var aggregateEventStream = newInterceptorChainForOperation(operation,
                                                                   eventStoreInterceptors,
                                                                   (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                                                   () -> persistenceStrategy.persist(unitOfWork,
                                                                                                     aggregateType,
                                                                                                     aggregateId,
                                                                                                     appendEventsAfterEventOrder,
                                                                                                     events))
                .proceed();
        unitOfWork.registerEventsPersisted(aggregateEventStream.eventList());
        return aggregateEventStream;
    }


    @Override
    public <ID> Optional<PersistedEvent> loadLastPersistedEventRelatedTo(AggregateType aggregateType,
                                                                         ID aggregateId) {
        var operation = new EventStoreInterceptor.LoadLastPersistedEventRelatedTo<>(aggregateType,
                                                                                    aggregateId);
        return newInterceptorChainForOperation(operation,
                                               eventStoreInterceptors,
                                               (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                               () -> persistenceStrategy.loadLastPersistedEventRelatedTo(unitOfWorkFactory.getRequiredUnitOfWork(),
                                                                                                         aggregateType,
                                                                                                         aggregateId))
                .proceed();

    }

    @Override
    public Optional<PersistedEvent> loadEvent(AggregateType aggregateType,
                                              EventId eventId) {
        var operation = new EventStoreInterceptor.LoadEvent(aggregateType,
                                                            eventId);
        return newInterceptorChainForOperation(operation,
                                               eventStoreInterceptors,
                                               (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                               () -> persistenceStrategy.loadEvent(unitOfWorkFactory.getRequiredUnitOfWork(),
                                                                                   aggregateType,
                                                                                   eventId))
                .proceed();
    }

    @Override
    public <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                               ID aggregateId,
                                                               LongRange eventOrderRange,
                                                               Optional<Tenant> tenant) {
        var operation = new EventStoreInterceptor.FetchStream<>(aggregateType,
                                                                aggregateId,
                                                                eventOrderRange,
                                                                tenant);
        return newInterceptorChainForOperation(operation,
                                               eventStoreInterceptors,
                                               (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                               () -> persistenceStrategy.loadAggregateEvents(unitOfWorkFactory.getRequiredUnitOfWork(),
                                                                                             aggregateType,
                                                                                             aggregateId,
                                                                                             eventOrderRange,
                                                                                             tenant))
                .proceed();
    }

    @Override
    public <ID, AGGREGATE> Optional<AGGREGATE> inMemoryProjection(AggregateType aggregateType,
                                                                  ID aggregateId,
                                                                  Class<AGGREGATE> projectionType) {
        requireNonNull(projectionType, "No projectionType provided");
        var inMemoryProjector = inMemoryProjectorPerProjectionType.computeIfAbsent(projectionType, _aggregateType -> inMemoryProjectors.stream().filter(_inMemoryProjection -> _inMemoryProjection.supports(projectionType))
                                                                                                                                       .findFirst()
                                                                                                                                       .orElseThrow(() -> new EventStoreException(msg("Couldn't find an {} that supports projection-type '{}'",
                                                                                                                                                                                      InMemoryProjector.class.getSimpleName(),
                                                                                                                                                                                      projectionType.getName()))));
        return inMemoryProjection(aggregateType,
                                  aggregateId,
                                  projectionType,
                                  inMemoryProjector);
    }

    @Override
    public <ID, AGGREGATE> Optional<AGGREGATE> inMemoryProjection(AggregateType aggregateType,
                                                                  ID aggregateId,
                                                                  Class<AGGREGATE> projectionType,
                                                                  InMemoryProjector inMemoryProjector) {
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(aggregateId, "No aggregateId provided");
        requireNonNull(projectionType, "No projectionType provided");
        requireNonNull(inMemoryProjector, "No inMemoryProjector provided");

        if (!inMemoryProjector.supports(projectionType)) {
            throw new IllegalArgumentException(msg("The provided {} '{}' does not support projection type '{}'",
                                                   InMemoryProjector.class.getName(),
                                                   inMemoryProjector.getClass().getName(),
                                                   projectionType.getName()));
        }
        return inMemoryProjector.projectEvents(aggregateType,
                                               aggregateId,
                                               projectionType,
                                               this);
    }

    @Override
    public Stream<PersistedEvent> loadEventsByGlobalOrder(AggregateType aggregateType,
                                                          LongRange globalEventOrderRange,
                                                          List<GlobalEventOrder> includeAdditionalGlobalOrders,
                                                          Optional<Tenant> onlyIncludeEventIfItBelongsToTenant) {
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(globalEventOrderRange, "You must specify a globalOrderRange");
        requireNonNull(onlyIncludeEventIfItBelongsToTenant, "You must specify an onlyIncludeEventIfItBelongsToTenant option");

        var operation = new EventStoreInterceptor.LoadEventsByGlobalOrder(aggregateType,
                                                                          globalEventOrderRange,
                                                                          onlyIncludeEventIfItBelongsToTenant);
        return newInterceptorChainForOperation(operation,
                                               eventStoreInterceptors,
                                               (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                               () -> persistenceStrategy.loadEventsByGlobalOrder(unitOfWorkFactory.getRequiredUnitOfWork(),
                                                                                                 aggregateType,
                                                                                                 globalEventOrderRange,
                                                                                                 includeAdditionalGlobalOrders,
                                                                                                 onlyIncludeEventIfItBelongsToTenant))
                .proceed();
    }

    @Override
    public Flux<PersistedEvent> pollEvents(AggregateType aggregateType,
                                           long fromInclusiveGlobalOrder,
                                           Optional<Integer> loadEventsByGlobalOrderBatchSize,
                                           Optional<Duration> pollingInterval,
                                           Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                           Optional<SubscriberId> subscriberId) {
        requireNonNull(aggregateType, "You must supply an aggregateType");
        requireNonNull(pollingInterval, "You must supply a pollingInterval option");
        requireNonNull(onlyIncludeEventIfItBelongsToTenant, "You must supply a onlyIncludeEventIfItBelongsToTenant option");
        requireNonNull(subscriberId, "You must supply a subscriberId option");

        var eventStreamLogName  = "EventStream:" + aggregateType + ":" + subscriberId.orElseGet(SubscriberId::random);
        var eventStoreStreamLog = LoggerFactory.getLogger(EventStore.class.getName() + ".PollingEventStream");

        long batchFetchSize = loadEventsByGlobalOrderBatchSize.orElse(DEFAULT_QUERY_BATCH_SIZE);
        eventStoreStreamLog.debug("[{}] Creating polling reactive '{}' EventStream with fromInclusiveGlobalOrder {} and batch size {}",
                                  eventStreamLogName,
                                  aggregateType,
                                  fromInclusiveGlobalOrder,
                                  batchFetchSize);
        var consecutiveNoPersistedEventsReturned = new AtomicInteger(0);
        var lastBatchSizeForThisQuery            = new AtomicLong(batchFetchSize);
        var nextFromInclusiveGlobalOrder         = new AtomicLong(fromInclusiveGlobalOrder);
        var subscriptionGapHandler               = subscriberId.map(eventStreamGapHandler::gapHandlerFor);
        var persistedEventsFlux = Flux.defer(() -> {
            EventStoreUnitOfWork unitOfWork;
            try {
                unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
            } catch (ConnectionException e) {
                eventStoreStreamLog.debug(msg("[{}] Experienced a Postgresql Connection issue, will return an empty Flux",
                                              eventStreamLogName), e);
                return Flux.empty();
            }

            try {
                long batchSizeForThisQuery = resolveBatchSizeForThisQuery(aggregateType, eventStreamLogName, eventStoreStreamLog, lastBatchSizeForThisQuery.get(), batchFetchSize, consecutiveNoPersistedEventsReturned,
                                                                          nextFromInclusiveGlobalOrder, unitOfWork);
                if (batchSizeForThisQuery == 0) {
                    consecutiveNoPersistedEventsReturned.set(0);
                    lastBatchSizeForThisQuery.set(batchFetchSize);

                    eventStoreStreamLog.debug("[{}] Skipping polling as no new events have been persisted since last poll",
                                              eventStreamLogName);
                    return Flux.empty();
                } else {
                    lastBatchSizeForThisQuery.set(batchSizeForThisQuery);
                }

                var globalOrderRange = LongRange.from(nextFromInclusiveGlobalOrder.get(), batchSizeForThisQuery);
                var transientGapsToIncludeInQuery = subscriptionGapHandler.map(gapHandler -> gapHandler.findTransientGapsToIncludeInQuery(aggregateType, globalOrderRange))
                                                                          .orElse(null);
                var persistedEvents = loadEventsByGlobalOrder(aggregateType,
                                                              globalOrderRange,
                                                              transientGapsToIncludeInQuery,
                                                              onlyIncludeEventIfItBelongsToTenant).collect(Collectors.toList());
                subscriptionGapHandler.ifPresent(gapHandler -> gapHandler.reconcileGaps(aggregateType,
                                                                                        globalOrderRange,
                                                                                        persistedEvents,
                                                                                        transientGapsToIncludeInQuery));
                unitOfWork.commit();
                if (persistedEvents.size() > 0) {
                    consecutiveNoPersistedEventsReturned.set(0);
                    if (log.isTraceEnabled()) {
                        eventStoreStreamLog.debug("[{}] loadEventsByGlobalOrder using globalOrderRange {} and transientGapsToIncludeInQuery {} returned {} events: {}",
                                                  eventStreamLogName,
                                                  globalOrderRange,
                                                  transientGapsToIncludeInQuery,
                                                  persistedEvents.size(),
                                                  persistedEvents.stream().map(PersistedEvent::globalEventOrder).collect(Collectors.toList()));
                    } else {
                        eventStoreStreamLog.debug("[{}] loadEventsByGlobalOrder using globalOrderRange {} and transientGapsToIncludeInQuery {} returned {} events",
                                                  eventStreamLogName,
                                                  globalOrderRange,
                                                  transientGapsToIncludeInQuery,
                                                  persistedEvents.size());
                    }
                } else {
                    consecutiveNoPersistedEventsReturned.incrementAndGet();
                    eventStoreStreamLog.trace("[{}] loadEventsByGlobalOrder using globalOrderRange {} and transientGapsToIncludeInQuery {} returned no events",
                                              eventStreamLogName,
                                              globalOrderRange,
                                              transientGapsToIncludeInQuery);
                }

                return Flux.fromIterable(persistedEvents);
            } catch (RuntimeException e) {
                log.error(msg("[{}] Polling failed", eventStreamLogName), e);
                if (unitOfWork != null) {
                    try {
                        unitOfWork.rollback(e);
                    } catch (Exception rollbackException) {
                        log.error(msg("[{}] Failed to rollback unit of work", eventStreamLogName), rollbackException);
                    }
                }
                eventStoreStreamLog.error(msg("[{}] Returning Error for '{}' EventStream with nextFromInclusiveGlobalOrder {}",
                                              eventStreamLogName,
                                              aggregateType,
                                              nextFromInclusiveGlobalOrder.get()),
                                          e);
                return Flux.error(e);
            }
        }).doOnNext(event -> {
            final long nextGlobalOrder = event.globalEventOrder().longValue() + 1L;
            eventStoreStreamLog.trace("[{}] Updating nextFromInclusiveGlobalOrder from {} to {}",
                                      eventStreamLogName,
                                      nextFromInclusiveGlobalOrder.get(),
                                      nextGlobalOrder);
            nextFromInclusiveGlobalOrder.set(nextGlobalOrder);
        }).doOnError(throwable -> {
            eventStoreStreamLog.error(msg("[{}] Failed: {}",
                                          eventStreamLogName,
                                          throwable.getMessage()),
                                      throwable);
        });

        return persistedEventsFlux
                .repeatWhen(longFlux -> Flux.interval(pollingInterval.orElse(Duration.ofMillis(DEFAULT_POLLING_INTERVAL_MILLISECONDS)))
                                            .onBackpressureDrop()
                                            .publishOn(Schedulers.newSingle("Publish-" + subscriberId.orElse(NO_SUBSCRIBER_ID) + "-" + aggregateType, true)));
    }

    protected long resolveBatchSizeForThisQuery(AggregateType aggregateType,
                                                String eventStreamLogName,
                                                Logger eventStoreStreamLog,
                                                long lastBatchSizeForThisQuery,
                                                long defaultBatchFetchSize,
                                                AtomicInteger consecutiveNoPersistedEventsReturned,
                                                AtomicLong nextFromInclusiveGlobalOrder,
                                                EventStoreUnitOfWork unitOfWork) {
        var batchSizeForThisQuery                       = lastBatchSizeForThisQuery;
        var currentConsecutiveNoPersistedEventsReturned = consecutiveNoPersistedEventsReturned.get();
        if (currentConsecutiveNoPersistedEventsReturned > 0 && currentConsecutiveNoPersistedEventsReturned % 100 == 0) {
            var highestPersistedGlobalEventOrder = persistenceStrategy.findHighestGlobalEventOrderPersisted(unitOfWork, aggregateType);
            if (highestPersistedGlobalEventOrder.isPresent()) {
                if (highestPersistedGlobalEventOrder.get().longValue() == nextFromInclusiveGlobalOrder.get() - 1) {
                    eventStoreStreamLog.debug("[{}] loadEventsByGlobalOrder RESETTING query batchSize back to default {} since highestPersistedGlobalEventOrder {} is the same as nextFromInclusiveGlobalOrder {} - 1",
                                              eventStreamLogName,
                                              defaultBatchFetchSize,
                                              highestPersistedGlobalEventOrder.get(),
                                              nextFromInclusiveGlobalOrder.get());
                    batchSizeForThisQuery = 0;
                } else {
                    batchSizeForThisQuery = highestPersistedGlobalEventOrder.map(highestGlobalEventOrder -> highestGlobalEventOrder.longValue() - nextFromInclusiveGlobalOrder.get() - 1 + defaultBatchFetchSize)
                                                                            .orElse(defaultBatchFetchSize);
                    if (batchSizeForThisQuery > defaultBatchFetchSize) {
                        eventStoreStreamLog.debug("[{}] loadEventsByGlobalOrder temporarily INCREASED query batchSize to {} from {} instead of default {} since highestPersistedGlobalEventOrder is {}",
                                                  eventStreamLogName,
                                                  batchSizeForThisQuery,
                                                  lastBatchSizeForThisQuery,
                                                  defaultBatchFetchSize,
                                                  highestPersistedGlobalEventOrder.get());
                    }
                }
            }
        } else if (currentConsecutiveNoPersistedEventsReturned > 0 && currentConsecutiveNoPersistedEventsReturned % 10 == 0) {
            batchSizeForThisQuery = (long) (batchSizeForThisQuery + defaultBatchFetchSize * (currentConsecutiveNoPersistedEventsReturned / 10) * 0.3f);
            if (batchSizeForThisQuery > defaultBatchFetchSize) {
                eventStoreStreamLog.debug("[{}] loadEventsByGlobalOrder temporarily INCREASED query batchSize to {} from {} instead of default {} since number of consecutiveNoPersistedEventsReturned was {}",
                                          eventStreamLogName,
                                          batchSizeForThisQuery,
                                          lastBatchSizeForThisQuery,
                                          defaultBatchFetchSize,
                                          currentConsecutiveNoPersistedEventsReturned);
            }
        } else if (currentConsecutiveNoPersistedEventsReturned == 0) {
            if (batchSizeForThisQuery != defaultBatchFetchSize) {
                eventStoreStreamLog.debug("[{}] loadEventsByGlobalOrder RESETTING query batchSize back to default {} from {} as new events have been received",
                                          eventStreamLogName,
                                          defaultBatchFetchSize,
                                          batchSizeForThisQuery);

                batchSizeForThisQuery = defaultBatchFetchSize;
            }
        }
        return batchSizeForThisQuery;
    }

    @Override
    public EventStoreUnitOfWorkFactory getUnitOfWorkFactory() {
        return unitOfWorkFactory;
    }

    @Override
    public ConfigurableEventStore<CONFIG> addAggregateEventStreamConfiguration(CONFIG aggregateTypeConfiguration) {
        persistenceStrategy.addAggregateEventStreamConfiguration(aggregateTypeConfiguration);
        return this;
    }

    @Override
    public ConfigurableEventStore<CONFIG> addAggregateEventStreamConfiguration(AggregateType aggregateType, AggregateIdSerializer aggregateIdSerializer) {
        persistenceStrategy.addAggregateEventStreamConfiguration(aggregateType, aggregateIdSerializer);
        return this;
    }

    @Override
    public ConfigurableEventStore<CONFIG> addAggregateEventStreamConfiguration(AggregateType aggregateType, Class<?> aggregateIdType) {
        persistenceStrategy.addAggregateEventStreamConfiguration(aggregateType, aggregateIdType);
        return this;
    }

    @Override
    public Optional<CONFIG> findAggregateEventStreamConfiguration(AggregateType aggregateType) {
        return persistenceStrategy.findAggregateEventStreamConfiguration(aggregateType);
    }

    @Override
    public CONFIG getAggregateEventStreamConfiguration(AggregateType aggregateType) {
        return persistenceStrategy.getAggregateEventStreamConfiguration(aggregateType);
    }
}
