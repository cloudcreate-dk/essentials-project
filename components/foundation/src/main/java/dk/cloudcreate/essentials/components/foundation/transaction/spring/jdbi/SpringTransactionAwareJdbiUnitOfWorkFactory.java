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

package dk.cloudcreate.essentials.components.foundation.transaction.spring.jdbi;

import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.*;
import org.jdbi.v3.core.*;
import org.slf4j.*;
import org.springframework.transaction.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * A {@link SpringTransactionAwareUnitOfWorkFactory} version of the {@link HandleAwareUnitOfWorkFactory} which supports the standard {@link HandleAwareUnitOfWork} using {@link Jdbi}
 */
public class SpringTransactionAwareJdbiUnitOfWorkFactory
        extends SpringTransactionAwareUnitOfWorkFactory<PlatformTransactionManager, SpringTransactionAwareJdbiUnitOfWorkFactory.SpringTransactionAwareHandleAwareUnitOfWork>
        implements HandleAwareUnitOfWorkFactory<SpringTransactionAwareJdbiUnitOfWorkFactory.SpringTransactionAwareHandleAwareUnitOfWork> {
    private static final Logger log = LoggerFactory.getLogger(SpringTransactionAwareJdbiUnitOfWorkFactory.class);
    final                Jdbi   jdbi;

    public SpringTransactionAwareJdbiUnitOfWorkFactory(Jdbi jdbi,
                                                       PlatformTransactionManager platformTransactionManager) {
        super(platformTransactionManager);
        this.jdbi = requireNonNull(jdbi, "No jdbi instance provided");
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    @Override
    protected SpringTransactionAwareHandleAwareUnitOfWork createUnitOfWorkForFactoryManagedTransaction(TransactionStatus transaction) {
        return new SpringTransactionAwareHandleAwareUnitOfWork(this, transaction);
    }

    @Override
    protected SpringTransactionAwareHandleAwareUnitOfWork createUnitOfWorkForSpringManagedTransaction() {
        return new SpringTransactionAwareHandleAwareUnitOfWork(this);
    }

    public static class SpringTransactionAwareHandleAwareUnitOfWork extends SpringTransactionAwareUnitOfWork<PlatformTransactionManager, SpringTransactionAwareHandleAwareUnitOfWork> implements HandleAwareUnitOfWork {
        private static final Logger log = LoggerFactory.getLogger(SpringTransactionAwareHandleAwareUnitOfWork.class);
        private              Handle handle;

        public SpringTransactionAwareHandleAwareUnitOfWork(SpringTransactionAwareUnitOfWorkFactory<PlatformTransactionManager, SpringTransactionAwareHandleAwareUnitOfWork> unitOfWorkFactory) {
            super(unitOfWorkFactory);
        }

        public SpringTransactionAwareHandleAwareUnitOfWork(SpringTransactionAwareUnitOfWorkFactory<PlatformTransactionManager, SpringTransactionAwareHandleAwareUnitOfWork> unitOfWorkFactory, TransactionStatus manuallyManagedSpringTransaction) {
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
            handle = ((SpringTransactionAwareJdbiUnitOfWorkFactory) unitOfWorkFactory).jdbi.open();
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
    }
}