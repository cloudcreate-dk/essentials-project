/*
 * Copyright 2021-2024 the original author or authors.
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
import dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.transaction.mongo.ClientSessionAwareUnitOfWork;
import dk.cloudcreate.essentials.reactive.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import java.time.Duration;
import java.util.Optional;

/**
 * <u>Security</u><br>
 * It is the responsibility of the user of this component to sanitize the {@link #setFencedLocksCollectionName(String)}
 * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoFencedLockStorage} component, used by the {@link MongoFencedLockManager}, will
 * call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
 * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
 * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
 * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
 * <br>
 * It is highly recommended that the {@code fencedLocksCollectionName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code fencedLocksCollectionName} value.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
 */
public final class MongoFencedLockManagerBuilder {
    private MongoTemplate                                             mongoTemplate;
    private UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory;
    private Optional<String>                                          lockManagerInstanceId     = Optional.empty();
    private String                                                    fencedLocksCollectionName = MongoFencedLockStorage.DEFAULT_FENCED_LOCKS_COLLECTION_NAME;
    private Duration                                                  lockTimeOut;
    private Duration                                                  lockConfirmationInterval;
    private Optional<EventBus>                                        eventBus                  = Optional.empty();

    /**
     * @param mongoTemplate the mongoTemplate instance
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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
     * @param fencedLocksCollectionName The name of the collection where the locks will be stored.
     *                                  <strong>Note:</strong><br>
     *                                  To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
     *                                  which exposes the component to the risk of malicious input.<br>
     *                                  <br>
     *                                  <strong>Security Note:</strong><br>
     *                                  It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
     *                                  to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoFencedLockStorage} component, used by the {@link MongoFencedLockManager}, will
     *                                  call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
     *                                  The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                  However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
     *                                  <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     *                                  Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
     *                                  Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
     *                                  <br>
     *                                  It is highly recommended that the {@code fencedLocksCollectionName} value is only derived from a controlled and trusted source.<br>
     *                                  To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code fencedLocksCollectionName} value.<br>
     *                                  <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setFencedLocksCollectionName(String fencedLocksCollectionName) {
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
    public MongoFencedLockManagerBuilder setEventBus(Optional<EventBus> eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    /**
     * @param eventBus optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     * @return this builder instance
     */
    public MongoFencedLockManagerBuilder setEventBus(EventBus eventBus) {
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