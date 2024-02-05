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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;

import java.util.Optional;

/**
 * {@link PersistedEvent} Event handler interface for use with the {@link EventStoreSubscriptionManager}'s:
 * <ul>
 *     <li>{@link EventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)}</li>
 * </ul>
 *
 * @see PatternMatchingTransactionalPersistedEventHandler
 */
public interface TransactionalPersistedEventHandler {
    /**
     * This method will be called when ever a {@link PersistedEvent} is published with in a {@link UnitOfWork}
     *
     * @param event      the event published
     * @param unitOfWork the {@link UnitOfWork} associated with the <code>event</code>
     */
    void handle(PersistedEvent event, UnitOfWork unitOfWork);
}
