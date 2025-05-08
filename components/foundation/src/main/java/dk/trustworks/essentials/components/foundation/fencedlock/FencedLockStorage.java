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

package dk.trustworks.essentials.components.foundation.fencedlock;

import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FencedLockStorage<UOW extends UnitOfWork, LOCK extends DBFencedLock> {
    /**
     * Initialize the database storage (e.g. create table, collection, etc.)
     *
     * @param lockManager the lock manager
     * @param uow         the active unit of work
     */
    void initializeLockStorage(DBFencedLockManager<UOW, LOCK> lockManager, UOW uow);

    /**
     * Update the database status for the lock as being confirmed by this lock manager instance
     *
     * @param lockManager        the lock manager
     * @param uow                the active unit of work
     * @param fencedLock         the fenced lock instance that is being confirmed
     * @param confirmedTimestamp the new confirmed timestamp
     * @return <code>true</code> if the update in the DB was successful or <code>false</code> if we couldn't confirm the lock (e.g. because another lock manager instance had acquired the lock
     * and updated the {@link FencedLock#getCurrentToken()} value in the DB
     */
    boolean confirmLockInDB(DBFencedLockManager<UOW, LOCK> lockManager, UOW uow, LOCK fencedLock, OffsetDateTime confirmedTimestamp);

    /**
     * Update the database status for the lock as being released
     *
     * @param lockManager the lock manager
     * @param uow         the active unit of work
     * @param fencedLock  the fenced lock instance that is being released
     * @return <code>true</code> if the update in the DB was successful or <code>false</code> if we couldn't release the lock (e.g. because another lock manager instance had acquired the lock
     * and updated the {@link FencedLock#getCurrentToken()} value in the DB
     */
    boolean releaseLockInDB(DBFencedLockManager<UOW, LOCK> lockManager, UOW uow, LOCK fencedLock);

    /**
     * Lookup a Lock in the underlying database
     *
     * @param lockManager the lock manager
     * @param uow         the active unit of work
     * @param lockName    the name of the lock to find in the DB
     * @return the result of the search
     */
    Optional<LOCK> lookupLockInDB(DBFencedLockManager<UOW, LOCK> lockManager, UOW uow, LockName lockName);

    /**
     * Create an un-initialized lock (with a default DB specific un-initialized value for the {@link DBFencedLock#getCurrentToken()})
     *
     * @param lockManager the lock manager
     * @param lockName    the name of the lock
     * @return the concrete DB specific {@link DBFencedLock} subclass
     */
    LOCK createUninitializedLock(DBFencedLockManager<UOW, LOCK> lockManager,
                                 LockName lockName);

    /**
     * Create an initialized lock
     *
     * @param lockManager                   the lock manager
     * @param name                          the name of the lock
     * @param currentToken                  the current token value
     * @param lockedByLockManagerInstanceId the id (if any) of the local managed that has acquired the lock
     * @param lockAcquiredTimestamp         the lock Acquired Timestamp
     * @param lockLastConfirmedTimestamp    the lock Last Confirmed Timestamp
     * @return the concrete DB specific {@link DBFencedLock} subclass
     */
    LOCK createInitializedLock(DBFencedLockManager<UOW, LOCK> lockManager,
                               LockName name,
                               long currentToken,
                               String lockedByLockManagerInstanceId,
                               OffsetDateTime lockAcquiredTimestamp,
                               OffsetDateTime lockLastConfirmedTimestamp);

    /**
     * Return the DB specific uninitialized value for a {@link FencedLock#getCurrentToken()} that has yet to be persisted for the very first time
     *
     * @return the DB specific uninitialized value for a {@link FencedLock#getCurrentToken()} that has yet to be persisted for the very first time
     */
    Long getUninitializedTokenValue();

    /**
     * Return the DB specific value for a {@link FencedLock#getCurrentToken()} that is being persisted the very first time
     *
     * @return the DB specific value for a {@link FencedLock#getCurrentToken()} that is being persisted the very first time
     */
    long getInitialTokenValue();

    /**
     * Insert the local into the DB for the very first time
     *
     * @param lockManager                           the lock manager
     * @param uow                                   the unit of work
     * @param initialLock                           the initial and no yet persisted lock (with no {@link FencedLock#getCurrentToken()} value set yet)
     * @param lockAcquiredAndLastConfirmedTimestamp the timestamp of when the lock is being acquired and last confirmed
     * @return @return <code>true</code> if the insert in the DB was successful or <code>false</code> if we couldn't insert the lock (e.g. because another lock manager instance had acquired the lock)
     */
    boolean insertLockIntoDB(DBFencedLockManager<UOW, LOCK> lockManager, UOW uow, LOCK initialLock, OffsetDateTime lockAcquiredAndLastConfirmedTimestamp);

    /**
     * Update the lock in the DB.
     *
     * @param lockManager                     the lock manager
     * @param uow                             the unit of work
     * @param timedOutLock                    the lock that has timed out
     * @param newLockReadyToBeAcquiredLocally the lock that was acquired by this lock manager instance
     * @return <code>true</code> if the update in the DB was successful or <code>false</code> if we couldn't confirm the lock (e.g. because another lock manager instance had acquired the lock)
     */
    boolean updateLockInDB(DBFencedLockManager<UOW, LOCK> lockManager, UOW uow, LOCK timedOutLock, LOCK newLockReadyToBeAcquiredLocally);

    /**
     * Delete a lock in the DB. Should be used with caution (i.e. when you're sure that the lock is no longer used by any nodes
     *
     * @param lockManager  the lock manager
     * @param uow          the unit of work
     * @param nameOfLockToDelete the name of the lock to delete
     */
    void deleteLockInDB(DBFencedLockManager<UOW, LOCK> lockManager, UOW uow, LockName nameOfLockToDelete);

    /**
     * Delete all locks in the DB. Should be used with caution and typically only for testing purposes
     *
     * @param lockManager the lock manager
     * @param uow         the unit of work
     */
    void deleteAllLocksInDB(DBFencedLockManager<UOW, LOCK> lockManager, UOW uow);

}
