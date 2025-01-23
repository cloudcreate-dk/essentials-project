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

import dk.cloudcreate.essentials.components.document_db.DocumentDbRepository
import dk.cloudcreate.essentials.components.document_db.DocumentDbRepositoryFactory
import dk.cloudcreate.essentials.components.document_db.VersionedEntity
import dk.cloudcreate.essentials.components.document_db.annotations.DocumentEntity
import dk.cloudcreate.essentials.components.foundation.json.JSONSerializer
import dk.cloudcreate.essentials.kotlin.types.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSuperclassOf


/**
 * Query condition
 * **See [VersionedEntity]'s security warning.***
 */
class Condition<T>(val jsonSerializer: JSONSerializer) {
    internal val conditions = mutableListOf<String>()
    internal val bindings = mutableMapOf<String, Any?>()

    // Unique binding names counter
    private var bindCounter = 0

    internal fun uniqueBindName(property: Property<*, *>): String {
        return "${property.name()}__$bindCounter".also { bindCounter++ }
    }

    /**
     * Provide the constraints on the Query by applying a [Condition]
     * (which is created by calling [DocumentDbRepository.condition])
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
     */
    infix fun matching(init: Condition<T>.() -> Unit): Condition<T> {
        this.init()
        return this
    }

    /**
     * Similar to SQL equals =
     */
    infix fun <R> KProperty1<T, R>.eq(value: R) = applyCondition(this, "=", value)

    /**
     * Similar to SQL <
     */
    infix fun <R> KProperty1<T, R>.lt(value: R) = applyCondition(this, "<", value)

    /**
     * Similar to SQL <=
     */
    infix fun <R> KProperty1<T, R>.lte(value: R) = applyCondition(this, "<=", value)

    /**
     * Similar to SQL >
     */
    infix fun <R> KProperty1<T, R>.gt(value: R) = applyCondition(this, ">", value)

    /**
     * Similar to SQL >=
     */
    infix fun <R> KProperty1<T, R>.gte(value: R) = applyCondition(this, ">=", value)

//    infix fun <R> KProperty1<T, R>.contains(value: R): Condition<T> = applyCondition(this, "@>", value)

    /**
     * Similar to SQL like
     *
     * Example:
     * ```
     * result = orderRepository.queryBuilder()
     *     .where(orderRepository.condition()
     *         .matching {
     *             (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                 .and(Order::description like "%unique%")
     *         })
     *     .find()
     * ```
     */
    infix fun KProperty1<T, String>.like(value: String): Condition<T> = applyCondition(this, "LIKE", value)

//    infix fun KProperty1<T, Collection<String>>.anyLike(value: String): KProperty1<T, Collection<String>> {
//        val conditionProperty = SingleProperty(this)
//        val bindName = uniqueBindName(conditionProperty)
//        conditions.add("EXISTS (SELECT 1 FROM jsonb_array_elements_text(${conditionProperty.toJSONValueArrowPath()}) AS elem WHERE elem LIKE :$bindName)")
//        bindings[bindName] = value
//        return this
//    }

    /**
     * Chain [KProperty1]'s to create a [NestedProperty]
     *
     * Example:
     * ```
     *  val result = orderRepository.queryBuilder()
     *             .where(orderRepository.condition()
     *                 .matching {
     *                     Order::contactDetails then ContactDetails::address then Address::city like "Some Other%"
     *                 })
     *             .orderBy(Order::contactDetails then ContactDetails::address then Address::city, QueryBuilder.Order.ASC)
     *             .limit(200)
     *             .find()
     * ```
     */
    infix fun <R, V> KProperty1<T, R>.then(property: KProperty1<R, V>): NestedProperty<T, V> {
        return NestedProperty(this@Condition, listOf(this, property))
    }


