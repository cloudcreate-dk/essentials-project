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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.reactive.*;
import org.slf4j.*;

import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Top level {@link EventStore} specific {@link EventBus}, which ensures that {@link PersistedEvents} will be published
 * at all {@link CommitStage}'s, as coordinated by the provided {@link EventStoreUnitOfWorkFactory}
 */
public class EventStoreEventBus implements EventBus<PersistedEvents> {
    private static final Logger log = LoggerFactory.getLogger("EventStoreLocalEventBus");

    private EventBus<PersistedEvents> eventBus;

    /**
     * Wrap an existing {@link EventBus} and provide the proper {@link UnitOfWorkLifecycleCallback} to ensure that {@link PersistedEvents} will be published
     * at all {@link CommitStage}'s
     *
     * @param eventBus          the {@link EventBus} that is being delegated to
     * @param unitOfWorkFactory the {@link EventStoreUnitOfWorkFactory} that's coordinating the {@link UnitOfWork} life cycle
     */
    public EventStoreEventBus(EventBus<PersistedEvents> eventBus,
                              EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory) {
        requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory was supplied");
        this.eventBus = requireNonNull(eventBus, "No localEventBus was supplied");
        addUnitOfWorkLifeCycleCallback(unitOfWorkFactory);
    }

    /**
     * Default implementation that provides an internal {@link LocalEventBus} instance.
     *
     * @param unitOfWorkFactory the {@link EventStoreUnitOfWorkFactory} that's coordinating the {@link UnitOfWork} life cycle such that
     *                          this {@link EventBus} instance will ensure that {@link PersistedEvents} will be published
     *                          at all {@link CommitStage}'s
     */
    public EventStoreEventBus(EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory) {
        this(new LocalEventBus<>("EventStoreLocalBus",
                                 EventStoreEventBus::onErrorHandler),
             unitOfWorkFactory);

    }

    private void addUnitOfWorkLifeCycleCallback(EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory) {
        unitOfWorkFactory.registerPersistedEventsCommitLifeCycleCallback(new PersistedEventsCommitLifecycleCallback() {
            @Override
            public void beforeCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                eventBus.publish(new PersistedEvents(CommitStage.BeforeCommit, unitOfWork, persistedEvents));
            }

            @Override
            public void afterCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                eventBus.publish(new PersistedEvents(CommitStage.AfterCommit, unitOfWork, persistedEvents));
            }

            @Override
            public void afterRollback(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                eventBus.publish(new PersistedEvents(CommitStage.AfterRollback, unitOfWork, persistedEvents));
            }
        });
    }

    private static void onErrorHandler(EventHandler<PersistedEvents> persistedEventsConsumer, PersistedEvents persistedEvents, Exception e) {
        log.error(msg("Failed to publish PersistedEvents to consumer {}", persistedEventsConsumer.getClass().getName()), e);
    }

    @Override
    public EventBus<PersistedEvents> publish(PersistedEvents event) {
        eventBus.publish(event);
        return this;
    }

    @Override
    public EventBus<PersistedEvents> addAsyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        eventBus.addAsyncSubscriber(subscriber);
        return this;
    }

    @Override
    public EventBus<PersistedEvents> removeAsyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        eventBus.removeAsyncSubscriber(subscriber);
        return this;
    }

    @Override
    public EventBus<PersistedEvents> addSyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        eventBus.addSyncSubscriber(subscriber);
        return this;
    }

    @Override
    public EventBus<PersistedEvents> removeSyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        eventBus.removeSyncSubscriber(subscriber);
        return this;
    }

    @Override
    public boolean hasSyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        return eventBus.hasSyncSubscriber(subscriber);
    }

    @Override
    public boolean hasAsyncSubscriber(EventHandler<PersistedEvents> subscriber) {
        return eventBus.hasAsyncSubscriber(subscriber);
    }

    @Override
    public String toString() {
        return eventBus.toString();
    }
}
