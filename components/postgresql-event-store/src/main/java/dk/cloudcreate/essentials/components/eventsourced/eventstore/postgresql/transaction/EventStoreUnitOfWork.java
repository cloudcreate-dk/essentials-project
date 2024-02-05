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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.EventStoreEventBus;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork;

import java.util.List;

/**
 * Variant of the {@link UnitOfWork} that allows the {@link EventStore}
 * to register any {@link PersistedEvent}'s persisted during a {@link UnitOfWork},
 * such that these events can be published on the {@link EventStoreEventBus}
 */
public interface EventStoreUnitOfWork extends HandleAwareUnitOfWork {
    void registerEventsPersisted(List<PersistedEvent> eventsPersistedInThisUnitOfWork);
}