    /**
     * Combine two conditions with an **and** statement:
     *
     * Example:
     * ```
     * result = orderRepository.queryBuilder()
     *     .where(orderRepository.condition()
     *         .matching {
     *             (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                 .and(Order::description like "%unique%")
     *         })
     *     .find()
     * ```
     */
    infix fun and(other: Condition<T>): Condition<T> = apply {
        val lastCondition = conditions.removeLast()
        val previousCondition = conditions.removeLast()
        conditions.add("($previousCondition AND $lastCondition)")
    }

    /**
     * Combine two conditions with an **or** statement:
     *
     * Example:
     * ```
     * result = orderRepository.queryBuilder()
     *     .where(orderRepository.condition()
     *         .matching {
     *             (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                 .and(Order::description like "%unique%")
     *         })
     *     .find()
     * ```
     */
    infix fun or(other: Condition<T>): Condition<T> = apply {
        val lastCondition = conditions.removeLast()
        val previousCondition = conditions.removeLast()
        conditions.add("($previousCondition OR $lastCondition)")
    }

    private fun <R> applyCondition(property: KProperty1<T, R>, operator: String, value: R): Condition<T> {
        return applyCondition(SingleProperty(property), operator, value)
    }

    internal fun <R> applyCondition(property: Property<T, R>, operator: String, value: R): Condition<T> {
        val bindName = uniqueBindName(property)
        val propertyType: KType = property.returnType()
        val classifier = propertyType.classifier as? KClass<*>
            ?: throw IllegalArgumentException("Unsupported type '${propertyType.classifier}' for property ${property.name()}")

        val dbType = when {
            classifier == LocalDate::class -> "DATE"
            LocalDateValueType::class.isSuperclassOf(classifier) -> "DATE"
            classifier == LocalTime::class -> "TIME"
            LocalTimeValueType::class.isSuperclassOf(classifier) -> "TIME"
            classifier == LocalDateTime::class -> "TIMESTAMP"
            LocalDateTimeValueType::class.isSuperclassOf(classifier) -> "TIMESTAMP"
            classifier == Instant::class || classifier == OffsetDateTime::class || classifier == ZonedDateTime::class -> "TIMESTAMPTZ"
            InstantValueType::class.isSuperclassOf(classifier) || OffsetDateTimeValueType::class.isSuperclassOf(classifier) || ZonedDateTimeValueType::class.isSuperclassOf(classifier) -> "TIMESTAMPTZ"
            IntValueType::class.isSuperclassOf(classifier) -> "INTEGER"
            LongValueType::class.isSuperclassOf(classifier) -> "BIGINT"
            FloatValueType::class.isSuperclassOf(classifier) -> "REAL"
            DoubleValueType::class.isSuperclassOf(classifier) -> "DOUBLE PRECISION"
            BigDecimalValueType::class.isSuperclassOf(classifier) -> "DOUBLE PRECISION"
            BigIntegerValueType::class.isSuperclassOf(classifier) -> "NUMERIC"
            BooleanValueType::class.isSuperclassOf(classifier) -> "BOOLEAN"
            ShortValueType::class.isSuperclassOf(classifier) -> "SMALLINT"
            ByteValueType::class.isSuperclassOf(classifier) -> "SMALLINT"
            StringValueType::class.isSuperclassOf(classifier) -> "TEXT"
            classifier == Int::class -> "INTEGER"
            classifier == Long::class -> "BIGINT"
            classifier == Float::class -> "REAL"
            classifier == Double::class -> "DOUBLE PRECISION"
            classifier == BigDecimal::class -> "DOUBLE PRECISION"
            classifier == BigInteger::class -> "NUMERIC"
            classifier == Boolean::class -> "BOOLEAN"
            classifier == Short::class -> "SMALLINT"
            classifier == Byte::class -> "SMALLINT"
            classifier == String::class -> "TEXT"
            else -> null
        }

        val condition = if (dbType != null) {
            "CAST(${property.toJSONValueArrowPath()} AS $dbType) $operator :$bindName"
        } else {
            "${property.toJSONValueArrowPath()} $operator :$bindName"
        }

        conditions.add(condition)

        bindings[bindName] = when (classifier) {
            LocalDate::class -> (value as LocalDate).format(DateTimeFormatter.ISO_LOCAL_DATE)
            LocalTime::class -> (value as LocalTime).format(DateTimeFormatter.ISO_LOCAL_TIME)
            LocalDateTime::class -> (value as LocalDateTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            OffsetDateTime::class -> (value as OffsetDateTime).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ZonedDateTime::class -> (value as ZonedDateTime).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
            else -> value
        }
        return this
    }

    internal fun build(): Pair<String, Map<String, Any?>> = Pair(conditions.joinToString(" AND "), bindings)
}

/**
 * Reference to a property of a [VersionedEntity]
 */
interface Property<T, R> {
    fun toJSONValueArrowPath(): String
    fun toJSONArrowPath(): String
    fun returnType(): KType
    fun name(): String
}

/**
 * Wrapper for a single [KProperty1]
 */
data class SingleProperty<T, R>(val property: KProperty1<T, R>) : Property<T, R> {
    override fun toJSONValueArrowPath(): String {
        return "data->>'${property.name}'"
    }

