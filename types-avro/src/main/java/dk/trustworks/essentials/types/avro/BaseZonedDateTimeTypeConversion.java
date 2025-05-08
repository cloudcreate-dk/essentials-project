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

package dk.trustworks.essentials.types.avro;

import dk.trustworks.essentials.types.*;
import org.apache.avro.*;

import java.time.*;

/**
 * Base {@link Conversion} for all custom types of type {@link ZonedDateTimeType}<br>
 * The handling is similar to AVRO's <code>timestamp-millis</code> logical type:<br>
 * <blockquote>
 * The {@link ZonedDateTimeType} logical type represents an <b>instant</b>, i.e. of date with time, but without time-zone, and with a precision of 1 millisecond.<br>
 * The {@link ZonedDateTimeType} logical type annotates an Avro <b>long</b>.<br>
 * The Avro long stores the number of number of milliseconds since the UNIX epoch: 1 January 1970 00:00:00.000 UTC (ISO-8601 calendar system)
 * </blockquote>
 * <br>
 * Each concrete {@link ZonedDateTimeType} that must be support Avro serialization and deserialization must have a dedicated
 * {@link Conversion}, {@link LogicalType} and {@link LogicalTypes.LogicalTypeFactory} pair registered with the <b><code>avro-maven-plugin</code></b>.<br>
 * <br>
 * <b>Important:</b> The AVRO field/property must be use the AVRO primitive type: <b><code>long</code></b><br>
 * <pre>{@code
 * @namespace("dk.trustworks.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *      @logicalType("TransactionTime")
 *       long transactionTime;
 *   }
 * }
 * }</pre>
 * <br>
 * <b>Example:Support AVRO serialization and Deserialization for a given TransactionTime type:</b><br>
 * <b><u>1. Create the <code>TransactionTimeLogicalType</code> and <code>TransactionTimeLogicalTypeFactory</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class TransactionTimeLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
 *     public static final LogicalType TRANSACTION_TIME = new ZonedDateTimeTypeLogicalType("TransactionTime");
 *
 *     @Override
 *     public LogicalType fromSchema(Schema schema) {
 *         return TRANSACTION_TIME;
 *     }
 *
 *     @Override
 *     public String getTypeName() {
 *         return TRANSACTION_TIME.getName();
 *     }
 * }
 * }</pre>
 * <b><u>2. Create the <code>TransactionTimeConversion</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class TransactionTimeConversion extends BaseZonedDateTimeTypeConversion<TransactionTime> {
 *     @Override
 *     public Class<TransactionTime> getConvertedType() {
 *         return TransactionTime.class;
 *     }
 *
 *     @Override
 *     protected LogicalType getLogicalType() {
 *         return TRANSACTION_TIME;
 *     }
 * }
 * }</pre>
 * <b><u>3. Register the <code>TransactionTimeConversion</code> and <code>TransactionTimeLogicalTypeFactory</code> with the <code>avro-maven-plugin</code></u></b>:<br>
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
 *                     <logicalTypeFactory>com.myproject.types.avro.TransactionTimeLogicalTypeFactory</logicalTypeFactory>
 *                 </customLogicalTypeFactories>
 *                 <customConversions>
 *                     <conversion>com.myproject.types.avro.TransactionTimeConversion</conversion>
 *                 </customConversions>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 * <b><u>Create a Record the uses the "TransactionTime" logical type</u></b><br>
 * Example IDL <code>"order.avdl"</code>:<br>
 * <pre>{@code
 * @namespace("dk.trustworks.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       string           id;
 *       @logicalType("TransactionTime")
 *       long            transactionTime;
 *   }
 * }
 * }</pre>
 * <br>
 * This will generate an Order class that looks like this:
 * <pre>{@code
 * public class Order extends SpecificRecordBase implements SpecificRecord {
 *   ...
 *   private java.lang.String id;
 *   private com.myproject.types.TransactionTime transactionTime;
 *   ...
 * }
 * }</pre>
 *
 * @param <T> the concrete {@link ZonedDateTimeType} sub-type
 * @see ZonedDateTimeTypeLogicalType
 */
public abstract class BaseZonedDateTimeTypeConversion<T extends ZonedDateTimeType<T>> extends Conversion<T> {
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
               SingleValueType.from(ZonedDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.of("UTC")),
                                    getConvertedType());
    }

    @Override
    public Long toLong(T value, Schema schema, LogicalType type) {
        return value == null ?
               null :
               value.value().toInstant().toEpochMilli();
    }
}
