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

package dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo;

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.transaction.mongo.ClientSessionAwareUnitOfWork;
import dk.cloudcreate.essentials.reactive.LocalEventBus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import java.time.Duration;
import java.util.Optional;

public class MongoFencedLockManagerBuilder {
    private MongoTemplate                                             mongoTemplate;
    private MongoConverter                                            mongoConverter;
    private UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory;
    private Optional<String>                                          lockManagerInstanceId     = Optional.empty();
    private Optional<String>                                          fencedLocksCollectionName = Optional.empty();
    private Duration                                                  lockTimeOut;
    private Duration                                                  lockConfirmationInterval;
    private Optional<LocalEventBus<Object>>                           eventBus                  = Optional.empty();

    /**
     * @param mongoTemplate the mongoTemplate instance
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        return this;
    }

    /**
     * @param mongoConverter the mongo converter
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setMongoConverter(MongoConverter mongoConverter) {
        this.mongoConverter = mongoConverter;
        return this;
    }

    /**
     * @param unitOfWorkFactory The unit of work factory
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setUnitOfWorkFactory(UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory) {
        this.unitOfWorkFactory = unitOfWorkFactory;
        return this;
    }

    /**
     * @param lockManagerInstanceId The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setLockManagerInstanceId(Optional<String> lockManagerInstanceId) {
        this.lockManagerInstanceId = lockManagerInstanceId;
        return this;
    }

    /**
     * @param fencedLocksCollectionName The name of the collection where the locks will be stored. If left {@link Optional#empty()} then the {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME} value will be used
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setFencedLocksCollectionName(Optional<String> fencedLocksCollectionName) {
        this.fencedLocksCollectionName = fencedLocksCollectionName;
        return this;
    }

    /**
     * @param lockManagerInstanceId The unique name for this lock manager instance. If null then the machines hostname is used
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setLockManagerInstanceId(String lockManagerInstanceId) {
        this.lockManagerInstanceId = Optional.ofNullable(lockManagerInstanceId);
        return this;
    }

    /**
     * @param fencedLocksCollectionName The name of the collection where the locks will be stored. If null then the {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME} value will be used
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setFencedLocksCollectionName(String fencedLocksCollectionName) {
        this.fencedLocksCollectionName = Optional.ofNullable(fencedLocksCollectionName);
        return this;
    }

    /**
     * @param lockTimeOut the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setLockTimeOut(Duration lockTimeOut) {
        this.lockTimeOut = lockTimeOut;
        return this;
    }

    /**
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setLockConfirmationInterval(Duration lockConfirmationInterval) {
        this.lockConfirmationInterval = lockConfirmationInterval;
        return this;
    }

    /**
     * @param eventBus optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setEventBus(Optional<LocalEventBus<Object>> eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    /**
     * @param eventBus optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setEventBus(LocalEventBus<Object> eventBus) {
        this.eventBus = Optional.ofNullable(eventBus);
        return this;
    }

    /**
     * Create the new {@link MongoFencedLockManager} instance
     *
     * @return the new {@link MongoFencedLockManager} instance
     */
    public MongoFencedLockManager build() {
        return new MongoFencedLockManager(mongoTemplate,
                                          mongoConverter,
                                          unitOfWorkFactory,
                                          lockManagerInstanceId,
                                          fencedLocksCollectionName,
                                          lockTimeOut,
                                          lockConfirmationInterval,
                                          eventBus);
    }


    /**
     * Create the new {@link MongoFencedLockManager} instance and call {@link MongoFencedLockManager#start()} immediately
     *
     * @return the new {@link MongoFencedLockManager} instance
     */
    public MongoFencedLockManager buildAndStart() {
        var instance = build();
        instance.start();
        return instance;
    }
}