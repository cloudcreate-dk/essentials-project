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

package dk.cloudcreate.essentials.components.foundation.transaction.spring;

import dk.cloudcreate.essentials.components.foundation.transaction.*;
import org.slf4j.*;
import org.springframework.transaction.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Spring transaction-aware {@link UnitOfWork}<br>
 * Specializations can choose to override {@link #onStart()} to initialize any datastore specific resources (e.g. a JDBI handle) and {@link #onCleanup()} to release/cleanup the resources.
 *
 * @param <TRX_MGR> the {@link PlatformTransactionManager} specialization that this {@link SpringTransactionAwareUnitOfWork} is compatible with
 * @param <UOW>     the {@link SpringTransactionAwareUnitOfWork} specialization
 */
public class SpringTransactionAwareUnitOfWork<TRX_MGR extends PlatformTransactionManager, UOW extends SpringTransactionAwareUnitOfWork<TRX_MGR, UOW>> implements UnitOfWork {
    private static final Logger log = LoggerFactory.getLogger(SpringTransactionAwareUnitOfWork.class);

    protected SpringTransactionAwareUnitOfWorkFactory<TRX_MGR, UOW> unitOfWorkFactory;
    private   Optional<TransactionStatus>                           manuallyStartedSpringTransaction;
    Exception        causeOfRollback;
    UnitOfWorkStatus status;
    protected Map<UnitOfWorkLifecycleCallback<Object>, List<Object>> unitOfWorkLifecycleCallbackResources;


    public SpringTransactionAwareUnitOfWork(SpringTransactionAwareUnitOfWorkFactory<TRX_MGR, UOW> unitOfWorkFactory) {
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory provided");
        status = UnitOfWorkStatus.Ready;
        unitOfWorkLifecycleCallbackResources = new HashMap<>();
        manuallyStartedSpringTransaction = Optional.empty();
    }

    public SpringTransactionAwareUnitOfWork(SpringTransactionAwareUnitOfWorkFactory<TRX_MGR, UOW> unitOfWorkFactory, TransactionStatus manuallyManagedSpringTransaction) {
        this(unitOfWorkFactory);
        this.manuallyStartedSpringTransaction = Optional.of(requireNonNull(manuallyManagedSpringTransaction, "No manuallyManagedSpringTransaction provided"));
    }

    @Override
    public void start() {
        if (status == UnitOfWorkStatus.Ready || status.isCompleted()) {
            log.debug("Starting {} manged Spring Transaction-Aware {} with initial status {}",
                      manuallyStartedSpringTransaction.isPresent() ? "manually" : "fully",
                      this.getClass().getSimpleName(),
                      status);
            onStart();

            status = UnitOfWorkStatus.Started;
        } else if (status == UnitOfWorkStatus.Started) {
            log.warn("The Spring Transaction-Aware UnitOfWork was already started");
        } else {
            cleanup();
            unitOfWorkFactory.removeUnitOfWork();
            throw new UnitOfWorkException(msg("Cannot start an {} as it has status {} and not the expected status {}, {} or {}",
                                              this.getClass().getSimpleName(),
                                              status,
                                              UnitOfWorkStatus.Started,
                                              UnitOfWorkStatus.Committed,
                                              UnitOfWorkStatus.RolledBack));
        }
    }

    /**
     * Called when the {@link UnitOfWork} is started.<br>
     * Here any unit of work related resources (e.g. JDBI handle) can be created
     */
    protected void onStart() {
    }

    /**
     * Called on clean up (e.g. after commit/rollback) of the {@link UnitOfWork}.<br>
     * Here any unit of work related resources (e.g. JDBI handle) created in {@link #onStart()} can be closed/cleaned-up
     */
    protected void onCleanup() {
    }

    void cleanup() {
        log.trace("Cleaning up");
        try {
            onCleanup();
        } catch (Exception e) {
            log.error("Failed to cleanup", e);
        } finally {
            unitOfWorkLifecycleCallbackResources.clear();
        }
    }

    @Override
    public void commit() {
        if (status == UnitOfWorkStatus.Started && manuallyStartedSpringTransaction.isPresent()) {
            if (manuallyStartedSpringTransaction.get().isCompleted()) {
                log.warn("Cannot commit the already COMPLETED manually managed Spring Transaction, associated with this UnitOfWork");
            } else if (manuallyStartedSpringTransaction.get().isRollbackOnly()) {
                log.info("Cannot commit the MARKED FOR ROLLBACK ONLY manually managed Spring Transaction, associated with this UnitOfWork");
            } else {
                log.debug("Committing the manually managed Spring Transaction associated with this UnitOfWork");
                unitOfWorkFactory.transactionManager.commit(manuallyStartedSpringTransaction.get());
            }
        } else if (status == UnitOfWorkStatus.MarkedForRollbackOnly) {
            log.debug("Rolling back UnitOfWork with status {}", status);
            unitOfWorkFactory.transactionManager.rollback(manuallyStartedSpringTransaction.get());
        } else {
            log.debug("Ignoring call to commit for the fully Spring managed Transaction associated with UnitOfWork with status {}", status);
        }
    }

    @Override
    public void rollback(Exception cause) {
        causeOfRollback = cause != null ? cause : causeOfRollback;
        var correctStatus = status == UnitOfWorkStatus.Started || status == UnitOfWorkStatus.MarkedForRollbackOnly;
        if (correctStatus && manuallyStartedSpringTransaction.isPresent()) {
            if (manuallyStartedSpringTransaction.get().isCompleted()) {
                final String description = msg("Skipping rolling back the already COMPLETED manually managed Spring Transaction associated with UnitOfWork with status {}{}", status, causeOfRollback != null ? " due to " + causeOfRollback.getMessage() : "");
                if (log.isTraceEnabled()) {
                    log.trace(description, causeOfRollback);
                } else {
                    log.debug(description);
                }
            } else {
                final String description = msg("Rolling back the manually managed Spring Transaction associated with UnitOfWork with status {}{}", status, causeOfRollback != null ? " due to " + causeOfRollback.getMessage() : "");
                if (log.isTraceEnabled()) {
                    log.trace(description, causeOfRollback);
                } else {
                    log.debug(description);
                }
                unitOfWorkFactory.transactionManager.rollback(manuallyStartedSpringTransaction.get());
            }
        } else {
            log.debug(msg("Ignoring call to rollback the fully Spring Managed Transaction associated with UnitOfWork with status {}", status), causeOfRollback);
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
            log.debug("Ignoring call to mark the fully Spring Managed Transaction, associated with this UnitOfWork, as rollbackOnly. Current UnitOfWork status {}", status);
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
}
