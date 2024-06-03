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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkException;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.*;
import org.jdbi.v3.core.*;
import org.slf4j.*;
import org.springframework.transaction.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * A {@link SpringTransactionAwareUnitOfWorkFactory} version of the {@link EventStoreUnitOfWorkFactory} which supports the standard {@link EventStoreUnitOfWork}
 *
 * @see dk.cloudcreate.essentials.components.foundation.transaction.spring.jdbi.SpringTransactionAwareJdbiUnitOfWorkFactory
 */
public class SpringTransactionAwareEventStoreUnitOfWorkFactory
        extends SpringTransactionAwareUnitOfWorkFactory<PlatformTransactionManager, SpringTransactionAwareEventStoreUnitOfWorkFactory.SpringTransactionAwareEventStoreUnitOfWork>
        implements EventStoreUnitOfWorkFactory<SpringTransactionAwareEventStoreUnitOfWorkFactory.SpringTransactionAwareEventStoreUnitOfWork> {
    private static final Logger                                       log = LoggerFactory.getLogger(SpringTransactionAwareEventStoreUnitOfWorkFactory.class);
    final                Jdbi                                         jdbi;
    private final        List<PersistedEventsCommitLifecycleCallback> persistedEventsLifecycleCallbacks;

    public SpringTransactionAwareEventStoreUnitOfWorkFactory(Jdbi jdbi,
                                                             PlatformTransactionManager platformTransactionManager) {
        super(platformTransactionManager);
        this.jdbi = requireNonNull(jdbi, "No jdbi instance provided");
        persistedEventsLifecycleCallbacks = new ArrayList<>();
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    @Override
    protected SpringTransactionAwareEventStoreUnitOfWork createUnitOfWorkForFactoryManagedTransaction(TransactionStatus transaction) {
        return new SpringTransactionAwareEventStoreUnitOfWork(this, transaction);
    }

    @Override
    protected SpringTransactionAwareEventStoreUnitOfWork createUnitOfWorkForSpringManagedTransaction() {
        return new SpringTransactionAwareEventStoreUnitOfWork(this);
    }

    @Override
    protected void afterCommitAfterCallingLifecycleCallbackResources(SpringTransactionAwareEventStoreUnitOfWork unitOfWork) {
        log.trace("Calling PersistedEventsCommitLifecycleCallback#afterCommit after committing the Spring Transaction-Aware UnitOfWork");
        for (var callback : persistedEventsLifecycleCallbacks) {
            try {
                log.trace("AfterCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), unitOfWork.eventsPersisted.size());
                callback.afterCommit(unitOfWork, unitOfWork.eventsPersisted);
            } catch (RuntimeException e) {
                log.error(msg("Failed {} failed during afterCommit PersistedEvents", callback.getClass().getName()), e);
            }
        }
    }

    @Override
    protected void beforeCommitAfterCallingLifecycleCallbackResources(SpringTransactionAwareEventStoreUnitOfWork unitOfWork) {
        log.trace("Calling PersistedEventsCommitLifecycleCallback#beforeCommit prior to committing the Spring Transaction-Aware UnitOfWork");
        for (var callback : persistedEventsLifecycleCallbacks) {
            try {
                log.trace("BeforeCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), unitOfWork.eventsPersisted.size());
                callback.beforeCommit(unitOfWork, unitOfWork.eventsPersisted);
            } catch (RuntimeException e) {
                UnitOfWorkException unitOfWorkException = new UnitOfWorkException(msg("{} failed during beforeCommit PersistedEvents", callback.getClass().getName()), e);
                unitOfWorkException.fillInStackTrace();
                throw unitOfWorkException;
            }
        }
    }

    @Override
    public EventStoreUnitOfWorkFactory registerPersistedEventsCommitLifeCycleCallback(PersistedEventsCommitLifecycleCallback callback) {
        persistedEventsLifecycleCallbacks.add(requireNonNull(callback, "No callback provided"));
        return this;
    }

    public static class SpringTransactionAwareEventStoreUnitOfWork extends SpringTransactionAwareUnitOfWork<PlatformTransactionManager, SpringTransactionAwareEventStoreUnitOfWork> implements EventStoreUnitOfWork {
        private static final Logger log = LoggerFactory.getLogger(SpringTransactionAwareEventStoreUnitOfWork.class);
        private              Handle handle;
        List<PersistedEvent> eventsPersisted = new ArrayList<>();

        public SpringTransactionAwareEventStoreUnitOfWork(SpringTransactionAwareUnitOfWorkFactory<PlatformTransactionManager, SpringTransactionAwareEventStoreUnitOfWork> unitOfWorkFactory) {
            super(unitOfWorkFactory);
        }

        public SpringTransactionAwareEventStoreUnitOfWork(SpringTransactionAwareUnitOfWorkFactory<PlatformTransactionManager, SpringTransactionAwareEventStoreUnitOfWork> unitOfWorkFactory, TransactionStatus manuallyManagedSpringTransaction) {
            super(unitOfWorkFactory, manuallyManagedSpringTransaction);
        }

        @Override
        public Handle handle() {
            if (handle == null) throw new IllegalStateException("UnitOfWork hasn't been started");
            return handle;
        }

        @Override
        protected void onStart() {
            log.trace("Opening JDBI handle");
            handle = ((SpringTransactionAwareEventStoreUnitOfWorkFactory) unitOfWorkFactory).jdbi.open();
            handle.begin();
        }

        @Override
        protected void onCleanup() {
            if (handle == null) {
                return;
            }
            log.trace("Closing JDBI handle");
            try {
                handle.close();
            } catch (Exception e) {
                log.error("Failed to close JDBI handle", e);
            } finally {
                handle = null;
            }
        }

        @Override
        public void registerEventsPersisted(List<PersistedEvent> eventsPersistedInThisUnitOfWork) {
            requireNonNull(eventsPersistedInThisUnitOfWork, "No eventsPersistedInThisUnitOfWork provided");
            this.eventsPersisted.addAll(eventsPersistedInThisUnitOfWork);
        }

        @Override
        public void removeFlushedEventsPersisted(List<PersistedEvent> eventsPersistedToRemoveFromThisUnitOfWork) {
            requireNonNull(eventsPersistedToRemoveFromThisUnitOfWork, "No eventsPersistedToRemoveFromThisUnitOfWork provided");
            this.eventsPersisted.removeAll(eventsPersistedToRemoveFromThisUnitOfWork);
        }

        @Override
        public void removeFlushedEventPersisted(PersistedEvent eventPersistedToRemoveFromThisUnitOfWork) {
            requireNonNull(eventPersistedToRemoveFromThisUnitOfWork, "No eventPersistedToRemoveFromThisUnitOfWork provided");
            this.eventsPersisted.remove(eventPersistedToRemoveFromThisUnitOfWork);
        }
    }
}
