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

package dk.cloudcreate.essentials.types.springdata.jpa.model;

import dk.cloudcreate.essentials.types.*;
import jakarta.persistence.Embeddable;

import java.util.Random;

@Embeddable
public class OrderId extends LongType<OrderId> implements Identifier {
    private static Random RANDOM_ID_GENERATOR = new Random();

    /**
     * Required as otherwise JPA/Hibernate complains with "dk.cloudcreate.essentials.types.springdata.jpa.model.OrderId has no persistent id property"
     * as it has problems with supporting SingleValueType immutable objects for identifier fields (as SingleValueType doesn't contain the necessary JPA annotations)
     */
    private Long orderId;

    /**
     * Is required by JPA
     */
    protected OrderId() {
        super(-1L);
    }

    public OrderId(Long value) {
        super(value);
        orderId = value;
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
