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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.foundation.transaction.*;

import java.util.List;

/**
 * Callback that can be registered with the {@link UnitOfWorkFactory}.<br/>
 * This life cycle will be called when ever any {@link UnitOfWork} is committed with any persisted {@link PersistedEvent}'s
 *
 * @see UnitOfWorkLifecycleCallback
 */
public interface PersistedEventsCommitLifecycleCallback {
    /**
     * Before the {@link UnitOfWork} is committed.<br/>
     * This method is called AFTER {@link UnitOfWorkLifecycleCallback#beforeCommit(UnitOfWork, java.util.List)}!
     *
     * @param unitOfWork      the unit of work
     * @param persistedEvents ALL the {@link PersistedEvent}'s that were associated with the {@link UnitOfWork}
     */
    void beforeCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents);

    /**
     * After the {@link UnitOfWork} was committed.<br/>
     * This method is called AFTER {@link UnitOfWorkLifecycleCallback#afterCommit(UnitOfWork, java.util.List)}
     *
     * @param unitOfWork      the unit of work
     * @param persistedEvents ALL the {@link PersistedEvent}'s that were associated with the {@link UnitOfWork}
     */
    void afterCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents);

    void afterRollback(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents);
}
