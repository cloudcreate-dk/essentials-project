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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import org.jdbi.v3.core.*;
import org.slf4j.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * {@link EventStore} compatible {@link UnitOfWorkFactory} variant where the {@link EventStore}'s {@link UnitOfWork}'s underlying
 * transaction is managed by Spring
 *
 * @deprecated Please use {@link SpringTransactionAwareEventStoreUnitOfWorkFactory} instead
 */
@Deprecated(forRemoval = true)
public class SpringManagedUnitOfWorkFactory implements EventStoreUnitOfWorkFactory<EventStoreUnitOfWork> {
    private static final Logger log = LoggerFactory.getLogger(SpringManagedUnitOfWorkFactory.class);

    private final Jdbi                                         jdbi;
    private final PlatformTransactionManager                   transactionManager;
    private final List<PersistedEventsCommitLifecycleCallback> lifecycleCallbacks;
    private final DefaultTransactionDefinition                 defaultTransactionDefinition;

    public SpringManagedUnitOfWorkFactory(Jdbi jdbi,
                                          PlatformTransactionManager transactionManager) {
        this.jdbi = requireNonNull(jdbi, "No jdbi instance provided");
        this.transactionManager = requireNonNull(transactionManager, "No transactionManager provided");
        lifecycleCallbacks = new ArrayList<>();
        defaultTransactionDefinition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED);
        defaultTransactionDefinition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public EventStoreUnitOfWork getRequiredUnitOfWork() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new NoActiveUnitOfWorkException();
        }
        return getOrCreateNewUnitOfWork();
    }

    @Override
    public EventStoreUnitOfWork getOrCreateNewUnitOfWork() {
        SpringManagedUnitOfWork unitOfWork;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("Manually starting a new Spring Managed Transaction and associating it with a new UnitOfWork");
            var transaction = transactionManager.getTransaction(defaultTransactionDefinition);
            unitOfWork = new SpringManagedUnitOfWork(transaction);
            unitOfWork.start();
            TransactionSynchronizationManager.bindResource(SpringManagedUnitOfWork.class, unitOfWork);
            log.trace("Registering a {} for the UnitOfWork", SpringManagedUnitOfWorkSynchronization.class.getName());
            TransactionSynchronizationManager.registerSynchronization(new SpringManagedUnitOfWorkSynchronization(unitOfWork));
        } else {
            unitOfWork = (SpringManagedUnitOfWork) TransactionSynchronizationManager.getResource(SpringManagedUnitOfWork.class);
            if (unitOfWork == null) {
                log.debug("Creating a new UnitOfWork and associating with an existing Spring Transaction");
                unitOfWork = new SpringManagedUnitOfWork();
                unitOfWork.start();
                TransactionSynchronizationManager.bindResource(SpringManagedUnitOfWork.class, unitOfWork);
                log.trace("Registering a {} for the UnitOfWork", SpringManagedUnitOfWorkSynchronization.class.getName());
                TransactionSynchronizationManager.registerSynchronization(new SpringManagedUnitOfWorkSynchronization(unitOfWork));
            }
        }
        return unitOfWork;
    }

    @Override
    public EventStoreUnitOfWorkFactory registerPersistedEventsCommitLifeCycleCallback(PersistedEventsCommitLifecycleCallback callback) {
        lifecycleCallbacks.add(requireNonNull(callback, "No callback provided"));
        return this;
    }

    @Override
    public Optional<EventStoreUnitOfWork> getCurrentUnitOfWork() {
        return Optional.ofNullable((SpringManagedUnitOfWork) TransactionSynchronizationManager.getResource(SpringManagedUnitOfWork.class));
    }

    private void removeUnitOfWork() {
        log.debug("Removing Spring Managed UnitOfWork");
        TransactionSynchronizationManager.unbindResource(SpringManagedUnitOfWork.class);
    }

    private class SpringManagedUnitOfWork implements EventStoreUnitOfWork {
        private Optional<TransactionStatus>                            manuallyStartedSpringTransaction;
        private UnitOfWorkStatus                                       status;
        private Handle                                                 handle;
        private Map<UnitOfWorkLifecycleCallback<Object>, List<Object>> unitOfWorkLifecycleCallbackResources;
        private List<PersistedEvent>                                   eventsPersisted;
        private Exception                                              causeOfRollback;


        public SpringManagedUnitOfWork() {
            status = UnitOfWorkStatus.Ready;
            unitOfWorkLifecycleCallbackResources = new HashMap<>();
            eventsPersisted = new ArrayList<>();
            manuallyStartedSpringTransaction = Optional.empty();
        }

        public SpringManagedUnitOfWork(TransactionStatus manuallyStartedSpringTransaction) {
            status = UnitOfWorkStatus.Ready;
            unitOfWorkLifecycleCallbackResources = new HashMap<>();
            eventsPersisted = new ArrayList<>();
            this.manuallyStartedSpringTransaction = Optional.of(requireNonNull(manuallyStartedSpringTransaction, "No manuallyStartedSpringTransaction provided"));
        }

        @Override
        public void start() {
            if (status == UnitOfWorkStatus.Ready || status.isCompleted()) {
                log.debug("Starting {} manged Spring Managed UnitOfWork with initial status {}",
                          manuallyStartedSpringTransaction.isPresent() ? "manually" : "fully",
                          status);
                log.trace("Opening JDBI handle");
                handle = jdbi.open();
                // TODO: Should it need a delegate to avoid duplicate transactions
                handle.begin();
                status = UnitOfWorkStatus.Started;
            } else if (status == UnitOfWorkStatus.Started) {
                log.warn("The Spring Managed UnitOfWork was already started");
            } else {
                close();
                removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot start an EventStoreManaged UnitOfWork as it has status {} and not the expected status {}, {} or {}", status, UnitOfWorkStatus.Started, UnitOfWorkStatus.Committed, UnitOfWorkStatus.RolledBack));
            }
        }

        /**
         * Called by {@link SpringManagedUnitOfWorkSynchronization#afterCompletion(int)}
         */
        private void close() {
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
                unitOfWorkLifecycleCallbackResources.clear();
            }
        }

        @Override
        public void commit() {
            if (status == UnitOfWorkStatus.Started && manuallyStartedSpringTransaction.isPresent()) {
                log.debug("Committing the manually managed Spring Transaction associated with this UnitOfWork");
                transactionManager.commit(manuallyStartedSpringTransaction.get());
            } else {
                log.debug("Ignoring call to commit the fully Spring Managed UnitOfWork with status {}", status);
            }
        }

        @Override
        public void rollback(Exception cause) {
            var correctStatus = status == UnitOfWorkStatus.Started || status == UnitOfWorkStatus.MarkedForRollbackOnly;
            if (correctStatus && manuallyStartedSpringTransaction.isPresent()) {
                causeOfRollback = cause != null ? cause : causeOfRollback;
                final String description = msg("Rolling back the manually managed Spring Transaction associated with UnitOfWork with status {}{}", status, cause != null ? " due to " + causeOfRollback.getMessage() : "");
                if (log.isTraceEnabled()) {
                    log.trace(description, causeOfRollback);
                } else {
                    log.debug(description);
                }
                transactionManager.rollback(manuallyStartedSpringTransaction.get());
            } else {
                log.debug("Ignoring call to rollback the fully Spring Managed UnitOfWork with status {}", status);
            }
        }

        @Override
        public UnitOfWorkStatus status() {
            return status;
        }

        @Override
        public Exception getCauseOfRollback() {
            return causeOfRollback;
        }

        @Override
        public void markAsRollbackOnly(Exception cause) {
            if (status == UnitOfWorkStatus.Started && manuallyStartedSpringTransaction.isPresent()) {
                final String description = msg("Marking the manually managed Spring Transaction associated with this UnitOfWork for Rollback Only {}", cause != null ? "due to " + cause.getMessage() : "");
                if (log.isTraceEnabled()) {
                    log.trace(description, cause);
                } else {
                    log.debug(description);
                }

                status = UnitOfWorkStatus.MarkedForRollbackOnly;
                causeOfRollback = cause;
            } else {
                log.debug("Ignoring call to mark the fully Spring Managed UnitOfWork as rollbackOnly. Current status {}", status);
            }
        }

        @Override
        public <T> T registerLifecycleCallbackForResource(T resource, UnitOfWorkLifecycleCallback<T> associatedUnitOfWorkCallback) {
            requireNonNull(resource, "You must provide a resource");
            requireNonNull(associatedUnitOfWorkCallback, "You must provide a UnitOfWorkLifecycleCallback");
            List<Object> resources = unitOfWorkLifecycleCallbackResources.computeIfAbsent((UnitOfWorkLifecycleCallback<Object>) associatedUnitOfWorkCallback, callback -> new LinkedList<>());
            resources.add(resource);
            return resource;

        }

        @Override
        public void registerEventsPersisted(List<PersistedEvent> eventsPersistedInThisUnitOfWork) {
            requireNonNull(eventsPersistedInThisUnitOfWork, "No eventsPersistedInThisUnitOfWork provided");
            this.eventsPersisted.addAll(eventsPersistedInThisUnitOfWork);
        }

        @Override
        public Handle handle() {
            if (handle == null) {
                throw new UnitOfWorkException("No active transaction");
            }
            return handle;
        }
    }

    private class SpringManagedUnitOfWorkSynchronization implements TransactionSynchronization {
        private final SpringManagedUnitOfWork unitOfWork;
        private       boolean                 readOnly;

        public SpringManagedUnitOfWorkSynchronization(SpringManagedUnitOfWork unitOfWork) {
            this.unitOfWork = requireNonNull(unitOfWork, "No unitOfWork provided");
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            this.readOnly = readOnly;
            if (readOnly) {
                log.debug("Ignoring beforeCommit as the transaction is readOnly");
                return;
            }

            log.trace("Calling UnitOfWorkLifecycleCallbacks#beforeCommit prior to committing the Spring Managed UnitOfWork");
            unitOfWork.unitOfWorkLifecycleCallbackResources.forEach((key, resources) -> {
                try {
                    log.trace("BeforeCommit: Calling {} with {} associated resource(s)",
                              key.getClass().getName(),
                              resources.size());
                    key.beforeCommit(unitOfWork, resources);
                } catch (RuntimeException e) {
                    UnitOfWorkException unitOfWorkException = new UnitOfWorkException(msg("{} failed during beforeCommit", key.getClass().getName()), e);
                    unitOfWorkException.fillInStackTrace();
                    throw unitOfWorkException;
                }
            });

            log.trace("Calling PersistedEventsCommitLifecycleCallback#beforeCommit prior to committing the Spring Managed UnitOfWork");
            for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
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
        public void afterCommit() {
            if (readOnly) {
                log.debug("Ignoring afterCommit as the transaction is readOnly");
                return;
            }

            unitOfWork.status = UnitOfWorkStatus.Committed;
            log.trace("Calling UnitOfWorkLifecycleCallbacks#afterCommit of the Spring Managed UnitOfWork");
            unitOfWork.unitOfWorkLifecycleCallbackResources.forEach((key, resources) -> {
                try {
                    log.trace("AfterCommit: Calling {} with {} associated resource(s)",
                              key.getClass().getName(),
                              resources.size());
                    key.afterCommit(unitOfWork, resources);
                } catch (RuntimeException e) {
                    log.error(msg("Failed {} failed during afterCommit", key.getClass().getName()), e);
                }
            });

            log.trace("Calling PersistedEventsCommitLifecycleCallback#afterCommit of the Spring Managed UnitOfWork");
            for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
                try {
                    log.trace("AfterCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), unitOfWork.eventsPersisted.size());
                    callback.afterCommit(unitOfWork, unitOfWork.eventsPersisted);
                } catch (RuntimeException e) {
                    log.error(msg("Failed {} failed during afterCommit PersistedEvents", callback.getClass().getName()), e);
                }
            }
        }

        @Override
        public void afterCompletion(int status) {
            if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                log.trace("Calling UnitOfWorkLifecycleCallbacks#afterRollback and of the Spring Managed UnitOfWork");
                unitOfWork.unitOfWorkLifecycleCallbackResources.forEach((key, resources) -> {
                    try {
                        log.trace("AfterRollback: Calling {} with {} associated resource(s)",
                                  key.getClass().getName(),
                                  resources.size());
                        key.afterRollback(unitOfWork, resources, unitOfWork.causeOfRollback);
                    } catch (RuntimeException e) {
                        log.error(msg("{} failed during afterRollback", key.getClass().getName()), e);
                    }
                });

            }
            unitOfWork.close();
            removeUnitOfWork();
        }
    }
}
