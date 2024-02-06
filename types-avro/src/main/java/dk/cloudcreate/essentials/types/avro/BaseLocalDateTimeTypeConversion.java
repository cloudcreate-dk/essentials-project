/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.types.avro;

import dk.cloudcreate.essentials.types.*;
import org.apache.avro.*;

import java.time.*;

/**
 * Base {@link Conversion} for all custom types of type {@link LocalDateTimeType}<br>
 * The handling is similar to AVRO's <code> timestamp-millis</code> logical type:<br>
 * <blockquote>
 * The {@link LocalDateTimeType} logical type represents an <b>instant</b>, i.e. of date with time, but without time-zone, and with a precision of 1 millisecond.<br>
 * The {@link LocalDateTimeType} logical type annotates an Avro <b>long</b>.<br>
 * The Avro long stores the number of number of milliseconds since the UNIX epoch: 1 January 1970 00:00:00.000 UTC (ISO-8601 calendar system)
 * </blockquote>
 * <br>
 * Each concrete {@link LocalDateTimeType} that must be support Avro serialization and deserialization must have a dedicated
 * {@link Conversion}, {@link LogicalType} and {@link LogicalTypes.LogicalTypeFactory} pair registered with the <b><code>avro-maven-plugin</code></b>.<br>
 * <br>
 * <b>Important:</b> The AVRO field/property must be use the AVRO primitive type: <b><code>long</code></b><br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *      @logicalType("Created")
 *       long created;
 *   }
 * }
 * }</pre>
 * <br>
 * <b>Example:Support AVRO serialization and Deserialization for a given Created type:</b><br>
 * <b><u>1. Create the <code>CreatedLogicalType</code> and <code>CreatedLogicalTypeFactory</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class CreatedLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
 *     public static final LogicalType CREATED = new LocalDateTimeTypeLogicalType("Created");
 *
 *     @Override
 *     public LogicalType fromSchema(Schema schema) {
 *         return CREATED;
 *     }
 *
 *     @Override
 *     public String getTypeName() {
 *         return CREATED.getName();
 *     }
 * }
 * }</pre>
 * <b><u>2. Create the <code>CreatedConversion</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class CreatedConversion extends BaseLocalDateTimeTypeConversion<Created> {
 *     @Override
 *     public Class<Created> getConvertedType() {
 *         return Created.class;
 *     }
 *
 *     @Override
 *     protected LogicalType getLogicalType() {
 *         return CREATED;
 *     }
 * }
 * }</pre>
 * <b><u>3. Register the <code>CreatedConversion</code> and <code>CreatedLogicalTypeFactory</code> with the <code>avro-maven-plugin</code></u></b>:<br>
 * <pre>{@code
 * <plugin>
 *     <groupId>org.apache.avro</groupId>
 *     <artifactId>avro-maven-plugin</artifactId>
 *     <version>${avro.version}</version>
 *     <executions>
 *         <execution>
 *             <phase>generate-sources</phase>
 *             <goals>
 *                 <goal>idl-protocol</goal>
 *             </goals>
 *             <configuration>
 *                 <stringType>String</stringType>
 *                 <enableDecimalLogicalType>false</enableDecimalLogicalType>
 *                 <customLogicalTypeFactories>
 *                     <logicalTypeFactory>com.myproject.types.avro.CreatedLogicalTypeFactory</logicalTypeFactory>
 *                 </customLogicalTypeFactories>
 *                 <customConversions>
 *                     <conversion>com.myproject.types.avro.CreatedConversion</conversion>
 *                 </customConversions>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 * <b><u>Create a Record the uses the "Created" logical type</u></b><br>
 * Example IDL <code>"order.avdl"</code>:<br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       string           id;
 *       @logicalType("Created")
 *       long            created;
 *   }
 * }
 * }</pre>
 * <br>
 * This will generate an Order class that looks like this:
 * <pre>{@code
 * public class Order extends SpecificRecordBase implements SpecificRecord {
 *   ...
 *   private java.lang.String id;
 *   private com.myproject.types.Created created;
 *   ...
 * }
 * }</pre>
 *
 * @param <T> the concrete {@link LocalDateTimeType} sub-type
 * @see LocalDateTimeTypeLogicalType
 */
public abstract class BaseLocalDateTimeTypeConversion<T extends LocalDateTimeType<T>> extends Conversion<T> {
    protected abstract LogicalType getLogicalType();

    @Override
    public final Schema getRecommendedSchema() {
        return getLogicalType().addToSchema(Schema.create(Schema.Type.LONG));
    }

    @Override
    public final String getLogicalTypeName() {
        return getLogicalType().getName();
    }

    @Override
    public T fromLong(Long value, Schema schema, LogicalType type) {
        return value == null ?
               null :
               SingleValueType.from(LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.of("UTC")),
                                    getConvertedType());
    }

    @Override
    public Long toLong(T value, Schema schema, LogicalType type) {
        return value == null ?
               null :
               value.value().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
