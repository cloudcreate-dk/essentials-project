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

package dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql;

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.reactive.*;
import org.jdbi.v3.core.Jdbi;

import java.time.Duration;
import java.util.Optional;

/**
 * Provides a {@link FencedLockManager} implementation using Postgresql to coordinate intra-service distributed locks<br>
 * <br>
 * <u><b>Security:</b></u><br>
 * To support customization of storage table name, the {@code fencedLocksTableName} will be directly used in constructing SQL statements
 * through string concatenation, which exposes the component to SQL injection attacks.<br>
 * <br>
 * <strong>Security Note:</strong><br>
 * It is the responsibility of the user of this component to sanitize the {@code fencedLocksTableName}
 * to ensure the security of all the SQL statements generated by this component. The {@link PostgresqlFencedLockStorage} component will
 * call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
 * The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
 * <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
 * Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
 * <br>
 * It is highly recommended that the {@code fencedLocksTableName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code fencedLocksTableName} value.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
 * vulnerabilities, compromising the security and integrity of the database.</b>
 */
public final class PostgresqlFencedLockManager extends DBFencedLockManager<HandleAwareUnitOfWork, DBFencedLock> {
    /**
     * <u><b>Security:</b></u><br>
     * To support customization of storage table name, the {@code fencedLocksTableName} will be directly used in constructing SQL statements
     * through string concatenation, which exposes the component to SQL injection attacks.<br>
     * <br>
     * <strong>Security Note:</strong><br>
     * It is the responsibility of the user of this component to sanitize the {@code fencedLocksTableName}
     * to ensure the security of all the SQL statements generated by this component. The {@link PostgresqlFencedLockStorage} component will
     * call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
     * The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
     * However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
     * <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
     * Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
     * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
     * <br>
     * It is highly recommended that the {@code fencedLocksTableName} value is only derived from a controlled and trusted source.<br>
     * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code fencedLocksTableName} value.<br>
     * <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
     * vulnerabilities, compromising the security and integrity of the database.</b>
     *
     * @return a new builder
     */
    public static PostgresqlFencedLockManagerBuilder builder() {
        return new PostgresqlFencedLockManagerBuilder();
    }

    /**
     * @param jdbi                     the jdbi instance
     * @param unitOfWorkFactory        The unit of work factory
     * @param lockManagerInstanceId    The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param fencedLocksTableName     the name of the table where the fenced locks will be stored<br>
     *                                 <strong>Note:</strong><br>
     *                                 To support customization of storage table name, the {@code fencedLocksTableName} will be directly used in constructing SQL statements
     *                                 through string concatenation, which exposes the component to SQL injection attacks.<br>
     *                                 <br>
     *                                 <strong>Security Note:</strong><br>
     *                                 It is the responsibility of the user of this component to sanitize the {@code fencedLocksTableName}
     *                                 to ensure the security of all the SQL statements generated by this component. The {@link PostgresqlFencedLockStorage} component will
     *                                 call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
     *                                 The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                 However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
     *                                 <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
     *                                 Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
     *                                 Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
     *                                 <br>
     *                                 It is highly recommended that the {@code fencedLocksTableName} value is only derived from a controlled and trusted source.<br>
     *                                 To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code fencedLocksTableName} value.<br>
     *                                 <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
     *                                 vulnerabilities, compromising the security and integrity of the database.</b>
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public PostgresqlFencedLockManager(Jdbi jdbi,
                                       HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                       Optional<String> lockManagerInstanceId,
                                       String fencedLocksTableName,
                                       Duration lockTimeOut,
                                       Duration lockConfirmationInterval) {
        super(new PostgresqlFencedLockStorage(jdbi,
                                              fencedLocksTableName),
              unitOfWorkFactory,
              lockManagerInstanceId,
              lockTimeOut,
              lockConfirmationInterval,
              Optional.empty()
             );
    }

    /**
     * @param jdbi                     the jdbi instance
     * @param unitOfWorkFactory        The unit of work factory
     * @param lockManagerInstanceId    The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param fencedLocksTableName     the name of the table where the fenced locks will be stored<br>
     *                                 <strong>Note:</strong><br>
     *                                 To support customization of storage table name, the {@code fencedLocksTableName} will be directly used in constructing SQL statements
     *                                 through string concatenation, which exposes the component to SQL injection attacks.<br>
     *                                 <br>
     *                                 <strong>Security Note:</strong><br>
     *                                 It is the responsibility of the user of this component to sanitize the {@code fencedLocksTableName}
     *                                 to ensure the security of all the SQL statements generated by this component. The {@link PostgresqlFencedLockStorage} component will
     *                                 call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
     *                                 The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                 However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
     *                                 <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
     *                                 Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
     *                                 Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
     *                                 <br>
     *                                 It is highly recommended that the {@code fencedLocksTableName} value is only derived from a controlled and trusted source.<br>
     *                                 To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code fencedLocksTableName} value.<br>
     *                                 <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
     *                                 vulnerabilities, compromising the security and integrity of the database.</b>
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param eventBus                 optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public PostgresqlFencedLockManager(Jdbi jdbi,
                                       HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                       Optional<String> lockManagerInstanceId,
                                       String fencedLocksTableName,
                                       Duration lockTimeOut,
                                       Duration lockConfirmationInterval,
                                       Optional<EventBus> eventBus) {
        super(new PostgresqlFencedLockStorage(jdbi,
                                              fencedLocksTableName),
              unitOfWorkFactory,
              lockManagerInstanceId,
              lockTimeOut,
              lockConfirmationInterval,
              eventBus
             );
    }

    /**
     * Create a {@link PostgresqlFencedLockManager} with the machines hostname as lock manager instance name and the default fenced lock table name ({@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME})
     *
     * @param jdbi                     the jdbi instance
     * @param unitOfWorkFactory        The unit of work factory
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param eventBus                 optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     */
    public PostgresqlFencedLockManager(Jdbi jdbi,
                                       HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                       Duration lockTimeOut,
                                       Duration lockConfirmationInterval,
                                       Optional<EventBus> eventBus) {
        this(jdbi,
             unitOfWorkFactory,
             Optional.empty(),
             PostgresqlFencedLockStorage.DEFAULT_FENCED_LOCKS_TABLE_NAME,
             lockTimeOut,
             lockConfirmationInterval,
             eventBus
            );
    }

    /**
     * Create a {@link PostgresqlFencedLockManager} with the machines hostname as lock manager instance name and the default fenced lock table name ({@link PostgresqlFencedLockStorage#DEFAULT_FENCED_LOCKS_TABLE_NAME})
     *
     * @param jdbi                     the jdbi instance
     * @param unitOfWorkFactory        The unit of work factory
     * @param lockManagerInstanceId    The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     */
    public PostgresqlFencedLockManager(Jdbi jdbi,
                                       HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                       Optional<String> lockManagerInstanceId,
                                       Duration lockTimeOut,
                                       Duration lockConfirmationInterval
                                      ) {
        this(jdbi,
             unitOfWorkFactory,
             lockManagerInstanceId,
             PostgresqlFencedLockStorage.DEFAULT_FENCED_LOCKS_TABLE_NAME,
             lockTimeOut,
             lockConfirmationInterval,
             Optional.empty()
            );
    }
}
