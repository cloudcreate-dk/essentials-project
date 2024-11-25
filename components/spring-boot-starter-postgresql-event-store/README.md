# Essentials Components - Essentials Postgresql: Spring Boot starter

This library provides Spring Boot auto-configuration for all Postgresql focused Essentials components as well as the Postgresql `EventStore`.

> **NOTE:**  
> **The library is WORK-IN-PROGRESS**

All `@Beans` auto-configured by this library use `@ConditionalOnMissingBean` to allow for easy overriding.

> Please see the **Security** notices below, as well as **Security** notices for the individual components, to familiarize yourself with the security risks  
> Such as:
> - [foundation-types](../foundation-types/README.md)
> - [postgresql-distributed-fenced-lock](../postgresql-distributed-fenced-lock/README.md)
> - [postgresql-queue](../postgresql-queue/README.md)
> - [postgresql-event-store](../postgresql-event-store/README.md)
> - [eventsourced-aggregates](../eventsourced-aggregates/README.md)

To use `spring-boot-starter-postgresql-event-store` to add the following dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>spring-boot-starter-postgresql-event-store</artifactId>
    <version>0.40.18</version>
</dependency>
```

This will ensure to include the `spring-boot-starter-postgresql` starter and its `EssentialsComponentsConfiguration` auto-configuration, so you don't need to include it as well.

## `EventStoreConfiguration` auto-configuration:

>If you in your own Spring Boot application choose to override the Beans defined by this starter,
 then you need to check the component document to learn about the Security implications of each configuration.
> - dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.PostgresqlDurableSubscriptionRepository
> - dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypePersistenceStrategy
> - dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory
> - dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration
> - dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.EventStreamTableColumnNames

### Beans provided by the `EventStoreConfiguration` auto-configuration:
- `PostgresqlEventStore` using `PostgresqlEventStreamGapHandler` (using default configuration)
  - You can configure `NoEventStreamGapHandler` using Spring properties:
  - `essentials.event-store.use-event-stream-gap-handler=false`
- `SeparateTablePerAggregateTypePersistenceStrategy` using `IdentifierColumnType.TEXT` for persisting `AggregateId`'s and `JSONColumnType.JSONB` for persisting Event and EventMetadata JSON payloads
  - ColumnTypes can be overridden by using Spring properties:
  - ```
       essentials.event-store.identifier-column-type=uuid
       essentials.event-store.json-column-type=jsonb
      ```
- `EventStoreUnitOfWorkFactory` in the form of `SpringTransactionAwareEventStoreUnitOfWorkFactory`
- `EventStoreEventBus` with an internal `LocalEventBus` with bus-name `EventStoreLocalBus`
- `PersistableEventMapper` with basic setup. Override this bean if you need additional meta-data, such as event-id, event-type, event-order, event-timestamp, event-meta-data, correlation-id, tenant-id included
- `AggregateEventStreamPersistenceStrategy` in the form of `SeparateTablePerAggregateTypePersistenceStrategy` using `SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration`
- `EventStoreSubscriptionManager` with default `EventStoreSubscriptionManagerProperties` values using `PostgresqlDurableSubscriptionRepository`
  - The default `EventStoreSubscriptionManager` values can be overridden using Spring properties:
  - ```
      essentials.event-store.subscription-manager.event-store-polling-batch-size=5
      essentials.event-store.subscription-manager.snapshot-resume-points-every=2s
      essentials.event-store.subscription-manager.event-store-polling-interval=200
     ```
- `MicrometerTracingEventStoreInterceptor` if property `management.tracing.enabled` has value `true`
  - The default `MicrometerTracingEventStoreInterceptor` values can be overridden using Spring properties:
  - ```
    essentials.event-store.verbose-tracing=true
    ```
- `EventProcessorDependencies` to simplify configuring `EventProcessor`'s
- `JacksonJSONEventSerializer` which uses an internally configured `ObjectMapper`, which provides good defaults for JSON serialization, and includes all Jackson `Module`'s defined in the `ApplicationContext`

## `EssentialsComponentsConfiguration` auto-configuration:

>If you in your own Spring Boot application choose to override the Beans defined by this starter,
then you need to check the component document to learn about the Security implications of each configuration, such as:
> - dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues
> - dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager
> - dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockStorage
> - dk.cloudcreate.essentials.components.foundation.postgresql.MultiTableChangeListener

### Beans provided by the `EssentialsComponentsConfiguration` auto-configuration:
- Jackson/FasterXML JSON modules:
  - `EssentialTypesJacksonModule`
  - `EssentialsImmutableJacksonModule` (if `Objenesis` is on the classpath)
- `JacksonJSONSerializer` which uses an internally configured `ObjectMapper`, which provides good defaults for JSON serialization, and includes all Jackson `Module`'s defined in the `ApplicationContext`
  - This `JSONSerializer` will only be auto-registered if the `JSONEventSerializer` is not on the classpath (see `EventStoreConfiguration`)
- `Jdbi` to use the provided Spring `DataSource`
- `SpringTransactionAwareJdbiUnitOfWorkFactory` configured to use the Spring provided `PlatformTransactionManager`
  - This `UnitOfWorkFactory` will only be auto-registered if the `SpringTransactionAwareEventStoreUnitOfWorkFactory` is not on the classpath (see `EventStoreConfiguration`)
  - `PostgresqlFencedLockManager` using the `JSONSerializer` as JSON serializer
    - It Supports additional properties:
    - ```
      essentials.fenced-lock-manager.fenced-locks-table-name=fenced_locks
      essentials.fenced-lock-manager.lock-confirmation-interval=5s
      essentials.fenced-lock-manager.lock-time-out=12s
      essentials.fenced-lock-manager.release-acquired-locks-in-case-of-i-o-exceptions-during-lock-confirmation=false
      ```
    - **Security Notice regarding `essentials.fenced-lock-manager.fenced-locks-table-name`:**
      - This property, no matter if it's set using properties, System properties, env variables or yaml configuration, will be provided to the `PostgresqlFencedLockManager`'s `PostgresqlFencedLockStorage` as the `fencedLocksTableName` parameter.
      - To support customization of storage table name, the `essentials.fenced-lock-manager.fenced-locks-table-name` provided through the Spring configuration to the `PostgresqlFencedLockManager`'s `PostgresqlFencedLockStorage`,
        will be directly used in constructing SQL statements through string concatenation, which exposes the component to **SQL injection attacks**.
      - It is the responsibility of the user of this starter component to sanitize the `essentials.fenced-lock-manager.fenced-locks-table-name` to ensure the security of all the SQL statements generated by the `PostgresqlFencedLockManager`'s `PostgresqlFencedLockStorage`.
        - The `PostgresqlFencedLockStorage` component will call the `PostgresqlUtil#checkIsValidTableOrColumnName(String)` method to validate the table name as a first line of defense.
        - The `PostgresqlUtil#checkIsValidTableOrColumnName(String)` provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.
          - **However, Essentials components as well as `PostgresqlUtil#checkIsValidTableOrColumnName(String)` does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.**
        - The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
        - Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names
        - **Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**
      - It is highly recommended that the `essentials.fenced-lock-manager.fenced-locks-table-name` value is only derived from a controlled and trusted source.
        - To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the `essentials.fenced-lock-manager.fenced-locks-table-name` value.
        - **Failure to adequately sanitize and validate this value could expose the application to SQL injection  vulnerabilities, compromising the security and integrity of the database.**
