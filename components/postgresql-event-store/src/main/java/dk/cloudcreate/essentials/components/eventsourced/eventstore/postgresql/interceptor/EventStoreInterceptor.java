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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations.*;

import java.util.*;
import java.util.stream.Stream;

/**
 * An {@link EventStoreInterceptor} allows you to modify events prior to being persisted to the {@link EventStore} or after they're loaded/fetched from the {@link EventStore}<br>
 * Each interceptor method allows you to perform before, after or around interceptor logic according to your needs.<br>
 * An interceptor is added using {@link ConfigurableEventStore#addEventStoreInterceptor(EventStoreInterceptor)}
 */
public interface EventStoreInterceptor {

    /**
     * Intercept the {@link AppendToStream} operation (which includes {@link EventStore#startStream(AggregateType, Object, List)}/{@link EventStore#appendToStream(AppendToStream)})
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
     * Intercept the {@link LoadEvents} operation
     *
     * @param operation                  the operation instance
     * @param eventStoreInterceptorChain the interceptor chain
     * @return the result of the processing (default implementation just calls {@link EventStoreInterceptorChain#proceed()})
     */
    default List<PersistedEvent> intercept(LoadEvents operation, EventStoreInterceptorChain<LoadEvents, List<PersistedEvent>> eventStoreInterceptorChain) {
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

}
