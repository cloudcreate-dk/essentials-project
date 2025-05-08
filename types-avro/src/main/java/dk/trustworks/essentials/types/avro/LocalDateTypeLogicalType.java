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

import dk.trustworks.essentials.types.LocalDateType;
import org.apache.avro.*;

/**
 * AVRO {@link LogicalType} for {@link LocalDateType}.<br>
 * The handling is similar to AVRO's <code>date</code> logical type:<br>
 * <blockquote>
 * The {@link LocalDateType} logical type represents an <b>instant</b>, i.e. of date with time, but without time-zone, and with a precision of 1 millisecond.<br>
 * The {@link LocalDateType} logical type annotates an Avro <b>long</b>.<br>
 * The Avro int stores the number of days from the UNIX epoch - 1 January 1970 (ISO-8601 calendar system).
 * </blockquote>
 * Example of how to use:
 * <pre>{@code
 * @namespace("dk.trustworks.essentials.types.avro.test")
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
 *
 * @see BaseLocalDateTypeConversion
 */
public class LocalDateTypeLogicalType extends LogicalType {
    public LocalDateTypeLogicalType(String logicalTypeName) {
        super(logicalTypeName);
    }

    @Override
    public void validate(Schema schema) {
        super.validate(schema);
        if (schema.getType() != Schema.Type.INT) {
            throw new IllegalArgumentException("'" + getName() + "' can only be used with type '" + Schema.Type.INT.getName() + "'. Invalid schema: " + schema.toString(true));
        }
    }
}
