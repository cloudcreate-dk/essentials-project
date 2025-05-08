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

import dk.trustworks.essentials.types.LocalTimeType;
import org.apache.avro.*;

/**
 * AVRO {@link LogicalType} for {@link LocalTimeType}.<br>
 * The handling is similar to AVRO's <code>time-micros</code> logical type:<br>
 * <blockquote>
 * The {@link LocalTimeType} logical type represents a <b>time</b> of day without a date or time-zone.<br>
 * The {@link LocalTimeType} logical type annotates an Avro <b>long</b>.<br>
 * The Avro long stores the number of microseconds after midnight: 00:00:00.000000 (ISO-8601 calendar system)
 * </blockquote>
 * Example of how to use:
 * <pre>{@code
 * @namespace("dk.trustworks.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *      @logicalType("TimeOfDay")
 *      long timeOfDay;
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
 *
 * @see BaseLocalTimeTypeConversion
 */
public class LocalTimeTypeLogicalType extends LogicalType {
    public LocalTimeTypeLogicalType(String logicalTypeName) {
        super(logicalTypeName);
    }

    @Override
    public void validate(Schema schema) {
        super.validate(schema);
        if (schema.getType() != Schema.Type.LONG) {
            throw new IllegalArgumentException("'" + getName() + "' can only be used with type '" + Schema.Type.LONG.getName() + "'. Invalid schema: " + schema.toString(true));
        }
    }
}
