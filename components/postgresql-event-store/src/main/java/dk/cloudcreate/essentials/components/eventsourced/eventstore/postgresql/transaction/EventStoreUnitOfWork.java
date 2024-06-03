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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager.DefaultEventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.TransactionalPersistedEventHandler;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;

import java.util.*;

/**
 * Variant of the {@link UnitOfWork} that allows the {@link EventStore}
 * to register any {@link PersistedEvent}'s persisted during a {@link UnitOfWork},
 * such that these events can be published on the {@link EventStoreEventBus}
 */
public interface EventStoreUnitOfWork extends HandleAwareUnitOfWork {
    /**
     * Register {@link PersistedEvent}'s in the {@link EventStoreUnitOfWork} that will be published during {@link CommitStage#BeforeCommit},
     * {@link CommitStage#AfterCommit} and  {@link CommitStage#AfterRollback}
     *
     * @param eventsPersistedInThisUnitOfWork the {@link PersistedEvent}'s to add
     * @see #removeFlushedEventsPersisted(List)
     * @see #removeFlushedEventPersisted(PersistedEvent)
     */
    void registerEventsPersisted(List<PersistedEvent> eventsPersistedInThisUnitOfWork);

    /**
     * Remove {@link PersistedEvent}'s from the {@link EventStoreUnitOfWork} such that it won't be published during {@link CommitStage#BeforeCommit},
     * {@link CommitStage#AfterCommit} and  {@link CommitStage#AfterRollback}<br>
     * Used by {@link DefaultEventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)}
     * if {@link PersistedEvents#commitStage} is {@link CommitStage#Flush}
     *
     * @param eventsPersistedToRemoveFromThisUnitOfWork the {@link PersistedEvent}'s to remove
     * @see EventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)
     * @see FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStream
     */
    void removeFlushedEventsPersisted(List<PersistedEvent> eventsPersistedToRemoveFromThisUnitOfWork);

    /**
     * Remove {@link PersistedEvent} from the {@link EventStoreUnitOfWork} such that it won't be published during {@link CommitStage#BeforeCommit},
     * {@link CommitStage#AfterCommit} and  {@link CommitStage#AfterRollback}<br>
     * Used by {@link DefaultEventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)}
     * if {@link PersistedEvents#commitStage} is {@link CommitStage#Flush}
     *
     * @param eventPersistedToRemoveFromThisUnitOfWork the {@link PersistedEvent} to remove
     * @see EventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)
     * @see FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStream
     */
    void removeFlushedEventPersisted(PersistedEvent eventPersistedToRemoveFromThisUnitOfWork);
}
