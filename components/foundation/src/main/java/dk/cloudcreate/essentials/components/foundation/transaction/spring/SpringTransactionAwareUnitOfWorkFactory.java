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

package dk.cloudcreate.essentials.components.foundation.transaction.spring;

import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.jdbi.SpringTransactionAwareJdbiUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory;
import dk.cloudcreate.essentials.shared.types.GenericType;
import org.slf4j.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Variant of the {@link UnitOfWorkFactory} which can delegate the creation of the underlying transaction to the provided {@link PlatformTransactionManager} instance.<br>
 * The {@link UnitOfWork} type specialization needs to extend {@link SpringTransactionAwareUnitOfWork}
 *
 * @param <TRX_MGR> the platform transaction manager that this {@link UnitOfWorkFactory} is compatible with
 * @param <UOW>     the Unit of work implementation that this {@link SpringTransactionAwareUnitOfWorkFactory} specialization is compatible with
 * @see SpringTransactionAwareJdbiUnitOfWorkFactory
 * @see SpringMongoTransactionAwareUnitOfWorkFactory
 */
public abstract class SpringTransactionAwareUnitOfWorkFactory<TRX_MGR extends PlatformTransactionManager, UOW extends SpringTransactionAwareUnitOfWork<TRX_MGR, UOW>> implements UnitOfWorkFactory<UOW> {
    protected static final Logger log = LoggerFactory.getLogger(SpringTransactionAwareUnitOfWorkFactory.class);

    protected final TRX_MGR                      transactionManager;
    protected final DefaultTransactionDefinition defaultTransactionDefinition;
    /**
     * The class representation of the <code>UOW</code> generic type parameter.<br>
     * The {@link UnitOfWork} is bound a resource with the {@link TransactionSynchronizationManager}
     * using the {@link #unitOfWorkType} class as key.
     */
    protected final Class<?>                     unitOfWorkType;

    public SpringTransactionAwareUnitOfWorkFactory(TRX_MGR transactionManager) {
        this.transactionManager = requireNonNull(transactionManager, "No transactionManager provided");
        defaultTransactionDefinition = createDefaultTransactionDefinition();
        unitOfWorkType = resolveUnitOfWorkType();
        log.info("Configured '{}' with UnitOfWork type '{}' and defaultTransactionDefinition {}",
                 this.getClass().getName(),
                 unitOfWorkType.getName(),
                 defaultTransactionDefinition);
    }

    protected Class<?> resolveUnitOfWorkType() {
        return GenericType.resolveGenericTypeForInterface(this.getClass(),
                                                          UnitOfWorkFactory.class,
                                                          0);
    }

