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

import org.apache.avro.*;

/**
 * {@link LogicalType} and {@link org.apache.avro.LogicalTypes.LogicalTypeFactory} for
 * {@link dk.trustworks.essentials.types.Percentage}.<br>
 * <b>Important:</b> The AVRO field/property must be use the AVRO primitive type: <b><code>string</code></b><br>
 * <pre>{@code
 * @namespace("dk.trustworks.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       @logicalType("Percentage")
 *       string  salesTax;
 *   }
 * }
 * }</pre>
 * <br>
 * To support Avro code generation for fields/properties that use the logical-type "Percentage" you
 * need to perform the following steps:
 * <b><u>Register the <code>PercentageConversion</code> and <code>PercentageLogicalTypeFactory</code> with the <code>avro-maven-plugin</code></u></b>:<br>
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
 *                     <logicalTypeFactory>dk.trustworks.essentials.types.avro.PercentageLogicalTypeFactory</logicalTypeFactory>
 *                 </customLogicalTypeFactories>
 *                 <customConversions>
 *                     <conversion>dk.trustworks.essentials.types.avro.PercentageConversion</conversion>
 *                 </customConversions>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 * <b><u>Create a Record the uses the "Percentage" logical type</u></b><br>
 * Example IDL <code>"order.avdl"</code>:<br>
 * <pre>{@code
 * @namespace("dk.trustworks.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       string           id;
 *       @logicalType("Percentage")
 *       string           salesTax;
 *   }
 * }
 * }</pre>
 * <br>
 * This will generate an Order class that looks like this:
 * <pre>{@code
 * public class Order extends SpecificRecordBase implements SpecificRecord {
 *   ...
 *   private java.lang.String id;
 *   private dk.trustworks.essentials.types.Percentage salesTax;
 *   ...
 * }
 * }</pre>
 */
public final class PercentageLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
    public static final LogicalType PERCENTAGE = new BigDecimalTypeLogicalType("Percentage");

    @Override
    public LogicalType fromSchema(Schema schema) {
        return PERCENTAGE;
    }

    @Override
    public String getTypeName() {
        return PERCENTAGE.getName();
    }
}
