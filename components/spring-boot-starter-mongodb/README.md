# Essentials Components - Essentials MongoDB: Spring Boot starter

This library provides Spring Boot auto-configuration for all MongoDB focused Essentials components.
All `@Beans` auto-configured by this library use `@ConditionalOnMissingBean` to allow for easy overriding.

To use `spring-boot-starter-postgresql` to add the following dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>spring-boot-starter-mongodb</artifactId>
    <version>0.9.4</version>
</dependency>
```

The `EssentialsComponentsConfiguration` auto-configures:
- Jackson/FasterXML JSON modules:
    - `EssentialTypesJacksonModule`
    - `EssentialsImmutableJacksonModule` (if `Objenesis` is on the classpath)
    - `ObjectMapper` with bean name `essentialComponentsObjectMapper` which provides good defaults for JSON serialization
- `SingleValueTypeRandomIdGenerator` to support server generated Id creations for SpringData Mongo classes with @Id fields of type `SingleValueType`
- `MongoCustomConversions` with a `SingleValueTypeConverter` covering `LockName`, `QueueEntryId` and `QueueName`
- `MongoTransactionManager` as it is needed by the `SpringMongoTransactionAwareUnitOfWorkFactory`
- `SpringMongoTransactionAwareUnitOfWorkFactory` configured to use the `MongoTransactionManager`
- `MongoFencedLockManager` using the `essentialComponentsObjectMapper` as JSON serializer
  - Supports additional properties:
  - ```
    essentials.fenced-lock-manager.fenced-locks-collection-name=fenced_locks
    essentials.fenced-lock-manager.lock-confirmation-interval=5s
    essentials.fenced-lock-manager.lock-time-out=12s
    ```
- `MongoDurableQueues` using the `essentialComponentsObjectMapper` as JSON serializer
  - Supports additional properties:
  - ```
    essentials.durable-queues.shared-queue-collection-name=durable_queues
    essentials.durable-queues.transactional-mode=fullytransactional
    # Only relevant if transactional-mode=singleoperationtransaction
    # essentials.durable-queues.message-handling-timeout=5s
    essentials.durable-queues.polling-delay-interval-increment-factor=0.5
    essentials.durable-queues.max-polling-interval=2s
    ```
- `Inboxes`, `Outboxes` and `DurableLocalCommandBus` configured to use `MongoDurableQueues`
- `LocalEventBus` with bus-name `default` and Bean name `eventBus`
- `ReactiveHandlersBeanPostProcessor` (for auto-registering `EventHandler` and `CommandHandler` Beans with the `EventBus`'s and `CommandBus` beans found in the `ApplicationContext`)
- Automatically calling `Lifecycle.start()`/`Lifecycle.stop`, on any Beans implementing the `Lifecycle` interface, when the `ApplicationContext` is started/stopped

  
Full configuration:

Optional overriding of values in `src/main/resources/application.properties`:
```
essentials.durable-queues.shared-queue-collection-name=durable_queues
essentials.durable-queues.transactional-mode=fullytransactional
# Only relevant if transactional-mode=singleoperationtransaction
# essentials.durable-queues.message-handling-timeout=5s

essentials.fenced-lock-manager.fenced-locks-collection-name=fenced_locks
essentials.fenced-lock-manager.lock-confirmation-interval=5s
essentials.fenced-lock-manager.lock-time-out=12s
```

`pom.xml` dependencies:
```
<dependencies>
    <dependency>
        <groupId>dk.cloudcreate.essentials.components</groupId>
        <artifactId>spring-boot-starter-mongodb</artifactId>
        <version>${essentials.version}</version>
    </dependency>
    <dependency>
        <groupId>org.objenesis</groupId>
        <artifactId>objenesis</artifactId>
        <version>${objenesis.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
        <version>${spring-boot.version}</version>
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
        <artifactId>mongodb</artifactId>
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