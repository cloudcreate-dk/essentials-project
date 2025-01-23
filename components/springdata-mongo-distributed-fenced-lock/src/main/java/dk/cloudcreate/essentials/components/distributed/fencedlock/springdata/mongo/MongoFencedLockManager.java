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

package dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo;

import dk.cloudcreate.essentials.components.foundation.IOExceptionUtil;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.mongo.ClientSessionAwareUnitOfWork;
import dk.cloudcreate.essentials.reactive.*;
import org.slf4j.*;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.*;
import java.util.Optional;

/**
 * Provides a {@link FencedLockManager} implementation using MongoDB and the SpringData MongoDB library to coordinate intra-service distributed locks<br>
 * <br>
 * <u><b>Security:</b></u><br>
 * To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
 * which exposes the component to the risk of malicious input.<br>
 * <br>
 * <strong>Security Note:</strong><br>
 * It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
 * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoFencedLockStorage} component, used by {@link MongoFencedLockManager} will
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
public final class MongoFencedLockManager extends DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> {
    private static final Logger log = LoggerFactory.getLogger(MongoFencedLockManager.class);

    private MongoTemplate mongoTemplate;

    /**
     * To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
     * which exposes the component to the risk of malicious input.<br>
     * <br>
     * <strong>Security Note:</strong><br>
     * It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
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
     *
     * @return a new builder
     */
    public static MongoFencedLockManagerBuilder builder() {
        return new MongoFencedLockManagerBuilder();
    }

    /**
     * @param mongoTemplate                                                  the mongoTemplate instance
     * @param unitOfWorkFactory                                              The unit of work factory
     * @param lockManagerInstanceId                                          The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param fencedLocksCollectionName                                      The name of the collection where the locks will be stored.
     *                                                                       <strong>Note:</strong><br>
     *                                                                       To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
     *                                                                       which exposes the component to the risk of malicious input.<br>
     *                                                                       <br>
     *                                                                       <strong>Security Note:</strong><br>
     *                                                                       It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
     *                                                                       to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoFencedLockStorage} component, used by the {@link MongoFencedLockManager}, will
     *                                                                       call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
     *                                                                       The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                                                       However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
     *                                                                       <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     *                                                                       Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
     *                                                                       Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
     *                                                                       <br>
     *                                                                       It is highly recommended that the {@code fencedLocksCollectionName} value is only derived from a controlled and trusted source.<br>
     *                                                                       To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code fencedLocksCollectionName} value.<br>
     *                                                                       <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
     * @param lockTimeOut                                                    the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval                                       how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation Should {@link FencedLock}'s acquired by this {@link FencedLockManager} be released in case calls to {@link FencedLockStorage#confirmLockInDB(DBFencedLockManager, UnitOfWork, DBFencedLock, OffsetDateTime)} fails
     *                                                                       with an exception where {@link IOExceptionUtil#isIOException(Throwable)} returns true -
     *                                                                       If releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation is true, then {@link FencedLock}'s will be released locally,
     *                                                                       otherwise we will retain the {@link FencedLock}'s as locked.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public MongoFencedLockManager(MongoTemplate mongoTemplate,
                                  UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory,
                                  Optional<String> lockManagerInstanceId,
                                  String fencedLocksCollectionName,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval,
                                  boolean releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation) {
        super(new MongoFencedLockStorage(mongoTemplate,
                                         fencedLocksCollectionName),
              unitOfWorkFactory,
              lockManagerInstanceId,
              lockTimeOut,
              lockConfirmationInterval,
              releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation,
              Optional.empty()
             );
    }

    /**
     * @param mongoTemplate                                                  the mongoTemplate instance
     * @param unitOfWorkFactory                                              The unit of work factory
     * @param lockManagerInstanceId                                          The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param fencedLocksCollectionName                                      The name of the collection where the locks will be stored.
     *                                                                       <strong>Note:</strong><br>
     *                                                                       To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
     *                                                                       which exposes the component to the risk of malicious input.<br>
     *                                                                       <br>
     *                                                                       <strong>Security Note:</strong><br>
     *                                                                       It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
     *                                                                       to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoFencedLockStorage} component, used by the {@link MongoFencedLockManager}, will
     *                                                                       call the {@link MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
     *                                                                       The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                                                       However, Essentials components as well as {@link MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
     *                                                                       <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     *                                                                       Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
     *                                                                       Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
     *                                                                       <br>
     *                                                                       It is highly recommended that the {@code fencedLocksCollectionName} value is only derived from a controlled and trusted source.<br>
     *                                                                       To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code fencedLocksCollectionName} value.<br>
     *                                                                       <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
     * @param lockTimeOut                                                    the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval                                       how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation Should {@link FencedLock}'s acquired by this {@link FencedLockManager} be released in case calls to {@link FencedLockStorage#confirmLockInDB(DBFencedLockManager, UnitOfWork, DBFencedLock, OffsetDateTime)} fails
     *                                                                       with an exception where {@link IOExceptionUtil#isIOException(Throwable)} returns true -
     *                                                                       If releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation is true, then {@link FencedLock}'s will be released locally,
     *                                                                       otherwise we will retain the {@link FencedLock}'s as locked.
     * @param eventBus                                                       optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public MongoFencedLockManager(MongoTemplate mongoTemplate,
                                  UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory,
                                  Optional<String> lockManagerInstanceId,
                                  String fencedLocksCollectionName,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval,
                                  boolean releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation,
                                  Optional<EventBus> eventBus) {
        super(new MongoFencedLockStorage(mongoTemplate,
                                         fencedLocksCollectionName),
              unitOfWorkFactory,
              lockManagerInstanceId,
              lockTimeOut,
              lockConfirmationInterval,
              releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation,
              eventBus
             );
    }

    /**
     * Create a {@link MongoFencedLockManager} with the machines hostname as lock manager instance name and
     * the default fenced lock collection name {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME}
     *
     * @param mongoTemplate                                                  the mongoTemplate instance
     * @param unitOfWorkFactory                                              The unit of work factory
     * @param lockTimeOut                                                    the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval                                       how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation Should {@link FencedLock}'s acquired by this {@link FencedLockManager} be released in case calls to {@link FencedLockStorage#confirmLockInDB(DBFencedLockManager, UnitOfWork, DBFencedLock, OffsetDateTime)} fails
     *                                                                       with an exception where {@link IOExceptionUtil#isIOException(Throwable)} returns true -
     *                                                                       If releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation is true, then {@link FencedLock}'s will be released locally,
     *                                                                       otherwise we will retain the {@link FencedLock}'s as locked.
     * @param eventBus                                                       optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public MongoFencedLockManager(MongoTemplate mongoTemplate,
                                  UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory,
                                  Optional<String> lockManagerInstanceId,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval,
                                  boolean releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation,
                                  Optional<EventBus> eventBus) {
        this(mongoTemplate,
             unitOfWorkFactory,
             lockManagerInstanceId,
             MongoFencedLockStorage.DEFAULT_FENCED_LOCKS_COLLECTION_NAME,
             lockTimeOut,
             lockConfirmationInterval,
             releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation,
             eventBus
            );
    }
}