    override fun toJSONArrowPath(): String {
        return "data->'${property.name}'"
    }

    override fun returnType(): KType {
        return property.returnType
    }

    override fun name(): String {
        return property.name
    }
}

/**
 * Wrapper for a chain of [KProperty1]'s
 *
 * Example:
 * ```
 *  val result = orderRepository.queryBuilder()
 *             .where(orderRepository.condition()
 *                 .matching {
 *                     Order::contactDetails then ContactDetails::address then Address::city like "Some Other%"
 *                 })
 *             .orderBy(Order::contactDetails then ContactDetails::address then Address::city, QueryBuilder.Order.ASC)
 *             .limit(200)
 *             .find()
 * ```
 */
data class NestedProperty<T, R>(
    val condition: Condition<T>,
    val properties: List<KProperty1<*, *>>
) : Property<T, R> {

    /**
     * Chain [KProperty1]'s on a [NestedProperty] to create a new [NestedProperty]
     *
     * Example:
     * ```
     *  val result = orderRepository.queryBuilder()
     *             .where(orderRepository.condition()
     *                 .matching {
     *                     Order::contactDetails then ContactDetails::address then Address::city like "Some Other%"
     *                 })
     *             .orderBy(Order::contactDetails then ContactDetails::address then Address::city, QueryBuilder.Order.ASC)
     *             .limit(200)
     *             .find()
     * ```
     */
    infix fun <V> then(next: KProperty1<R, V>): NestedProperty<T, V> = NestedProperty(condition, properties + next)

    /**
     * Similar to SQL equals =
     */
    infix fun eq(value: R): Condition<T> {
        return condition.applyCondition(this, "=", value)
    }

    /**
     * Similar to SQL <
     */
    infix fun lt(value: R): Condition<T> {
        return condition.applyCondition(this, "<", value)
    }

    /**
     * Similar to SQL <=
     */
    infix fun lte(value: R): Condition<T> {
        return condition.applyCondition(this, "<=", value)
    }

    /**
     * Similar to SQL >
     */
    infix fun gt(value: R): Condition<T> {
        return condition.applyCondition(this, ">", value)
    }
    /**
     * Similar to SQL >=
     */
    infix fun gte(value: R): Condition<T> {
        return condition.applyCondition(this, ">=", value)
    }

//    infix fun contains(value: R): Condition<T> {
//        return condition.applyCondition(this, "@>", value)
//    }

    /**
     * Similar to SQL like
     *
     * Example:
     * ```
     * result = orderRepository.queryBuilder()
     *     .where(orderRepository.condition()
     *         .matching {
     *             (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                 .and(Order::description like "%unique%")
     *         })
     *     .find()
     * ```
     */
    infix fun like(value: R): Condition<T> {
        return condition.applyCondition(this, "LIKE", value)
    }

//    infix fun anyLike(value: String): Condition<T> {
//        val bindName = condition.uniqueBindName(this)
//        condition.conditions.add("EXISTS (SELECT 1 FROM jsonb_array_elements_text(${this.toJSONValueArrowPath()}) AS elem WHERE elem LIKE :$bindName)")
//        condition.bindings[bindName] = value
//        return condition
//    }

    /**
     * Combine two conditions with an **and** statement:
     *
     * Example:
     * ```
     * result = orderRepository.queryBuilder()
     *     .where(orderRepository.condition()
     *         .matching {
     *             (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                 .and(Order::description like "%unique%")
     *         })
     *     .find()
     * ```
     */
    infix fun and(other: Condition<T>): Condition<T> = condition.apply {
        val lastCondition = conditions.removeLast()
        val previousCondition = conditions.removeLast()
        conditions.add("($previousCondition AND $lastCondition)")
    }

    /**
     * Combine two conditions with an **or** statement:
     *
     * Example:
     * ```
     * result = orderRepository.queryBuilder()
     *     .where(orderRepository.condition()
     *         .matching {
     *             (Order::personName like "%John%").or(Order::personName like "%Jane%")
     *                 .and(Order::description like "%unique%")
     *         })
     *     .find()
     * ```
     */
    infix fun or(other: Condition<T>): Condition<T> = condition.apply {
        val lastCondition = conditions.removeLast()
        val previousCondition = conditions.removeLast()
        conditions.add("($previousCondition OR $lastCondition)")
    }

    override fun name(): String {
        if (properties.isEmpty()) throw IllegalStateException("Cannot call on an empty Nested Property")
        return properties.joinToString(separator = "_") { it.name }
    }

    override fun toJSONValueArrowPath(): String {
        if (properties.isEmpty()) throw IllegalStateException("Cannot call on an empty Nested Property")
        return "data->" + properties.dropLast(1).joinToString(separator = "->") { "'${it.name}'" } + "->>'${properties.last().name}'"
    }

    override fun toJSONArrowPath(): String {
        if (properties.isEmpty()) throw IllegalStateException("Cannot call on an empty Nested Property")
        return "data->" + properties.joinToString(separator = "->") { "'${it.name}'" }
    }

    override fun returnType(): KType {
        if (properties.isEmpty()) throw IllegalStateException("Cannot call on an empty Nested Property")
        return properties.last().returnType
    }
}

/**
 * Query Builder
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
 * The [DocumentEntity.tableName] and `all the names of the properties in your entity classes` will be directly used in constructing SQL statements through string concatenation.
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
 * **See [VersionedEntity]'s and [DocumentDbRepository]'s security warning.***
 */
class QueryBuilder<ID, ENTITY : VersionedEntity<ID, ENTITY>>(
    private val entityConfiguration: EntityConfiguration<ID, ENTITY>,
    private val documentDbRepository: DocumentDbRepository<ENTITY, ID>
) {
    private val conditions = mutableListOf<Condition<ENTITY>>()
    private val orderByFields = mutableListOf<Pair<Property<ENTITY, *>, Order>>()

    //    private val groupByFields = mutableListOf<Property<ENTITY, *>>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null

    enum class Order { ASC, DESC }

    /**
     * Provide the constraints on the Query by applying a [Condition]
     * (which is created by calling [DocumentDbRepository.condition])
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
     */
    infix fun where(condition: Condition<ENTITY>) = apply { conditions.add(condition) }

    /**
     * Order by a single property
     *
     * Example:
     * ```
     * val query = orderRepository.queryBuilder()
     *     .where(orderRepository.condition()
     *         .matching {
     *             Order::additionalProperty lt 50
     *         })
     *     .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
     *     .limit(200)
     *     .offset(0)
     * ```
     *
     * ### Security
     * The [DocumentEntity.tableName]` and `all the names of the properties in your entity classes`, and thereby also the [property] provided to this method, will be directly used in constructing SQL statements through string concatenation.
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
     */
    fun orderBy(property: KProperty1<ENTITY, *>, order: Order = Order.ASC) = apply { orderByFields.add(SingleProperty(property) to order) }

    /**
     * Order by a single nested property
     *
     * Example:
     * ```
     * val result = orderRepository.queryBuilder()
     *     .where(orderRepository.condition()
     *         .matching {
     *             Order::contactDetails then ContactDetails::address then Address::city like "Some Other%"
     *         })
     *     .orderBy(Order::contactDetails then ContactDetails::address then Address::city, QueryBuilder.Order.ASC)
     *     .limit(200)
     *     .find()
     * ```
     *
     * ### Security
     * The [DocumentEntity.tableName]` and `all the names of the properties in your entity classes`, and thereby also the [property] provided to this method, will be directly used in constructing SQL statements through string concatenation.
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
     */
    fun orderBy(property: NestedProperty<ENTITY, *>, order: Order = Order.ASC) = apply { orderByFields.add(property to order) }

//    fun groupBy(vararg properties: KProperty1<ENTITY, *>) = apply { groupByFields.addAll(properties.map { SingleProperty(it) }) }
//    fun groupBy(vararg properties: NestedProperty<ENTITY, *>) = apply { groupByFields.addAll(properties) }

    /**
     * Limit the number of matching entities returned - can be combined with [offset] to provide pagination support
     */
    infix fun limit(value: Int) = apply { this.limitValue = value }

    /**
     * Defines how many entities to skip before beginning to return any entities - can be combined with [limit] to provide pagination support
     */
    infix fun offset(value: Int) = apply { this.offsetValue = value }

    private fun castOrderBy(propertyPair: Pair<Property<ENTITY, *>, Order>): String {
        val property = propertyPair.first
        val propertyType: KType = property.returnType()
        val classifier = propertyType.classifier as? KClass<*>
            ?: throw IllegalArgumentException("Unsupported type '${propertyType.classifier}' for property ${property.name()}")


        return when {
            classifier == LocalDate::class -> "CAST(${property.toJSONValueArrowPath()} AS DATE)"
            LocalDateValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS DATE)"

            classifier == LocalTime::class -> "CAST(${property.toJSONValueArrowPath()} AS TIME)"
            LocalTimeValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS TIME)"

            classifier == LocalDateTime::class -> "CAST(${property.toJSONValueArrowPath()} AS TIMESTAMP)"
            LocalDateTimeValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS TIMESTAMP)"

