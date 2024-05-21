# Essentials Components - PostgreSQL Document DB

This library provides a **very experimental** Document DB storage approach using PostgreSQL and JSONB

> **NOTE:**  
> **The library is WORK-IN-PROGRESS**

> Please see the **Security** notices below to familiarize yourself with the security risks

## DocumentDB Concept

The `DocumentDbRepository` is a flexible repository interface designed for persisting and managing entities as 
JSON documents using PostgreSQL.   
This concept allows you to leverage the flexibility of JSON for storing complex and dynamic data structures, 
while still benefiting from the robustness and reliability of PostgreSQL.

### Security
To support customization of in which PostgreSQL table each entity type is stored you can provide your own `tableName` in the `@DocumentEntity` annotation

The `tableName` and `all the names of the properties in your entity classes` will be directly used in constructing SQL statements through string concatenation.  
This can potentially expose components, such as `dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository`, to SQL injection attacks.

**It is the responsibility of the user of this component to sanitize both the `@DocumentEntity`  `tableName` and `all Entity property names` to ensure the security of all the SQL statements generated 
by this component.**

The `dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository` instance, e.g. created by `dk.cloudcreate.essentials.components.document_db.DocumentDbRepositoryFactory.create`, 
will through `EntityConfiguration#configureEntity` call the `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` method to validate the table name 
and Entity property names as a first line of defense.

The `PostgresqlUtil#checkIsValidTableOrColumnName` provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.    
**However, Essentials components as well as `PostgresqlUtil#checkIsValidTableOrColumnName` does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.**
> The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
> Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names

**Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**

It is highly recommended that the `@DocumentEntity` `tableName` and `all the Entity property names` are only derived from controlled and trusted sources.

To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the `tableName` or property names.

### Key `DocumentDBRepository` Features 

1. **JSON Storage**: Entities are stored as JSON documents, allowing for flexible and dynamic data structures that can easily evolve over time without requiring schema migrations.
2. **Versioning**: The repository uses a versioning scheme to manage entity versions, ensuring data consistency and enabling optimistic locking to prevent concurrent update conflicts.
3. **Optimistic Locking**: Ensures data integrity by preventing conflicting updates, using version checks to detect and handle concurrent modifications.
4. **Query Building**: Provides a fluent API for building complex queries, including support for nested properties, sorting, pagination, and more.
5. **Indexing**: Indexes for top level properties (using the `@Indexed` annotation) or `DocumentDBRepository.addIndex(...)`

### Basic Concepts

#### Entity

An entity is a data object that you want to persist in the database.  
Each entity must implement the `VersionedEntity` interface, which includes a `version` property for tracking 
the entity's version.

```kotlin
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
```

#### Version
The `Version` class represents the version of an entity.  
It is used to implement optimistic locking, ensuring that updates do not conflict with each other.

```kotlin
data class Version(val value: Int) {
    companion object {
        val ZERO = Version(0)
        val NOT_SAVED_YET = Version(-1)
    }
}
```

### Repository

The `DocumentDbRepository` interface provides methods for saving, updating, deleting, and querying entities.  
It uses JSON for storing entities, allowing for flexible and schema-less data structures.

```kotlin
interface DocumentDbRepository<ENTITY : VersionedEntity<ID, ENTITY>, ID> {
    fun save(entity: ENTITY, initialVersion: Version = Version.ZERO): ENTITY
    fun update(entity: ENTITY): ENTITY
    fun update(entity: ENTITY, nextVersion: Version): ENTITY
    fun find(queryBuilder: QueryBuilder<ID, ENTITY>): List<ENTITY>
    fun findById(id: ID): ENTITY?
    fun getById(id: ID): ENTITY
    fun existsById(id: ID): Boolean
    fun saveAll(entities: Iterable<ENTITY>): List<ENTITY>
    fun updateAll(entities: Iterable<ENTITY>): List<ENTITY>
    fun deleteById(id: ID)
    fun delete(entity: ENTITY)
    fun deleteAll(entities: Iterable<ENTITY>)
    fun findAll(): List<ENTITY>
    fun findAllById(ids: Iterable<ID>): List<ENTITY>
    fun count(): Long
    fun deleteAllById(ids: Iterable<ID>)
    fun deleteAll()
    fun entityConfiguration(): EntityConfiguration<ID, ENTITY>
    fun queryBuilder(): QueryBuilder<ID, ENTITY>
    fun condition(): Condition<ENTITY>
}
```

