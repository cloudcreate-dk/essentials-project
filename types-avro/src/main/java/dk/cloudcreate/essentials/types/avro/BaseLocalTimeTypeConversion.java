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

package dk.cloudcreate.essentials.types.avro;

import dk.cloudcreate.essentials.types.*;
import org.apache.avro.*;

import java.time.LocalTime;

/**
 * Base {@link Conversion} for all custom types of type {@link LocalTimeType}<br>
 * The handling is similar to AVRO's <code>time-micros</code> logical type:
 * <blockquote>
 * The {@link LocalTimeType} logical type represents a <b>time</b> of day without a date or time-zone.<br>
 * The {@link LocalTimeType} logical type annotates an Avro <b>long</b>.<br>
 * The Avro long stores the number of microseconds after midnight: 00:00:00.000000 (ISO-8601 calendar system)
 * </blockquote>
 * <br>
 * Each concrete {@link LocalTimeType} that must be support Avro serialization and deserialization must have a dedicated
 * {@link Conversion}, {@link LogicalType} and {@link LogicalTypes.LogicalTypeFactory} pair registered with the <b><code>avro-maven-plugin</code></b>.<br>
 * <br>
 * <b>Important:</b> The AVRO field/property must be use the AVRO primitive type: <b><code>long</code></b><br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *      @logicalType("TimeOfDay")
 *       long timeOfDay;
 *   }
 * }
 * }</pre>
 * <br>
 * <b>Example:Support AVRO serialization and Deserialization for a given TimeOfDay type:</b><br>
 * <b><u>1. Create the <code>TimeOfDayLogicalType</code> and <code>TimeOfDayLogicalTypeFactory</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class TimeOfDayLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
 *     public static final LogicalType TIME_OF_DAY = new LocalTimeTypeLogicalType("TimeOfDay");
 *
 *     @Override
 *     public LogicalType fromSchema(Schema schema) {
 *         return TIME_OF_DAY;
 *     }
 *
 *     @Override
 *     public String getTypeName() {
 *         return TIME_OF_DAY.getName();
 *     }
 * }
 * }</pre>
 * <b><u>2. Create the <code>TimeOfDayConversion</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class TimeOfDayConversion extends BaseLocalTimeTypeConversion<TimeOfDay> {
 *     @Override
 *     public Class<TimeOfDay> getConvertedType() {
 *         return TimeOfDay.class;
 *     }
 *
 *     @Override
 *     protected LogicalType getLogicalType() {
 *         return TIME_OF_DAY;
 *     }
 * }
 * }</pre>
 * <b><u>3. Register the <code>TimeOfDayConversion</code> and <code>TimeOfDayLogicalTypeFactory</code> with the <code>avro-maven-plugin</code></u></b>:<br>
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
 *                     <logicalTypeFactory>com.myproject.types.avro.TimeOfDayLogicalTypeFactory</logicalTypeFactory>
 *                 </customLogicalTypeFactories>
 *                 <customConversions>
 *                     <conversion>com.myproject.types.avro.TimeOfDayConversion</conversion>
 *                 </customConversions>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 * <b><u>Create a Record the uses the "TimeOfDay" logical type</u></b><br>
 * Example IDL <code>"order.avdl"</code>:<br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       string           id;
 *       @logicalType("TimeOfDay")
 *       long            timeOfDay;
 *   }
 * }
 * }</pre>
 * <br>
 * This will generate an Order class that looks like this:
 * <pre>{@code
 * public class Order extends SpecificRecordBase implements SpecificRecord {
 *   ...
 *   private java.lang.String id;
 *   private com.myproject.types.TimeOfDay timeOfDay;
 *   ...
 * }
 * }</pre>
 *
 * @param <T> the concrete {@link LocalTimeType} sub-type
 * @see LocalTimeTypeLogicalType
 */
public abstract class BaseLocalTimeTypeConversion<T extends LocalTimeType<T>> extends Conversion<T> {
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
               SingleValueType.from(LocalTime.ofNanoOfDay(value * 1000), getConvertedType());
    }

    @Override
    public Long toLong(T value, Schema schema, LogicalType type) {
        return value == null ?
               null :
               value.value().toNanoOfDay() / 1000;
    }
}