            classifier == Instant::class || classifier == OffsetDateTime::class || classifier == ZonedDateTime::class -> "CAST(${property.toJSONValueArrowPath()} AS TIMESTAMPTZ)"
            InstantValueType::class.isSuperclassOf(classifier) || OffsetDateTimeValueType::class.isSuperclassOf(classifier) || ZonedDateTimeValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS TIMESTAMPTZ)"

            IntValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS INTEGER)"
            LongValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS BIGINT)"
            FloatValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS REAL)"
            DoubleValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS DOUBLE PRECISION)"
            BigDecimalValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS DOUBLE PRECISION)"
            BigIntegerValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS NUMERIC)"
            BooleanValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS BOOLEAN)"
            ShortValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS SMALLINT)"
            ByteValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS SMALLINT)"
            StringValueType::class.isSuperclassOf(classifier) -> "CAST(${property.toJSONValueArrowPath()} AS TEXT)"
            classifier == Int::class -> "CAST(${property.toJSONValueArrowPath()} AS INTEGER)"
            classifier == Long::class -> "CAST(${property.toJSONValueArrowPath()} AS BIGINT)"
            classifier == Float::class -> "CAST(${property.toJSONValueArrowPath()} AS REAL)"
            classifier == Double::class -> "CAST(${property.toJSONValueArrowPath()} AS DOUBLE PRECISION)"
            classifier == BigDecimal::class -> "CAST(${property.toJSONValueArrowPath()} AS DOUBLE PRECISION)"
            classifier == BigInteger::class -> "CAST(${property.toJSONValueArrowPath()} AS NUMERIC)"
            classifier == Boolean::class -> "CAST(${property.toJSONValueArrowPath()} AS BOOLEAN)"
            classifier == Short::class -> "CAST(${property.toJSONValueArrowPath()} AS SMALLINT)"
            classifier == Byte::class -> "CAST(${property.toJSONValueArrowPath()} AS SMALLINT)"
            classifier == String::class -> "CAST(${property.toJSONValueArrowPath()} AS TEXT)"
            else -> throw IllegalArgumentException("Unsupported type '${classifier.qualifiedName}' for property ${property.name()}")
        }

    }

    internal fun build(): JdbiQuery<ID, ENTITY> {
        val sql = StringBuilder("SELECT data FROM ${entityConfiguration.tableName()}")
        val bindings = mutableMapOf<String, Any?>()

        if (conditions.isNotEmpty()) {
            val conditionStrings = conditions.map { it.build().first }
            sql.append(" WHERE ").append(conditionStrings.joinToString(" AND "))
            conditions.forEach { bindings.putAll(it.build().second) }
        }

//        if (groupByFields.isNotEmpty()) {
//            sql.append(" GROUP BY ").append(groupByFields.joinToString(", ") { "(${it.toJSONArrowPath()}::TEXT)" })
//        }

        if (orderByFields.isNotEmpty()) {
            sql.append(" ORDER BY ").append(orderByFields.joinToString(", ") { castOrderBy(it) + " ${it.second}" })
        }

        limitValue?.let { sql.append(" LIMIT :limit"); bindings["limit"] = it }
        offsetValue?.let { sql.append(" OFFSET :offset"); bindings["offset"] = it }

        return JdbiQuery(sql.toString(), bindings)
    }

    fun find(): List<ENTITY> {
        return documentDbRepository.find(this)
    }
}

