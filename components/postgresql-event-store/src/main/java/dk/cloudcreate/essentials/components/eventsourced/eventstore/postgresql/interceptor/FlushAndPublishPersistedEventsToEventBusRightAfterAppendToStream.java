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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations.AppendToStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;

import java.util.Optional;

/**
 * Add this interceptor to the {@link EventStore} if you want {@link EventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)}
 * to receive {@link PersistedEvent}'s as soon as they are appended to the {@link EventStore} (i.e. {@link EventStore#appendToStream(AppendToStream)} is performed)
 */
public class FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStream implements EventStoreInterceptor {
    @Override
    public <ID> AggregateEventStream<ID> intercept(AppendToStream<ID> operation, EventStoreInterceptorChain<AppendToStream<ID>, AggregateEventStream<ID>> eventStoreInterceptorChain) {
        var eventsPersisted = eventStoreInterceptorChain.proceed();
        if (eventsPersisted.isNotEmpty()) {
            eventStoreInterceptorChain.eventStore().localEventBus().publish(new PersistedEvents(CommitStage.Flush, eventStoreInterceptorChain.eventStore().getUnitOfWorkFactory().getRequiredUnitOfWork(), eventsPersisted.eventList()));
        }
        return eventsPersisted;
    }
}
