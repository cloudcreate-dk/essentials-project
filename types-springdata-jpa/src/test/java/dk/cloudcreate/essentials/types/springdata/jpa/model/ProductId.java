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

package dk.cloudcreate.essentials.types.springdata.jpa.model;

import dk.cloudcreate.essentials.types.*;

import javax.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class ProductId extends CharSequenceType<ProductId> implements Identifier {
    /**
     * Required as otherwise JPA/Hibernate complains with "dk.cloudcreate.essentials.types.springdata.jpa.model.OrderId has no persistent id property"
     * as it has problems with supporting SingleValueType immutable objects for identifier fields (as SingleValueType doesn't contain the necessary JPA annotations)
     */
    private String productId;

    /**
     * Is required by JPA
     */
    protected ProductId() {
        super("null");
    }

    public ProductId(CharSequence value) {
        super(value);
        productId = value.toString();
    }

    public static ProductId of(CharSequence value) {
        return new ProductId(value);
    }

    public static ProductId ofNullable(CharSequence value) {
        return value != null ? new ProductId(value) : null;
    }

    public static ProductId random() {
        // You can use any random Id generator like e.g. https://github.com/codahale/time-id
        return new ProductId(UUID.randomUUID().toString());
    }
}