- `PostgresqlDurableQueues` using the `JSONSerializer` as JSON serializer
  - Supports additional properties:
  - ```
    essentials.durable-queues.shared-queue-table-name=durable_queues
    essentials.durable-queues.transactional-mode=fullytransactional or singleoperationtransaction (default)
    essentials.durable-queues.polling-delay-interval-increment-factor=0.5
    essentials.durable-queues.max-polling-interval=2s
    essentials.durable-queues.verbose-tracing=false
    # Only relevant if transactional-mode=singleoperationtransaction
    essentials.durable-queues.message-handling-timeout=5s
    ```
  - **Security Notice regarding `essentials.durable-queues.shared-queue-table-name`:**
    - This property, no matter if it's set using properties, System properties, env variables or yaml configuration, will be provided to the `PostgresqlDurableQueues` as the `sharedQueueTableName` parameter.
    - To support customization of storage table name, the `essentials.durable-queues.shared-queue-table-name` provided through the Spring configuration to the `PostgresqlDurableQueues`,
      will be directly used in constructing SQL statements through string concatenation, which exposes the component to **SQL injection attacks**.
    - It is the responsibility of the user of this starter component to sanitize the `essentials.durable-queues.shared-queue-table-name` to ensure the security of all the SQL statements generated by the `PostgresqlDurableQueues`.
      - The `PostgresqlDurableQueues` component will call the `PostgresqlUtil#checkIsValidTableOrColumnName(String)` method to validate the table name as a first line of defense.
      - The `PostgresqlUtil#checkIsValidTableOrColumnName(String)` provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.
        - **However, Essentials components as well as `PostgresqlUtil#checkIsValidTableOrColumnName(String)` does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.**
      - The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.
      - Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names
      - **Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.**
    - It is highly recommended that the `essentials.durable-queues.shared-queue-table-name` value is only derived from a controlled and trusted source.
      - To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the `essentials.durable-queues.shared-queue-table-name` value.
      - **Failure to adequately sanitize and validate this value could expose the application to SQL injection vulnerabilities, compromising the security and integrity of the database.**
