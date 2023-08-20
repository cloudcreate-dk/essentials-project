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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.micrometer;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.PersistableEventEnricher;
import io.micrometer.observation.*;
import io.micrometer.observation.transport.*;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.Optional;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class MicrometerTracingEventStoreInterceptor implements EventStoreInterceptor, PersistableEventEnricher {
    public static final String                         AGGREGATE_TYPE         = "AggregateType";
    public static final String                         AGGREGATE_ID           = "AggregateId";
    public static final String                         EVENT_TYPE             = "EventType";
    public static final String                         EVENT_ID               = "EventId";
    private final       Tracer                         tracer;
    private final       Propagator                     propagator;
    private final       ObservationRegistry            observationRegistry;
    private final       boolean                        verboseTracing;
    private final       ThreadLocal<Observation.Scope> activeObservationScope = new ThreadLocal<>();

    /**
     * @param tracer              The micrometer {@link Tracer}
     * @param propagator          The micrometer {@link Propagator}
     * @param observationRegistry The micrometer {@link ObservationRegistry}
     * @param verboseTracing      Should the Tracing produces only include all operations or only top level operations
     */
    public MicrometerTracingEventStoreInterceptor(Tracer tracer,
                                                  Propagator propagator,
                                                  ObservationRegistry observationRegistry,
                                                  boolean verboseTracing) {
        this.tracer = requireNonNull(tracer, "No tracer instance provided");
        this.propagator = requireNonNull(propagator, "No propagator instance provided");
        this.observationRegistry = requireNonNull(observationRegistry, "No observationRegistry instance provided");
        this.verboseTracing = verboseTracing;
    }

    @Override
    public PersistableEvent enrich(PersistableEvent event) {
        storeTraceContext(event.metaData());
        return event;
    }

    @Override
    public <ID> AggregateEventStream<ID> intercept(AppendToStream<ID> operation, EventStoreInterceptorChain<AppendToStream<ID>, AggregateEventStream<ID>> eventStoreInterceptorChain) {
        var observation = Observation.createNotStarted("PersistEvents:" + operation.aggregateType.toString(), observationRegistry)
                                     .lowCardinalityKeyValue(AGGREGATE_TYPE, operation.aggregateType.toString())
                                     .highCardinalityKeyValue(AGGREGATE_ID, operation.aggregateId.toString())
                                     .highCardinalityKeyValue("EventCount", Integer.toString(operation.getEventsToAppend().size()));
        return observation.observe(eventStoreInterceptorChain::proceed);
    }

    @Override
    public <ID> Optional<PersistedEvent> intercept(LoadLastPersistedEventRelatedTo<ID> operation,
                                                   EventStoreInterceptorChain<LoadLastPersistedEventRelatedTo<ID>, Optional<PersistedEvent>> eventStoreInterceptorChain) {
        if (verboseTracing) {
            var potentialEvent = Observation.createNotStarted("LoadLastPersistedEventRelatedTo:" + operation.aggregateType.toString(), observationRegistry)
                                            .lowCardinalityKeyValue(AGGREGATE_TYPE, operation.aggregateType.toString())
                                            .highCardinalityKeyValue(AGGREGATE_ID, operation.aggregateId.toString())
                                            .observe(eventStoreInterceptorChain::proceed);

//            return potentialEvent.map(event -> restoreTraceContext(event, "LoadLastPersistedEventRelatedTo"));
            return potentialEvent;
        }
        return eventStoreInterceptorChain.proceed();
    }

    @Override
    public Optional<PersistedEvent> intercept(LoadEvent operation, EventStoreInterceptorChain<LoadEvent, Optional<PersistedEvent>> eventStoreInterceptorChain) {
        if (verboseTracing) {
            var potentialEvent = Observation.createNotStarted("LoadEvent:" + operation.aggregateType.toString(), observationRegistry)
                                            .lowCardinalityKeyValue(AGGREGATE_TYPE, operation.aggregateType.toString())
                                            .highCardinalityKeyValue(EVENT_ID, operation.eventId.toString())
                                            .observe(eventStoreInterceptorChain::proceed);

//            return potentialEvent.map(event -> restoreTraceContext(event, "LoadEvent"));
            return potentialEvent;
        }
        return eventStoreInterceptorChain.proceed();
    }

    @Override
    public <ID> Optional<AggregateEventStream<ID>> intercept(FetchStream<ID> operation, EventStoreInterceptorChain<FetchStream<ID>, Optional<AggregateEventStream<ID>>> eventStoreInterceptorChain) {
        var potentialStream = Observation.createNotStarted("FetchStream:" + operation.aggregateType.toString(), observationRegistry)
                                         .lowCardinalityKeyValue(AGGREGATE_TYPE, operation.aggregateType.toString())
                                         .highCardinalityKeyValue(AGGREGATE_ID, operation.aggregateId.toString())
                                         .highCardinalityKeyValue("EventOrderRange", operation.getEventOrderRange().toString())
                                         .observe(eventStoreInterceptorChain::proceed);

//        potentialStream.ifPresent(stream -> restoreTraceContext(stream.lastEvent(), "FetchStream"));
        return potentialStream;
    }

    @Override
    public Stream<PersistedEvent> intercept(LoadEventsByGlobalOrder operation, EventStoreInterceptorChain<LoadEventsByGlobalOrder, Stream<PersistedEvent>> eventStoreInterceptorChain) {
        return Observation.createNotStarted("FetchStream:" + operation.aggregateType.toString(), observationRegistry)
                          .lowCardinalityKeyValue(AGGREGATE_TYPE, operation.aggregateType.toString())
                          .highCardinalityKeyValue("GlobalEventOrderRange", operation.getGlobalEventOrderRange().toString())
                          .highCardinalityKeyValue("IncludeAdditionalGlobalOrders", operation.getIncludeAdditionalGlobalOrders().toString())
                          .observe(eventStoreInterceptorChain::proceed);
    }

    protected void storeTraceContext(EventMetaData eventMetaData) {
        if (eventMetaData != null) {
            var currentTraceContext = tracer.currentTraceContext();
            if (currentTraceContext != null && currentTraceContext.context() != null) {
                propagator.inject(currentTraceContext.context(),
                                  eventMetaData,
                                  EventMetaData::put);
            }
        }
    }

    protected PersistedEvent restoreTraceContext(PersistedEvent event, String contextDescription) {
        requireNonNull(event, "No queuedMessage provided");
        requireNonNull(contextDescription, "No contextDescription provided");
        closeAnyActiveObservationScope();
        var observation = Observation.start(contextDescription + ":" + event.aggregateType().toString(),
                                            () -> createTraceContextForEvent(event),
                                            observationRegistry);
        observation.lowCardinalityKeyValue(AGGREGATE_TYPE, event.aggregateType().toString())
                   .lowCardinalityKeyValue(EVENT_TYPE, event.event().getEventTypeOrNamePersistenceValue())
                   .highCardinalityKeyValue(EVENT_ID, event.eventId().toString())
                   .highCardinalityKeyValue(AGGREGATE_ID, event.aggregateId().toString());
        activeObservationScope.set(observation.openScope());
        return event;
    }

    private ReceiverContext<EventMetaData> createTraceContextForEvent(PersistedEvent event) {
        requireNonNull(event, "No event provided");
        var context = new ReceiverContext<>(EventMetaData::get,
                                            Kind.CONSUMER);
        context.setCarrier(event.metaData().deserialize());
        return context;
    }

    private void closeAnyActiveObservationScope() {
        var activeScope = activeObservationScope.get();
        if (activeScope != null) {
            activeScope.close();
            activeScope.getCurrentObservation().stop();
            activeObservationScope.remove();
        }
    }
}
