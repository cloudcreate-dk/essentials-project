/*
 * Copyright 2021-2023 the original author or authors.
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
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.GenericHandleAwareUnitOfWorkFactory;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * {@link UnitOfWorkFactory} variant where the {@link EventStore} is manually
 * managing the {@link UnitOfWork} and the underlying database Transaction.<br>
 * If you need to have the {@link EventStore} {@link UnitOfWork} join in with an
 * existing <b>Spring</b> Managed transaction then please use the Spring specific {@link UnitOfWorkFactory},
 * <code>SpringManagedUnitOfWorkFactory</code>, provided with the <b>spring-postgresql-event-store</b> module.
 */
public class EventStoreManagedUnitOfWorkFactory extends GenericHandleAwareUnitOfWorkFactory<EventStoreUnitOfWork> implements EventStoreUnitOfWorkFactory<EventStoreUnitOfWork> {
    private static final Logger log = LoggerFactory.getLogger(EventStoreManagedUnitOfWorkFactory.class);

    private final List<PersistedEventsCommitLifecycleCallback> lifecycleCallbacks;


    public EventStoreManagedUnitOfWorkFactory(Jdbi jdbi) {
        super(jdbi);
        lifecycleCallbacks = new ArrayList<>();
    }

    @Override
    protected EventStoreUnitOfWork createNewUnitOfWorkInstance(GenericHandleAwareUnitOfWorkFactory<EventStoreUnitOfWork> unitOfWorkFactory) {
        return new EventStoreManagedUnitOfWork(unitOfWorkFactory, lifecycleCallbacks);
    }

    @Override
    public EventStoreUnitOfWorkFactory<EventStoreUnitOfWork> registerPersistedEventsCommitLifeCycleCallback(PersistedEventsCommitLifecycleCallback callback) {
        lifecycleCallbacks.add(requireNonNull(callback, "No callback provided"));
        return this;
    }

}
