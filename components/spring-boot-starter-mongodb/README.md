# Essentials Components - Essentials MongoDB: Spring Boot starter

This library provides Spring Boot auto-configuration for all MongoDB focused Essentials components.

> **NOTE:**  
> **The library is WORK-IN-PROGRESS**

All `@Beans` auto-configured by this library use `@ConditionalOnMissingBean` to allow for easy overriding.

> Please see the **Security** notices below, as well as **Security** notices for the individual components included, to familiarize yourself with the security risks related to Collection name configurations
> Such as:
> - [foundation-types](../foundation-types/README.md)
> - [springdata-mongo-distributed-fenced-lock](../springdata-mongo-distributed-fenced-lock/README.md)
> - [springdata-mongo-queue](../springdata-mongo-queue/README.md)

To use `spring-boot-starter-mongodb` to add the following dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>spring-boot-starter-mongodb</artifactId>
    <version>0.40.23</version>
</dependency>
```

## `EssentialsComponentsConfiguration` auto-configuration:

>If you in your own Spring Boot application choose to override the Beans defined by this starter,
then you need to check the component document to learn about the Security implications of each configuration, such as:
> - dk.cloudcreate.essentials.components.queue.springdata.mongodb.MongoDurableQueues
> - dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockManager
> - dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage

### Beans provided by the `EssentialsComponentsConfiguration` auto-configuration:
- Jackson/FasterXML JSON modules:
    - `EssentialTypesJacksonModule`
    - `EssentialsImmutableJacksonModule` (if `Objenesis` is on the classpath AND `essentials.immutable-jackson-module-enabled` has value `true`)
- `JacksonJSONSerializer` which uses an internally configured `ObjectMapper`, which provides good defaults for JSON serialization, and includes all Jackson `Module`'s defined in the `ApplicationContext`
- `SingleValueTypeRandomIdGenerator` to support server generated Id creations for SpringData Mongo classes with @Id fields of type `SingleValueType`
- `MongoCustomConversions` with a `SingleValueTypeConverter` covering `LockName`, `QueueEntryId` and `QueueName`
- `MongoTransactionManager` as it is needed by the `SpringMongoTransactionAwareUnitOfWorkFactory`
- `SpringMongoTransactionAwareUnitOfWorkFactory` configured to use the `MongoTransactionManager`
- `MongoFencedLockManager` using the `JSONSerializer` as JSON serializer
  - Supports additional properties:
  - ```
    essentials.fenced-lock-manager.fenced-locks-collection-name=fenced_locks
    essentials.fenced-lock-manager.lock-confirmation-interval=5s
    essentials.fenced-lock-manager.lock-time-out=12s
    essentials.fenced-lock-manager.release-acquired-locks-in-case-of-i-o-exceptions-during-lock-confirmation=false
    ```
  - **Security Notice regarding `essentials.fenced-lock-manager.fenced-locks-collection-name`:**
    - This property, no matter if it's set using properties, System properties, env variables or yaml configuration, will be provided to the `MongoFencedLockStorage` component, used by `MongoFencedLockManager`,  as the `fencedLocksCollectionName` parameter.
    - To support customization of storage table name, the `essentials.fenced-lock-manager.fenced-locks-collection-name` provided through the Spring configuration to the `MongoFencedLockStorage`, through the `MongoFencedLockManager`,
      and will be directly used as Collection name, which exposes the component to the risk of malicious input.  
    - **It is the responsibility of the user of this component to sanitize the `fencedLocksCollectionName` to avoid the risk of malicious input and that can compromise the security and integrity of the database**
    - The `MongoFencedLockStorage` component,  will call the `MongoUtil.checkIsValidCollectionName(String)` method to validate the collection name as a first line of defense.
      - The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.
        - **However, Essentials components as well as `MongoUtil.checkIsValidCollectionName(String)` does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc.**
    - **The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.**
    - Users must ensure thorough sanitization and validation of API input parameters,  collection names.
    - Insufficient attention to these practices may leave the application vulnerable to attacks, potentially
      endangering the security and integrity of the database. It is highly recommended that the `fencedLocksCollectionName` value is only derived from a controlled and trusted source.
    - To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the `fencedLocksCollectionName` value.
    - **Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.**
- `MongoDurableQueues` using the `JSONSerializer` as JSON serializer
  - Supports additional properties:
  - ```
    essentials.durable-queues.shared-queue-collection-name=durable_queues
    essentials.durable-queues.transactional-mode=fullytransactional or singleoperationtransaction (default)
    essentials.durable-queues.polling-delay-interval-increment-factor=0.5
    essentials.durable-queues.max-polling-interval=2s
    essentials.durable-queues.verbose-tracing=false
    # Only relevant if transactional-mode=singleoperationtransaction
    essentials.durable-queues.message-handling-timeout=5s
    ```
  - **Security Notice regarding `essentials.durable-queues.shared-queue-collection-name`:**
    - This property, no matter if it's set using properties, System properties, env variables or yaml configuration, will be provided to the `MongoDurableQueues` as the `sharedQueueCollectionName` parameter.
    - To support customization of storage table name, the `essentials.durable-queues.shared-queue-collection-name` provided through the Spring configuration to the `MongoDurableQueues`,
      will be directly used as Collection name, which exposes the component to the risk of malicious input.
      To support customization of storage collection name, the `sharedQueueCollectionName` will be directly used as Collection name, which exposes the component to the risk of malicious input.  
    - **It is the responsibility of the user of this component to sanitize the `sharedQueueCollectionName` to avoid the risk of malicious input and that can compromise the security and integrity of the database** 
    - The `MongoDurableQueues` component,  will call the `MongoUtil.checkIsValidCollectionName(String)` method to validate the collection name as a first line of defense.
      - The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.   
        - **However, Essentials components as well as `MongoUtil.checkIsValidCollectionName(String)` does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc.**
    - **The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.** 
    - Users must ensure thorough sanitization and validation of API input parameters,  collection names. 
    - Insufficient attention to these practices may leave the application vulnerable to attacks, potentially
endangering the security and integrity of the database. It is highly recommended that the `sharedQueueCollectionName` value is only derived from a controlled and trusted source. 
    - To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the `sharedQueueCollectionName` value.
    - **Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.**
- `Inboxes`, `Outboxes` and `DurableLocalCommandBus` configured to use `MongoDurableQueues`
- `LocalEventBus` with bus-name `default` and Bean name `eventBus`
  - Supports additional configuration properties:
  - ```
    essentials.reactive.event-bus-backpressure-buffer-size=1024
    essentials.reactive.overflow-max-retries=20
    essentials.reactive.queued-task-cap-factor=1.5
    #essentials.reactive.event-bus-parallel-threads=4
    #essentials.reactive.command-bus-parallel-send-and-dont-wait-consumers=4
    ```
- **Metrics:**
  - **Overview:**  
    This configuration controls the collection of performance metrics and determines the log level at which operations are reported.    
    When metrics collection is enabled for a component (such as durable queues, command bus, or message handlers), the duration of each operation is measured.   
    If the duration exceeds certain thresholds, the operation is logged at the corresponding level:
    - **errorThreshold:** If the duration exceeds this value, the operation is logged at **ERROR** level.
    - **warnThreshold:** If the duration exceeds this value (but is less than the error threshold), it is logged at **WARN** level.
    - **infoThreshold:** If the duration exceeds this value (but is less than the warn threshold), it is logged at **INFO** level.
    - **debugThreshold:** If the duration exceeds this value (but is less than the info threshold), it is logged at **DEBUG** level.
    - If none of the thresholds are met and metrics collection is enabled, the operation is logged at **TRACE** level.

  - **How to Configure:**  
    Each component can be configured individually. For each component, you can:
    - Enable or disable metrics collection.
    - Set the minimum duration (using a time unit such as `ms`) for each logging level.  
      These settings allow you to fine-tune how sensitive the logging should be, based on the performance characteristics you expect.

  - **YAML Example:**
    ```yaml
    essentials:
      metrics:
        durable-queues:
          enabled: true
          thresholds:
            debug: 25ms    # Log at DEBUG if duration ≥ 25ms (and below the INFO threshold)
            info: 200ms    # Log at INFO if duration ≥ 200ms (and below the WARN threshold)
            warn: 500ms    # Log at WARN if duration ≥ 500ms (and below the ERROR threshold)
            error: 5000ms  # Log at ERROR if duration ≥ 5000ms
        command-bus:
          enabled: true
          thresholds:
            debug: 25ms
            info: 200ms
            warn: 500ms
            error: 5000ms
        message-handler:
          enabled: true
          thresholds:
            debug: 25ms
            info: 200ms
            warn: 500ms
            error: 5000ms
    ```

  - **Properties Example:**
    ```properties
    essentials.metrics.durable-queues.enabled=true
    essentials.metrics.durable-queues.thresholds.debug=25ms
    essentials.metrics.durable-queues.thresholds.info=200ms
    essentials.metrics.durable-queues.thresholds.warn=500ms
    essentials.metrics.durable-queues.thresholds.error=5000ms

    essentials.metrics.command-bus.enabled=true
    essentials.metrics.command-bus.thresholds.debug=25ms
    essentials.metrics.command-bus.thresholds.info=200ms
    essentials.metrics.command-bus.thresholds.warn=500ms
    essentials.metrics.command-bus.thresholds.error=5000ms

    essentials.metrics.message-handler.enabled=true
    essentials.metrics.message-handler.thresholds.debug=25ms
    essentials.metrics.message-handler.thresholds.info=200ms
    essentials.metrics.message-handler.thresholds.warn=500ms
    essentials.metrics.message-handler.thresholds.error=5000ms
    ```

  - **Adjusting Log Levels:**  
    In addition to these properties, you can control which metrics are actually written to your log files by configuring the log levels for the corresponding logger classes in your logging framework (e.g. Logback or Log4j). For example:
    - For durable queues metrics, adjust the log level for:  
      `dk.cloudcreate.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeDurableQueueInterceptor`
    - For command bus metrics, adjust the log level for:  
      `dk.cloudcreate.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeCommandBusInterceptor`
    - For message handler metrics, adjust the log level for:  
      `dk.cloudcreate.essentials.components.foundation.interceptor.micrometer.RecordExecutionTimeMessageHandlerInterceptor`

- `ReactiveHandlersBeanPostProcessor` (for auto-registering `EventHandler` and `CommandHandler` Beans with the `EventBus`'s and `CommandBus` beans found in the `ApplicationContext`)
  - You can disable post-processing by setting: `essentials.reactive-bean-post-processor-enabled=false`
- Automatically calling `Lifecycle.start()`/`Lifecycle.stop`, on any Beans implementing the `Lifecycle` interface, when the `ApplicationContext` is started/stopped through the `DefaultLifecycleManager`
  - You can disable starting `Lifecycle` Beans by using setting this property to false:
    - `essentials.life-cycles.start-life-cycles=false`
- `DurableQueuesMicrometerTracingInterceptor` and `DurableQueuesMicrometerInterceptor` if property `management.tracing.enabled` has value `true`
  - The default `DurableQueuesMicrometerTracingInterceptor` values can be overridden using Spring properties:
  -  ```
       essentials.durable-queues.verbose-tracing=true
       ```
- `DurableLocalCommandBus`
  - The `DurableLocalCommandBus` supports two different error handling concepts for true **fire-and-forget asynchronous command processing** (i.e., when `CommandBus.sendAndDontWait` is used):
    - `SendAndDontWaitErrorHandler` -  The `SendAndDontWaitErrorHandler` exception handler will handle errors that occur while processing Commands sent using `CommandBus.sendAndDontWait`.
      - If this handler doesn't rethrow the exception, then the message will not be retried by the underlying `DurableQueues`,  nor will the message be marked as a dead-letter/poison message.
      - Default it uses `SendAndDontWaitErrorHandler.RethrowingSendAndDontWaitErrorHandler`.
      - To override this configuration, you need to register a Spring bean of type `SendAndDontWaitErrorHandler`
    - `RedeliveryPolicy` which sets the `RedeliveryPolicy` used when handling queued commands sent using `CommandBus.sendAndDontWait`.
      - Default it's using `DurableLocalCommandBus.DEFAULT_REDELIVERY_POLICY`.
      - To override this, you need to register a Spring bean of type `RedeliveryPolicy`
  - Example of custom Spring configuration:
     ```
     /**
      * Custom {@link RedeliveryPolicy} used by the {@link DurableLocalCommandBus} that is autoconfigured by the springboot starter
      * @return The {@link RedeliveryPolicy} used for {@link DurableLocalCommandBusBuilder#setCommandQueueRedeliveryPolicy(RedeliveryPolicy)}
      */
     @Bean
     RedeliveryPolicy durableLocalCommandBusRedeliveryPolicy() {
         return RedeliveryPolicy.exponentialBackoff()
                                .setInitialRedeliveryDelay(Duration.ofMillis(200))
                                .setFollowupRedeliveryDelay(Duration.ofMillis(200))
                                .setFollowupRedeliveryDelayMultiplier(1.1d)
                                .setMaximumFollowupRedeliveryDelayThreshold(Duration.ofSeconds(3))
                                .setMaximumNumberOfRedeliveries(20)
                                .setDeliveryErrorHandler(
                                        MessageDeliveryErrorHandler.stopRedeliveryOn(
                                                ConstraintViolationException.class,
                                                HttpClientErrorException.BadRequest.class))
                                .build();
     }
     
     
     /**
      * Custom {@link SendAndDontWaitErrorHandler} used by the {@link DurableLocalCommandBus} that is autoconfigured by the springboot starter
      * @return The {@link SendAndDontWaitErrorHandler} used for {@link DurableLocalCommandBusBuilder#setSendAndDontWaitErrorHandler(SendAndDontWaitErrorHandler)}
      */
     @Bean
     SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler() {
         return (exception, commandMessage, commandHandler) -> {
             // Example of not retrying HttpClientErrorException.Unauthorized at all -
             // if this exception is encountered then the failure is logged, but the command is never retried
             // nor marked as a dead-letter/poison message
             if (exception instanceof HttpClientErrorException.Unauthorized) {
                 log.error("Unauthorized exception", exception);
             } else {
                 Exceptions.sneakyThrow(exception);
             }
         };
     }
     ```
## Spring Data Mongo converters
**Converter**'s for core semantic types (`LockName`, `QueueEntryId` and `QueueName`) are automatically registered by the `EssentialsComponentsConfiguration` during its configuration of the
`MongoCustomConversions` Bean (you can choose to provide your own Bean).

You can add support for additional concrete `CharSequenceType`'s by providing a Bean of type `AdditionalCharSequenceTypesSupported`:
```java
@Bean
AdditionalCharSequenceTypesSupported additionalCharSequenceTypesSupported() {
    return new AdditionalCharSequenceTypesSupported(OrderId.class);
}
```

Similarly, you can add additional `Converter`/`GenericConverter`/etc by providing a Bean of type `AdditionalConverters`:

```java
@Bean
AdditionalConverters additionalGenericConverters() {
    return new AdditionalConverters(Jsr310Converters.StringToDurationConverter.INSTANCE,
                                    Jsr310Converters.DurationToStringConverter.INSTANCE);
}
```

## Dependencies
Typical `pom.xml` dependencies required to use this starter
```
<dependencies>
    <dependency>
        <groupId>dk.cloudcreate.essentials.components</groupId>
        <artifactId>spring-boot-starter-mongodb</artifactId>
        <version>${essentials.version}</version>
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
