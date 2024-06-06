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

import dk.cloudcreate.essentials.components.document_db.annotations.DocumentEntity
import dk.cloudcreate.essentials.components.document_db.postgresql.*
import dk.cloudcreate.essentials.components.foundation.json.JSONSerializer
import dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory
import dk.cloudcreate.essentials.components.foundation.types.RandomIdGenerator
import org.jdbi.v3.core.Jdbi
import kotlin.reflect.KClass

/**
 * Common interface for a Repository that can persist an [ENTITY] in the underlying
 * database as a JSON Document
 *
 * Use the [DocumentDbRepositoryFactory] to create an instance of the [DocumentDbRepository]
 * for a given [ENTITY] type:
 *
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
 *
 * val repositoryFactory: DocumentDbRepositoryFactory = getDocumentDbRepositoryFactory()
 * val orderRepository: DocumentDbRepository<Order, OrderId> = repositoryFactory.create(Order::class)
 * ```
 *
 * ## Security
 * To support customization of which PostgreSQL table each entity type is stored you can provide your own `tableName` through the [DocumentEntity.tableName] annotation.
 *
 * The [DocumentEntity.tableName]` and `all the names of the properties in your entity classes` will be directly used in constructing SQL statements through string concatenation.
 * This can potentially expose components, such as [PostgresqlDocumentDbRepository], to SQL injection attacks.
 *
 * **It is the responsibility of the user of this component to sanitize both the [DocumentEntity.tableName] and `all Entity property names` to ensure the security of all the SQL statements generated
 * by this component.**
 *
 * The [PostgresqlDocumentDbRepository] instance, e.g. created by [DocumentDbRepositoryFactory.create],
 * will through [EntityConfiguration.configureEntity] call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] method to validate the table name
 * and Entity property names as a first line of defense.
 *
 * The  [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] provides an initial layer of defense against SQL injection by applying naming conventions intended to
 * reduce the risk of malicious input.
 * **However, Essentials components as well as  [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] does not offer exhaustive protection,
 * nor does it assure the complete security of the resulting SQL against SQL injection threats.**
 * > The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
 * > Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.
 *
 * **Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**
 *
 * It is highly recommended that the [DocumentEntity.tableName] and `all the Entity property names` are only derived from controlled and trusted sources.
 *
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the `tableName` or entity property names.
 *
 * **See also [VersionedEntity]'s security warning.***
 *
 * @see VersionedEntity
 * @see DocumentDbRepositoryFactory
 * @see PostgresqlDocumentDbRepository
 */
interface DocumentDbRepository<ENTITY : VersionedEntity<ID, ENTITY>, ID> {
    /**
     * Add an index to the repository to speedup queries of properties of concrete [VersionedEntity] classes
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
     *
     * orderRepository.addIndex(Index(name="city", listOf(Order::contactDetails then ContactDetails::address then Address::city)))
     * orderRepository.addIndex(Index(name="orderdate_amount", listOf(Order::orderDate.asProperty(), Order::amount.asProperty())))
     * ```
     *
     * which, following the pattern: `idx_${tableName}_$indexName` as lower case, will add two indexes to the `orders` table:
     * - `idx_orders_city`
     * - `idx_orders_orderdate_amount`
     *
     * ## Security
     * The [Index.name] and `all the names of the properties in your entity classes` specified in [Index.properties] will be directly used in constructing SQL statements through string concatenation.
     * This can potentially expose components, such as [PostgresqlDocumentDbRepository], to SQL injection attacks.
     *
     * **It is the responsibility of the user of this component to sanitize both the [Index.name] and `all the names of the properties in your entity classes` specified in [Index.properties]
     * to ensure the security of all the SQL statements generated by this component.**
     *
     * The [PostgresqlDocumentDbRepository.addIndex] will call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] method to validate the index name
     * as a first line of defense.
     *
     * The  [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] provides an initial layer of defense against SQL injection by applying naming conventions
     * intended to reduce the risk of malicious input.
     *
     * **However, Essentials components as well as  [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] does not offer exhaustive protection,
     * nor does it assure the complete security of the resulting SQL against SQL injection threats.**
     * > The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
     * > Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.
     *
     * **Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**
     *
     * It is highly recommended that the [Index.name] and `all the names of the properties in your entity classes` specified in [Index.properties]  are only derived from controlled and trusted sources.
     *
     * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the [Index.name] nor any of `the names of the properties in your entity classes` specified in [Index.properties]
     *
     * **See also [VersionedEntity]'s security warning.***
     *
     * @param index The index to add, which specifies the properties to include in the index.
     * @return this [DocumentDbRepository] to allow chaining of method calls
     */
    fun addIndex(index: Index<ENTITY>): DocumentDbRepository<ENTITY, ID>

