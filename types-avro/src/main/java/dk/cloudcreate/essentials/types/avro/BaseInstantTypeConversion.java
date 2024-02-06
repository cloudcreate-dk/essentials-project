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

import java.time.Instant;

/**
 * Base {@link Conversion} for all custom types of type {@link InstantType}<br>
 * The handling is similar to AVRO's <code>timestamp-millis</code> logical type:<br>
 * <blockquote>
 * The {@link InstantType} logical type represents an <b>instant</b>, i.e. of date with time, but without time-zone, and with a precision of 1 millisecond.<br>
 * The {@link InstantType} logical type annotates an Avro <b>long</b>.<br>
 * The Avro long stores the number of number of milliseconds since the UNIX epoch: 1 January 1970 00:00:00.000 UTC (ISO-8601 calendar system)
 * </blockquote>
 * <br>
 * Each concrete {@link InstantType} that must be support Avro serialization and deserialization must have a dedicated
 * {@link Conversion}, {@link LogicalType} and {@link LogicalTypes.LogicalTypeFactory} pair registered with the <b><code>avro-maven-plugin</code></b>.<br>
 * <br>
 * <b>Important:</b> The AVRO field/property must be use the AVRO primitive type: <b><code>long</code></b><br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *      @logicalType("LastUpdated")
 *       long lastUpdated;
 *   }
 * }
 * }</pre>
 * <br>
 * <b>Example:Support AVRO serialization and Deserialization for a given LastUpdated type:</b><br>
 * <b><u>1. Create the <code>LastUpdatedLogicalType</code> and <code>LastUpdatedLogicalTypeFactory</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class LastUpdatedLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
 *     public static final LogicalType LAST_UPDATED = new InstantTypeLogicalType("LastUpdated");
 *
 *     @Override
 *     public LogicalType fromSchema(Schema schema) {
 *         return LAST_UPDATED;
 *     }
 *
 *     @Override
 *     public String getTypeName() {
 *         return LAST_UPDATED.getName();
 *     }
 * }
 * }</pre>
 * <b><u>2. Create the <code>LastUpdatedConversion</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class LastUpdatedConversion extends BaseInstantTypeConversion<LastUpdated> {
 *     @Override
 *     public Class<LastUpdated> getConvertedType() {
 *         return LastUpdated.class;
 *     }
 *
 *     @Override
 *     protected LogicalType getLogicalType() {
 *         return LAST_UPDATED;
 *     }
 * }
 * }</pre>
 * <b><u>3. Register the <code>LastUpdatedConversion</code> and <code>LastUpdatedLogicalTypeFactory</code> with the <code>avro-maven-plugin</code></u></b>:<br>
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
 *                     <logicalTypeFactory>com.myproject.types.avro.LastUpdatedLogicalTypeFactory</logicalTypeFactory>
 *                 </customLogicalTypeFactories>
 *                 <customConversions>
 *                     <conversion>com.myproject.types.avro.LastUpdatedConversion</conversion>
 *                 </customConversions>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 * <b><u>Create a Record the uses the "LastUpdated" logical type</u></b><br>
 * Example IDL <code>"order.avdl"</code>:<br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       string           id;
 *       @logicalType("LastUpdated")
 *       long            lastUpdated;
 *   }
 * }
 * }</pre>
 * <br>
 * This will generate an Order class that looks like this:
 * <pre>{@code
 * public class Order extends SpecificRecordBase implements SpecificRecord {
 *   ...
 *   private java.lang.String id;
 *   private com.myproject.types.LastUpdated lastUpdated;
 *   ...
 * }
 * }</pre>
 *
 * @param <T> the concrete {@link InstantType} sub-type
 * @see InstantTypeLogicalType
 */
public abstract class BaseInstantTypeConversion<T extends InstantType<T>> extends Conversion<T> {
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
               SingleValueType.from(Instant.ofEpochMilli(value),
                                    getConvertedType());
    }

    @Override
    public Long toLong(T value, Schema schema, LogicalType type) {
        return value == null ?
               null :
               value.value().toEpochMilli();
    }
}
