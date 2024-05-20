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

package dk.cloudcreate.essentials.components.document_db

import java.time.OffsetDateTime
import dk.cloudcreate.essentials.kotlin.types.StringValueType

/**
 * Interface which support versioning of Entities with the goal of ensuring Optimistic Locking for each update to
 * an Entity. Versioning is based on [Version] which is an ever-increasing Long value.
 *
 * Example:
 * ```kotlin
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
 *
 * The `@Id` annotated property MUST be a `String` or a String value class (such as those implementing [StringValueType])
 *
 * To strengthen the type safety and the ubiquitous language it's a good idea to base your type on one of the Semantic types defined
 * in the `types` library.
 *
 * Example of creating a semantic type called `OrderId` which is based on the [StringValueType] from the `types` library:
 * ```kotlin
 * @JvmInline
 * value class OrderId(override val value: String) : StringValueType<OrderId> {
 *     companion object {
 *         fun random(): OrderId = OrderId(RandomIdGenerator.generate())
 *     }
 * }
 * ```
 *
 * If you create your own semantic type you also need to create a corresponding `Jdbi` `ArgumentFactory` and `ColumnMapper`.
 *
 * Example:
 * ```kotlin
 * class OrderIdArgumentFactory : StringValueTypeArgumentFactory<OrderId>()
 * class OrderIdColumnMapper : StringValueTypeColumnMapper<OrderId>()
 * ```
 *
 * Projections of event-sourced Aggregates will typically use the
 * [dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder] value of each
 * event being projected - see
 * [dk.cloudcreate.essentials.components.foundation.messaging.queue.OrderedMessage] when using with
 * [dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.EventProcessor]
 *
 * Each Entity persisted using [dk.cloudcreate.essentials.components.document_db.DocumentDbRepository]
 * must implement this interface.
 *
 * ** Security notice**
 * The [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository] instance created,
 * e.g. by [dk.cloudcreate.essentials.components.document_db.DocumentDbRepositoryFactory.create],will call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] to check table name
 * (see [dk.cloudcreate.essentials.components.document_db.annotations.DocumentEntity]) and JSON property names, that are resulting from the JSON serialization of the concrete [VersionedEntity] being persisted,
 * using [dk.cloudcreate.essentials.components.document_db.postgresql.EntityConfiguration.checkPropertyNames], since the table name and JSON property names will be used in SQL string concatenations,
 *  which exposes the components (such as [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository]) to SQL injection attacks.
 *
 * The [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] and [dk.cloudcreate.essentials.components.document_db.postgresql.EntityConfiguration.checkPropertyNames]
 * provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.
 *
 * However, Essentials components as well as [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] and
 * [dk.cloudcreate.essentials.components.document_db.postgresql.EntityConfiguration.Companion.checkPropertyNames] does not offer exhaustive protection,
 * nor does it assure the complete security of the resulting SQL against SQL injection threats.
 *
 * **The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes**
 *
 * Users must ensure thorough sanitization and validation of API input parameters, column, table, and index names.
 *
 * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.
 *
 * It is highly recommended that the table name and JSON property name values are only derived from a controlled and trusted source.
 *
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide these values.
 */
interface VersionedEntity<ID, SELF_TYPE : VersionedEntity<ID, SELF_TYPE>> {
    /**
     * Name of version property. Aligned with [VERSION_PROPERTY_NAME].
     * Default value should be [Version.NOT_SAVED_YET]
     */
    var version: Version

    /**
     * When was the entity last updated - this value is automatically maintained by the [DocumentDBRepository]
     */
    var lastUpdated: OffsetDateTime
}