- `Inboxes`, `Outboxes` and `DurableLocalCommandBus` configured to use `PostgresqlDurableQueues`
- `EventStoreEventBus` with bus-name `EventStoreLocalBus` and Bean name `eventBus`
  - Supports additional configuration properties:
  - ```
    essentials.reactive.event-bus-backpressure-buffer-size=1024
    essentials.reactive.overflow-max-retries=20
    essentials.reactive.queued-task-cap-factor=1.5
    #essentials.reactive.parallel-threads=4
    ```
- `ReactiveHandlersBeanPostProcessor` (for auto-registering `EventHandler` and `CommandHandler` Beans with the `EventBus`'s and `CommandBus` beans found in the `ApplicationContext`)
- `MultiTableChangeListener` which is used for optimizing `PostgresqlDurableQueues` message polling
- Automatically calling `Lifecycle.start()`/`Lifecycle.stop`, on any Beans implementing the `Lifecycle` interface, when the `ApplicationContext` is started/stopped through the `DefaultLifecycleManager`
  - In addition, during `ContextRefreshedEvent`, it will call `JdbiConfigurationCallback#configure(Jdbi)` on all `ApplicationContext` Beans that implement the `JdbiConfigurationCallback` interface,
    thereby providing them with an instance of the `Jdbi` instance.
  - Notice: The `JdbiConfigurationCallback#configure(Jdbi)` will be called BEFORE any `Lifecycle` beans have been started.
  - You can disable starting `Lifecycle` Beans by using setting this property to false:
    - `essentials.life-cycles.start-life-cycles=false`
- `DurableQueuesMicrometerTracingInterceptor` and `DurableQueuesMicrometerInterceptor` if property `management.tracing.enabled` has value `true`
  - The default `DurableQueuesMicrometerTracingInterceptor` values can be overridden using Spring properties:
   -  ```
       essentials.durable-queues.verbose-tracing=true
       ```

## Dependencies
Typical `pom.xml` dependencies required to use this starter
```
<dependencies>
    <dependency>
        <groupId>dk.cloudcreate.essentials.components</groupId>
        <artifactId>spring-boot-starter-postgresql-event-store</artifactId>
        <version>${essentials.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
        <version>${spring-boot.version}</version>
    </dependency>
    <dependency>
        <groupId>org.jdbi</groupId>
        <artifactId>jdbi3-core</artifactId>
        <version>${jdbi.version}</version>
    </dependency>
    <dependency>
        <groupId>org.jdbi</groupId>
        <artifactId>jdbi3-postgres</artifactId>
        <version>${jdbi.version}</version>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>${postgresql.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jdk8</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-core</artifactId>
        <version>${reactor.version}</version>
    </dependency>

    <!-- Test -->
    dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>${junit.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-engine</artifactId>
        <version>${junit.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.vintage</groupId>
        <artifactId>junit-vintage-engine</artifactId>
        <version>${junit.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>${assertj.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <version>${awaitility.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>${logback.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <version>${spring-boot.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```
