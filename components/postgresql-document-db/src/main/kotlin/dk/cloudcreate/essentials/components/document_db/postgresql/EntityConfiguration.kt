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

import dk.cloudcreate.essentials.components.document_db.Version
import dk.cloudcreate.essentials.components.document_db.VersionedEntity
import dk.cloudcreate.essentials.components.document_db.annotations.DocumentEntity
import dk.cloudcreate.essentials.components.document_db.annotations.Id
import dk.cloudcreate.essentials.components.document_db.annotations.Indexed
import dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil
import dk.cloudcreate.essentials.shared.reflection.BoxedTypes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

const val VERSION_PROPERTY_NAME = "version"
const val LAST_UPDATED_PROPERTY_NAME = "lastUpdated"

/**
 * [VersionedEntity] Configuration class used by [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository]
 *
 * **See [VersionedEntity]'s and [EntityConfiguration.checkPropertyNames] security warning.***
 */
@Suppress("UNCHECKED_CAST")
class EntityConfiguration<ID, T : VersionedEntity<ID, T>>(private val entityClass: KClass<T>) {
    private var tableName: String? = null
    private var idProperty: KProperty1<T, ID>? = null
    private var versionProperty: KMutableProperty1<T, Version>? = null
    private var lastUpdatedProperty: KMutableProperty1<T, ZonedDateTime>? = null
    private val indexedProperties = mutableListOf<KProperty1<T, *>>()
    private var idPropertyType: KClass<*>? = null

    fun tableName(tableName: String) = apply {
        log.info("Resolved Entity '{}' table name '{}'", entityClass.qualifiedName, tableName)
        PostgresqlUtil.checkIsValidTableOrColumnName(tableName)
        this.tableName = tableName
    }

    fun idProperty(idProperty: KProperty1<T, ID>) = apply {
        log.info("Resolved Entity '{}' @Id property '{}'", entityClass.simpleName, idProperty)
        PostgresqlUtil.checkIsValidTableOrColumnName(idProperty.name)
        this.idProperty = idProperty
        this.idPropertyType = idProperty.returnType.classifier as? KClass<*>
    }

    fun isValueClassWrappingString(kClass: KClass<*>?): Boolean {
        if (kClass == null) return false

        val primaryConstructor = kClass.primaryConstructor
        val hasSingleStringParameter = primaryConstructor?.parameters?.singleOrNull()?.type?.jvmErasure == String::class

        return hasSingleStringParameter && kClass.isValue
    }

    fun versionProperty(versionProperty: KProperty1<T, Version>) =
        apply {
            PostgresqlUtil.checkIsValidTableOrColumnName(versionProperty.name)
            val readWriteableVersionProperty = versionProperty as? KMutableProperty1<T, Version>
                ?: throw IllegalStateException("The '${VERSION_PROPERTY_NAME}' property '${versionProperty}' is read-only")
            this.versionProperty = readWriteableVersionProperty
        }

    fun lastUpdatedProperty(lastUpdatedProperty: KProperty1<T, ZonedDateTime>) =
        apply {
            PostgresqlUtil.checkIsValidTableOrColumnName(lastUpdatedProperty.name)
            val readWriteableVersionProperty = lastUpdatedProperty as? KMutableProperty1<T, ZonedDateTime>
                ?: throw IllegalStateException("The '${LAST_UPDATED_PROPERTY_NAME}' property '${lastUpdatedProperty}' is read-only")
            this.lastUpdatedProperty = readWriteableVersionProperty
        }

    fun addIndexedProperty(property: KProperty1<T, *>) = apply {
        log.info("Adding Entity '{}' indexed property '{}'", entityClass.simpleName, property)
        PostgresqlUtil.checkIsValidTableOrColumnName(property.name)
        this.indexedProperties.add(property)
    }

    fun indexedFields(): List<KProperty1<T, *>> = indexedProperties

    fun entityClass(): KClass<T> = entityClass

    fun tableName(): String {
        return tableName ?: throw IllegalStateException("Table name is not configured")
    }

    fun idProperty(): KProperty1<T, ID> {
        return idProperty ?: throw IllegalStateException("@Id property is not configured")
    }

    fun idPropertyType(): KClass<*> {
        return idPropertyType
            ?: throw IllegalStateException("The type of the @Id property '${idProperty!!.name}' type isn't a KClass")
    }

    fun versionProperty(): KMutableProperty1<T, dk.cloudcreate.essentials.components.document_db.Version> {
        return versionProperty ?: throw IllegalStateException("$VERSION_PROPERTY_NAME property is not configured")
    }

    fun lastUpdatedProperty(): KMutableProperty1<T, ZonedDateTime> {
        return lastUpdatedProperty ?: throw IllegalStateException("$LAST_UPDATED_PROPERTY_NAME property is not configured")
    }

