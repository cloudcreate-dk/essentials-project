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

import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.jdbi.*;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.*;

import java.time.OffsetDateTime;
import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class PostgresqlFencedLockStorage implements FencedLockStorage<HandleAwareUnitOfWork, DBFencedLock> {
    private static final Logger log = LoggerFactory.getLogger(PostgresqlFencedLockStorage.class);
    public static final  long   FIRST_TOKEN              = 1L;
    public static final  long   UNINITIALIZED_LOCK_TOKEN = -1L;
    public static final String DEFAULT_FENCED_LOCKS_TABLE_NAME = "fenced_locks";

    private final Jdbi   jdbi;
    private final String fencedLocksTableName;

    public PostgresqlFencedLockStorage(Jdbi jdbi, Optional<String> fencedLocksTableName) {
        requireNonNull(fencedLocksTableName, "No fencedLocksTableName option provided");
        this.jdbi = requireNonNull(jdbi, "You must supply a jdbi instance");
        this.fencedLocksTableName = fencedLocksTableName.orElse(DEFAULT_FENCED_LOCKS_TABLE_NAME);

        jdbi.registerArgument(new LockNameArgumentFactory());
        jdbi.registerColumnMapper(new LockNameColumnMapper());
    }

    @Override
    public void initializeLockStorage(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager, HandleAwareUnitOfWork unitOfWork) {
        var rowsUpdated = unitOfWork.handle().execute("CREATE TABLE IF NOT EXISTS " + fencedLocksTableName + " (\n" +
                                                              "lock_name TEXT NOT NULL,\n" +      // The name of the lock
                                                              "last_issued_fence_token bigint,\n" +    // The token issued at lock_last_confirmed_ts. Every time a lock is acquired or confirmed a new token is issued (ever growing value)
                                                              "locked_by_lockmanager_instance_id TEXT,\n" + // which JVM/Bus instance acquired the lock
                                                              "lock_acquired_ts TIMESTAMP WITH TIME ZONE,\n" + // at what time did the JVM/Bus instance acquire the lock (at first acquiring the lock_last_confirmed_ts is set to lock_acquired_ts)
                                                              "lock_last_confirmed_ts TIMESTAMP WITH TIME ZONE,\n" + // when did the JVM/Bus instance that acquired the lock last confirm that it still has access to the lock
                                                              "PRIMARY KEY (lock_name)\n" +
                                                              ")");
        if (rowsUpdated == 1) {
            log.info("[{}] Created the '{}' fenced locks table", lockManager.getLockManagerInstanceId(), fencedLocksTableName);
        }

        // -------------------------------------------------------------------------------
        var indexName = fencedLocksTableName + "_current_token_index";
        rowsUpdated = unitOfWork.handle().execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + fencedLocksTableName + " (lock_name, last_issued_fence_token)");
        if (rowsUpdated == 1) {
            log.debug("[{}] Created the '{}' index on fenced locks table '{}'", lockManager.getLockManagerInstanceId(), indexName, fencedLocksTableName);
        }
    }

    @Override
    public boolean insertLockIntoDB(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                                    HandleAwareUnitOfWork unitOfWork,
                                    DBFencedLock initialLock,
                                    OffsetDateTime lockAcquiredAndLastConfirmedTimestamp) {
        var rowsUpdated = unitOfWork.handle()
                                    .createUpdate("INSERT INTO " + fencedLocksTableName + " (" +
                                                          "lock_name, last_issued_fence_token, locked_by_lockmanager_instance_id ,\n" +
                                                          "lock_acquired_ts, lock_last_confirmed_ts)\n" +
                                                          " VALUES (\n" +
                                                          ":lock_name, :last_issued_fence_token, :locked_by_lockmanager_instance_id,\n" +
                                                          ":lock_acquired_ts, :lock_last_confirmed_ts) ON CONFLICT DO NOTHING")
                                    .bind("lock_name", initialLock.getName())
                                    .bind("last_issued_fence_token", getInitialTokenValue())
                                    .bind("locked_by_lockmanager_instance_id", lockManager.getLockManagerInstanceId())
                                    .bind("lock_acquired_ts", lockAcquiredAndLastConfirmedTimestamp)
                                    .bind("lock_last_confirmed_ts", lockAcquiredAndLastConfirmedTimestamp)
                                    .execute();
        return rowsUpdated == 1;
    }

    @Override
    public boolean updateLockInDB(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                                  HandleAwareUnitOfWork unitOfWork,
                                  DBFencedLock timedOutLock,
                                  DBFencedLock newLockReadyToBeAcquiredLocally) {
        var rowsUpdated = unitOfWork.handle()
                                    .createUpdate("UPDATE " + fencedLocksTableName + " SET " +
                                                          "last_issued_fence_token=:last_issued_fence_token, " +
                                                          "locked_by_lockmanager_instance_id=:locked_by_lockmanager_instance_id,\n" +
                                                          "lock_acquired_ts=:lock_acquired_ts, " +
                                                          "lock_last_confirmed_ts=:lock_last_confirmed_ts\n" +
                                                          "WHERE lock_name=:lock_name AND last_issued_fence_token=:previous_last_issued_fence_token")
                                    .bind("lock_name", timedOutLock.getName())
                                    .bind("last_issued_fence_token", newLockReadyToBeAcquiredLocally.getCurrentToken())
                                    .bind("locked_by_lockmanager_instance_id", newLockReadyToBeAcquiredLocally.getLockedByLockManagerInstanceId())
                                    .bind("lock_acquired_ts", newLockReadyToBeAcquiredLocally.getLockAcquiredTimestamp())
                                    .bind("lock_last_confirmed_ts", newLockReadyToBeAcquiredLocally.getLockLastConfirmedTimestamp())
                                    .bind("previous_last_issued_fence_token", timedOutLock.getCurrentToken())
                                    .execute();
        return rowsUpdated == 1;
    }

    @Override
    public boolean confirmLockInDB(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                                   HandleAwareUnitOfWork unitOfWork,
                                   DBFencedLock fencedLock,
                                   OffsetDateTime confirmedTimestamp) {
        var rowsUpdated = unitOfWork.handle()
                                    .createUpdate("UPDATE " + fencedLocksTableName + " SET " +
                                                          "lock_last_confirmed_ts=:lock_last_confirmed_ts\n" +
                                                          "WHERE lock_name=:lock_name AND last_issued_fence_token=:last_issued_fence_token AND locked_by_lockmanager_instance_id=:locked_by_lockmanager_instance_id")
                                    .bind("lock_name", fencedLock.getName())
                                    .bind("locked_by_lockmanager_instance_id", requireNonNull(fencedLock.getLockedByLockManagerInstanceId(), msg("[{}] getLockedByLockManagerInstanceId was NULL. Details: {}", lockManager.getLockManagerInstanceId(), fencedLock)))
                                    .bind("lock_last_confirmed_ts", confirmedTimestamp)
                                    .bind("last_issued_fence_token", fencedLock.getCurrentToken())
                                    .execute();
        return rowsUpdated == 1;
    }

    @Override
    public boolean releaseLockInDB(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                                   HandleAwareUnitOfWork unitOfWork,
                                   DBFencedLock fencedLock) {
        var rowsUpdated = unitOfWork.handle()
                                    .createUpdate("UPDATE " + fencedLocksTableName + " SET " +
                                                          "locked_by_lockmanager_instance_id=NULL\n" +
                                                          "WHERE lock_name=:lock_name AND last_issued_fence_token=:lock_last_issued_token")
                                    .bind("lock_name", fencedLock.getName())
                                    .bind("lock_last_issued_token", fencedLock.getCurrentToken())
                                    .execute();
        return rowsUpdated == 1;
    }

    @Override
    public Optional<DBFencedLock> lookupLockInDB(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                                                 HandleAwareUnitOfWork unitOfWork,
                                                 LockName lockName) {
        return unitOfWork.handle()
                         .createQuery("SELECT * FROM " + fencedLocksTableName + " WHERE lock_name=:lock_name")
                         .bind("lock_name", lockName)
                         .map(row -> new DBFencedLock(lockManager,
                                                      lockName,
                                                      row.getColumn("last_issued_fence_token", Long.class),
                                                      row.getColumn("locked_by_lockmanager_instance_id", String.class),
                                                      row.getColumn("lock_acquired_ts", OffsetDateTime.class),
                                                      row.getColumn("lock_last_confirmed_ts", OffsetDateTime.class)))
                         .findOne();
    }

    @Override
    public DBFencedLock createUninitializedLock(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                                                LockName lockName) {
        return new DBFencedLock(lockManager,
                                lockName,
                                getUninitializedTokenValue(),
                                null,
                                null,
                                null);
    }

    @Override
    public DBFencedLock createInitializedLock(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                                              LockName name,
                                              long currentToken,
                                              String lockedByLockManagerInstanceId,
                                              OffsetDateTime lockAcquiredTimestamp,
                                              OffsetDateTime lockLastConfirmedTimestamp) {
        return new DBFencedLock(lockManager,
                                name,
                                currentToken,
                                lockedByLockManagerInstanceId,
                                lockAcquiredTimestamp,
                                lockLastConfirmedTimestamp);
    }

    @Override
    public Long getUninitializedTokenValue() {
        return UNINITIALIZED_LOCK_TOKEN;
    }

    @Override
    public long getInitialTokenValue() {
        return FIRST_TOKEN;
    }

    @Override
    public void deleteLockInDB(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                               HandleAwareUnitOfWork unitOfWork,
                               LockName nameOfLockToDelete) {
        var rowsUpdated = unitOfWork.handle()
                                    .createUpdate("DELETE FROM " + fencedLocksTableName +
                                                          "WHERE lock_name=:lock_name")
                                    .bind("lock_name", nameOfLockToDelete)
                                    .execute();
        if (rowsUpdated == 1) {
            log.debug("[{}] Deleted lock '{}'", lockManager.getLockManagerInstanceId(),
                      nameOfLockToDelete);
        }

    }

    @Override
    public void deleteAllLocksInDB(DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> lockManager,
                                   HandleAwareUnitOfWork unitOfWork) {
        var rowsUpdated = unitOfWork.handle()
                                    .createUpdate("DELETE FROM " + fencedLocksTableName)
                                    .execute();
        log.debug("[{}] Deleted all {} locks", lockManager.getLockManagerInstanceId(), rowsUpdated);
    }
}
