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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreUnitOfWork;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Encapsulates all events Persisted within a {@link UnitOfWork}
 */
public final class PersistedEvents {
    public final CommitStage          commitStage;
    public final EventStoreUnitOfWork unitOfWork;
    public final List<PersistedEvent> events;

    public PersistedEvents(CommitStage commitStage, EventStoreUnitOfWork unitOfWork, List<PersistedEvent> events) {
        this.commitStage = requireNonNull(commitStage, "No commitStage provided");
        this.unitOfWork = requireNonNull(unitOfWork, "No unitOfWork provided");
        this.events = new ArrayList<>(requireNonNull(events, "No events provided"));
    }

    @Override
    public String toString() {
        return "PersistedEvents{" +
                "commitStage=" + commitStage + ", " +
                "events=" + events.size() +
                '}';
    }
}