internal data class JdbiQuery<ID, ENTITY : VersionedEntity<ID, ENTITY>>(val sql: String, val bindings: Map<String, Any?>)

/**
 * Chain [KProperty1]'s to create a [NestedProperty]
 *
 * Example:
 * ```
 *  val result = orderRepository.queryBuilder()
 *             .where(orderRepository.condition()
 *                 .matching {
 *                     Order::contactDetails then ContactDetails::address then Address::city like "Some Other%"
 *                 })
 *             .orderBy(Order::contactDetails then ContactDetails::address then Address::city, QueryBuilder.Order.ASC)
 *             .limit(200)
 *             .find()
 * ```
 */
infix fun <T, R, V> KProperty1<T, R>.then(property: KProperty1<R, V>): NestedProperty<T, V> {
    return NestedProperty(Condition(NoJSONSerializer()), listOf(this, property))
}

/**
 * Converts a [KProperty1] to a [SingleProperty]
 */
fun <T, R> KProperty1<T, R>.asProperty(): Property<T, R> {
    return SingleProperty(this)
}

internal class NoJSONSerializer : JSONSerializer {
    override fun serialize(obj: Any?): String {
        throw NotImplementedError("Not supported")
    }

    override fun serializeAsBytes(obj: Any?): ByteArray {
        throw NotImplementedError("Not supported")
    }

    override fun <T : Any?> deserialize(json: String?, javaType: String?): T {
        throw NotImplementedError("Not supported")
    }

    override fun <T : Any?> deserialize(json: String?, javaType: Class<T>?): T {
        throw NotImplementedError("Not supported")
    }

    override fun <T : Any?> deserialize(json: ByteArray?, javaType: String?): T {
        throw NotImplementedError("Not supported")
    }

    override fun <T : Any?> deserialize(json: ByteArray?, javaType: Class<T>?): T {
        throw NotImplementedError("Not supported")
    }

    override fun getClassLoader(): ClassLoader {
        throw NotImplementedError("Not supported")
    }

    override fun setClassLoader(classLoader: ClassLoader?) {
        throw NotImplementedError("Not supported")
    }
}





