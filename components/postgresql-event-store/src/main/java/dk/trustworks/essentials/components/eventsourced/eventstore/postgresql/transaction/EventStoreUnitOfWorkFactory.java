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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.trustworks.essentials.components.foundation.transaction.*;
import dk.trustworks.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory;

/**
 * A {@link UnitOfWorkFactory} that works with {@link EventStoreUnitOfWork}
 *
 * @param <UOW> the concrete type of {@link EventStoreUnitOfWork}
 */
public interface EventStoreUnitOfWorkFactory<UOW extends EventStoreUnitOfWork> extends HandleAwareUnitOfWorkFactory<UOW> {
    /**
     * Register a {@link UnitOfWork} callback that will be called with any persisted {@link PersistedEvent}'s during the
     * life cycle of the {@link UnitOfWork}
     *
     * @param callback the callback to register
     * @return the {@link UnitOfWorkFactory} instance this method was called on
     */
    EventStoreUnitOfWorkFactory<UOW> registerPersistedEventsCommitLifeCycleCallback(PersistedEventsCommitLifecycleCallback callback);
}
