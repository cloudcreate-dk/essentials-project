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

package dk.cloudcreate.essentials.components.foundation.fencedlock;

import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.shared.FailFast;

import java.time.*;
import java.util.*;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class DBFencedLock implements FencedLock {
    /**
     * The name of the lock
     */
    private LockName lockName;
    /**
     * The current token value as of the {@link #getLockLastConfirmedTimestamp()} for this Lock across all {@link FencedLockManager} instances<br>
     * Every time a lock is acquired a new token is issued (i.e. it's ever-growing monotonic value)
     * <p>
     * To avoid two lock holders from interacting with other services, the fencing token MUST be passed
     * to external services. The external services must store the largest fencing token received, whereby they can ignore
     * requests with a lower fencing token.
     */
    private Long     currentToken;

    /**
     * Which JVM/{@link FencedLockManager#getLockManagerInstanceId()} that has acquired this lock
     */
    private String         lockedByLockManagerInstanceId;
    /**
     * At what time did the JVM/{@link FencedLockManager#getLockManagerInstanceId()} that currently has acquired the lock acquire it (at first acquiring the lock_last_confirmed_ts is set to lock_acquired_ts)
     */
    private OffsetDateTime lockAcquiredTimestamp;
    /**
     * At what time did the JVM/{@link FencedLockManager}, that currently has acquired the lock, last confirm that it still has access to the lock
     */
    private OffsetDateTime lockLastConfirmedTimestamp;

    private transient DBFencedLockManager<? extends UnitOfWork, DBFencedLock> fencedLockManager;
    private transient List<LockCallback>  lockCallbacks;

    public DBFencedLock(DBFencedLockManager<? extends UnitOfWork, DBFencedLock> fencedLockManager,
                        LockName lockName,
                        Long currentToken,
                        String lockedByBusInstanceId,
                        OffsetDateTime lockAcquiredTimestamp,
                        OffsetDateTime lockLastConfirmedTimestamp) {
        this.fencedLockManager = fencedLockManager;
        this.lockName = lockName;
        this.currentToken = currentToken;
        this.lockedByLockManagerInstanceId = lockedByBusInstanceId;
        this.lockAcquiredTimestamp = lockAcquiredTimestamp;
        this.lockLastConfirmedTimestamp = lockLastConfirmedTimestamp;
        lockCallbacks = new ArrayList<>();
    }

    @Override
    public LockName getName() {
        return lockName;
    }

    @Override
    public Long getCurrentToken() {
        return currentToken;
    }

    @Override
    public String getLockedByLockManagerInstanceId() {
        return lockedByLockManagerInstanceId;
    }

    @Override
    public OffsetDateTime getLockAcquiredTimestamp() {
        return lockAcquiredTimestamp;
    }

    @Override
    public OffsetDateTime getLockLastConfirmedTimestamp() {
        return lockLastConfirmedTimestamp;
    }

    @Override
    public boolean isLocked() {
        return lockedByLockManagerInstanceId != null;
    }

    @Override
    public boolean isLockedByThisLockManagerInstance() {
        return isLocked() && Objects.equals(lockedByLockManagerInstanceId, fencedLockManager.getLockManagerInstanceId());
    }

    @Override
    public void release() {
        if (isLockedByThisLockManagerInstance()) {
            fencedLockManager.releaseLock(this);
        }
    }

    @Override
    public void registerCallback(LockCallback lockCallback) {
        lockCallbacks.add(lockCallback);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (DBFencedLock) o;
        return getName().equals(that.getName());
    }


    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }

    public Duration getDurationSinceLastConfirmation() {
        FailFast.requireNonNull(lockLastConfirmedTimestamp, msg("FencedLock '{}' doesn't have a lockLastConfirmedTimestamp", getName()));
        return Duration.between(lockLastConfirmedTimestamp, ZonedDateTime.now()).abs();
    }

    public void markAsReleased() {
        lockedByLockManagerInstanceId = null;
        lockCallbacks.forEach(lockCallback -> lockCallback.lockReleased(this));
    }

    DBFencedLock markAsConfirmed(OffsetDateTime confirmedTimestamp) {
        lockLastConfirmedTimestamp = confirmedTimestamp;
        return this;
    }

    public DBFencedLock markAsLocked(OffsetDateTime lockTime, String lockedByLockManagerInstanceId, long currentToken) {
        this.lockAcquiredTimestamp = lockTime;
        this.lockLastConfirmedTimestamp = lockTime;
        this.lockedByLockManagerInstanceId = lockedByLockManagerInstanceId;
        this.currentToken = currentToken;
        lockCallbacks.forEach(lockCallback -> lockCallback.lockAcquired(this));
        return this;
    }

    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "lockName=" + lockName +
                ", currentTokenIssuedToThisLockInstance=" + currentToken +
                ", lockedByLockManagerInstanceId='" + lockedByLockManagerInstanceId + '\'' +
                ", lockAcquiredTimestamp=" + lockAcquiredTimestamp +
                ", lockLastConfirmedTimestamp=" + lockLastConfirmedTimestamp +
                '}';
    }
}
