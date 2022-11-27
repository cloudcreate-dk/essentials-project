/*
 * Copyright 2021-2022 the original author or authors.
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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.reactive.*;
import org.slf4j.*;

import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class EventStoreLocalEventBus {
    private static final Logger log = LoggerFactory.getLogger("EventStoreLocalEventBus");

    private LocalEventBus<PersistedEvents> localEventBus;

    public EventStoreLocalEventBus(LocalEventBus<PersistedEvents> localEventBus,
                                   EventStoreUnitOfWorkFactory unitOfWorkFactory) {
        requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory was supplied");
        this.localEventBus = requireNonNull(localEventBus, "No localEventBus was supplied");
        addUnitOfWorkLifeCycleCallback(unitOfWorkFactory);
    }

    public EventStoreLocalEventBus(EventStoreUnitOfWorkFactory unitOfWorkFactory) {
        requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory was supplied");
        localEventBus = new LocalEventBus<>("EventStoreLocalBus",
                                            3,
                                            this::onErrorHandler);
        addUnitOfWorkLifeCycleCallback(unitOfWorkFactory);
    }

    private void addUnitOfWorkLifeCycleCallback(EventStoreUnitOfWorkFactory unitOfWorkFactory) {
        unitOfWorkFactory.registerPersistedEventsCommitLifeCycleCallback(new PersistedEventsCommitLifecycleCallback() {
            @Override
            public void beforeCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                localEventBus.publish(new PersistedEvents(CommitStage.BeforeCommit, unitOfWork, persistedEvents));
            }

            @Override
            public void afterCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                localEventBus.publish(new PersistedEvents(CommitStage.AfterCommit, unitOfWork, persistedEvents));
            }

            @Override
            public void afterRollback(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                localEventBus.publish(new PersistedEvents(CommitStage.AfterRollback, unitOfWork, persistedEvents));
            }
        });
    }

    public LocalEventBus<PersistedEvents> localEventBus() {
        return localEventBus;
    }

    private void onErrorHandler(EventHandler<PersistedEvents> persistedEventsConsumer, PersistedEvents persistedEvents, Exception e) {
        log.error(msg("Failed to publish PersistedEvents to consumer {}", persistedEventsConsumer.getClass().getName()), e);
    }

    public LocalEventBus<PersistedEvents> addAsyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        return localEventBus.addAsyncSubscriber(subscriber);
    }

    public LocalEventBus<PersistedEvents> removeAsyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        return localEventBus.removeAsyncSubscriber(subscriber);
    }

    public LocalEventBus<PersistedEvents> addSyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        return localEventBus.addSyncSubscriber(subscriber);
    }

    public LocalEventBus<PersistedEvents> removeSyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        return localEventBus.removeSyncSubscriber(subscriber);
    }
}
