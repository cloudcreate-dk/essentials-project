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

package dk.trustworks.essentials.types.avro.test;

import dk.trustworks.essentials.types.avro.LocalDateTimeTypeLogicalType;
import org.apache.avro.*;

public class CreatedLogicalTypeFactory implements LogicalTypes.LogicalTypeFactory {
    public static final LogicalType CREATED = new LocalDateTimeTypeLogicalType("Created");

    @Override
    public LogicalType fromSchema(Schema schema) {
        return CREATED;
    }

    @Override
    public String getTypeName() {
        return CREATED.getName();
    }
}