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

import java.time.OffsetDateTime;

/**
 * Represents a data transfer object for a database-backed fenced lock.
 * This class provides a record structure to encapsulate the state of a lock
 * and its associated metadata.
 */
public record ApiDBFencedLock(
        String lockName,
        Long currentToken,
        String lockedByLockManagerInstanceId,
        OffsetDateTime lockAcquiredTimestamp,
        OffsetDateTime lockLastConfirmedTimestamp
) {

    /**
     * Converts a {@link DBFencedLock} instance into an {@link ApiDBFencedLock} instance.
     *
     * @param lock the {@link DBFencedLock} instance to be converted
     * @return a new {@link ApiDBFencedLock} instance created from the given {@link DBFencedLock}
     */
    public static ApiDBFencedLock from(DBFencedLock lock) {
        return new ApiDBFencedLock(lock.getName().toString(),
                lock.getCurrentToken(),
                lock.getLockedByLockManagerInstanceId(),
                lock.getLockAcquiredTimestamp(),
                lock.getLockLastConfirmedTimestamp()
        );
    }
}
