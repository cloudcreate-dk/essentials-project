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

import java.math.BigDecimal;

/**
 * Logical-Type for {@link dk.trustworks.essentials.types.BigDecimalType}<br>
 * <b>NOTICE:</b>
 * <blockquote>
 * This Conversion requires that AVRO field/property must be use the AVRO primitive type: <b><code>string</code></b> and NEITHER the <code>"double"</code> NOR the more sophisticated logical type <code>"decimal"</code>.<br>
 * This means that you can have multiple concrete {@link BigDecimalType}'s mapped with in a single Schema, which comes at the risk of loosing precision in the number representation in the conversion between the concrete
 * {@link BigDecimalType}'s internal {@link BigDecimal} and the corresponding <code>string</code> value in Avro!<br>
 * <br>
 * In case you want to use logical type <code>decimal</code> then you need to use the {@link SingleConcreteBigDecimalTypeConversion}, which comes with the limitation that ALL Avro <code>decimal</code> fields/properties will be
 * mapped to a single concrete {@link BigDecimalType} supported by your implementation of the {@link SingleConcreteBigDecimalTypeConversion}<br>
 * </blockquote>
 * <br>
 * Each concrete {@link BigDecimalType} that must be support Avro serialization and deserialization to/from <code>string</code> must have a dedicated
 * {@link Conversion} and {@link org.apache.avro.LogicalTypes.LogicalTypeFactory} pair registered with the <b><code>avro-maven-plugin</code></b>.<br>
 * <br>
 * <b>Important:</b> The AVRO field/property must be use the AVRO primitive type: <b><code>string</code></b><br>
 * <pre>{@code
 * @namespace("dk.trustworks.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       @logicalType("MyLogicalType")
 *       string  tax;
 *   }
 * }
 * }</pre>
 * <br>
 * <b>Example:Support AVRO serialization and Deserialization for {@link Amount}:</b><br>
 * <b><u>1. Create the <code>AmountLogicalType</code> and <code>AmountLogicalTypeFactory</code></u></b>:<br>
 * <pre>{@code
 * public class AmountLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
 *     public static final LogicalType AMOUNT = new BigDecimalTypeLogicalType("Amount");
 *
 *     @Override
 *     public LogicalType fromSchema(Schema schema) {
 *         return AMOUNT;
 *     }
 *
 *     @Override
 *     public String getTypeName() {
 *         return AMOUNT.getName();
 *     }
 * }
 * }</pre>
 * <b><u>2. Create the <code>AmountConversion</code></u></b>:<br>
 * <pre>{@code
 * public class AmountConversion extends BaseBigDecimalTypeConversion<Amount> {
 *     @Override
 *     public Class<Amount> getConvertedType() {
 *         return Amount.class;
 *     }
 *
 *     @Override
 *     protected LogicalType getLogicalType() {
 *         return AmountLogicalTypeFactory.AMOUNT;
 *     }
 * }
 * }</pre>
 * <b><u>3. Register the <code>AmountConversion</code> and <code>AmountLogicalTypeFactory</code> with the <code>avro-maven-plugin</code></u></b>:<br>
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
 *                     <logicalTypeFactory>dk.trustworks.essentials.types.avro.AmountLogicalTypeFactory</logicalTypeFactory>
 *                 </customLogicalTypeFactories>
 *                 <customConversions>
 *                     <conversion>dk.trustworks.essentials.types.avro.AmountConversion</conversion>
 *                 </customConversions>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 * @see BaseBigDecimalTypeConversion
 */
public class BigDecimalTypeLogicalType extends LogicalType {
    public BigDecimalTypeLogicalType(String logicalTypeName) {
        super(logicalTypeName);
    }

    @Override
    public void validate(Schema schema) {
        super.validate(schema);
        if (schema.getType() != Schema.Type.STRING) {
            throw new IllegalArgumentException("'" + getName() + "' can only be used with type '" + Schema.Type.STRING.getName() + "'. Invalid schema: " + schema.toString(true));
        }
    }
}