    fun build(): EntityConfiguration<ID, T> {
        requireNotNull(tableName) { "Table name must not be null. Have you applied the @DocumentEntity at the type level?" }
        requireNotNull(idProperty) { "@Id property must not be null" }
        requireNotNull(versionProperty) { "version property must not be null" }
        requireNotNull(lastUpdatedProperty) { "lastUpdated property must not be null" }
        return this
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(EntityConfiguration::class.java)

        /**
         * **See [VersionedEntity]'s  and [EntityConfiguration.checkPropertyNames]'s security warning**
         */
        fun <T : VersionedEntity<ID, T>, ID> configureEntity(entityClass: KClass<T>): EntityConfiguration<ID, T> {
            val config = EntityConfiguration(entityClass)
            val documentEntityAnnotation = entityClass.findAnnotation<DocumentEntity>() ?: throw IllegalArgumentException("Entity class ${entityClass.qualifiedName} doesn't contain a @${DocumentEntity::class.simpleName} annotation")

            documentEntityAnnotation!!.let {
                config.tableName(it.tableName)
            }
            val context = listOf(entityClass.simpleName!!)
            entityClass.declaredMemberProperties.forEach { property ->
                when {
                    property.findAnnotation<Id>() != null -> config.idProperty(property as KProperty1<T, ID>)
                    property.name == VERSION_PROPERTY_NAME -> config.versionProperty(property as KProperty1<T, Version>)
                    property.name == LAST_UPDATED_PROPERTY_NAME -> config.lastUpdatedProperty(property as KProperty1<T, ZonedDateTime>)
                    property.findAnnotation<Indexed>() != null -> config.addIndexedProperty(property)
                }
                checkPropertyNames(property, context)
            }
            return config.build()
        }

        /**
         * Check [KProperty1.name] and sub property names of the [KProperty1.returnType] properties, using [KClass.memberProperties], recursively using [PostgresqlUtil.checkIsValidTableOrColumnName]
         *
         * ** Security notice**
         * The [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository] instance created,
         * e.g. by [dk.cloudcreate.essentials.components.document_db.DocumentDbRepositoryFactory.create], will call the [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName]
         * to check table name (see [dk.cloudcreate.essentials.components.document_db.annotations.DocumentEntity]) and JSON property names, that are resulting from the JSON serialization of the concrete [VersionedEntity] being persisted,
         * using [dk.cloudcreate.essentials.components.document_db.postgresql.EntityConfiguration.checkPropertyNames], since the table name and JSON property names will be used in SQL string concatenations,
         * which exposes the components (such as [dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository]) to SQL injection attacks.
         *
         * This method attempts to check all property name against [dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName]
         *
         * This method is not provide exhaustive protection and may overlook properties that should have been checked.
         *
         * This method does not assure the complete security of the resulting SQL against SQL injection threats.
         *
         * **The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes**
         *
         * Users must ensure thorough sanitization and validation of API input parameters, column, table, and index names.
         *
         * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.
         */
        fun <T> checkPropertyNames(property: KProperty1<T, *>, propertyContext: List<String> = listOf(), alreadyCheckedTypes: MutableSet<KClass<*>> = mutableSetOf()) {
            val context = (propertyContext + property.name).joinToString(separator = ".")
            PostgresqlUtil.checkIsValidTableOrColumnName(property.name, context)
            val classifier = property.returnType.classifier
            val propertyType = if (classifier is KClass<*>) classifier else {
                log.debug(
                    "CheckPropertyNames: Context '{}' with property type '{}' doesn't have a classifier",
                    context,
                    property.returnType
                )
                return
            }
            // Avoid double-checking and infinite recursion for cyclic dependencies
            if (propertyType in alreadyCheckedTypes) return

            if (propertyType.isValue ||
                isKotlinOrJavaBuiltInType(propertyType) ||
                isKotlinCollectionOrMapType(propertyType)
            ) {
                if (log.isDebugEnabled) {
                    log.debug(
                        "CheckPropertyNames: Context '{}' with property type '{}' -> isValueClass: {}, isKotlinOrJavaBuiltInType: {}, isKotlinCollectionOrMapType: {}",
                        context,
                        property.returnType,
                        propertyType.isValue,
                        isKotlinOrJavaBuiltInType(propertyType),
                        isKotlinCollectionOrMapType(propertyType)
                    )
                }
                return
            }

            alreadyCheckedTypes.add(propertyType)
            val subPropertyContext =  propertyContext + property.name
            propertyType.memberProperties.forEach { memberProperty ->
                checkPropertyNames(memberProperty as KProperty1<Any, *>, subPropertyContext, alreadyCheckedTypes)
            }
        }

        /**
         * Check for whether a type is a Kotlin or Java built in type - the test is not an exhaustive and may fail to identify some types as Kotlin/Java built-in types
         *
         * Check is it's a [Number], [CharSequence], [dk.cloudcreate.essentials.shared.reflection.BoxedTypes.isBoxedType],
         * [dk.cloudcreate.essentials.shared.reflection.BoxedTypes.isPrimitiveType] or if the type belongs to these package names:
         * `"kotlin", "kotlin.collections", "java.math"`
         */
        fun isKotlinOrJavaBuiltInType(type: KClass<*>): Boolean {
            if (type is Number || Number::class.java.isAssignableFrom(type.javaObjectType) ||
                type is CharSequence || CharSequence::class.java.isAssignableFrom(type.javaObjectType) ||
                BoxedTypes.isBoxedType(type.java) || BoxedTypes.isPrimitiveType(type.java) ||
                isKotlinCollectionOrMapType(type)
            ) return true

            val packageName = type.qualifiedName?.substringBeforeLast('.')
            return packageName in setOf("kotlin", "kotlin.collections", "java.math", "java.time")
        }

        /**
         * Checks if the [type]'s [KClass.javaObjectType] is a Java [Collection] or [Map] (and thereby it should also be a Kotlin Collection and Map)
         */
        fun isKotlinCollectionOrMapType(type: KClass<*>): Boolean {
            return type.javaObjectType.let {
                Collection::class.java.isAssignableFrom(it) || Map::class.java.isAssignableFrom(it)
            }
        }
    }
}
