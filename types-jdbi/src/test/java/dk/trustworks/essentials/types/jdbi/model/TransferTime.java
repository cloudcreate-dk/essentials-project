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

package dk.trustworks.essentials.types.jdbi.model;

import dk.trustworks.essentials.types.OffsetDateTimeType;

import java.time.OffsetDateTime;

public class TransferTime extends OffsetDateTimeType<TransferTime> {
    public TransferTime(OffsetDateTime value) {
        super(value);
    }

    public static TransferTime of(OffsetDateTime value) {
        return new TransferTime(value);
    }

    public static TransferTime now() {
        return new TransferTime(OffsetDateTime.now());
    }
}
