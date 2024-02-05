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

package dk.cloudcreate.essentials.components.foundation.transaction.jdbi;

import dk.cloudcreate.essentials.components.foundation.transaction.*;
import org.jdbi.v3.core.*;
import org.slf4j.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Generic {@link HandleAwareUnitOfWorkFactory} variant where the implementation manually
 * manages the {@link UnitOfWork} and the underlying database Transaction.<br>
 * This class is built for inheritance, but can also be used directly - e.g. through the {@link JdbiUnitOfWorkFactory}<br>
 * <br>
 * If you e.g. need to have a {@link UnitOfWork} join in with an
 * existing <b>Spring</b> Managed transaction then please use the Spring specific {@link UnitOfWorkFactory},
 * <code>SpringManagedUnitOfWorkFactory</code>, provided with the <b>spring-postgresql-event-store</b> module.
 *
 * @param <UOW> the concrete {@link HandleAwareUnitOfWork} - such as the {@link GenericHandleAwareUnitOfWork}
 * @see GenericHandleAwareUnitOfWork
 * @see JdbiUnitOfWorkFactory
 */
public abstract class GenericHandleAwareUnitOfWorkFactory<UOW extends HandleAwareUnitOfWork> implements HandleAwareUnitOfWorkFactory<UOW> {
    private static final Logger log = LoggerFactory.getLogger(GenericHandleAwareUnitOfWorkFactory.class);

    private final Jdbi jdbi;

    /**
     * Contains the currently active {@link UnitOfWork}
     */
    private final ThreadLocal<UOW> unitOfWorks = new ThreadLocal<>();

    /**
     * Example of using the GenericHandleAwareUnitOfWorkFactory directly
     * <pre>{@code
     *         unitOfWorkFactory = new GenericHandleAwareUnitOfWorkFactory<>(jdbi) {
     *
     *             @Override
     *             protected GenericHandleAwareUnitOfWork createNewUnitOfWorkInstance(GenericHandleAwareUnitOfWorkFactory<GenericHandleAwareUnitOfWork> unitOfWorkFactory) {
     *                 return new GenericHandleAwareUnitOfWork(unitOfWorkFactory);
     *             }
     *         };
     * }</pre>
     *
     * @param jdbi the jdbi instance which provides access to the underlying database
     */
    public GenericHandleAwareUnitOfWorkFactory(Jdbi jdbi) {
        this.jdbi = requireNonNull(jdbi, "No jdbi instance provided");
    }

    @Override
    public UOW getRequiredUnitOfWork() {
        var unitOfWork = unitOfWorks.get();
        if (unitOfWork == null) {
            throw new NoActiveUnitOfWorkException();
        }
        return unitOfWork;
    }

    @Override
    public UOW getOrCreateNewUnitOfWork() {
        var unitOfWork = unitOfWorks.get();
        if (unitOfWork == null) {
            log.debug("Creating Managed UnitOfWork");
            unitOfWork = createNewUnitOfWorkInstance(this);
            unitOfWork.start();
            unitOfWorks.set(unitOfWork);
        }
        return unitOfWork;
    }

    protected abstract UOW createNewUnitOfWorkInstance(GenericHandleAwareUnitOfWorkFactory<UOW> unitOfWorkFactory);

    private void removeUnitOfWork() {
        log.debug("Removing Managed UnitOfWork");
        unitOfWorks.remove();
    }

    @Override
    public Optional<UOW> getCurrentUnitOfWork() {
        return Optional.ofNullable(unitOfWorks.get());
    }

    public static class GenericHandleAwareUnitOfWork implements HandleAwareUnitOfWork {
        private final Logger log = LoggerFactory.getLogger(GenericHandleAwareUnitOfWork.class);

        private final GenericHandleAwareUnitOfWorkFactory<?>                 unitOfWorkFactory;
        private final Map<UnitOfWorkLifecycleCallback<Object>, List<Object>> unitOfWorkLifecycleCallbackResources;