    /**
     * Remove an index from the repository.
     * @param index The index to remove
     * @return this [DocumentDbRepository] to allow chaining of method calls
     */
    fun removeIndex(index: Index<ENTITY>): DocumentDbRepository<ENTITY, ID> {
        return removeIndex(index.name)
    }

    /**
     * Remove an index from the repository.
     * @param index The name of the index to remove
     * @return this [DocumentDbRepository] to allow chaining of method calls
     */
    fun removeIndex(indexName: String): DocumentDbRepository<ENTITY, ID>

    /**
     * Save a new entity instance
     * @param entity The entity to persist. If the entity [Id] annotated property is null and of type [String] or type [StringValueType],
     * then a random String id value will be assigned using the [RandomIdGenerator]
     * @param initialVersion The initial value for the [VersionedEntity.version] - default is [Version.ZERO]
     * @return the [entity] provided with the [VersionedEntity.version] and [VersionedEntity.lastUpdated] properties updated
     * @throws OptimisticLockingException in case the entity already exists in the database
     */
    fun save(entity: ENTITY, initialVersion: Version = Version.ZERO): ENTITY

    /**
     * Update an existing entity (i.e. an entity that has previously been saved using [save])
     * @param entity The entity to save. The [VersionedEntity.version] will automatically be incremented
     * @return the [entity] provided with the [VersionedEntity.version] and [VersionedEntity.lastUpdated] properties updated
     * @throws OptimisticLockingException in case the entity has been updated in the meantime (i.e. the value of
     * [VersionedEntity.version] in the database doesn't match the [VersionedEntity.version] of the [entity])
     */
    fun update(entity: ENTITY): ENTITY

    /**
     * Update an existing entity (i.e. an entity that has previously been saved using [save])
     * @param entity The entity to save.
     * @param nextVersion  The new value for [VersionedEntity.version]
     * @return the [entity] provided with the [VersionedEntity.version] and [VersionedEntity.lastUpdated] properties updated
     * @throws OptimisticLockingException in case the entity has been updated in the meantime (i.e. the value of
     * [VersionedEntity.version] in the database doesn't match the [VersionedEntity.version] of the [entity])
     */
    fun update(entity: ENTITY, nextVersion: Version): ENTITY

    /**
     * Find matching entities using the [query] as criteria
     *
     * Example:
     * ```
     * val query = repository.queryBuilder()
     *     .where(repository.condition()
     *         .matching {
     *             Order::additionalProperty lt 50
     *         })
     *     .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
     *     .limit(200)
     *     .offset(0)
     *     .build()
     *
     * var result = repository.find(query)
     * ```
     * or
     * ```
     * val result = orderRepository.queryBuilder()
     *             .where(orderRepository.condition()
     *                 .matching {
     *                     (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                         .and(Order::description like "%unique%")
     *                 })
     *             .find()
     * ```
     *
     * @param queryBuilder The query builder used to build the query
     * @return a list of entities matching the query criteria
     */
    fun find(queryBuilder: QueryBuilder<ID, ENTITY>): List<ENTITY>

    /**
     * Find an entity by its ID
     * @param id The ID of the entity to find
     * @return the entity with the given ID or null if not found
     */
    fun findById(id: ID): ENTITY?

    /**
     * Get an entity by its ID
     * @param id The ID of the entity to get
     * @return the entity with the given ID or throws an exception if not found
     */
    fun getById(id: ID): ENTITY {
        return findById(id)!!
    }

    /**
     * Check if an entity exists by its ID
     * @param id The ID of the entity to check
     * @return true if the entity exists, false otherwise
     */
    fun existsById(id: ID): Boolean

    /**
     * Save multiple entities
     * @param entities The entities to save
     * @return a list of the saved entities
     */
    fun saveAll(entities: Iterable<ENTITY>): List<ENTITY>

    /**
     * Save multiple entities
     * @param entities The entities to save
     * @return a list of the saved entities
     */
    fun saveAll(vararg entities: ENTITY): List<ENTITY> {
        return saveAll(entities.toList())
    }

    /**
     * Update multiple entities
     * @param entities The entities to update
     * @return a list of the updated entities
     */
    fun updateAll(entities: Iterable<ENTITY>): List<ENTITY>

