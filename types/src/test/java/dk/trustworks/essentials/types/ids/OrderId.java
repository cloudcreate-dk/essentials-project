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

package dk.trustworks.essentials.types.ids;

import dk.trustworks.essentials.types.*;

import java.util.Random;

public class OrderId extends LongType<OrderId> implements Identifier {
    private static Random RANDOM_ID_GENERATOR = new Random();

    public OrderId(Long value) {
        super(value);
    }

    public static OrderId of(long value) {
        return new OrderId(value);
    }

    public static OrderId ofNullable(Long value) {
        return value != null ? new OrderId(value) : null;
    }

    public static OrderId random() {
        return new OrderId(RANDOM_ID_GENERATOR.nextLong());
    }
}
