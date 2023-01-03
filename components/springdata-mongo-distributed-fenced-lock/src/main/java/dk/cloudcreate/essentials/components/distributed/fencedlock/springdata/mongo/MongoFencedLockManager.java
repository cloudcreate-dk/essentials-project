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
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.transaction.mongo.ClientSessionAwareUnitOfWork;
import dk.cloudcreate.essentials.reactive.*;
import org.slf4j.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import java.time.Duration;
import java.util.Optional;

/**
 * Provides a {@link FencedLockManager} implementation using MongoDB and the SpringData MongoDB library to coordinate intra-service distributed locks
 */
public class MongoFencedLockManager extends DBFencedLockManager<ClientSessionAwareUnitOfWork, DBFencedLock> {
    private static final Logger log = LoggerFactory.getLogger(MongoFencedLockManager.class);

    private MongoTemplate mongoTemplate;

    public static MongoFencedLockManagerBuilder builder() {
        return new MongoFencedLockManagerBuilder();
    }

    /**
     * @param mongoTemplate             the mongoTemplate instance
     * @param mongoConverter            the mongo converter
     * @param unitOfWorkFactory         The unit of work factory
     * @param lockManagerInstanceId     The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param fencedLocksCollectionName The name of the collection where the locks will be stored. If left {@link Optional#empty()} then the {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME} value will be used
     * @param lockTimeOut               the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval  how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public MongoFencedLockManager(MongoTemplate mongoTemplate,
                                  MongoConverter mongoConverter,
                                  UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory,
                                  Optional<String> lockManagerInstanceId,
                                  Optional<String> fencedLocksCollectionName,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval) {
        super(new MongoFencedLockStorage(mongoTemplate,
                                         mongoConverter,
                                         fencedLocksCollectionName),
              unitOfWorkFactory,
              lockManagerInstanceId,
              lockTimeOut,
              lockConfirmationInterval,
              Optional.empty()
             );
    }

    /**
     * @param mongoTemplate             the mongoTemplate instance
     * @param mongoConverter            the mongo converter
     * @param unitOfWorkFactory         The unit of work factory
     * @param lockManagerInstanceId     The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param fencedLocksCollectionName The name of the collection where the locks will be stored. If left {@link Optional#empty()} then the {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME} value will be used
     * @param lockTimeOut               the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval  how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param eventBus                  optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public MongoFencedLockManager(MongoTemplate mongoTemplate,
                                  MongoConverter mongoConverter,
                                  UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory,
                                  Optional<String> lockManagerInstanceId,
                                  Optional<String> fencedLocksCollectionName,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval,
                                  Optional<EventBus> eventBus) {
        super(new MongoFencedLockStorage(mongoTemplate,
                                         mongoConverter,
                                         fencedLocksCollectionName),
              unitOfWorkFactory,
              lockManagerInstanceId,
              lockTimeOut,
              lockConfirmationInterval,
              eventBus
             );
    }

    /**
     * Create a {@link MongoFencedLockManager} with the machines hostname as lock manager instance name and
     * the default fenced lock collection name {@link MongoFencedLockStorage#DEFAULT_FENCED_LOCKS_COLLECTION_NAME}
     *
     * @param mongoTemplate            the mongoTemplate instance
     * @param mongoConverter           the mongo converter
     * @param unitOfWorkFactory        The unit of work factory
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param eventBus                 optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public MongoFencedLockManager(MongoTemplate mongoTemplate,
                                  MongoConverter mongoConverter,
                                  UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval,
                                  Optional<EventBus> eventBus) {
        this(mongoTemplate,
             mongoConverter,
             unitOfWorkFactory,
             Optional.empty(),
             Optional.empty(),
             lockTimeOut,
             lockConfirmationInterval,
             eventBus
            );
    }
}
