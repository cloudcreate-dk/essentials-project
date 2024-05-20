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

package dk.cloudcreate.essentials.components.document_db.postgresql

import dk.cloudcreate.essentials.components.document_db.*
import dk.cloudcreate.essentials.components.foundation.json.JSONSerializer
import dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWork
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory
import dk.cloudcreate.essentials.components.foundation.types.RandomIdGenerator
import dk.cloudcreate.essentials.kotlin.types.StringValueType
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.primaryConstructor

/**
 * Default [DocumentDbRepository] returned by the [DocumentDbRepositoryFactory.create]
 *
 * **See [VersionedEntity]'s security warning.***
 */
class PostgresqlDocumentDbRepository<ENTITY : VersionedEntity<ID, ENTITY>, ID>(
    private val unitOfWorkFactory: HandleAwareUnitOfWorkFactory<out HandleAwareUnitOfWork>,
    entityClass: KClass<ENTITY>,
    private val jsonSerializer: JSONSerializer
) : DocumentDbRepository<ENTITY, ID> {
    private var entityConfiguration: EntityConfiguration<ID, ENTITY>

    init {
        entityConfiguration = EntityConfiguration.configureEntity(entityClass).build()
        PostgresqlUtil.checkIsValidTableOrColumnName(entityConfiguration.tableName())
        ensureEntityTableExists()
        ensureEntityIndexesExists()
    }

    private fun ensureEntityTableExists() {
        PostgresqlUtil.checkIsValidTableOrColumnName(entityConfiguration.tableName())
        unitOfWorkFactory.usingUnitOfWork {
            it.handle().execute(
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

        unitOfWorkFactory.usingUnitOfWork {
            indexedFields.forEach { field ->
                val fieldName = field.name
                PostgresqlUtil.checkIsValidTableOrColumnName(fieldName)

                val indexName = "idx_${entityConfiguration.tableName()}_${fieldName}".lowercase()
                PostgresqlUtil.checkIsValidTableOrColumnName(indexName)
                it.handle().execute("CREATE INDEX IF NOT EXISTS $indexName ON ${entityConfiguration.tableName()} ((data ->> '$fieldName'))")
                log.info(
                    "Ensured that Entity '{}' table '{}' index '{}' exists",
                    entityConfiguration.entityClass(),
                    entityConfiguration.tableName(),
                    indexName
                )
            }
        }
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
        var id = getEntityId(entity)
        log.trace(
            "Save '{}' with id: '{}' and initialVersion: '{}' called",
            entityConfiguration.entityClass().simpleName,
            id,
            initialVersion
        )

        if (id == null) {
            if (entityConfiguration.idProperty() is KMutableProperty1) {
                if (entityConfiguration.idProperty().returnType.classifier == String::class ||
                    entityConfiguration.idProperty().returnType.classifier == StringValueType::class
                ) {
                    // Assign a random id
                    id =
                        entityConfiguration.idPropertyType().primaryConstructor!!.call(RandomIdGenerator.generate()) as ID
                    (entityConfiguration.idProperty() as KMutableProperty1<ENTITY, ID>).setter(entity, id)
                    log.debug(
                        "Entity: '{}' -  Assigned random-id with value '{}'",
                        entityConfiguration.entityClass().simpleName,
                        id
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

        val rowsUpdated = unitOfWorkFactory.withUnitOfWork {
            it.handle()
                .createUpdate("INSERT INTO ${entityConfiguration.tableName()} (id, data, version, last_updated) VALUES (:id, :data::jsonb, :version, :lastUpdated) ON CONFLICT DO NOTHING")
                .bind("id", id)
                .bind("data", json)
                .bind("version", initialVersion)
                .bind("lastUpdated", entity.lastUpdated)
                .execute()
        }
        if (rowsUpdated == 0) {
            throw OptimisticLockingException("Failed to save entity of type '${entityConfiguration.entityClass().simpleName}' since was already saved. If you're trying to update an existing entity please use the update method.")
        }
        log.debug(
            "Saved '{}' with id: '{}' and initialVersion: '{}'",
            entityConfiguration.entityClass().simpleName,
            id,
            initialVersion
        )

        return entity
    }

    override fun update(entity: ENTITY): ENTITY {
        return update(entity, getRequiredEntityVersion(entity).increment())
    }

    override fun update(entity: ENTITY, nextVersion: Version): ENTITY {
        var id: ID = getRequiredEntityId(entity)
        val loadedVersion = getRequiredEntityVersion(entity)
        log.debug(
            "Update '{}' with id: '{}', loaded-version: '{}' and next-version: '{}'",
            entityConfiguration.entityClass().simpleName,
            id,
            loadedVersion,
            nextVersion
        )

        updateVersionedEntityProperties(entity, nextVersion)
        val json = jsonSerializer.serialize(entity)


        val sql =
            "UPDATE ${entityConfiguration.tableName()} SET data = :data::jsonb, version = :nextVersion, last_updated = :lastUpdated WHERE id = :id AND version = :loadedVersion"

        val rowsUpdated = unitOfWorkFactory.withUnitOfWork {
            it.handle().createUpdate(sql)
                .bind("data", json)
                .bind("nextVersion", nextVersion)
                .bind("loadedVersion", loadedVersion)
                .bind("lastUpdated", entity.lastUpdated)
                .bind("id", id)
                .execute()
        }

        if (rowsUpdated == 0) {
            throw OptimisticLockingException("Failed to update entity of type ${entityConfiguration.entityClass().simpleName} due to version mismatch.")
        }
        log.debug(
            "Updated '{}' with id: '{}' and version: '{}'",
            entityConfiguration.entityClass().simpleName,
            id,
            nextVersion
        )
        return entity
    }

    override fun deleteById(id: ID) {
        unitOfWorkFactory.usingUnitOfWork {
            var rowsUpdated = it.handle().execute("DELETE FROM ${entityConfiguration.tableName()} WHERE id = ?", id)
            if (rowsUpdated == 1) {
                log.debug(
                    "Deleted Entity '{}' with id '{}'",
                    entityConfiguration.entityClass().simpleName,
                    id
                )
            } else {
                log.debug(
                    "Entity '{}' with id '{}' was already deleted",
                    entityConfiguration.entityClass().simpleName,
                    id
                )
            }
        }
    }

    override fun find(queryBuilder: QueryBuilder<ID, ENTITY>): List<ENTITY> {
        return unitOfWorkFactory.withUnitOfWork<List<ENTITY>> {
            val query = queryBuilder.build()
            log.trace(
                "Query '{}' using SQL: '{}'",
                entityConfiguration.entityClass().simpleName,
                query.sql
            )
            val result = it.handle().createQuery(query.sql)
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
        val matchingEntity = unitOfWorkFactory.withUnitOfWork {
            it.handle().createQuery("SELECT data FROM ${entityConfiguration.tableName()} WHERE id = :id")
                .bind("id", id)
                .mapTo(String::class.java)
                .findOne()
                .map { json -> jsonSerializer.deserialize(json, entityConfiguration.entityClass().java) }
                .orElse(null)
        }
        if (matchingEntity != null) {
            log.debug(
                "Found Entity '{}' with id: '{}'",
                entityConfiguration.entityClass().simpleName,
                id
            )
        } else {
            log.debug(
                "Did NOT find Entity '{}' with id: '{}'",
                entityConfiguration.entityClass().simpleName,
                id
            )
        }
        return matchingEntity
    }

    override fun delete(entity: ENTITY) {
        deleteById(getRequiredEntityId(entity))
    }

    override fun existsById(id: ID): Boolean {
        val exists = unitOfWorkFactory.withUnitOfWork {
            it.handle().createQuery("SELECT COUNT(*) FROM ${entityConfiguration.tableName()} WHERE id = :id")
                .bind("id", id)
                .mapTo(Int::class.java)
                .one() > 0
        }
        if (exists) {
            log.debug(
                "Exists -> Entity '{}' Entity with id: '{}'",
                entityConfiguration.entityClass().simpleName,
                id
            )
        } else {
            log.debug(
                "Does NOT Exist -> Entity '{}' with id: '{}'",
                entityConfiguration.entityClass().simpleName,
                id
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

        unitOfWorkFactory.usingUnitOfWork {
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

        unitOfWorkFactory.usingUnitOfWork {
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

        unitOfWorkFactory.usingUnitOfWork {
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
        val count = unitOfWorkFactory.withUnitOfWork {
            it.handle().createQuery("SELECT count(*) FROM ${entityConfiguration.tableName()}")
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
        val matches = unitOfWorkFactory.withUnitOfWork {
            it.handle().createQuery("SELECT data FROM ${entityConfiguration.tableName()} WHERE id IN (<ids>)")
                .bindList("ids", ids)
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
        val numberOfDeletedRows = unitOfWorkFactory.withUnitOfWork {
            it.handle()
                .createUpdate("DELETE FROM ${entityConfiguration.tableName()} WHERE id IN (<ids>)")
                .bindList("ids", ids)
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
        val numberOfDeletedRows = unitOfWorkFactory.withUnitOfWork {
            it.handle().createUpdate("DELETE FROM ${entityConfiguration.tableName()}")
                .execute()
        }
        log.debug(
            "deleteAll: Deleted {} '{}' entities",
            numberOfDeletedRows,
            entityConfiguration.entityClass().simpleName
        )
    }

    private fun executeEntityListQuery(sql: String): List<ENTITY> {
        return unitOfWorkFactory.withUnitOfWork {
            it.handle()
                .createQuery(sql)
                .mapTo(String::class.java)
                .map { json -> jsonSerializer.deserialize(json, entityConfiguration.entityClass().java) as ENTITY }
                .list()
        }
    }

    private fun updateVersionedEntityProperties(entity: ENTITY, version: Version): ENTITY {
        if (log.isTraceEnabled) {
            log.trace(
                "Entity '{}' with id '{}' setting '{}' to '{}'",
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

    private fun getEntityId(entity: ENTITY): ID? {
        return entityConfiguration.idProperty().getter.call(entity)
    }

    private fun getRequiredEntityId(entity: ENTITY): ID {
        return getEntityId(entity)
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