### `save` operation
When saving a new entity using the `save` method, the entity is assigned an initial version.  
By default, this version is set to `Version.ZERO`, but a different initial version can be specified if needed.   
If the entity already exists in the database, an `OptimisticLockingException` is thrown to prevent data overwriting.
```kotlin
fun save(entity: ENTITY, initialVersion: Version = Version.ZERO): ENTITY
```

### `update` operation
The `update` method is used to update an existing entity.  
When an entity is updated, its `version` is automatically incremented to ensure that no concurrent updates have been made. 
If the `version` of the entity in the database does not match the version of the entity being updated, 
an `OptimisticLockingException` is thrown, indicating that the entity has been modified by another process.
```kotlin
fun update(entity: ENTITY): ENTITY
```

### Custom Version Update during `update`
In some cases, you may want to specify the next `version` explicitly.  
The `update` method with the `nextVersion` parameter allows you to set the new `version` of the entity.  
This method also checks for version consistency to prevent concurrent modifications.
```kotlin
fun update(entity: ENTITY, nextVersion: Version): ENTITY
```

### Optimistic Locking
The versioning scheme implements optimistic locking, a concurrency control method used to prevent conflicting updates.  
Each time an entity is updated, its `version` is checked against the `version` of the same entity in the database.  
If the versions do not match, the operation is aborted, and an `OptimisticLockingException` is thrown.  
This approach ensures that updates do not overwrite changes made by other processes.


## Using the PostgreSQL DocumentDB
First you need to ensure that the `DocumentDbRepositoryFactory` is fully configured.

Here's an example of configuring it standalone - if you're using the `spring-boot-starter-postgresql`/`spring-boot-starter-postgresql-event-store`
both the `Jdbi`, `UnitOfWorkFactory` and `JSONSerializer` are available as Spring Beans that can be injected.

```kotlin
val repositoryFactory = DocumentDbRepositoryFactory(
            jdbi,
            JdbiUnitOfWorkFactory(jdbi),
            JacksonJSONSerializer(
                EssentialsImmutableJacksonModule.createObjectMapper(
                    Jdk8Module(),
                    JavaTimeModule()
                ).registerKotlinModule()
            )
        )
```

### Defining Entities that can be persisted
Entities that are persistable using a `DocumentDBRepository` MUST implement the `VersionedEntity` interface
and be annotated with the `@DocumentEntity` annotation, which defines that name of the table where the entities is stored

Additionally, the entities `identifier` (or primary-key) must be annotated with the `@Id` annotation:
```kotlin
@DocumentEntity("orders")
data class Order(
    @Id
    val orderId: OrderId,
    var description: String,
    var amount: Amount,
    var additionalProperty: Int,
    var orderDate: LocalDateTime,
    @Indexed
    var personName: String,
    var invoiceAddress: Address,
    var contactDetails: ContactDetails,
    override var version: Version = Version.NOT_SAVED_YET,
    override var lastUpdated: OffsetDateTime = OffsetDateTime.now(UTC)
) : VersionedEntity<OrderId, Order> 
```

The `@Id` annotated property MUST be a `String` or a String value class (such as those implementing `StringValueType`) 

To strengthen the type safety and the ubiquitous language it's a good idea to base your type on one of the Semantic types defined
in the `types` library.

Example of creating a semantic type called `OrderId` which is based on the `StringValueType` from the `types` library:
```kotlin
@JvmInline
value class OrderId(override val value: String) : StringValueType<OrderId> {
    companion object {
        fun random(): OrderId = OrderId(RandomIdGenerator.generate())
    }
}
```

If you create your own semantic type you also need to create a corresponding Jdbi `ArgumentFactory` and `ColumnMapper`.

Example:
```kotlin
class OrderIdArgumentFactory : StringValueTypeArgumentFactory<OrderId>()
class OrderIdColumnMapper : StringValueTypeColumnMapper<OrderId>()
```

which MUST be registered with the `Jdbi` instance. E.g. using the Spring class callback `JdbiConfigurationCallback` which your configuration class can choose to implement:
```kotlin
@Configuration
class MyConfig: JdbiConfigurationCallback {
    override fun configure(jdbi: Jdbi) {
        jdbi.registerArgument(OrderIdArgumentFactory())
        jdbi.registerColumnMapper(OrderIdColumnMapper())
    }
}
```

