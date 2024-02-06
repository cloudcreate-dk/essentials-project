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

package dk.cloudcreate.essentials.types.avro.test;

import dk.cloudcreate.essentials.types.avro.BaseOffsetDateTimeTypeConversion;
import dk.cloudcreate.essentials.types.avro.test.types.TransferTime;
import org.apache.avro.LogicalType;

import static dk.cloudcreate.essentials.types.avro.test.TransferTimeLogicalTypeFactory.TRANSFER_TIME;

public class TransferTimeConversion extends BaseOffsetDateTimeTypeConversion<TransferTime> {
    @Override
    public Class<TransferTime> getConvertedType() {
        return TransferTime.class;
    }

    @Override
    protected LogicalType getLogicalType() {
        return TRANSFER_TIME;
    }
}