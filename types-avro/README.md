# Essentials Java building blocks

Essentials is a set of Java version 17 (and later) building blocks built from the ground up to have no dependencies on other libraries, unless explicitly mentioned.

The Essentials philosophy is to provide high level building blocks and coding constructs that allows for concise and strongly typed code, which doesn't depend on other libraries or frameworks, but
instead allows easy integrations with many of the most popular libraries and frameworks such as Jackson, Spring Boot, Spring Data, JPA, etc.

> **NOTE:**  
> **The library is WORK-IN-PROGRESS**

## Types-Avro

This library focuses purely on providing [AVRO](https://avro.apache.org) serialization and deserialization support for the **types** defined in the Essentials `types` library.

To use `Types-Avro` just add the following Maven dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>types-avro</artifactId>
    <version>0.40.12</version>
</dependency>
```

`Types-Avro` usually needs additional third party dependencies to work, such as:
```
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>

<dependency>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro</artifactId>
</dependency>
```

**NOTE:**
**This library is WORK-IN-PROGRESS**

### Configuration

To support your own concrete `SingleValueType` that must be support Avro serialization and deserialization you must create a specific `LogicalType` and specify this logical type in your Avro Schema/IDL.  
Each concrete `SingleValueType` must have a dedicated `Conversion`, `LogicalType` and `LogicalTypeFactory` pair and must be registered with the `avro-maven-plugin`

Depending on the `SingleValueType` you implement you can choose to extend one of the supplied **Base** Conversions provided with this library:

| `SingleValueType` specialization | Base `Conversion` class                  | Avro primitive type | 
|----------------------------------|------------------------------------------|---------------------|
| `BigDecimalType`                 | `BaseBigDecimalTypeConversion`           | `string`            |
| `BigDecimalType`                 | `SingleConcreteBigDecimalTypeConversion` | `decimal`           |
| `CharSequenceType`               | `BaseCharSequenceTypeConversion`         | `string`            |
| `DoubleType`                     | `BaseDoubleTypeConversion`               | `double`            |
| `FloatType`                      | `BaseFloatTypeConversion`                | `float`             |
| `IntegerType`                    | `BaseIntegerTypeConversion`              | `int`               |
| `LongType`                       | `BaseLongTypeConversion`                 | `long`              |

Some concrete `Types` such as `Amount`, `Percentage`, `CurrencyCode`, `CountryCode` and `EmailAddress` come with supported our of the box.  
This allows you to define Avro schema/IDL protocol and directly refer these logical-types in your Avro Schema/IDL protocol.  

Example `order.avdl`:

```
@namespace("dk.cloudcreate.essentials.types.avro.test")
protocol Test {
  record Order {
      string           id;
      @logicalType("Amount")
      string           totalAmountWithoutSalesTax;
      @logicalType("CurrencyCode")
      string           currency;
      @logicalType("CountryCode")
      string           country;
      @logicalType("Percentage")
      string           salesTax;
      @logicalType("EmailAddress")
      string           email;
  }
}
```

If the required `Conversions` and `LogicalTypeFactory` configurations are added to the `avro-maven-plugin`:

```
<plugin>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro-maven-plugin</artifactId>
    <version>${avro.version}</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>idl-protocol</goal>
            </goals>
            <configuration>
                <stringType>String</stringType>
                <enableDecimalLogicalType>false</enableDecimalLogicalType>
                <customLogicalTypeFactories>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.CurrencyCodeLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.AmountLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.PercentageLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.CountryCodeLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.EmailAddressLogicalTypeFactory</logicalTypeFactory>
                </customLogicalTypeFactories>
                <customConversions>
                    <conversion>dk.cloudcreate.essentials.types.avro.CurrencyCodeConversion</conversion>
                    <conversion>dk.cloudcreate.essentials.types.avro.AmountConversion</conversion>
                    <conversion>dk.cloudcreate.essentials.types.avro.PercentageConversion</conversion>
                    <conversion>dk.cloudcreate.essentials.types.avro.CountryCodeConversion</conversion>
                    <conversion>dk.cloudcreate.essentials.types.avro.EmailAddressConversion</conversion>
                </customConversions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

then an `Order` class that looks like this will be generated:

```
@org.apache.avro.specific.AvroGenerated
public class Order extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  ...    
  private java.lang.String                             id;
  private dk.cloudcreate.essentials.types.Amount       totalAmountWithoutSalesTax;
  private dk.cloudcreate.essentials.types.CurrencyCode currency;
  private dk.cloudcreate.essentials.types.CountryCode  country;
  private dk.cloudcreate.essentials.types.Percentage   salesTax;
  private dk.cloudcreate.essentials.types.EmailAddress email;
  ...
}
```

### JSR 310 Semantic Types

This library also supports Logical-Types for all `JSR310SingleValueType` which wraps existing JSR-310 types (java.time):

| `JSR310SingleValueType` specialization | Value Type |
|----------------------------------|-------------------------|
| `InstantType`                    | `Instant`               |
| `LocalDateTimeType`              | `LocalDateTime`         |
| `LocalDateType`                  | `LocalDate`             |
| `LocalTimeType`                  | `LocalTime`             |
| `OffsetDateTimeType`             | `OffsetDateTime`        |
| `ZonedDateTimeType`              | `ZonedDateTime`         |

Caveats:
- The `JSR310SingleValueType` LogicalType converters provided by this library defaults to using ZoneId `UTC`,
  hence it's recommended to use `ZoneId.of("UTC")` or `Clock.systemUTC()` when creating the underlying JSR-310 instances.
- The `LocalDateTimeType`, `OffsetDateTimeType` and `ZonedDateTimeType` LogicalTypes 's `only provide millisecond time precision, hence it's recommended to use `.with(ChronoField.NANO_OF_SECOND, 0)`
  or `.withNano(0)` 

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

### Write your own  `Conversion` and `LogicalTypeFactory`

Let's say you want to introduce your own `OrderId` type:

```
package com.myproject.types;

public class OrderId extends CharSequenceType<OrderId> implements Identifier {
    public OrderId(CharSequence value) {
        super(value);
    }

    public static OrderId of(CharSequence value) {
        return new OrderId(value);
    }
}
```

and you want to use it in your Avro schema/IDL protocol:

```
@namespace("dk.cloudcreate.essentials.types.avro.test")
protocol Test {
  record Order {
      @logicalType("OrderId")
      string           id;
      @logicalType("Amount")
      string           totalAmountWithoutSalesTax;
      @logicalType("CurrencyCode")
      string           currency;
      @logicalType("CountryCode")
      string           country;
      @logicalType("Percentage")
      string           salesTax;
      @logicalType("EmailAddress")
      string           email;
  }
}
```

then you will need to define the following classes:

#### 1. Create the `OrderIdLogicalTypeFactory`

```
package com.myproject.types.avro;

public class OrderIdLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
    public static final LogicalType ORDER_ID = new CharSequenceTypeLogicalType("OrderId");

    @Override
    public LogicalType fromSchema(Schema schema) {
        return ORDER_ID;
    }

    @Override
    public String getTypeName() {
        return ORDER_ID.getName();
    }
}
```

#### 2. Create the `OrderIdConversion`

```
package com.myproject.types.avro;

public class OrderIdConversion extends BaseCharSequenceConversion<OrderId> {
    @Override
    public Class<OrderId> getConvertedType() {
        return OrderId.class;
    }

    @Override
    protected LogicalType getLogicalType() {
        return OrderIdLogicalTypeFactory.CURRENCY_CODE;
    }
}
```

#### 3. Register the `OrderIdConversion` and `OrderIdLogicalTypeFactory` with the `avro-maven-plugin`

```
<plugin>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro-maven-plugin</artifactId>
    <version>${avro.version}</version>
    <executions>
        <execution>
            <phase>generate-test-sources</phase>
            <goals>
                <goal>idl-protocol</goal>
            </goals>
            <configuration>
                <stringType>String</stringType>
                <enableDecimalLogicalType>false</enableDecimalLogicalType>
                <customLogicalTypeFactories>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.CurrencyCodeLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.AmountLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.PercentageLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.CountryCodeLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>dk.cloudcreate.essentials.types.avro.EmailAddressLogicalTypeFactory</logicalTypeFactory>
                    <logicalTypeFactory>com.myproject.types.avro.OrderIdLogicalTypeFactory</logicalTypeFactory>
                </customLogicalTypeFactories>
                <customConversions>
                    <conversion>dk.cloudcreate.essentials.types.avro.CurrencyCodeConversion</conversion>
                    <conversion>dk.cloudcreate.essentials.types.avro.AmountConversion</conversion>
                    <conversion>dk.cloudcreate.essentials.types.avro.PercentageConversion</conversion>
                    <conversion>dk.cloudcreate.essentials.types.avro.CountryCodeConversion</conversion>
                    <conversion>dk.cloudcreate.essentials.types.avro.EmailAddressConversion</conversion>
                    <conversion>com.myproject.types.avro.OrderIdConversion</conversion>
                </customConversions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

This will generate an `Order` class that now includes the `OrderId` and which will look like this:

```
@org.apache.avro.specific.AvroGenerated
public class Order extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  ...    
  private com.myproject.types.OrderId                  id;
  private dk.cloudcreate.essentials.types.Amount       totalAmountWithoutSalesTax;
  private dk.cloudcreate.essentials.types.CurrencyCode currency;
  private dk.cloudcreate.essentials.types.CountryCode  country;
  private dk.cloudcreate.essentials.types.Percentage   salesTax;
  private dk.cloudcreate.essentials.types.EmailAddress email;
  ...
}
```
