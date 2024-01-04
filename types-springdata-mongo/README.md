# Essentials Java building blocks

Essentials is a set of Java version 17 (and later) building blocks built from the ground up to have no dependencies
on other libraries, unless explicitly mentioned.

> Note: [For Java version 11 to 16 support, please use version 0.9.*](https://github.com/cloudcreate-dk/essentials-project/tree/java11)

The Essentials philosophy is to provide high level building blocks and coding constructs that allows for concise and
strongly typed code, which doesn't depend on other libraries or frameworks, but instead allows easy integrations with
many of the most popular libraries and frameworks such as Jackson, Spring Boot, Spring Data, JPA, etc.

## Types-SpringData-Mongo

This library focuses purely on providing [Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb) persistence support for the **types** defined in the
Essentials `types` library.

To use `Types-SpringData-Mongo` just add the following Maven dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>types-springdata-mongo</artifactId>
    <version>0.40.2</version>
</dependency>
```

`Types-SpringData-Mongo` usually needs additional third party dependencies to work, such as:
```
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-mongodb</artifactId>
</dependency>
```

**NOTE:**
**This library is WORK-IN-PROGRESS**

### Configuration

All you need to do is to register the following Spring Beans to your Spring configuration to support
Types conversions and automatic Id generation.

Example:

```
@Bean
public SingleValueTypeRandomIdGenerator registerIdGenerator() {
    return new SingleValueTypeRandomIdGenerator();
}

@Bean
public MongoCustomConversions mongoCustomConversions() {
    return new MongoCustomConversions(List.of(new SingleValueTypeConverter()));
}
```

Additionally, to support this Entity that uses a strongly types Map key, that can contain an `ObjectId#toString` as value, such as the `ProductId` key type in the `orderLines` property defined in the 
`Order`:

```
@Document
public class Order {
    @Id
    public OrderId id;
    public CustomerId customerId;
    public AccountId accountId;
    public Map<ProductId, Quantity> orderLines;
    
    ...
}    
```

and where `ProductId` is defined as (note the `random` method uses `new ProductId(ObjectId.get().toString())`):
```
public class ProductId extends CharSequenceType<ProductId> implements Identifier {
    public ProductId(CharSequence value) {
        super(value);
    }

    public static ProductId of(CharSequence value) {
        return new ProductId(value);
    }

    public static ProductId random() {
        return new ProductId(ObjectId.get().toString());
    }
}
```

Then you additionally need to explicit define that `ProductId` must be convertable to and from `ObjectId` when configuring the `MongoCustomConversions`:

```
@Bean
public MongoCustomConversions mongoCustomConversions() {
    return new MongoCustomConversions(List.of(
            new SingleValueTypeConverter(ProductId.class)));
}
```

### JSR 310 Semantic Types

This library also supports for the following `JSR310SingleValueType` which wraps existing JSR-310 types (java.time):

| `JSR310SingleValueType` specialization | Value Type |
|----------------------------------|-------------------------|
| `InstantType`                    | `Instant`               |
| `LocalDateTimeType`              | `LocalDateTime`         |
| `LocalDateType`                  | `LocalDate`             |
| `LocalTimeType`                  | `LocalTime`             |

Note: `OffsetDateTimeType` and `ZonedDateTimeType` are not supported as the Spring Data Mongo doesn't support converting
`OffsetDateTime` and `ZonedDateTime`  

This implementation is compatible with both the NativeDriver JavaTime Codec and SpringData JavaTime Codes:
```
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(mongoConverterConfigurationAdapter -> {
            mongoConverterConfigurationAdapter.useNativeDriverJavaTimeCodecs();
            // Default if nothing is specified ---> mongoConverterConfigurationAdapter.useSpringDataJavaTimeCodecs();
            mongoConverterConfigurationAdapter.registerConverters(List.of(
                    new SingleValueTypeConverter(ProductId.class)));
        });
    }
```


Caveats: 
- The `JSR310SingleValueType` converters provided by this library defaults to using ZoneId `UTC` (as MongoDB's `ISODate` is UTC based),
hence it's recommended to use `ZoneId.of("UTC")` or `Clock.systemUTC()` when creating the underlying JSR-310 instances.
- MongoDB's `ISODate` doesn't provide nano time precision, hence it's recommended to use `.with(ChronoField.NANO_OF_SECOND, 0)`
or `.withNano(0)` (depending on what the underlying JSR-310 type supports)

Example `InstantType`:
```
public class LastUpdated extends InstantType<LastUpdated> {
    public LastUpdated(Instant value) {
        super(value);
    }

    public static LastUpdated of(Instant value) {
        return new LastUpdated(value);
    }

    public static LastUpdated now() {
        return new LastUpdated(Instant.now(Clock.systemUTC()).with(ChronoField.NANO_OF_SECOND, 0));
    }
}
```

Example `LocalDateTimeType`:
```
public class Created extends LocalDateTimeType<Created> {
    public Created(LocalDateTime value) {
        super(value);
    }

    public static Created of(LocalDateTime value) {
        return new Created(value);
    }

    public static Created now() {
        return new Created(LocalDateTime.now(Clock.systemUTC()).withNano(0));
    }
}
```