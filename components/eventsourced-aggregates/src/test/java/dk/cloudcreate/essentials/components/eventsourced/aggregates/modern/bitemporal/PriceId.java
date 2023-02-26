/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.bitemporal;

import dk.cloudcreate.essentials.components.foundation.types.RandomIdGenerator;
import dk.cloudcreate.essentials.types.CharSequenceType;

/**
 * Unique id that identifies a specific price entry.<br>
 * Adjusting the price adds a new {@link PriceId} to the {@link ProductPrice}.<br>
 * Adjustments to the validity of an existing price always refers to the {@link PriceId} of the price entry.
 */
public class PriceId extends CharSequenceType<PriceId> {

    protected PriceId(CharSequence value) {
        super(value);
    }

    public static PriceId random() {
        return new PriceId(RandomIdGenerator.generate());
    }

    public static PriceId of(CharSequence id) {
        return new PriceId(id);
    }
}