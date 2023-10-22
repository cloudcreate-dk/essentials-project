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

package dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo;

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.transaction.mongo.ClientSessionAwareUnitOfWork;
import org.bson.Document;
import org.slf4j.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.*;

import java.time.*;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class MongoFencedLockStorage implements FencedLockStorage<ClientSessionAwareUnitOfWork, DBFencedLock> {
    protected static final Logger log                                  = LoggerFactory.getLogger(MongoFencedLockStorage.class);
    public static final  long   FIRST_TOKEN                          = 1L;
    public static final  Long   UNINITIALIZED_LOCK_TOKEN             = -1L;
    public static final  String DEFAULT_FENCED_LOCKS_COLLECTION_NAME = "fenced_locks";

    protected final MongoTemplate  mongoTemplate;
    protected final MongoConverter mongoConverter;
    protected final String         fencedLocksCollectionName;

    public MongoFencedLockStorage(MongoTemplate mongoTemplate,
                                  MongoConverter mongoConverter,
                                  Optional<String> fencedLocksCollectionName) {
        this.mongoTemplate = requireNonNull(mongoTemplate, "You must supply a mongoTemplate instance");
        this.mongoConverter = requireNonNull(mongoConverter, "You must supply a mongoConverter instance");
        this.fencedLocksCollectionName = fencedLocksCollectionName.orElse(DEFAULT_FENCED_LOCKS_COLLECTION_NAME);
        initializeFencedLockCollection(mongoTemplate, fencedLocksCollectionName);
    }

    /**
     * Override only if the default collection or index name approach doesn't work for you
     */
    protected void initializeFencedLockCollection(MongoTemplate mongoTemplate, Optional<String> fencedLocksCollectionName) {
        if (!mongoTemplate.collectionExists(this.fencedLocksCollectionName)) {
            try {
                mongoTemplate.createCollection(this.fencedLocksCollectionName);
            } catch (Exception e) {
                if (!mongoTemplate.collectionExists(this.fencedLocksCollectionName)) {
                    throw new RuntimeException(msg("Failed to create FencedLock collection '{}'", this.fencedLocksCollectionName), e);
                }
            }
        }


        // Ensure indexes
        var indexes = List.of(new Index( )
                                      .named("find_lock")
                                      .on("name", Sort.Direction.ASC)
                                      .on("lastIssuedFencedToken", Sort.Direction.ASC),
                              new Index()
                                      .named("confirm_lock")
                                      .on("name", Sort.Direction.ASC)
                                      .on("lastIssuedFencedToken", Sort.Direction.ASC)
                                      .on("lockedByLockManagerInstanceId", Sort.Direction.ASC));
        indexes.forEach(index -> {
            log.debug("Ensuring Index on Collection '{}': {}",
                      fencedLocksCollectionName,
                      index);
            mongoTemplate.indexOps(this.fencedLocksCollectionName)
                         .ensureIndex(index);
        });
    }

    @Override
    public void initializeLockStorage(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                      ClientSessionAwareUnitOfWork unitOfWork) {
        // Do nothing as mongo doesn't support listCollections, etc in a multi document transaction
    }

    @Override
    public boolean insertLockIntoDB(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                    ClientSessionAwareUnitOfWork unitOfWork,
                                    DBFencedLock initialLock,
                                    OffsetDateTime lockAcquiredAndLastConfirmedTimestamp) {
        var dbInitialLock = new MongoFencedLock(initialLock);
        dbInitialLock.setLastIssuedFencedToken(getInitialTokenValue());
        dbInitialLock.setLockedByLockManagerInstanceId(lockManager.getLockManagerInstanceId());
        dbInitialLock.setLockAcquiredTimestamp(lockAcquiredAndLastConfirmedTimestamp.toInstant());
        dbInitialLock.setLockLastConfirmedTimestamp(lockAcquiredAndLastConfirmedTimestamp.toInstant());
        var result = mongoTemplate.execute(fencedLocksCollectionName, collection -> {
            var document = toDocument(dbInitialLock, mongoConverter);
            try {
                collection.insertOne(document);
                return dbInitialLock;
            } catch (DuplicateKeyException e) {
                unitOfWork.markAsRollbackOnly(e);
                return null;
            }
        });
        return result != null;
    }

    @Override
    public boolean updateLockInDB(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                  ClientSessionAwareUnitOfWork unitOfWork,
                                  DBFencedLock timedOutLock,
                                  DBFencedLock newLockReadyToBeAcquiredLocally) {
        var tokenOfLockToBeUpdated = timedOutLock.getCurrentToken();
        var query = new Query(Criteria.where("name").is(timedOutLock.getName())
                                      .and("lastIssuedFencedToken").is(tokenOfLockToBeUpdated));
        var dbLock = new MongoFencedLock(newLockReadyToBeAcquiredLocally);
        dbLock.setLastIssuedFencedToken(newLockReadyToBeAcquiredLocally.getCurrentToken());
        dbLock.setLockedByLockManagerInstanceId(lockManager.getLockManagerInstanceId());
        dbLock.setLockAcquiredTimestamp(newLockReadyToBeAcquiredLocally.getLockAcquiredTimestamp().toInstant());
        dbLock.setLockLastConfirmedTimestamp(newLockReadyToBeAcquiredLocally.getLockLastConfirmedTimestamp().toInstant());

        var doc    = toDocument(dbLock, mongoConverter);
        var update = Update.fromDocument(doc, "_id");
        var result = mongoTemplate.updateFirst(query,
                                               update,
                                               MongoFencedLock.class,
                                               fencedLocksCollectionName);
        return result.getModifiedCount() == 1;
    }


    @Override
    public boolean confirmLockInDB(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                   ClientSessionAwareUnitOfWork unitOfWork,
                                   DBFencedLock fencedLock,
                                   OffsetDateTime confirmedTimestamp) {
        var query = new Query(Criteria.where("name").is(fencedLock.getName())
                                      .and("lastIssuedFencedToken").is(fencedLock.getCurrentToken())
                                      .and("lockedByLockManagerInstanceId").is(fencedLock.getLockedByLockManagerInstanceId()));
        var dbLock = new MongoFencedLock(fencedLock);
        dbLock.setLastIssuedFencedToken(fencedLock.getCurrentToken());
        dbLock.setLockLastConfirmedTimestamp(confirmedTimestamp.toInstant());

        var doc    = toDocument(dbLock, mongoConverter);
        var update = Update.fromDocument(doc, "_id");
        var result = mongoTemplate.updateFirst(query,
                                               update,
                                               MongoFencedLock.class,
                                               fencedLocksCollectionName);
        return result.getModifiedCount() == 1;
    }

    @Override
    public boolean releaseLockInDB(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                   ClientSessionAwareUnitOfWork unitOfWork,
                                   DBFencedLock fencedLock) {
        var query = new Query(Criteria.where("name").is(fencedLock.getName())
                                      .and("lastIssuedFencedToken").is(fencedLock.getCurrentToken()));
        var dbLock = new MongoFencedLock(fencedLock);
        dbLock.setLockedByLockManagerInstanceId(null);

        var doc    = toDocument(dbLock, mongoConverter);
        var update = Update.fromDocument(doc, "_id");
        var result = mongoTemplate.updateFirst(query,
                                               update,
                                               MongoFencedLock.class,
                                               fencedLocksCollectionName);
        return result.getModifiedCount() == 1;
    }

    @Override
    public Optional<DBFencedLock> lookupLockInDB(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                                 ClientSessionAwareUnitOfWork unitOfWork,
                                                 LockName lockName) {
        var lock_ = mongoTemplate.findOne(new Query()
                                                  .addCriteria(Criteria.where("name").is(lockName.toString())),
                                          MongoFencedLock.class,
                                          fencedLocksCollectionName);
        return Optional.ofNullable(lock_)
                       .map(lock -> new DBFencedLock(lockManager,
                                                     lockName,
                                                     lock.getLastIssuedFencedToken(),
                                                     lock.getLockedByLockManagerInstanceId(),
                                                     lock.lockAcquiredTimestamp != null ? lock.lockAcquiredTimestamp.atOffset(ZoneOffset.UTC) : null,
                                                     lock.lockLastConfirmedTimestamp != null ? lock.lockLastConfirmedTimestamp.atOffset(ZoneOffset.UTC) : null));

    }

    @Override
    public DBFencedLock createUninitializedLock(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                                LockName lockName) {
        return new DBFencedLock(lockManager,
                                lockName,
                                getUninitializedTokenValue(),
                                null,
                                null,
                                null);
    }

    @Override
    public DBFencedLock createInitializedLock(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                              LockName name,
                                              long currentToken,
                                              String lockedByLockManagerInstanceId,
                                              OffsetDateTime lockAcquiredTimestamp,
                                              OffsetDateTime lockLastConfirmedTimestamp) {
        return new DBFencedLock(requireNonNull(lockManager, "lockManager is null"),
                                requireNonNull(name, "name is null"),
                                currentToken,
                                requireNonNull(lockedByLockManagerInstanceId, "lockedByLockManagerInstanceId is null"),
                                requireNonNull(lockAcquiredTimestamp, "lockAcquiredTimestamp is null"),
                                requireNonNull(lockLastConfirmedTimestamp, "lockLastConfirmedTimestamp is null"));
    }

    @Override
    public Long getUninitializedTokenValue() {
        return UNINITIALIZED_LOCK_TOKEN;
    }

    @Override
    public long getInitialTokenValue() {
        return FIRST_TOKEN;
    }


    private Document toDocument(MongoFencedLock bean, MongoWriter<? super MongoFencedLock> writer) {
        Document document = new Document();
        writer.write(bean, document);
        if (document.containsKey("_id") && document.get("_id") == null) {
            document.remove("_id");
        }
        return document;
    }

    @Override
    public void deleteLockInDB(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                               ClientSessionAwareUnitOfWork unitOfWork,
                               LockName nameOfLockToDelete) {
        var result = mongoTemplate.remove(new Query()
                                                  .addCriteria(Criteria.where("name").is(nameOfLockToDelete)),
                                          MongoFencedLock.class,
                                          fencedLocksCollectionName);
        if (result.getDeletedCount() == 1) {
            log.debug("[{}] Deleted lock '{}'", lockManager.getLockManagerInstanceId(),
                      nameOfLockToDelete);
        }
    }

    @Override
    public void deleteAllLocksInDB(DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> lockManager,
                                   ClientSessionAwareUnitOfWork unitOfWork) {
        var result = mongoTemplate.remove(new Query(), MongoFencedLock.class, fencedLocksCollectionName);
        log.debug("[{}] Deleted all {} locks", lockManager.getLockManagerInstanceId(), result.getDeletedCount());
    }

    private static class MongoFencedLock {
        /**
         * The name of the lock
         */
        @Id
        private LockName name;
        /**
         * The current token value as of the {@link FencedLock#getLockLastConfirmedTimestamp()} for this Lock across all {@link FencedLockManager} instances<br>
         * Every time a lock is acquired a new token is issued (i.e. it's ever-growing monotonic value)
         * <br>
         * An uninitialized Lock has token value null. This is an ever-growing monotonic value.<br/>
         * The fencing token is incremented everytime the Lock instance is acquired<br>
         * <p>
         * To avoid two lock holders from interacting with other services, the fencing token MUST be passed
         * to external services. The external services must store the largest fencing token received, whereby they can ignore
         * requests with a lower fencing token.
         */
        private Long     lastIssuedFencedToken;
        /**
         * Which JVM/{@link FencedLockManager#getLockManagerInstanceId()} that has acquired this lock
         */
        private String   lockedByLockManagerInstanceId;
        /**
         * At what time did the JVM/{@link FencedLockManager#getLockManagerInstanceId()} that currently has acquired the lock acquire it (at first acquiring the lock_last_confirmed_ts is set to lock_acquired_ts)
         */
        private Instant  lockAcquiredTimestamp;
        /**
         * At what time did the JVM/{@link FencedLockManager}, that currently has acquired the lock, last confirm that it still has access to the lock
         */
        private Instant  lockLastConfirmedTimestamp;

        public MongoFencedLock() {
        }

        public MongoFencedLock(LockName name,
                               Long lastIssuedFencedToken,
                               String lockedByLockManagerInstanceId,
                               Instant lockAcquiredTimestamp,
                               Instant lockLastConfirmedTimestamp) {
            this.name = name;
            this.lastIssuedFencedToken = lastIssuedFencedToken;
            this.lockedByLockManagerInstanceId = lockedByLockManagerInstanceId;
            this.lockAcquiredTimestamp = lockAcquiredTimestamp;
            this.lockLastConfirmedTimestamp = lockLastConfirmedTimestamp;
        }

        public MongoFencedLock(DBFencedLock lock) {
            this(lock.getName(),
                 lock.getCurrentToken(),
                 lock.getLockedByLockManagerInstanceId(),
                 lock.getLockAcquiredTimestamp() != null ? lock.getLockAcquiredTimestamp().toInstant() : null,
                 lock.getLockLastConfirmedTimestamp() != null ? lock.getLockLastConfirmedTimestamp().toInstant() : null);
        }

        public LockName getName() {
            return name;
        }

        public Long getLastIssuedFencedToken() {
            return lastIssuedFencedToken;
        }

        public void setLastIssuedFencedToken(Long currentToken) {
            this.lastIssuedFencedToken = currentToken;
        }

        public String getLockedByLockManagerInstanceId() {
            return lockedByLockManagerInstanceId;
        }

        public void setLockedByLockManagerInstanceId(String lockedByLockManagerInstanceId) {
            this.lockedByLockManagerInstanceId = lockedByLockManagerInstanceId;
        }

        public Instant getLockAcquiredTimestamp() {
            return lockAcquiredTimestamp;
        }

        public void setLockAcquiredTimestamp(Instant lockAcquiredTimestamp) {
            this.lockAcquiredTimestamp = lockAcquiredTimestamp;
        }

        public Instant getLockLastConfirmedTimestamp() {
            return lockLastConfirmedTimestamp;
        }

        public void setLockLastConfirmedTimestamp(Instant lockLastConfirmedTimestamp) {
            this.lockLastConfirmedTimestamp = lockLastConfirmedTimestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MongoFencedLock that = (MongoFencedLock) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "MongoFencedLock{" +
                    "lockName=" + name +
                    ", lastIssuedFencedToken=" + lastIssuedFencedToken +
                    ", lockedByLockManagerInstanceId='" + lockedByLockManagerInstanceId + '\'' +
                    ", lockAcquiredTimestamp=" + lockAcquiredTimestamp +
                    ", lockLastConfirmedTimestamp=" + lockLastConfirmedTimestamp +
                    '}';
        }
    }
}