    /**
     * Update multiple entities
     * @param entities The entities to update
     * @return a list of the updated entities
     */
    fun updateAll(vararg entities: ENTITY): List<ENTITY> {
        return updateAll(entities.toList())
    }

    /**
     * Delete an entity by its ID
     * @param id The ID of the entity to delete
     */
    fun deleteById(id: ID)

    /**
     * Delete an entity
     * @param entity The entity to delete
     */
    fun delete(entity: ENTITY)

    /**
     * Delete multiple entities
     * @param entities The entities to delete
     */
    fun deleteAll(entities: Iterable<ENTITY>)

    /**
     * Delete multiple entities
     * @param entities The entities to delete
     */
    fun deleteAll(vararg entities: ENTITY) {
        return deleteAll(entities.toList())
    }

    /**
     * Find all entities
     * @return a list of all entities
     */
    fun findAll(): List<ENTITY>

    /**
     * Find multiple entities by their IDs
     * @param ids The IDs of the entities to find
     * @return a list of the entities with the given IDs
     */
    fun findAllById(ids: Iterable<ID>): List<ENTITY>

    /**
     * Find multiple entities by their IDs
     * @param ids The IDs of the entities to find
     * @return a list of the entities with the given IDs
     */
    fun findAllById(vararg ids: ID): List<ENTITY> {
        return findAllById(ids.toList())
    }

    /**
     * Count the number of entities
     * @return the number of entities
     */
    fun count(): Long

    /**
     * Delete multiple entities by their IDs
     * @param ids The IDs of the entities to delete
     */
    fun deleteAllById(ids: Iterable<ID>)

    /**
     * Delete multiple entities by their IDs
     * @param ids The IDs of the entities to delete
     */
    fun deleteAllById(vararg ids: ID) {
        return deleteAllById(ids.toList())
    }

    /**
     * Delete all entities
     */
    fun deleteAll()

    /**
     * Get the entity configuration
     * @return the entity configuration
     */
    fun entityConfiguration(): EntityConfiguration<ID, ENTITY>

    /**
     * Create a [QueryBuilder] instance.
     *
     * **See [VersionedEntity]'s security warning.***
     *
     * Example:
     * ```
     * val result = orderRepository.queryBuilder()
     *             .where(orderRepository.condition()
     *                 .matching {
     *                     (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                         .and(Order::description like "%unique%")
     *                 })
     *             .find()
     * ```
     * or
     * ```
     * val query = repository.queryBuilder()
     *     .where(repository.condition()
     *         .matching {
     *             Order::additionalProperty lt 50
     *         })
     *     .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
     *     .limit(200)
     *     .offset(0)
     *     .build()
     *
     * val result = repository.find(query)
     * ```
     *
     * ### Security
     * The [DocumentEntity.tableName]` and `all the names of the properties in your entity classes` will be directly used in constructing SQL statements through string concatenation.
     * This can potentially expose components, such as [PostgresqlDocumentDbRepository], to SQL injection attacks.
     *
     * **It is the responsibility of the user of this component to sanitize both the [DocumentEntity.tableName] and `all Entity property names` to ensure the security of all the SQL statements generated
     * by this component.**
     *
     * The [PostgresqlDocumentDbRepository] instance, e.g. created by [DocumentDbRepositoryFactory.create],
     * will through [EntityConfiguration.configureEntity] call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] method to validate the table name
     * and Entity property names as a first line of defense.
     *
     * The  [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] provides an initial layer of defense against SQL injection by applying naming conventions intended to
     * reduce the risk of malicious input.
     * **However, Essentials components as well as  [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] does not offer exhaustive protection,
     * nor does it assure the complete security of the resulting SQL against SQL injection threats.**
     * > The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
     * > Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.
     *
     * **Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**
     *
     * It is highly recommended that the [DocumentEntity.tableName] and `all the Entity property names` are only derived from controlled and trusted sources.
     *
     * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the `tableName` or entity property names.
     *
     * @return a new [QueryBuilder] instance
     */
    fun queryBuilder(): QueryBuilder<ID, ENTITY>

    /**
     * Create a [Condition] that's part of a [QueryBuilder]'s [QueryBuilder.where] statement
     *
     * **See [VersionedEntity]'s security warning.***
     *
     * Example:
     * ```
     * val result = orderRepository.queryBuilder()
     *             .where(orderRepository.condition()
     *                 .matching {
     *                     (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                         .and(Order::description like "%unique%")
     *                 })
     *             .find()
     * ```
     * or
     * ```
     * val query = repository.queryBuilder()
     *     .where(repository.condition()
     *         .matching {
     *             Order::additionalProperty lt 50
     *         })
     *     .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
     *     .limit(200)
     *     .offset(0)
     *     .build()
     *
     * val result = repository.find(query)
     * ```
     * @return a new [Condition] instance
     */
    fun condition(): Condition<ENTITY>
}


