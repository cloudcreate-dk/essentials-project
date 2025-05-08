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

package dk.trustworks.essentials.components.eventsourced.aggregates.stateful.modern;

import dk.trustworks.essentials.components.eventsourced.aggregates.EventHandler;

/**
 * Marker interface that indicates that all state and all {@link EventHandler} annotated methods
 * will be hosted with the {@link AggregateState} object. The state object will be maintained by the {@link AggregateRoot}.<br>
 * You can use the {@link AggregateRoot#state()} or {@link AggregateRoot#state(Class)}  methodst o access the concrete State object.
 *
 * @param <ID>              the type of aggregate id
 * @param <EVENT_TYPE>      the type of event
 * @param <AGGREGATE_TYPE>  the type of aggregate
 * @param <AGGREGATE_STATE> the type of the aggregate-state
 * @see AggregateRoot#state()
 * @see AggregateRoot#state(Class)
 */
public interface WithState<ID, EVENT_TYPE, AGGREGATE_TYPE extends AggregateRoot<ID, EVENT_TYPE, AGGREGATE_TYPE>, AGGREGATE_STATE extends AggregateState<ID, EVENT_TYPE, AGGREGATE_TYPE>> {
}