        private UnitOfWorkStatus status;
        private Exception        causeOfRollback;
        private Handle           handle;

        public GenericHandleAwareUnitOfWork(GenericHandleAwareUnitOfWorkFactory<?> unitOfWorkFactory) {
            this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory instance provided");
            status = UnitOfWorkStatus.Ready;
            unitOfWorkLifecycleCallbackResources = new HashMap<>();
        }

        @Override
        public void start() {
            if (status == UnitOfWorkStatus.Ready || status.isCompleted()) {
                log.debug("Starting Managed UnitOfWork with initial status {}", status);
                log.trace("Opening JDBI handle and will begin a DB transaction");
                handle = unitOfWorkFactory.jdbi.open();
                handle.begin();
                status = UnitOfWorkStatus.Started;
            } else if (status == UnitOfWorkStatus.Started) {
                log.warn("The Managed UnitOfWork was already started");
            } else {
                close();
                unitOfWorkFactory.removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot start an Managed UnitOfWork as it has status {} and not the expected status {}, {} or {}", status, UnitOfWorkStatus.Started, UnitOfWorkStatus.Committed, UnitOfWorkStatus.RolledBack));
            }
        }

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
                unitOfWorkLifecycleCallbackResources.clear();
                afterClosingHandle();
            }
        }


        @Override
        public void commit() {
            if (status == UnitOfWorkStatus.Started) {
                log.trace("Calling UnitOfWorkLifecycleCallbacks#beforeCommit prior to committing the Managed UnitOfWork");
                unitOfWorkLifecycleCallbackResources.forEach((key, resources) -> {
                    try {
                        log.trace("BeforeCommit: Calling {} with {} associated resource(s)",
                                  key.getClass().getName(),
                                  resources.size());
                        key.beforeCommit(this, resources);
                    } catch (RuntimeException e) {
                        UnitOfWorkException unitOfWorkException = new UnitOfWorkException(msg("{} failed during beforeCommit", key.getClass().getName()), e);
                        unitOfWorkException.fillInStackTrace();
                        rollback(unitOfWorkException);
                        throw unitOfWorkException;
                    }
                });

                beforeCommitting();

                log.trace("Committing Managed UnitOfWork");
                try {
                    handle.commit();
                } catch (Exception e) {
                    throw new UnitOfWorkException("Failed to commit UnitOfWork", e);
                } finally {
                    close();
                    unitOfWorkFactory.removeUnitOfWork();
                }
                status = UnitOfWorkStatus.Committed;
                unitOfWorkLifecycleCallbackResources.forEach((key, resources) -> {
                    try {
                        log.trace("AfterCommit: Calling {} with {} associated resource(s)",
                                  key.getClass().getName(),
                                  resources.size());
                        key.afterCommit(this, resources);
                    } catch (RuntimeException e) {
                        log.error(msg("Failed {} failed during afterCommit", key.getClass().getName()), e);
                    }
                });

                afterCommitting();
            } else if (status == UnitOfWorkStatus.MarkedForRollbackOnly) {
                rollback();
            } else {
                close();
                unitOfWorkFactory.removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot commit Managed UnitOfWork as it has status {} and not the expected status {}", status, UnitOfWorkStatus.Started));
            }

        }


        @Override
        public void rollback(Exception cause) {
            if (status == UnitOfWorkStatus.Started || status == UnitOfWorkStatus.MarkedForRollbackOnly) {
                causeOfRollback = cause != null ? cause : causeOfRollback;
                final String description = msg("Rolling back Managed UnitOfWork with current status {}{}", status, cause != null ? " due to " + causeOfRollback.getMessage() : "");
                if (log.isTraceEnabled()) {
                    log.trace(description, causeOfRollback);
                } else {
                    log.debug(description);
                }

                unitOfWorkLifecycleCallbackResources.entrySet().forEach(unitOfWorkLifecycleCallbackListEntry -> {
                    try {
                        log.trace("BeforeRollback: Calling {} with {} associated resource(s)",
                                  unitOfWorkLifecycleCallbackListEntry.getKey().getClass().getName(),
                                  unitOfWorkLifecycleCallbackListEntry.getValue().size());
                        unitOfWorkLifecycleCallbackListEntry.getKey().beforeRollback(this, unitOfWorkLifecycleCallbackListEntry.getValue(), cause);
                    } catch (RuntimeException e) {
                        log.error(msg("Failed {} failed during beforeRollback", unitOfWorkLifecycleCallbackListEntry.getKey().getClass().getName()), e);
                    }
                });

                beforeRollback(cause);

                try {
                    handle.rollback();
                } catch (Exception e) {
                    // Ignore
                }
                status = UnitOfWorkStatus.RolledBack;
                close();

                unitOfWorkLifecycleCallbackResources.entrySet().forEach(unitOfWorkLifecycleCallbackListEntry -> {
                    try {
                        log.trace("AfterRollback: Calling {} with {} associated resource(s)",
                                  unitOfWorkLifecycleCallbackListEntry.getKey().getClass().getName(),
                                  unitOfWorkLifecycleCallbackListEntry.getValue().size());
                        unitOfWorkLifecycleCallbackListEntry.getKey().afterRollback(this, unitOfWorkLifecycleCallbackListEntry.getValue(), cause);
                    } catch (RuntimeException e) {
                        log.error(msg("Failed {} failed during afterRollback", unitOfWorkLifecycleCallbackListEntry.getKey().getClass().getName()), e);
                    }
                });

                afterRollback(cause);
                unitOfWorkFactory.removeUnitOfWork();
            } else {
                close();
                unitOfWorkFactory.removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot Rollback Managed UnitOfWork as it has status {} and not the expected status {} or {}", status, UnitOfWorkStatus.Started, UnitOfWorkStatus.MarkedForRollbackOnly), cause);
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
        public UnitOfWorkStatus status() {
            return status;
        }

        @Override
        public Exception getCauseOfRollback() {
            return causeOfRollback;
        }

        @Override
        public void markAsRollbackOnly(Exception cause) {
            if (status == UnitOfWorkStatus.Started) {
                final String description = msg("Marking Managed UnitOfWork for Rollback Only {}", cause != null ? "due to " + cause.getMessage() : "");
                if (log.isTraceEnabled()) {
                    log.trace(description, cause);
                } else {
                    log.debug(description);
                }

                status = UnitOfWorkStatus.MarkedForRollbackOnly;
                causeOfRollback = cause;
            } else if (status == UnitOfWorkStatus.MarkedForRollbackOnly) {
                log.debug("Cannot Mark current Managed UnitOfWork for Rollback Only as it has already been marked as such", cause);
            } else {
                close();
                unitOfWorkFactory.removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot Mark Managed UnitOfWork for Rollback Only it has status {} and not the expected status {}", status, UnitOfWorkStatus.Started), cause);
            }
        }

        @Override
        public Handle handle() {
            if (handle == null) {
                throw new UnitOfWorkException("No active transaction");
            }
            return handle;
        }

        /**
         * Called right after the {@link Handle} is closed. Perform any additional clean-ups by overriding this method
         */
        protected void afterClosingHandle() {
        }

        /**
         * Called right after {@link Handle#commit()} has been called.
         */
        protected void afterCommitting() {

        }

        /**
         * Called right before {@link Handle#commit()} is called.
         */
        protected void beforeCommitting() {

        }

        /**
         * Called right after {@link Handle#rollback()} has been called.
         *
         * @param cause the cause of the rollback
         */
        protected void afterRollback(Exception cause) {

        }

        /**
         * Called right before {@link Handle#rollback()} is called.
         *
         * @param cause the cause of the rollback
         */
        protected void beforeRollback(Exception cause) {

        }
    }
}
