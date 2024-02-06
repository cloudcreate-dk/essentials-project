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

import dk.cloudcreate.essentials.types.Amount;
import org.apache.avro.LogicalType;

import static dk.cloudcreate.essentials.types.avro.AmountLogicalTypeFactory.AMOUNT;

/**
 * {@link org.apache.avro.Conversion} for {@link Amount} - see {@link AmountLogicalTypeFactory}
 * for information about how to configure this conversion to be used during Avro code generation<br>
 * <br>
 * <b>Important:</b> The AVRO field/property must be use the AVRO primitive type: <b><code>string</code></b><br>
 * <pre>{@code
 * @namespace("dk.cloudcreate.essentials.types.avro.test")
 * protocol Test {
 *   record Order {
 *       @logicalType("Amount")
 *       string  tax;
 *   }
 * }
 * }</pre>
 */
public class AmountConversion extends BaseBigDecimalTypeConversion<Amount> {
    @Override
    public Class<Amount> getConvertedType() {
        return Amount.class;
    }

    @Override
    protected LogicalType getLogicalType() {
        return AMOUNT;
    }
}
