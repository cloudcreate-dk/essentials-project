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

import java.time.LocalDate;

/**
 * Base {@link Conversion} for all custom types of type {@link LocalDateType}<br>
 * The handling is similar to AVRO's <code>date</code> logical type:<br>
 * <blockquote>
 *   The {@link LocalDateType} logical type represents a calendar <b>date</b> without a time-zone.<br>
 *   The {@link LocalDateType} logical type annotates an Avro <b>int</b>.<br>
 *   The Avro int stores the number of days from the UNIX epoch - 1 January 1970 (ISO-8601 calendar system).
 * </blockquote>
 * <br>
 * Each concrete {@link LocalDateType} that must be support Avro serialization and deserialization must have a dedicated
 * {@link Conversion}, {@link LogicalType} and {@link LogicalTypes.LogicalTypeFactory} pair registered with the <b><code>avro-maven-plugin</code></b>.<br>
 * <br>
 * <b>Important:</b> The AVRO field/property must be use the AVRO primitive type: <b><code>int</code></b><br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *      @logicalType("DueDate")
*       int dueDate;
 *   }
 * }
 * }</pre>
 * <br>
 * <b>Example:Support AVRO serialization and Deserialization for a given DueDate type:</b><br>
 * <b><u>1. Create the <code>DueDateLogicalType</code> and <code>DueDateLogicalTypeFactory</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class DueDateLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
 *     public static final LogicalType DUE_DATE = new LocalDateTypeLogicalType("DueDate");
 *
 *     @Override
 *     public LogicalType fromSchema(Schema schema) {
 *         return DUE_DATE;
 *     }
 *
 *     @Override
 *     public String getTypeName() {
 *         return DUE_DATE.getName();
 *     }
 * }
 * }</pre>
 * <b><u>2. Create the <code>DueDateConversion</code></u></b>:<br>
 * <pre>{@code
 * package com.myproject.types.avro;
 *
 * public class DueDateConversion extends BaseLocalDateTypeConversion<DueDate> {
 *     @Override
 *     public Class<DueDate> getConvertedType() {
 *         return DueDate.class;
 *     }
 *
 *     @Override
 *     protected LogicalType getLogicalType() {
 *         return DUE_DATE;
 *     }
 * }
 * }</pre>
 * <b><u>3. Register the <code>DueDateConversion</code> and <code>DueDateLogicalTypeFactory</code> with the <code>avro-maven-plugin</code></u></b>:<br>
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
 *                     <logicalTypeFactory>com.myproject.types.avro.DueDateLogicalTypeFactory</logicalTypeFactory>
 *                 </customLogicalTypeFactories>
 *                 <customConversions>
 *                     <conversion>com.myproject.types.avro.DueDateConversion</conversion>
 *                 </customConversions>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 * <b><u>Create a Record the uses the "DueDate" logical type</u></b><br>
 * Example IDL <code>"order.avdl"</code>:<br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       string           id;
 *       @logicalType("DueDate")
 *       int             dueDate;
 *   }
 * }
 * }</pre>
 * <br>
 * This will generate an Order class that looks like this:
 * <pre>{@code
 * public class Order extends SpecificRecordBase implements SpecificRecord {
 *   ...
 *   private java.lang.String id;
 *   private com.myproject.types.DueDate dueDate;
 *   ...
 * }
 * }</pre>
 *
 * @param <T> the concrete {@link LocalDateType} sub-type
 * @see LocalDateTypeLogicalType
 */
public abstract class BaseLocalDateTypeConversion<T extends LocalDateType<T>> extends Conversion<T> {
    protected abstract LogicalType getLogicalType();

    @Override
    public final Schema getRecommendedSchema() {
        return getLogicalType().addToSchema(Schema.create(Schema.Type.INT));
    }

    @Override
    public final String getLogicalTypeName() {
        return getLogicalType().getName();
    }

    @Override
    public T fromInt(Integer value, Schema schema, LogicalType type) {
        return value == null ?
               null :
               SingleValueType.from(LocalDate.ofEpochDay(value), getConvertedType());
    }

    @Override
    public Integer toInt(T value, Schema schema, LogicalType type) {
        return value == null ?
               null :
               (int) value.value().toEpochDay();
    }
}
