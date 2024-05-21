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

package dk.cloudcreate.essentials.components.document_db.annotations


/**
 * Use this annotation on a concrete class implementing [dk.cloudcreate.essentials.components.document_db.VersionedEntity]
 * to specify the name of the postgresql table where entities annotated will be persisted.
 *
 * **Note:**
 * The [tableName] provided will be directly used in constructing SQL statements
 * through string concatenation, which exposes the components (such as
 * [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository]) to SQL injection attacks.
 *
 * **Security Note:**
 *
 * **It is the responsibility of the user of this component to sanitize the [tableName]
 * to ensure the security of all the SQL statements generated by this component.**
 *
 * The [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository] instance created,
 * e.g. by [dk.cloudcreate.essentials.components.document_db.DocumentDbRepositoryFactory.create],will
 * call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] method to validate the table name as a first line of defense.
 *
 * The [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] provides an initial layer of defense against SQL injection by applying
 * naming conventions intended to reduce the risk of malicious input.
 *
 * However, Essentials components as well as [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] does not offer exhaustive protection,
 * nor does it assure the complete security of the resulting SQL against SQL injection threats.
 *
 * **The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes**
 *
 * Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.
 *
 * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.
 *
 * It is highly recommended that the [tableName] value is only derived from a controlled and trusted source.
 *
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the [tableName] value.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
/**
 * @param tableName The name of the postgresql table where entities annotated will be persisted.
 *
 * **Note:**
 * The [tableName] provided will be directly used in constructing SQL statements
 * through string concatenation, which exposes the components (such as
 * [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository]) to SQL injection attacks.
 *
 * **Security Note:**
 *
 * **It is the responsibility of the user of this component to sanitize the [tableName]
 * to ensure the security of all the SQL statements generated by this component.**
 *
 * The [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository] instance created,
 * e.g. by [dk.cloudcreate.essentials.components.document_db.DocumentDbRepositoryFactory.create],will
 * call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] method to validate the table name as a first line of defense.
 *
 * The [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] provides an initial layer of defense against SQL injection by applying
 * naming conventions intended to reduce the risk of malicious input.
 *
 * However, Essentials components as well as [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] does not offer exhaustive protection,
 * nor does it assure the complete security of the resulting SQL against SQL injection threats.
 *
 * **The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes**
 *
 * Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.
 *
 * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.
 *
 * It is highly recommended that the [tableName] value is only derived from a controlled and trusted source.
 *
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the [tableName] value.
 */
annotation class DocumentEntity(val tableName: String)

/**
 * Use this annotation on a concrete class implementing [dk.cloudcreate.essentials.components.document_db.VersionedEntity]
 * to specify the individual top level properties that should be indexed
 *
 * Example:
 * ```
 * @DocumentEntity("orders")
 * data class Order(
 *     @Id
 *     val orderId: OrderId,
 *     var description: String,
 *     var amount: Amount,
 *     var additionalProperty: Int,
 *     var orderDate: LocalDateTime,
 *     @Indexed
 *     var personName: String,
 *     var invoiceAddress: Address,
 *     var contactDetails: ContactDetails,
 *     override var version: Version = Version.NOT_SAVED_YET,
 *     override var lastUpdated: OffsetDateTime = OffsetDateTime.now(UTC)
 * ) : VersionedEntity<OrderId, Order>
 * ```
 * which, following the pattern: `idx_${tableName}_${fieldName}` as lower case, will add index `idx_orders_personname` to the `orders` table
 *
 * ### Security note:
 * The name of the property will be used in the index name (together with the table name specified in [DocumentEntity.tableName]) and is further used in constructing SQL statements
 * through string concatenation, which exposes the components (such as
 * [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository]) to SQL injection attacks.
 *
 * **It is the responsibility of the user of this component to sanitize the [DocumentEntity.tableName] and the indexed property name
 * to ensure the security of all the SQL statements generated by this component.**
 *
 * The [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository] instance created,
 * e.g. by [dk.cloudcreate.essentials.components.document_db.DocumentDbRepositoryFactory.create], will
 * call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] method to validate the table name and index name as a first line of defense.
 *
 * The [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] provides an initial layer of defense against SQL injection by applying
 * naming conventions intended to reduce the risk of malicious input.
 *
 * However, Essentials components as well as [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] does not offer exhaustive protection,
 * nor does it assure the complete security of the resulting SQL against SQL injection threats.
 *
 * **The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes**
 *
 * Users must ensure thorough sanitization and validation of API input parameters, column, table, and index names.
 *
 * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.
 *
 * It is highly recommended that the [DocumentEntity.tableName] value and indexed property name is only derived from a controlled and trusted source.
 *
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the [DocumentEntity.tableName] value or the indexed property name.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Indexed

/**
 * Use this annotation on a concrete class implementing [dk.cloudcreate.essentials.components.document_db.VersionedEntity]
 * to specify the single property that contains the id / primary key
 *
 * The property annotated MUST be a [String] or a String value class, such as value class implementing [StringValueType]
 * ```
 * @JvmInline
 * value class OrderId(override val value: String) : StringValueType<OrderId> {
 *     companion object {
 *         fun random(): OrderId = OrderId(RandomIdGenerator.generate())
 *     }
 * }
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Id