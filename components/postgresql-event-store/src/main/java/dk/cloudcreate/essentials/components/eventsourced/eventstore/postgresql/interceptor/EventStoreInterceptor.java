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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.*;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * An {@link EventStoreInterceptor} allows you to modify events prior to being persisted to the {@link EventStore} or after they're loaded/fetched from the {@link EventStore}<br>
 * Each interceptor method allows you to perform before, after or around interceptor logic according to your needs.<br>
 * An interceptor is added using {@link ConfigurableEventStore#addEventStoreInterceptor(EventStoreInterceptor)}
 */
public interface EventStoreInterceptor {

    /**
     * Intercept the {@link AppendToStream} operation
     *
     * @param operation                  the operation instance
     * @param eventStoreInterceptorChain the interceptor chain
     * @param <ID>                       the id type for the aggregate
     * @return the result of the processing (default implementation just calls {@link EventStoreInterceptorChain#proceed()})
     */
    default <ID> AggregateEventStream<ID> intercept(AppendToStream<ID> operation, EventStoreInterceptorChain<AppendToStream<ID>, AggregateEventStream<ID>> eventStoreInterceptorChain) {
        return eventStoreInterceptorChain.proceed();
    }

    /**
     * Intercept the {@link LoadLastPersistedEventRelatedTo} operation
     *
     * @param operation                  the operation instance
     * @param eventStoreInterceptorChain the interceptor chain
     * @param <ID>                       the id type for the aggregate
     * @return the result of the processing (default implementation just calls {@link EventStoreInterceptorChain#proceed()})
     */
    default <ID> Optional<PersistedEvent> intercept(LoadLastPersistedEventRelatedTo<ID> operation, EventStoreInterceptorChain<LoadLastPersistedEventRelatedTo<ID>, Optional<PersistedEvent>> eventStoreInterceptorChain) {
        return eventStoreInterceptorChain.proceed();
    }

    /**
     * Intercept the {@link LoadEvent} operation
     *
     * @param operation                  the operation instance
     * @param eventStoreInterceptorChain the interceptor chain
     * @return the result of the processing (default implementation just calls {@link EventStoreInterceptorChain#proceed()})
     */
    default Optional<PersistedEvent> intercept(LoadEvent operation, EventStoreInterceptorChain<LoadEvent, Optional<PersistedEvent>> eventStoreInterceptorChain) {
        return eventStoreInterceptorChain.proceed();
    }

    /**
     * Intercept the {@link AppendToStream} operation
     *
     * @param operation                  the operation instance
     * @param eventStoreInterceptorChain the interceptor chain
     * @param <ID>                       the id type for the aggregate
     * @return the result of the processing (default implementation just calls {@link EventStoreInterceptorChain#proceed()})
     */
    default <ID> Optional<AggregateEventStream<ID>> intercept(FetchStream<ID> operation, EventStoreInterceptorChain<FetchStream<ID>, Optional<AggregateEventStream<ID>>> eventStoreInterceptorChain) {
        return eventStoreInterceptorChain.proceed();
    }

    /**
     * Intercept the {@link LoadEventsByGlobalOrder} operation
     *
     * @param operation                  the operation instance
     * @param eventStoreInterceptorChain the interceptor chain
     * @return the result of the processing (default implementation just calls {@link EventStoreInterceptorChain#proceed()})
     */
    default Stream<PersistedEvent> intercept(LoadEventsByGlobalOrder operation, EventStoreInterceptorChain<LoadEventsByGlobalOrder, Stream<PersistedEvent>> eventStoreInterceptorChain) {
        return eventStoreInterceptorChain.proceed();
    }

    /**
     * Operation matching the {@link EventStore#appendToStream(AggregateType, Object, Optional, List)} method call
     *
     * @param <ID> the id type for the aggregate
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    class AppendToStream<ID> {
        private final AggregateType  aggregateType;
        private final ID             aggregateId;
        private       Optional<Long> appendEventsAfterEventOrder;
        private       List<?>        eventsToAppend;

        public AppendToStream(AggregateType aggregateType, ID aggregateId, Optional<Long> appendEventsAfterEventOrder, List<?> eventsToAppend) {
            this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
            this.aggregateId = requireNonNull(aggregateId, "No aggregateId provided");
            this.appendEventsAfterEventOrder = requireNonNull(appendEventsAfterEventOrder, "No appendEventsAfterEventOrder provided");
            this.eventsToAppend = requireNonNull(eventsToAppend, "No eventsToAppend provided");
        }

        public void setAppendEventsAfterEventOrder(Optional<Long> appendEventsAfterEventOrder) {
            this.appendEventsAfterEventOrder = requireNonNull(appendEventsAfterEventOrder, "No appendEventsAfterEventOrder provided");
        }

        public void setEventsToAppend(List<?> eventsToAppend) {
            this.eventsToAppend = requireNonNull(eventsToAppend, "No eventsToAppend provided");
        }

        public AggregateType getAggregateType() {
            return aggregateType;
        }

        public ID getAggregateId() {
            return aggregateId;
        }

        public Optional<Long> getAppendEventsAfterEventOrder() {
            return appendEventsAfterEventOrder;
        }

        public List<?> getEventsToAppend() {
            return eventsToAppend;
        }

        @Override
        public String toString() {
            return "AppendToStream{" +
                    "aggregateType=" + aggregateType +
                    ", aggregateId=" + aggregateId +
                    ", appendEventsAfterEventOrder=" + appendEventsAfterEventOrder +
                    ", eventsToAppend=" + eventsToAppend +
                    '}';
        }
    }

    /**
     * Operation matching the {@link EventStore#loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *
     * @param <ID> the id type for the aggregate
     */
    class LoadLastPersistedEventRelatedTo<ID> {
        private final AggregateType aggregateType;
        private final ID            aggregateId;

        public LoadLastPersistedEventRelatedTo(AggregateType aggregateType, ID aggregateId) {
            this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
            this.aggregateId = requireNonNull(aggregateId, "No aggregateId provided");
        }

        public AggregateType getAggregateType() {
            return aggregateType;
        }

        public ID getAggregateId() {
            return aggregateId;
        }

        @Override
        public String toString() {
            return "LoadLastPersistedEventRelatedTo{" +
                    "aggregateType=" + aggregateType +
                    ", aggregateId=" + aggregateId +
                    '}';
        }
    }

    /**
     * Operation matching the {@link EventStore#loadEvent(AggregateType, EventId)}
     */
    class LoadEvent {
        private final AggregateType aggregateType;
        private final EventId       eventId;

        public LoadEvent(AggregateType aggregateType, EventId aggregateId) {
            this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
            this.eventId = requireNonNull(aggregateId, "No eventId provided");
        }

        public AggregateType getAggregateType() {
            return aggregateType;
        }

        public EventId getEventId() {
            return eventId;
        }

        @Override
        public String toString() {
            return "LoadEvent{" +
                    "aggregateType=" + aggregateType +
                    ", eventId=" + eventId +
                    '}';
        }
    }

    /**
     * Operation matching the {@link EventStore#fetchStream(AggregateType, Object, LongRange, Optional)} method call
     *
     * @param <ID> the id type for the aggregate
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    class FetchStream<ID> {
        private final AggregateType    aggregateType;
        private final ID               aggregateId;
        private       LongRange        eventOrderRange;
        private       Optional<Tenant> tenant;

        public FetchStream(AggregateType aggregateType, ID aggregateId, LongRange eventOrderRange, Optional<Tenant> tenant) {
            this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
            this.aggregateId = requireNonNull(aggregateId, "No aggregateId provided");
            this.eventOrderRange = requireNonNull(eventOrderRange, "No eventOrderRange provided");
            this.tenant = requireNonNull(tenant, "No tenant provided");
        }

        public AggregateType getAggregateType() {
            return aggregateType;
        }

        public ID getAggregateId() {
            return aggregateId;
        }

        public LongRange getEventOrderRange() {
            return eventOrderRange;
        }

        public Optional<Tenant> getTenant() {
            return tenant;
        }

        public void setEventOrderRange(LongRange eventOrderRange) {
            this.eventOrderRange = requireNonNull(eventOrderRange, "No eventOrderRange provided");
        }

        public void setTenant(Optional<Tenant> tenant) {
            this.tenant = requireNonNull(tenant, "No tenant provided");
        }

        @Override
        public String toString() {
            return "FetchStream{" +
                    "aggregateType=" + aggregateType +
                    ", aggregateId=" + aggregateId +
                    ", eventOrderRange=" + eventOrderRange +
                    ", tenant=" + tenant +
                    '}';
        }
    }

    /**
     * Operation matching the {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Optional)}  method call
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    class LoadEventsByGlobalOrder {
        private final AggregateType    aggregateType;
        private       LongRange        globalOrderRange;
        private       Optional<Tenant> onlyIncludeEventIfItBelongsToTenant;

        public LoadEventsByGlobalOrder(AggregateType aggregateType, LongRange globalOrderRange, Optional<Tenant> onlyIncludeEventIfItBelongsToTenant) {
            this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
            this.globalOrderRange = requireNonNull(globalOrderRange, "No globalOrderRange provided");
            this.onlyIncludeEventIfItBelongsToTenant = requireNonNull(onlyIncludeEventIfItBelongsToTenant, "No onlyIncludeEventIfItBelongsToTenant option provided");
        }

        public AggregateType getAggregateType() {
            return aggregateType;
        }

        public LongRange getGlobalOrderRange() {
            return globalOrderRange;
        }

        public Optional<Tenant> getOnlyIncludeEventIfItBelongsToTenant() {
            return onlyIncludeEventIfItBelongsToTenant;
        }

        public void setGlobalOrderRange(LongRange globalOrderRange) {
            this.globalOrderRange = requireNonNull(globalOrderRange, "No globalOrderRange provided");
        }

        public void setOnlyIncludeEventIfItBelongsToTenant(Optional<Tenant> onlyIncludeEventIfItBelongsToTenant) {
            this.onlyIncludeEventIfItBelongsToTenant = requireNonNull(onlyIncludeEventIfItBelongsToTenant, "No onlyIncludeEventIfItBelongsToTenant option provided");
        }

        @Override
        public String toString() {
            return "LoadEventsByGlobalOrder{" +
                    "aggregateType=" + aggregateType +
                    ", globalOrderRange=" + globalOrderRange +
                    ", onlyIncludeEventIfItBelongsToTenant=" + onlyIncludeEventIfItBelongsToTenant +
                    '}';
        }
    }

}