/**
 * The versioning scheme implements optimistic locking, a concurrency control method used to prevent conflicting updates.
 *
 * Each time an entity is updated, its [VersionedEntity.version] is checked against the [VersionedEntity.version]  of the same entity in the database.
 *
 * If the versions do not match, the operation is aborted, and an [OptimisticLockingException] is thrown.
 *
 * This approach ensures that updates do not overwrite changes made by other processes.
 * Exception thrown if an entity has be changed
 */
class OptimisticLockingException(message: String) : Exception(message)

/**
 * Create a new [DocumentDbRepositoryFactory] which can create [DocumentDbRepository] capable of persisting
 * entities as JSON Documents in the underlying database
 *
 * Here's an example of configuring it standalone - if you're using the `spring-boot-starter-postgresql`/`spring-boot-starter-postgresql-event-store`
 * both the `Jdbi`, `UnitOfWorkFactory` and `JSONSerializer` are available as Spring Beans that can be injected.
 *
 * ```kotlin
 * val repositoryFactory = DocumentDbRepositoryFactory(
 *             jdbi,
 *             JdbiUnitOfWorkFactory(jdbi),
 *             JacksonJSONSerializer(
 *                 EssentialsImmutableJacksonModule.createObjectMapper(
 *                     Jdk8Module(),
 *                     JavaTimeModule()
 *                 ).registerKotlinModule()
 *             )
 *         )
 * ```
 *
 * @param jdbi The [Jdbi] instance used to connect to the underlying database
 * @param unitOfWorkFactory The [HandleAwareUnitOfWorkFactory] used to control transactions against the underlying database
 * @param jsonSerializer The [JSONSerializer] used to serialize the entity instance to and from JSON
 */
class DocumentDbRepositoryFactory(
    jdbi: Jdbi,
    private val unitOfWorkFactory: HandleAwareUnitOfWorkFactory<out HandleAwareUnitOfWork>,
    private val jsonSerializer: JSONSerializer
) {
    init {
        jdbi.registerArgument(VersionArgumentFactory())
        jdbi.registerColumnMapper(VersionColumnMapper())
    }

    /**
     * Create a new [DocumentDbRepository] (based on [PostgresqlDocumentDbRepository]) instance for the given [entityClass]
     *
     *
     * @param entityClass The entity class implementation (e.g. a data class such as an Order, Product, etc.)
     * @see PostgresqlDocumentDbRepository
     */
    fun <ENTITY : VersionedEntity<ID, ENTITY>, ID> create(
        entityClass: KClass<ENTITY>
    ): DocumentDbRepository<ENTITY, ID> {
        return PostgresqlDocumentDbRepository(
            unitOfWorkFactory,
            entityClass,
            jsonSerializer
        )
    }
}

/**
 * Definition of an [Index] being added to [PostgresqlDocumentDbRepository] using [PostgresqlDocumentDbRepository.addIndex]
 *
 * ## Security
 * The [Index.name] and `all the names of the properties in your entity classes` specified in [Index.properties] will be directly used in constructing SQL statements through string concatenation.
 * This can potentially expose components, such as [PostgresqlDocumentDbRepository], to SQL injection attacks.
 *
 * **It is the responsibility of the user of this component to sanitize both the [Index.name] and `all the names of the properties in your entity classes` specified in [Index.properties]
 * to ensure the security of all the SQL statements generated by this component.**
 *
 * The [PostgresqlDocumentDbRepository.addIndex] will call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] method to validate the index name
 * as a first line of defense.
 *
 * The  [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] provides an initial layer of defense against SQL injection by applying naming conventions
 * intended to reduce the risk of malicious input.
 *
 * **However, Essentials components as well as  [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName] does not offer exhaustive protection,
 * nor does it assure the complete security of the resulting SQL against SQL injection threats.**
 * > The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
 * > Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.
 *
 * **Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**
 *
 * It is highly recommended that the [Index.name] and `all the names of the properties in your entity classes` specified in [Index.properties]  are only derived from controlled and trusted sources.
 *
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the [Index.name] nor any of `the names of the properties in your entity classes` specified in [Index.properties]
 *
 * **See also [VersionedEntity]'s security warning.***
 *
 * @param name the name of the index - see security note on [PostgresqlDocumentDbRepository.addIndex]
 * @param properties the properties that are included in the index - see security note on [PostgresqlDocumentDbRepository.addIndex]
 */
