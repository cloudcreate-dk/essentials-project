# Essentials Java building blocks

Essentials is a set of Java version 11 (and later) building blocks built from the ground up to have no dependencies
on other libraries, unless explicitly mentioned.

The Essentials philosophy is to provide high level building blocks and coding constructs that allows for concise and
strongly typed code, which doesn't depend on other libraries or frameworks, but instead allows easy integrations with
many of the most popular libraries and frameworks such as Jackson, Spring Boot, Spring Data, JPA, etc.

## Types-Spring-Web

This library focuses purely on providing [Spring](https://spring.io/projects/spring-framework) [WebMvc/MVC](https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#spring-web)
and [WebFlux](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#spring-webflux) `Converter` support for the **types** defined in the
Essentials `types` library.

To use `Types-Spring-Web` just add the following Maven dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>types-spring-web</artifactId>
    <version>0.9.13</version>
</dependency>
```

`Types-Spring-Web` usually needs additional third party dependencies to work, such as:
```
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
</dependency>
```

**NOTE:**
**This library is WORK-IN-PROGRESS**

### Configuration

#### WebMvc configuration

##### 1. If you want to be able to serialize/deserialize Objects and `SingleValueType`'s to JSON then you need to add a dependency `types-jackson`:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>types-jackson</artifactId>
</dependency>
```
and define a `@Bean` the adds the `EssentialTypesJacksonModule`:
```
@Bean
public com.fasterxml.jackson.databind.Module essentialJacksonModule() {
    return new EssentialTypesJacksonModule();
}
```

##### 2. Setup a `WebMvcConfigurer` that adds the `SingleValueTypeConverter`
This will allow you to deserialize `@PathVariable` `@RequestParam` method parameters of type `SingleValueType`:
```
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new SingleValueTypeConverter());
    }
}
```

Example:
```
@PostMapping("/order/for-customer/{customerId}/update/total-price")
public ResponseEntity<Order> updatePrice(@PathVariable CustomerId customerId,
                                         @RequestParam("price") Amount price) {
    ...
}
```

#### WebFlux configuration

##### 1. If you want to be able to serialize/deserialize Objects and `SingleValueType`'s to JSON then you need to add a dependency `types-jackson`:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>types-jackson</artifactId>
</dependency>
```
and define a `@Bean` the adds the `EssentialTypesJacksonModule`:
```
@Bean
public com.fasterxml.jackson.databind.Module essentialJacksonModule() {
    return new EssentialTypesJacksonModule();
}
```

##### 2. Setup a `WebFluxConfigurer` that adds the `SingleValueTypeConverter` and configures the `Jackson2JsonEncoder`/`Jackson2JsonDecoder`

This will allow you to deserialize `@PathVariable` `@RequestParam` method parameters of type `SingleValueType`:
```
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().jackson2JsonEncoder(
                new Jackson2JsonEncoder(objectMapper));
        configurer.defaultCodecs().jackson2JsonDecoder(
                new Jackson2JsonDecoder(objectMapper));
    }
    
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new SingleValueTypeConverter());
    }
}
```

Example:
```
@PostMapping("/reactive-order/for-customer/{customerId}/update/total-price")
public Mono<Order> updatePrice(@PathVariable CustomerId customerId,
                               @RequestParam("price") Amount price) {
    ...
}
```

### JSR 310 Semantic Types

This library also supports `JSR310SingleValueType` which wraps existing JSR-310 types (java.time):
| `JSR310SingleValueType` specialization | Value Type |
|----------------------------------|-------------------------|
| `InstantType`                    | `Instant`               |
| `LocalDateTimeType`              | `LocalDateTime`         |
| `LocalDateType`                  | `LocalDate`             |
| `LocalTimeType`                  | `LocalTime`             |
| `OffsetDateTimeType`             | `OffsetDateTime`        |
| `ZonedDateTimeType`              | `ZonedDateTime`         |

#### JSR-310 Semantic Types - JSON payload 
As described in [types-jackson](../types-jackson/README.md) then each JSON serializable concrete `JSR310SingleValueType` being transferred to/from the
MVCController/WebFlux operations much  **MUST** specify a `@JsonCreator` constructor.  

Example: `TransactionTime` which is a concrete `ZonedDateTimeType`:
```
public class TransactionTime extends ZonedDateTimeType<TransactionTime> {
    @JsonCreator
    public TransactionTime(ZonedDateTime value) {
        super(value);
    }

    public static TransactionTime of(ZonedDateTime value) {
        return new TransactionTime(value);
    }

    public static TransactionTime now() {
        return new TransactionTime(ZonedDateTime.now(ZoneId.of("UTC")));
    }
}
```

#### JSR-310 Semantic Types - Path variables and Request Parameters

This library also supports using all concrete `JSR310SingleValueType` as `@PathVariable` or `@RequestParam`'s.
Only limitation is that any `ZonedDateTimeType`'s values must be `URLEncoded` to properly work such as `mockMvc.perform(MockMvcRequestBuilders.get("/orders/by-transaction-time/{transactionTime}", URLEncoder.encode(transactionTime.toString(), StandardCharsets.UTF_8)))`

Example using a JSR-310 Semantic Type `@PathVariable` parameter:
```
@GetMapping("/orders")
public DueDate getOrdersWithParam(@RequestParam("dueDate") DueDate dueDate) {
    return dueDate;
}
```

Example using a JSR-310 Semantic Type `@@RequestParam` parameter
```
@GetMapping("/orders/by-due-date/{dueDate}")
public DueDate getOrders(@PathVariable DueDate dueDate) {
    return dueDate;
}
```