### Creating a `DocumentDbRepository` instance for persisting your Entity
All you need to create a `DocumentDbRepository` for persisting your Entity is to use
the configured `DocumentDbRepositoryFactory`'s `create` method:

```kotlin
val repositoryFactory: DocumentDbRepositoryFactory = getDocumentDbRepositoryFactory()

val orderRepository: DocumentDbRepository<Order, OrderId> = repositoryFactory.create(Order::class)
```

### Persisting Entities using  a `DocumentDbRepository` instance 
Example of **saving** a _new_ entity:
```kotlin
orderRepository.save(
            Order(
                orderId = OrderId("order1"),
                description = "Test Order 1",
                amount = Amount(100.0),
                orderDate = LocalDateTime.now(),
                personName = "John Doe",
                invoiceAddress = Address("123 Some Street", "Some City"),
                contactDetails = ContactDetails("John Doe", Address("123 Some Street", "Come City"), listOf("Some Phone Number")),
                additionalProperty = 10
            )
        )
```

Example of **loading** an entity by id:
```kotlin
val order = orderRepository.findById(OrderId("order1"))
```

Example of **loading** and **updating** an existing entity:
```kotlin
val order = orderRepository.findById(OrderId("order1"))!!
order.additionalProperty = 75
orderRepository.update(order) 
```

### Querying for entities

The `DocumentDBRepository` provides a simple type safe SQL like query API using the `QueryBuilder` together with its `find()` method:

```kotlin
// Find orders with a specific customer name
val orders = orderRepository.queryBuilder()
                            .where(orderRepository.condition()
                                .matching {
                                    Order::customerName eq "Jane Doe"
                                })
                            .find()
```

#### Query nested properties:
```kotlin
val result = orderRepository.queryBuilder()
                            .where(orderRepository.condition()
                                .matching {
                                    Order::contactDetails then ContactDetails::address then Address::city eq "Some City"
                                })
                            .find()
```

#### Query using `and`, `or`, `like`, etc.:
```kotlin
val result = orderRepository.queryBuilder()
                            .where(orderRepository.condition()
                                .matching {
                                    (Order::personName like "%John%").or(Order::personName like "%Jane%")
                                        .and(Order::description like "%unique%")
                                })
                            .find()
```

#### Query using `orderBy` and pagination (`offset` + `limit`):
```kotlin
val result = orderRepository.queryBuilder()
                            .where(orderRepository.condition()
                                .matching {
                                    Order::contactDetails then ContactDetails::address then Address::city like "Some Other%"
                                })
                            .orderBy(Order::contactDetails then ContactDetails::address then Address::city, QueryBuilder.Order.ASC)
                            .offset(100)
                            .limit(50)
                            .find()
```

