# Essentials Java building blocks

Essentials is a set of Java version 17 (and later) building blocks built from the ground up to have no dependencies
on other libraries, unless explicitly mentioned.

The Essentials philosophy is to provide high level building blocks and coding constructs that allows for concise and
strongly typed code, which doesn't depend on other libraries or frameworks, but instead allows easy integrations with
many of the most popular libraries and frameworks such as Jackson, Spring Boot, Spring Data, JPA, etc.

>**NOTE:**
>**This WORK-IN-PROGRESS library is experimental and unstable, and currently it only supports simple `AttributeConverter`'s.     
> It e.g. doesn't support Id _autogeneration_ for `@Id` annotated `SingleValueType` field/properties!!!**

> **Warning: This module is very experimental & unstable - subject to be removed**

## Types-SpringData-JPA

This library focuses purely on providing [Spring Data JPA](https://spring.io/projects/spring-data-jpa) persistence support for the **types** defined in the
Essentials `types` library.



To use `Types-SpringData-JPA` just add the following Maven dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>types-springdata-jpa</artifactId>
    <version>0.40.11</version>
</dependency>
```

`Types-SpringData-JPA` usually needs additional third party dependencies to work, such as:
```
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>jakarta.persistence</groupId>
    <artifactId>jakarta.persistence-api</artifactId>
    <version>3.1.0</version>
</dependency>
```

Example:

```java 
@Entity
@Table(name = "orders")
public class Order {
    @EmbeddedId
    public OrderId    id;
    public CustomerId customerId;
    public AccountId  accountId;
    
    ...
}
```

You need to create custom `AttributeConverter`'s.  
All `SingleValueTypes` are support and provide convenience Base AttributeConverters in
`dk.cloudcreate.essentials.types.springdata.jpa.converters` that you can extend:

- `BaseBigDecimalTypeAttributeConverter`
- `BaseByteTypeAttributeConverter`
- `BaseCharSequenceTypeAttributeConverter`
- `BaseDoubleTypeAttributeConverter`
- `BaseFloatTypeAttributeConverter`
- `BaseInstantTypeAttributeConverter`
- `BaseIntegerTypeAttributeConverter`
- `BaseLocalDateTimeTypeAttributeConverter`
- `BaseLocalDateTypeAttributeConverter`
- `BaseLocalTimeTypeAttributeConverter`
- `BaseLongTypeAttributeConverter`
- `BaseOffsetDateTimeTypeAttributeConverter`
- `BaseShortTypeAttributeConverter`
- `BaseZonedDateTimeTypeAttributeConverter`

Example:

```java 
@Converter(autoApply = true)
public class CustomerIdAttributeConverter extends BaseCharSequenceTypeAttributeConverter<CustomerId> {
    @Override
    protected Class<CustomerId> getConcreteCharSequenceType() {
        return CustomerId.class;
    }
}
```

or

```java 
@Converter(autoApply = true)
public class AccountIdAttributeConverter extends BaseLongTypeAttributeConverter<AccountId> {
    @Override
    protected Class<AccountId> getConcreteLongType() {
        return AccountId.class;
    }
}
```

## @Id / Primary key fields 

`@Id` or Primary key fields of type `SingleValueType` needs special mapping.
Below is an example that uses `@EmbeddedId` instead of `@Id`:

```java 
@Entity
@Table(name = "orders")
public class Order {
    @EmbeddedId
    public OrderId    id;
    ...
}
```

The `OrderId` must:
- Be annotated with `@Embeddable` 
- Furthermore, it needs to be modified to include an additional **id** field (in the example below called `orderId`),  which is the actual id value that Hibernate/JPA persist.
  - This **id** is required as otherwise JPA/Hibernate complains with "dk.cloudcreate.essentials.types.springdata.jpa.model.OrderId has no persistent id property".
  - JPA/Hibernate has problems supporting `SingleValueType` immutable objects for **identifier** fields because `SingleValueType` doesn't contain the necessary JPA annotations.  
- You also need to provide a default constructor, which due to `SingleValueType`'s design can NOT be **null**, so you need to provide a temporary value that indicates that the value is temporary.
- Finally, the single argument constructor needs to call both `super(value);` and maintain the **id** field (in the example called `orderId`)

```java
@Embeddable
public class OrderId extends LongType<OrderId> implements Identifier {
    private static Random RANDOM_ID_GENERATOR = new Random();

    /**
     * Required as otherwise JPA/Hibernate complains with "dk.cloudcreate.essentials.types.springdata.jpa.model.OrderId has no persistent id property"
     * as it has problems with supporting SingleValueType immutable objects for identifier fields (as SingleValueType doesn't contain the necessary JPA annotations)
     */
    private Long orderId;

    /**
     * Is required by JPA
     */
    protected OrderId() {
        super(-1L);
    }

    public OrderId(Long value) {
        super(value);
        orderId = value;
    }

    public static OrderId of(long value) {
        return new OrderId(value);
    }

    public static OrderId ofNullable(Long value) {
        return value != null ? new OrderId(value) : null;
    }

    public static OrderId random() {
        return new OrderId(RANDOM_ID_GENERATOR.nextLong());
    }
}
```