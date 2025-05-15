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

import dk.trustworks.essentials.components.foundation.fencedlock.LockName;

import java.util.List;

/**
 * This interface defines the contract for managing database-level fenced locks.
 * It provides methods to retrieve all locks and release specific locks.
 */
public interface DBFencedLockApi {

    /**
     * Retrieves all database-backed fenced locks currently present in the system.
     * This method ensures that the requesting principal has the appropriate
     * security role to perform the operation.
     *
     * @param principal the security principal requesting the list of locks.
     *                  This object is used to validate the security context of the caller.
     * @return a list of {@code ApiDBFencedLock} instances representing all the
     *         locks currently managed by the system.
     */
    List<ApiDBFencedLock> getAllLocks(Object principal);

    /**
     * Releases a database-backed fenced lock identified by the specified lock name.
     * This operation checks if the provided principal has sufficient permissions to
     * release the lock.
     *
     * @param principal the security principal requesting the lock release. This is
     *                  used to validate the security context of the caller.
     * @param lockName the name of the lock to be released.
     * @return {@code true} if the lock was successfully released, {@code false} otherwise.
     */
    boolean releaseLock(Object principal, LockName lockName);
}