> #### Security
> 
> The `DocumentEntity.tableName` and `all the names of the properties in your entity classes` will be directly used in constructing SQL statements through string concatenation. This can potentially expose components, such as `PostgresqlDocumentDbRepository`, to SQL injection attacks.
> 
> **It is the responsibility of the user of this component to sanitize both the `DocumentEntity.tableName` and `all Entity property names` to ensure the security of all the SQL statements generated by this component.**
> 
> The `PostgresqlDocumentDbRepository` instance, e.g., created by `DocumentDbRepositoryFactory.create`, will through `EntityConfiguration.configureEntity` call the `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` method to validate the table name and Entity property names as a first line of defense.
> 
> The `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.
> 
> **However, Essentials components as well as `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` do not offer exhaustive protection, nor do they assure the complete security of the resulting SQL against SQL injection threats.**
> > The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
> > Users must ensure thorough sanitization and validation of API input parameters, column, table, and index names.
> 
> **Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**
> 
> It is highly recommended that the `DocumentEntity.tableName` and `all the Entity property names` are only derived from controlled and trusted sources.
> 
> To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the `tableName` or entity property names.


### Indexing
To ensure that queries are fast it's a good idea to add indexes.

The `DocumentDbRepository` concept supports powerful indexing capabilities to improve the performance of queries on JSONB data stored in a PostgreSQL database. 
These capabilities allow developers to create and manage indexes on specific JSON properties, including nested properties, to optimize query performance.

#### Using the `Indexed` Annotation

You can use the `@Indexed` annotation on individual top level `VersionedEntity` properties within your entity classes to indicate that these properties should be indexed.    
If you want to add indexes for sub-properties, e.g. properties of the `Address` or `ContactDetails` objects, you have to use the `DocumentDbRepository` using the `addIndex` method.
#### Example
```kotlin
@DocumentEntity("orders")
data class Order(
    @Id
    val orderId: OrderId,
    var description: String,
    var amount: Amount,
    var additionalProperty: Int,
    var orderDate: LocalDateTime,
    @Indexed
    var personName: String,
    var invoiceAddress: Address,
    var contactDetails: ContactDetails,
    override var version: Version = Version.NOT_SAVED_YET,
    override var lastUpdated: OffsetDateTime = OffsetDateTime.now(UTC)
) : VersionedEntity<OrderId, Order>
```

which, following the pattern: `idx_${tableName}_${fieldName}` as lower case, will add index `idx_orders_personname`  to the `orders` table

> ##### Security note
> 
> The name of the property will be used in the index name (together with the table name specified in `DocumentEntity.tableName`) and further used in constructing SQL statements through string 
> concatenation, which exposes the components (such as `dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository`) to SQL injection attacks.
> 
> **It is the responsibility of the user of this component to sanitize the `DocumentEntity.tableName` and the indexed property name to ensure the security of all the SQL statements generated by 
> this component.**
> 
> The `dk.cloudcreate.essentials.components.document_db.postgresql.PostgresqlDocumentDbRepository` instance created, e.g., by 
> `dk.cloudcreate.essentials.components.document_db.DocumentDbRepositoryFactory.create`, will call the 
> `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` method to validate the table name and index name as a first line of defense.
> 
> The `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` provides an initial layer of defense against SQL injection by applying naming 
> conventions intended to reduce the risk of malicious input.
> 
> However, Essentials components as well as `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` do not offer exhaustive protection, 
> nor do they assure the complete security of the resulting SQL against SQL injection threats.
> 
> >The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
> 
> Users must ensure thorough sanitization and validation of API input parameters, column, table, and index names.
> 
> Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.
> 
> It is highly recommended that the `DocumentEntity.tableName` value and indexed property name are only derived from a controlled and trusted source.
> 
> To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the `DocumentEntity.tableName` value or the indexed property name.

#### Adding Indexes to the `DocumentDbRepository`

Indexes can be added to the `DocumentDbRepository` using the `addIndex` method.  
This method allows you to specify the properties to include in the index, including support for nested properties.

###### Example

```kotlin
val orderRepository: DocumentDbRepository<Order, OrderId> = repositoryFactory.create(Order::class)

// Add an index on a nested property
orderRepository.addIndex(Index(name = "city", listOf(Order::contactDetails then ContactDetails::address then Address::city)))

// Add a composite index on multiple properties
orderRepository.addIndex(Index(name = "orderdate_amount", listOf(Order::orderDate.asProperty(), Order::amount.asProperty())))
```

which, following the pattern: `idx_${tableName}_$indexName`  as lower case, will add two indexes to the `orders` table:
- `idx_orders_city`
- `idx_orders_orderdate_amount`

    ##### Security note
    The `Index.name` and `all the names of the properties in your entity classes` specified in `Index.properties` will be directly used in constructing SQL statements through string concatenation.    
    This can potentially expose components, such as `PostgresqlDocumentDbRepository`, to SQL injection attacks.
    
    **It is the responsibility of the user of this component to sanitize both the `Index.name` and `all the names of the properties in your entity classes` specified in `Index.properties` to ensure 
    the security of all the SQL statements generated by this component.**
    
    The `PostgresqlDocumentDbRepository.addIndex` will call the `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` method to validate the index
    name as a first line of defense.
    
    The `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` provides an initial layer of defense against SQL injection by applying naming  
    conventions intended to reduce the risk of malicious input.
    
    **However, Essentials components as well as `dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil.checkIsValidTableOrColumnName` do not offer exhaustive protection, 
    nor do they assure the complete security of the resulting SQL against SQL injection threats.**
    > The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
    > Users must ensure thorough sanitization and validation of API input parameters, column, table, and index names.
    
    **Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**
    
    It is highly recommended that the `Index.name` and `all the names of the properties in your entity classes` specified in `Index.properties` are only derived from controlled and trusted sources.
    
    To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the `Index.name` nor any of `the names of the properties in your entity classes` 
     specified in `Index.properties`.
    
    **See also `VersionedEntity`'s security warning.**
