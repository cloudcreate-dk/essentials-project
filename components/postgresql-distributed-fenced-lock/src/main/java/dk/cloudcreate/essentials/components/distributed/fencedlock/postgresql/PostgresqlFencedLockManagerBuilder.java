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

package dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql;

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.reactive.*;
import org.jdbi.v3.core.Jdbi;

import java.time.Duration;
import java.util.Optional;

public class PostgresqlFencedLockManagerBuilder {
    private Jdbi                                                          jdbi;
    private HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
    private Optional<String>                                              lockManagerInstanceId = Optional.empty();
    private Optional<String>                                              fencedLocksTableName  = Optional.empty();
    private Duration                                                      lockTimeOut;
    private Duration                                                      lockConfirmationInterval;
    private Optional<EventBus>                                    eventBus              = Optional.empty();

    /**
     * @param jdbi the jdbi instance used
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setJdbi(Jdbi jdbi) {
        this.jdbi = jdbi;
        return this;
    }

    /**
     * @param unitOfWorkFactory The unit of work factory
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setUnitOfWorkFactory(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
        this.unitOfWorkFactory = unitOfWorkFactory;
        return this;
    }

    /**
     * @param lockManagerInstanceId The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setLockManagerInstanceId(Optional<String> lockManagerInstanceId) {
        this.lockManagerInstanceId = lockManagerInstanceId;
        return this;
    }

    /**
     * @param fencedLocksTableName the name of the table where the fenced locks will be maintained - {@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME}
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setFencedLocksTableName(Optional<String> fencedLocksTableName) {
        this.fencedLocksTableName = fencedLocksTableName;
        return this;
    }

    /**
     * @param lockManagerInstanceId The unique name for this lock manager instance. If null then the machines hostname is used
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setLockManagerInstanceId(String lockManagerInstanceId) {
        this.lockManagerInstanceId = Optional.ofNullable(lockManagerInstanceId);
        return this;
    }

    /**
     * @param fencedLocksTableName the name of the table where the fenced locks will be maintained - {@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME}
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setFencedLocksTableName(String fencedLocksTableName) {
        this.fencedLocksTableName = Optional.ofNullable(fencedLocksTableName);
        return this;
    }

    /**
     * @param lockTimeOut the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setLockTimeOut(Duration lockTimeOut) {
        this.lockTimeOut = lockTimeOut;
        return this;
    }

    /**
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setLockConfirmationInterval(Duration lockConfirmationInterval) {
        this.lockConfirmationInterval = lockConfirmationInterval;
        return this;
    }

    /**
     * @param eventBus optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setEventBus(Optional<EventBus> eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    /**
     * @param eventBus optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     * @return this builder instance
     */
    public PostgresqlFencedLockManagerBuilder setEventBus(EventBus eventBus) {
        this.eventBus = Optional.ofNullable(eventBus);
        return this;
    }

    /**
     * Create the new {@link PostgresqlFencedLockManager} instance
     *
     * @return the new {@link PostgresqlFencedLockManager} instance
     */
    public PostgresqlFencedLockManager build() {
        return new PostgresqlFencedLockManager(jdbi,
                                               unitOfWorkFactory,
                                               lockManagerInstanceId,
                                               fencedLocksTableName,
                                               lockTimeOut,
                                               lockConfirmationInterval,
                                               eventBus);
    }

    /**
     * Create the new {@link PostgresqlFencedLockManager} instance and call {@link PostgresqlFencedLockManager#start()} immediately
     *
     * @return the new {@link PostgresqlFencedLockManager} instance
     */
    public PostgresqlFencedLockManager buildAndStart() {
        var instance = build();
        instance.start();
        return instance;
    }
}