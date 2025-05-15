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

package dk.trustworks.essentials.components.foundation.fencedlock.api;


import dk.trustworks.essentials.components.foundation.fencedlock.DBFencedLock;
import dk.trustworks.essentials.components.foundation.fencedlock.DBFencedLockManager;
import dk.trustworks.essentials.components.foundation.fencedlock.LockName;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.trustworks.essentials.shared.security.EssentialsSecurityProvider;
import dk.trustworks.essentials.shared.security.EssentialsSecurityRoles;

import java.util.List;

import static dk.trustworks.essentials.shared.security.EssentialsSecurityRoles.*;
import static dk.trustworks.essentials.shared.security.EssentialsSecurityValidator.validateHasAnyEssentialsSecurityRoles;
import static dk.trustworks.essentials.shared.security.EssentialsSecurityValidator.validateHasEssentialsSecurityRole;

/**
 * Default implementation of the {@link DBFencedLockApi} interface, providing methods for managing
 * database-level fenced locks, including retrieving all locks and releasing specific locks.
 * <p>
 * This class is responsible for ensuring access control via role validation, utilizing the provided
 * {@link EssentialsSecurityProvider} to enforce the roles required for read and write operations.
 * </p>
 * <p>
 * It leverages a {@link DBFencedLockManager} to interact with the persistence layer managing the
 * locks and uses a {@link UnitOfWorkFactory} to manage transactional scopes for operations.
 * </p>
 */
public class DefaultDBFencedLockApi implements DBFencedLockApi {

    private final EssentialsSecurityProvider securityProvider;
    private final DBFencedLockManager<? extends UnitOfWork, DBFencedLock> fencedLockManager;
    private final UnitOfWorkFactory<? extends UnitOfWork> unitOfWorkFactory;

    public DefaultDBFencedLockApi(EssentialsSecurityProvider securityProvider, DBFencedLockManager<? extends UnitOfWork, DBFencedLock> fencedLockManager, UnitOfWorkFactory<? extends UnitOfWork> unitOfWorkFactory) {
        this.securityProvider = securityProvider;
        this.fencedLockManager = fencedLockManager;
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    private void validateLockReaderRole(Object principal) {
        validateHasAnyEssentialsSecurityRoles(securityProvider, principal, LOCK_READER, ESSENTIALS_ADMIN);
    }

    private void validateLockWriterRole(Object principal) {
        validateHasAnyEssentialsSecurityRoles(securityProvider, principal, LOCK_WRITER, ESSENTIALS_ADMIN);
    }

    @Override
    public List<ApiDBFencedLock> getAllLocks(Object principal) {
        validateLockReaderRole(principal);
        return unitOfWorkFactory.withUnitOfWork(uow ->
                fencedLockManager.getAllLocksInDB().stream()
                .map(ApiDBFencedLock::from)
                .toList());
    }

    @Override
    public boolean releaseLock(Object principal, LockName lockName) {
        validateLockWriterRole(principal);
        return unitOfWorkFactory.withUnitOfWork(uow ->
                fencedLockManager.releaseLockInDB(lockName));

    }
}
