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

package dk.cloudcreate.essentials.components.document_db.postgresql

import dk.cloudcreate.essentials.components.document_db.*
import dk.cloudcreate.essentials.components.document_db.annotations.DocumentEntity
import dk.cloudcreate.essentials.components.foundation.json.JSONSerializer
import dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory
import dk.cloudcreate.essentials.components.foundation.types.RandomIdGenerator
import dk.cloudcreate.essentials.kotlin.types.StringValueType
import dk.cloudcreate.essentials.kotlin.types.jdbi.LongValueTypeArgumentFactory
import dk.cloudcreate.essentials.kotlin.types.jdbi.LongValueTypeColumnMapper
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.primaryConstructor

/**
 * Postgresql specific variant of the [DocumentDbRepository] interface
 * and the default [DocumentDbRepository] returned by the [DocumentDbRepositoryFactory.create]
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
 * **See also [VersionedEntity]'s and [DocumentDbRepository]'s security warning.***
 *
 * @see VersionedEntity
 * @see DocumentDbRepository
 * @see DocumentDbRepositoryFactory
 */
class PostgresqlDocumentDbRepository<ENTITY : VersionedEntity<ID, ENTITY>, ID>(
    private val unitOfWorkFactory: HandleAwareUnitOfWorkFactory<out HandleAwareUnitOfWork>,
    entityClass: KClass<ENTITY>,
    private val jsonSerializer: JSONSerializer,
    private val idSerializer: IdSerializer<ID>
) : DocumentDbRepository<ENTITY, ID> {
    private var entityConfiguration: EntityConfiguration<ID, ENTITY> = EntityConfiguration.configureEntity(entityClass).build()
    private val indexesAdded = mutableSetOf<Index<ENTITY>>()

    init {
        PostgresqlUtil.checkIsValidTableOrColumnName(entityConfiguration.tableName())
        ensureEntityTableExists()
        ensureEntityIndexesExists()
    }

    private fun ensureEntityTableExists() {
        PostgresqlUtil.checkIsValidTableOrColumnName(entityConfiguration.tableName())
        unitOfWorkFactory.usingUnitOfWork { uow ->
            uow.handle().execute(
                """
            CREATE TABLE IF NOT EXISTS ${entityConfiguration.tableName()} (
                id text PRIMARY KEY,
                data JSONB NOT NULL,
                version BIGINT,
                last_updated TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        """.trimIndent()
            )
            log.info(
                "Ensured that Entity '{}' table '{}' exists",
                entityConfiguration.entityClass(),
                entityConfiguration.tableName()
            )
        }
    }

    private fun ensureEntityIndexesExists() {
        PostgresqlUtil.checkIsValidTableOrColumnName(entityConfiguration.tableName())
        val indexedFields = entityConfiguration.indexedFields()

        unitOfWorkFactory.usingUnitOfWork { uow ->
            indexedFields.forEach { field ->
                val fieldName = field.name
                PostgresqlUtil.checkIsValidTableOrColumnName(fieldName)

                val indexName = "idx_${entityConfiguration.tableName()}_${fieldName}".lowercase()
                PostgresqlUtil.checkIsValidTableOrColumnName(indexName)
                uow.handle().execute("CREATE INDEX IF NOT EXISTS $indexName ON ${entityConfiguration.tableName()} ((data ->> '$fieldName'))")
                log.info(
                    "Ensured that Entity '{}' table '{}' index '{}' exists",
                    entityConfiguration.entityClass(),
                    entityConfiguration.tableName(),
                    indexName
                )
            }
        }
    }

    override fun addIndex(index: Index<ENTITY>): DocumentDbRepository<ENTITY, ID> {
        if (indexesAdded.contains(index)) {
            return this
        }
        PostgresqlUtil.checkIsValidTableOrColumnName(index.name)
        require(index.properties.isNotEmpty()) { "You have to specify at least 1 property" }
        index.properties.forEach { PostgresqlUtil.checkIsValidTableOrColumnName(it.name()) }

        indexesAdded.add(index)
        val properties = index.properties.joinToString(", ") { "(" + it.toJSONValueArrowPath() + ")" }
        val tableName = entityConfiguration().tableName()
        val createIndexSQL = """
            CREATE INDEX IF NOT EXISTS idx_${entityConfiguration.tableName()}_${index.name} ON $tableName (${properties})
        """.trimIndent()

        unitOfWorkFactory.usingUnitOfWork { uow ->
            uow.handle().execute(createIndexSQL)
            log.info(
                "Ensured index '{}' on Entity '{}' table '{}' exists",
                index.name,
                entityConfiguration.entityClass(),
                entityConfiguration.tableName()
            )
        }
        return this
    }

    override fun removeIndex(indexName: String): DocumentDbRepository<ENTITY, ID> {
        PostgresqlUtil.checkIsValidTableOrColumnName(indexName)
        if (indexesAdded.removeIf { it.name == indexName }) {
            unitOfWorkFactory.usingUnitOfWork { uow ->
                uow.handle().execute("DROP INDEX IF EXISTS idx_${entityConfiguration.tableName()}_$indexName")
                log.info(
                    "Ensured index '{}' on Entity '{}' table '{}' was removed",
                    indexName,
                    entityConfiguration.entityClass(),
                    entityConfiguration.tableName()
                )
            }
        }
        return this
    }

    override fun entityConfiguration(): EntityConfiguration<ID, ENTITY> {
        return entityConfiguration
    }

    override fun queryBuilder(): QueryBuilder<ID, ENTITY> {
        return QueryBuilder(entityConfiguration, this)
    }

    override fun condition(): Condition<ENTITY> {
        return Condition(jsonSerializer)
    }

    override fun save(entity: ENTITY, initialVersion: Version): ENTITY {
        var serializedId = getEntityId(entity)
        log.trace(
            "Save '{}' with serialized-id: '{}' and initialVersion: '{}' called",
            entityConfiguration.entityClass().simpleName,
            serializedId,
            initialVersion
        )

        if (serializedId == null) {
            if (entityConfiguration.idProperty() is KMutableProperty1) {
                if (entityConfiguration.idProperty().returnType.classifier == String::class ||
                    entityConfiguration.idProperty().returnType.classifier == StringValueType::class
                ) {
                    // Assign a random id
                    val typedId = createStringOrStringValueTypeId()
                    serializedId = idSerializer(typedId)
                    (entityConfiguration.idProperty() as KMutableProperty1<ENTITY, ID>).setter(entity, typedId)
                    log.debug(
                        "Entity: '{}' -  Assigned random-id with id '{}'",
                        entityConfiguration.entityClass().simpleName,
                        typedId
                    )
                } else {
                    throw IllegalArgumentException("${entityConfiguration.entityClass().simpleName}.${entityConfiguration.idProperty().name} is null, but cannot assign a random id value because the property is not of type String or StringValueType.")
                }
            } else {
                throw IllegalArgumentException("Cannot assign a random id value. ${entityConfiguration.entityClass().simpleName}.${entityConfiguration.idProperty().name} is null but also read-only.")
            }
        }

        updateVersionedEntityProperties(entity, initialVersion)
        val json = jsonSerializer.serialize(entity)

        val rowsUpdated = unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle()
                .createUpdate("INSERT INTO ${entityConfiguration.tableName()} (id, data, version, last_updated) VALUES (:id, :data::jsonb, :version, :lastUpdated) ON CONFLICT DO NOTHING")
                .bind("id", serializedId)
                .bind("data", json)
                .bind("version", initialVersion)
                .bind("lastUpdated", entity.lastUpdated)
                .execute()
        }
        if (rowsUpdated == 0) {
            throw OptimisticLockingException("Failed to save entity of type '${entityConfiguration.entityClass().simpleName}' since was already saved. If you're trying to update an existing entity please use the update method.")
        }
        log.debug(
            "Saved '{}' with serialized-id: '{}' and initialVersion: '{}'",
            entityConfiguration.entityClass().simpleName,
            serializedId,
            initialVersion
        )

        return entity
    }

    private fun createStringOrStringValueTypeId() = entityConfiguration.idPropertyType().primaryConstructor!!.call(RandomIdGenerator.generate()) as ID

    override fun update(entity: ENTITY): ENTITY {
        return update(entity, getRequiredEntityVersion(entity).increment())
    }

    override fun update(entity: ENTITY, nextVersion: Version): ENTITY {
        val serializedId = getRequiredEntityId(entity)
        val loadedVersion = getRequiredEntityVersion(entity)
        log.debug(
            "Update '{}' with serialized-id: '{}', loaded-version: '{}' and next-version: '{}'",
            entityConfiguration.entityClass().simpleName,
            serializedId,
            loadedVersion,
            nextVersion
        )

        updateVersionedEntityProperties(entity, nextVersion)
        val json = jsonSerializer.serialize(entity)


        val sql =
            "UPDATE ${entityConfiguration.tableName()} SET data = :data::jsonb, version = :nextVersion, last_updated = :lastUpdated WHERE id = :id AND version = :loadedVersion"

        val rowsUpdated = unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle().createUpdate(sql)
                .bind("data", json)
                .bind("nextVersion", nextVersion)
                .bind("loadedVersion", loadedVersion)
                .bind("lastUpdated", entity.lastUpdated)
                .bind("id", serializedId)
                .execute()
        }

        if (rowsUpdated == 0) {
            throw OptimisticLockingException("Failed to update entity of type ${entityConfiguration.entityClass().simpleName} due to version mismatch.")
        }
        log.debug(
            "Updated '{}' with serialized-id: '{}' and version: '{}'",
            entityConfiguration.entityClass().simpleName,
            serializedId,
            nextVersion
        )
        return entity
    }

    override fun deleteById(id: ID) {
        val serializedId = idSerializer(id)
        unitOfWorkFactory.usingUnitOfWork { uow ->
            var rowsUpdated = uow.handle().execute("DELETE FROM ${entityConfiguration.tableName()} WHERE id = ?", serializedId)
            if (rowsUpdated == 1) {
                log.debug(
                    "Deleted Entity '{}' with serialized-id '{}'",
                    entityConfiguration.entityClass().simpleName,
                    serializedId
                )
            } else {
                log.debug(
                    "Entity '{}' with serialized-id '{}' was already deleted",
                    entityConfiguration.entityClass().simpleName,
                    serializedId
                )
            }
        }
    }

    override fun find(queryBuilder: QueryBuilder<ID, ENTITY>): List<ENTITY> {
        return unitOfWorkFactory.withUnitOfWork<List<ENTITY>> { uow ->
            val query = queryBuilder.build()
            log.trace(
                "Query '{}' using SQL: '{}'",
                entityConfiguration.entityClass().simpleName,
                query.sql
            )
            val result = uow.handle().createQuery(query.sql)
            query.bindings.forEach { (key, value) ->
                result.bind(key, value)
            }
            result
                .mapTo(String::class.java)
                .map { json -> jsonSerializer.deserialize(json, entityConfiguration.entityClass().java) as ENTITY }
                .list()
        }
    }

    override fun findById(id: ID): ENTITY? {
        val serializedId = idSerializer(id)
        val matchingEntity = unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle().createQuery("SELECT data FROM ${entityConfiguration.tableName()} WHERE id = :id")
                .bind("id", serializedId)
                .mapTo(String::class.java)
                .findOne()
                .map { json -> jsonSerializer.deserialize(json, entityConfiguration.entityClass().java) }
                .orElse(null)
        }
        if (matchingEntity != null) {
            log.debug(
                "Found Entity '{}' with serialized-id: '{}'",
                entityConfiguration.entityClass().simpleName,
                serializedId
            )
        } else {
            log.debug(
                "Did NOT find Entity '{}' with serialized-id: '{}'",
                entityConfiguration.entityClass().simpleName,
                serializedId
            )
        }
        return matchingEntity
    }

    override fun delete(entity: ENTITY) {
        deleteById(getRequiredTypedEntityId(entity))
    }

    override fun existsById(id: ID): Boolean {
        val serializedId = idSerializer(id)
        val exists = unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle().createQuery("SELECT COUNT(*) FROM ${entityConfiguration.tableName()} WHERE id = :id")
                .bind("id", serializedId)
                .mapTo(Int::class.java)
                .one() > 0
        }
        if (exists) {
            log.debug(
                "Exists -> Entity '{}' Entity with serialized-id: '{}'",
                entityConfiguration.entityClass().simpleName,
                serializedId
            )
        } else {
            log.debug(
                "Does NOT Exist -> Entity '{}' with serialized-id: '{}'",
                entityConfiguration.entityClass().simpleName,
                serializedId
            )
        }
        return exists
    }

    override fun saveAll(entities: Iterable<ENTITY>): List<ENTITY> {
        log.debug(
            "saveAll: {} '{}' entities",
            entities.count(),
            entityConfiguration.entityClass().simpleName
        )

        unitOfWorkFactory.usingUnitOfWork { uow ->
            entities.forEach(this::save)
        }
        return entities.toList()
    }

    override fun updateAll(entities: Iterable<ENTITY>): List<ENTITY> {
        log.debug(
            "updateAll: {} '{}' entities",
            entities.count(),
            entityConfiguration.entityClass().simpleName
        )

        unitOfWorkFactory.usingUnitOfWork { uow ->
            entities.forEach(this::update)
        }
        return entities.toList()
    }

    override fun deleteAll(entities: Iterable<ENTITY>) {
        log.debug(
            "deleteAll: {} '{}' entities",
            entities.count(),
            entityConfiguration.entityClass().simpleName
        )

        unitOfWorkFactory.usingUnitOfWork { uow ->
            entities.forEach(this::delete)
        }
    }

    override fun findAll(): List<ENTITY> {
        log.debug(
            "findAll: '{}' entities",
            entityConfiguration.entityClass().simpleName
        )

        val matches = executeEntityListQuery("SELECT data FROM ${entityConfiguration.tableName()}")
        log.debug(
            "findAll: Found {} '{}' entities",
            matches.size,
            entityConfiguration.entityClass().simpleName
        )
        return matches
    }

    override fun count(): Long {
        val count = unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle().createQuery("SELECT count(*) FROM ${entityConfiguration.tableName()}")
                .mapTo(Long::class.java)
                .one()
        }
        log.debug(
            "count: Found {} '{}' entities",
            count,
            entityConfiguration.entityClass().simpleName,
        )
        return count
    }

    override fun findAllById(ids: Iterable<ID>): List<ENTITY> {
        log.debug(
            "findAllById: Find '{}' entities from {} id(s)",
            entityConfiguration.entityClass().simpleName,
            ids.count()
        )
        val matches = unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle().createQuery("SELECT data FROM ${entityConfiguration.tableName()} WHERE id IN (<ids>)")
                .bindList("ids", ids.map { idSerializer(it) })
                .mapTo(String::class.java)
                .map { json -> jsonSerializer.deserialize(json, entityConfiguration.entityClass().java) as ENTITY }
                .list()
        }
        log.debug(
            "findAllById: Found {} '{}' entities from {} id(s)",
            matches.size,
            entityConfiguration.entityClass().simpleName,
            ids.count()
        )
        return matches
    }

    override fun deleteAllById(ids: Iterable<ID>) {
        log.debug(
            "deleteAllById: Delete '{}' entities from {} id(s)",
            entityConfiguration.entityClass().simpleName,
            ids.count()
        )
        val numberOfDeletedRows = unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle()
                .createUpdate("DELETE FROM ${entityConfiguration.tableName()} WHERE id IN (<ids>)")
                .bindList("ids", ids.map { idSerializer(it) })
                .execute()
        }
        log.debug(
            "deleteAllById: Deleted {} '{}' entities from {} id(s)",
            numberOfDeletedRows,
            entityConfiguration.entityClass().simpleName,
            ids.count()
        )
    }

    override fun deleteAll() {
        val numberOfDeletedRows = unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle().createUpdate("DELETE FROM ${entityConfiguration.tableName()}")
                .execute()
        }
        log.debug(
            "deleteAll: Deleted {} '{}' entities",
            numberOfDeletedRows,
            entityConfiguration.entityClass().simpleName
        )
    }

    private fun executeEntityListQuery(sql: String): List<ENTITY> {
        return unitOfWorkFactory.withUnitOfWork { uow ->
            uow.handle()
                .createQuery(sql)
                .mapTo(String::class.java)
                .map { json -> jsonSerializer.deserialize(json, entityConfiguration.entityClass().java) as ENTITY }
                .list()
        }
    }

    private fun updateVersionedEntityProperties(entity: ENTITY, version: Version): ENTITY {
        if (log.isTraceEnabled) {
            log.trace(
                "Entity '{}' with serialized-id '{}' setting '{}' to '{}'",
                entityConfiguration.entityClass().simpleName,
                getEntityId(entity),
                VERSION_PROPERTY_NAME,
                version
            )
        }
        entityConfiguration.versionProperty().setter.call(entity, version)
        entityConfiguration.lastUpdatedProperty().setter.call(entity, OffsetDateTime.now(UTC))
        return entity
    }

    private fun getEntityVersion(entity: ENTITY): Version? {
        return entityConfiguration.versionProperty().getter.call(entity)
    }

    private fun getEntityId(entity: ENTITY): String? {
        val id = getTypedEntityId(entity)
        return if (id != null) idSerializer(id) else null
    }

    private fun getTypedEntityId(entity: ENTITY): ID? {
        return entityConfiguration.idProperty().getter.call(entity)
    }

    private fun getRequiredEntityId(entity: ENTITY): String {
        return idSerializer(getRequiredTypedEntityId(entity))
    }

    private fun getRequiredTypedEntityId(entity: ENTITY): ID {
        return getTypedEntityId(entity)
            ?: throw IllegalArgumentException("${entityConfiguration.entityClass().simpleName}.${entityConfiguration.idProperty().name} has value null. Expected a non-null value")
    }

    private fun getRequiredEntityVersion(entity: ENTITY): Version {
        return entityConfiguration.versionProperty().getter.call(entity) as? Version
            ?: throw IllegalArgumentException("${entityConfiguration.entityClass().simpleName}.${entityConfiguration.versionProperty().name} has value null. Expected a non-null value")
    }

    companion object {
        val log = LoggerFactory.getLogger(DocumentDbRepository::class.java)
    }
}

class VersionArgumentFactory : LongValueTypeArgumentFactory<Version>() {
}

class VersionColumnMapper : LongValueTypeColumnMapper<Version>() {
}