data class Index<T>(
    val name: String,
    val properties: List<Property<T, *>>
) {
    init {
        PostgresqlUtil.checkIsValidTableOrColumnName(name)
        require(properties.isNotEmpty()) { "You have to specify at least 1 property" }
        properties.forEach { PostgresqlUtil.checkIsValidTableOrColumnName(it.name()) }
    }
}

/**
 * Base class that can delegate all [DocumentDbRepository] method calls onto a delegate instance of
 * [DocumentDbRepository] such as [PostgresqlDocumentDbRepository] (which by design cannot be used as superclass)
 *
 * The [DelegatingDocumentDbRepository] can be used to create Entity specific repositories with custom query methods
 *
 * Example `OrderRepository`:
 * ```
 * class OrderRepository(delegateTo: DocumentDbRepository<Order, OrderId>) : DelegatingDocumentDbRepository<Order, OrderId>(delegateTo) {
 *     fun findOrdersWithAmountGreaterThan(amount: Amount): List<Order> {
 *         return queryBuilder().where(
 *             condition().matching {
 *                 Order::amount gt amount
 *             }
 *         ).find()
 *     }
 * }
 * ```
 *
 * Initializing the `OrderRepository`:
 * ```
 * val repositoryFactory = DocumentDbRepositoryFactory(
 *     jdbi,
 *     JdbiUnitOfWorkFactory(jdbi),
 *     JacksonJSONSerializer(
 *         EssentialsImmutableJacksonModule.createObjectMapper(
 *             Jdk8Module(),
 *             JavaTimeModule()
 *         ).registerKotlinModule()
 *     )
 * )
 *
 * val orderRepository = OrderRepository(repositoryFactory.create(Order::class))
 * ```
 * @param delegateTo The [DocumentDbRepository] instance where all [DocumentDbRepository] method calls will be delegated to
 */
open class DelegatingDocumentDbRepository<ENTITY : VersionedEntity<ID, ENTITY>, ID>(val delegateTo: DocumentDbRepository<ENTITY, ID>) : DocumentDbRepository<ENTITY, ID> {
    override fun addIndex(index: Index<ENTITY>): DocumentDbRepository<ENTITY, ID> = delegateTo.addIndex(index)

    override fun removeIndex(indexName: String): DocumentDbRepository<ENTITY, ID> = delegateTo.removeIndex(indexName)

    override fun deleteAll() = delegateTo.deleteAll()

    override fun findAll(): List<ENTITY> = delegateTo.findAll()

    override fun count(): Long = delegateTo.count()

    override fun entityConfiguration(): EntityConfiguration<ID, ENTITY> = delegateTo.entityConfiguration()

    override fun queryBuilder(): QueryBuilder<ID, ENTITY> = delegateTo.queryBuilder()

    override fun condition(): Condition<ENTITY> = delegateTo.condition()

    override fun deleteAllById(ids: Iterable<ID>) = delegateTo.deleteAllById(ids)

    override fun findAllById(ids: Iterable<ID>): List<ENTITY> = delegateTo.findAllById(ids)

    override fun deleteAll(entities: Iterable<ENTITY>) = delegateTo.deleteAll(entities)

    override fun delete(entity: ENTITY) = delegateTo.delete(entity)

    override fun deleteById(id: ID) = delegateTo.deleteById(id)

    override fun updateAll(entities: Iterable<ENTITY>): List<ENTITY> = delegateTo.updateAll(entities)

    override fun saveAll(entities: Iterable<ENTITY>): List<ENTITY> = delegateTo.saveAll(entities)

    override fun existsById(id: ID): Boolean = delegateTo.existsById(id)

    override fun findById(id: ID): ENTITY? = delegateTo.findById(id)

    override fun find(queryBuilder: QueryBuilder<ID, ENTITY>): List<ENTITY> = delegateTo.find(queryBuilder)

    override fun update(entity: ENTITY, nextVersion: Version): ENTITY = delegateTo.update(entity, nextVersion)

    override fun update(entity: ENTITY): ENTITY = delegateTo.update(entity)

    override fun save(entity: ENTITY, initialVersion: Version): ENTITY = delegateTo.save(entity, initialVersion)
}
