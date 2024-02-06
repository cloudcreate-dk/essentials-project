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

package dk.cloudcreate.essentials.components.queue.postgresql.test_data;

import dk.cloudcreate.essentials.types.CharSequenceType;

import java.util.UUID;

public class ProductId extends CharSequenceType<ProductId> {

    protected ProductId(CharSequence value) {
        super(value);
    }

    public static ProductId random() {
        return new ProductId(UUID.randomUUID().toString());
    }

    public static ProductId of(CharSequence id) {
        return new ProductId(id);
    }
}