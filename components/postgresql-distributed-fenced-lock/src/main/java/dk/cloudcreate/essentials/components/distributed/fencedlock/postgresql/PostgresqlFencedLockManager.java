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

package dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql;

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import org.jdbi.v3.core.Jdbi;

import java.time.Duration;
import java.util.Optional;

/**
 * Postgresql version of the {@link FencedLockManager} interface
 */
public class PostgresqlFencedLockManager extends DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> {
    public static PostgresqlFencedLockManagerBuilder builder() {
        return new PostgresqlFencedLockManagerBuilder();
    }

    /**
     * @param jdbi                     the jdbi instance
     * @param unitOfWorkFactory        The unit of work factory
     * @param lockManagerInstanceId    The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param fencedLocksTableName     the name of the table where the fenced locks will be maintained - {@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME}
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public PostgresqlFencedLockManager(Jdbi jdbi,
                                       HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                       Optional<String> lockManagerInstanceId,
                                       Optional<String> fencedLocksTableName,
                                       Duration lockTimeOut,
                                       Duration lockConfirmationInterval) {
        super(new PostgresqlFencedLockStorage(jdbi,
                                              fencedLocksTableName),
              unitOfWorkFactory,
              lockManagerInstanceId,
              lockTimeOut,
              lockConfirmationInterval
        );
    }

    /**
     * Create a {@link PostgresqlFencedLockManager} with the machines hostname as lock manager instance name and the default fenced lock table name ({@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME})
     *
     * @param jdbi                     the jdbi instance
     * @param unitOfWorkFactory        The unit of work factory
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     */
    public PostgresqlFencedLockManager(Jdbi jdbi,
                                       HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                       Duration lockTimeOut,
                                       Duration lockConfirmationInterval) {
        this(jdbi,
             unitOfWorkFactory,
             Optional.empty(),
             Optional.empty(),
             lockTimeOut,
             lockConfirmationInterval
        );
    }
}