    /**
     * Override to create a custom {@link DefaultTransactionDefinition}.
     *
     * @return per default, it generates a {@link DefaultTransactionDefinition} with propagation
     * {@link TransactionDefinition#PROPAGATION_REQUIRED} and Isolation-level {@link TransactionDefinition#ISOLATION_READ_COMMITTED}
     */
    protected DefaultTransactionDefinition createDefaultTransactionDefinition() {
        var defaultTransactionDefinition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED);
        defaultTransactionDefinition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return defaultTransactionDefinition;
    }

    public TRX_MGR getTransactionManager() {
        return transactionManager;
    }

    @Override
    public UOW getRequiredUnitOfWork() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new NoActiveUnitOfWorkException();
        }
        return getOrCreateNewUnitOfWork();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<UOW> getCurrentUnitOfWork() {
        return Optional.ofNullable((UOW) TransactionSynchronizationManager.getResource(unitOfWorkType));
    }

    @SuppressWarnings("unchecked")
    @Override
    public UOW getOrCreateNewUnitOfWork() {
        UOW unitOfWork;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("Manually starting a new Spring Transaction and associating it with a new Spring Transaction-Aware UnitOfWork");
            var transaction = transactionManager.getTransaction(defaultTransactionDefinition);
            unitOfWork = createUnitOfWorkForFactoryManagedTransaction(transaction);
            unitOfWork.start();
            TransactionSynchronizationManager.bindResource(unitOfWorkType, unitOfWork);
            log.trace("Registering a {} for the {}", SpringTransactionAwareUnitOfWorkSynchronization.class.getSimpleName(), unitOfWorkType.getSimpleName());
            TransactionSynchronizationManager.registerSynchronization(new SpringTransactionAwareUnitOfWorkSynchronization(unitOfWork));
        } else {
            unitOfWork = (UOW) TransactionSynchronizationManager.getResource(unitOfWorkType);
            if (unitOfWork == null) {
                log.debug("Using the existing Spring Transaction and associating it with a new Spring Transaction-Aware UnitOfWork");
                unitOfWork = createUnitOfWorkForSpringManagedTransaction();
                unitOfWork.start();
                TransactionSynchronizationManager.bindResource(unitOfWorkType, unitOfWork);
                log.trace("Registering a {} for the {}", SpringTransactionAwareUnitOfWorkSynchronization.class.getSimpleName(), unitOfWorkType.getSimpleName());
                TransactionSynchronizationManager.registerSynchronization(new SpringTransactionAwareUnitOfWorkSynchronization(unitOfWork));
            }
        }
        return unitOfWork;
    }

    /**
     * Create a {@link UnitOfWork} wrapping a Spring Transaction managed by this {@link UnitOfWorkFactory}
     *
     * @param transaction the Spring Transaction managed by this {@link UnitOfWorkFactory}
     * @return a {@link UnitOfWork} wrapping the existing active Spring managed transaction
     */
    protected abstract UOW createUnitOfWorkForFactoryManagedTransaction(TransactionStatus transaction);

    /**
     * Create a {@link UnitOfWork} participating in an existing Spring Managed Transaction that isn't managed by this {@link UnitOfWorkFactory}
     *
     * @return a {@link UnitOfWork} that is participating in an existing Spring Managed Transaction that isn't managed by this {@link UnitOfWorkFactory}
     */
    protected abstract UOW createUnitOfWorkForSpringManagedTransaction();

    void removeUnitOfWork() {
        log.debug("Removing Spring Transaction-Aware UnitOfWork");
        var resource  = TransactionSynchronizationManager.unbindResource(unitOfWorkType);
        var uow = (UOW) resource;
        if (!uow.status.isCompleted) {
            log.error("UOW in not completed {}", uow.info());
        }
        log.debug("Removed Spring Transaction-Aware UnitOfWork: {}", uow.info());
    }

    private class SpringTransactionAwareUnitOfWorkSynchronization implements TransactionSynchronization {
        private final UOW     unitOfWork;
        private       boolean readOnly;

        public SpringTransactionAwareUnitOfWorkSynchronization(UOW unitOfWork) {
            this.unitOfWork = requireNonNull(unitOfWork, "No unitOfWork provided");
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            this.readOnly = readOnly;
            if (readOnly) {
                log.debug("Ignoring beforeCommit as the transaction is readOnly");
                return;
            }

            log.trace("Calling UnitOfWorkLifecycleCallbacks#beforeCommit prior to committing the Spring Transaction-Aware UnitOfWork: {}", unitOfWork.info());
            beforeCommitBeforeCallingLifecycleCallbackResources(unitOfWork);
            var processingStatus = new AtomicReference<>(UnitOfWorkLifecycleCallback.BeforeCommitProcessingStatus.REQUIRED);
            while (processingStatus.get() == UnitOfWorkLifecycleCallback.BeforeCommitProcessingStatus.REQUIRED) {
                log.trace("BeforeCommit: Performing BeforeCommitProcessing since processingStatus is {}", processingStatus.get());
                processingStatus.set(UnitOfWorkLifecycleCallback.BeforeCommitProcessingStatus.COMPLETED);
                unitOfWork.unitOfWorkLifecycleCallbackResources.forEach((key, resources) -> {
                    try {
                        log.trace("BeforeCommit: Calling {} with {} associated resource(s)",
                                  key.getClass().getName(),
                                  resources.size());
                        var newProcessingStatus = key.beforeCommit(unitOfWork, resources);
                        if (newProcessingStatus == UnitOfWorkLifecycleCallback.BeforeCommitProcessingStatus.REQUIRED &&
                                processingStatus.get() == UnitOfWorkLifecycleCallback.BeforeCommitProcessingStatus.COMPLETED) {
                            log.trace("BeforeCommit: {} changed BeforeCommitProcessing processingStatus back to {}", key.getClass().getName(), newProcessingStatus);
                            processingStatus.set(newProcessingStatus);
                        }
                    } catch (RuntimeException e) {
                        UnitOfWorkException unitOfWorkException = new UnitOfWorkException(msg("{} failed during beforeCommit", key.getClass().getName()), e);
                        unitOfWorkException.fillInStackTrace();
                        throw unitOfWorkException;
                    }
                });
                beforeCommitAfterCallingLifecycleCallbackResources(unitOfWork);
            }
        }

        @Override
        public void afterCommit() {
            if (readOnly) {
                log.debug("Ignoring afterCommit as the transaction is readOnly");
                return;
            }

            unitOfWork.status = UnitOfWorkStatus.Committed;
            log.trace("Calling UnitOfWorkLifecycleCallbacks#afterCommit of the Spring Transaction-Aware UnitOfWork");
            afterCommitBeforeCallingLifecycleCallbackResources(unitOfWork);
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
            afterCommitAfterCallingLifecycleCallbackResources(unitOfWork);
        }

        @Override
        public void afterCompletion(int status) {
            if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                unitOfWork.status = UnitOfWorkStatus.RolledBack;
                afterRollbackBeforeCallingLifecycleCallbackResources(unitOfWork);
                log.trace("Calling UnitOfWorkLifecycleCallbacks#afterRollback and of the Spring Transaction-Aware UnitOfWork");
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
                afterRollbackAfterCallingLifecycleCallbackResources(unitOfWork);
            } else if (status == TransactionSynchronization.STATUS_COMMITTED) {
                unitOfWork.status = UnitOfWorkStatus.Committed;
            } else if (status == TransactionSynchronization.STATUS_UNKNOWN) {
                // We assume unknown status means the transaction didn't complete, hence we will mark is as rolled-back
                log.warn("Transaction status was UNKNOWN. Marking the UnitOfWork as Rolled-back as its assumed the transaction didn't commit on the DB server");
                unitOfWork.status = UnitOfWorkStatus.RolledBack;
            }
            unitOfWork.cleanup();
            removeUnitOfWork();
        }


    }

    /**
     * Called during {@link TransactionSynchronization#afterCommit()} AFTER all registered {@link SpringTransactionAwareUnitOfWork#unitOfWorkLifecycleCallbackResources} have
     * had their {@link UnitOfWorkLifecycleCallback#afterCommit(UnitOfWork, List)} method called
     *
     * @param unitOfWork the unit of work
     */
    protected void afterCommitAfterCallingLifecycleCallbackResources(UOW unitOfWork) {
    }

    /**
     * Called during {@link TransactionSynchronization#afterCommit()} BEFORE any registered {@link SpringTransactionAwareUnitOfWork#unitOfWorkLifecycleCallbackResources} have
     * had their {@link UnitOfWorkLifecycleCallback#afterCommit(UnitOfWork, List)} method called
     *
     * @param unitOfWork the unit of work
     */
    protected void afterCommitBeforeCallingLifecycleCallbackResources(UOW unitOfWork) {
    }

    /**
     * Called during {@link TransactionSynchronization#beforeCommit(boolean)} AFTER all registered {@link SpringTransactionAwareUnitOfWork#unitOfWorkLifecycleCallbackResources} have
     * had their {@link UnitOfWorkLifecycleCallback#beforeCommit(UnitOfWork, List)} method called
     *
     * @param unitOfWork the unit of work
     */
    protected void beforeCommitAfterCallingLifecycleCallbackResources(UOW unitOfWork) {
    }

    /**
     * Called during {@link TransactionSynchronization#beforeCommit(boolean)} BEFORE any registered {@link SpringTransactionAwareUnitOfWork#unitOfWorkLifecycleCallbackResources} have
     * had their {@link UnitOfWorkLifecycleCallback#beforeCommit(UnitOfWork, List)} method called
     *
     * @param unitOfWork the unit of work
     */
    protected void beforeCommitBeforeCallingLifecycleCallbackResources(UOW unitOfWork) {
    }

    /**
     * Called during {@link TransactionSynchronization#afterCompletion(int)} BEFORE any registered {@link SpringTransactionAwareUnitOfWork#unitOfWorkLifecycleCallbackResources} have
     * had their {@link UnitOfWorkLifecycleCallback#afterRollback(UnitOfWork, List, Exception)} method called
     *
     * @param unitOfWork the unit of work
     */
    protected void afterRollbackBeforeCallingLifecycleCallbackResources(UOW unitOfWork) {
    }

    /**
     * Called during {@link TransactionSynchronization#afterCompletion(int)} AFTER all registered {@link SpringTransactionAwareUnitOfWork#unitOfWorkLifecycleCallbackResources} have
     * had their {@link UnitOfWorkLifecycleCallback#afterRollback(UnitOfWork, List, Exception)} method called
     *
     * @param unitOfWork the unit of work
     */
    protected void afterRollbackAfterCallingLifecycleCallbackResources(UOW unitOfWork) {

    }

}
