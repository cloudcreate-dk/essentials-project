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

package dk.cloudcreate.essentials.components.foundation.types;

import dk.cloudcreate.essentials.types.*;

import java.util.Optional;

/**
 * A correlation id is used to link multiple Messages or Events together in a distributed system
 */
public class CorrelationId extends CharSequenceType<CorrelationId> implements Identifier {
    public CorrelationId(CharSequence value) {
        super(value);
    }

    /**
     * Converts a non-null <code>value</code> to an {@link Optional#of(Object)} with argument {@link CorrelationId},
     * otherwise it returns an {@link Optional#empty()}
     *
     * @param value the optional value
     * @return an {@link Optional} with an {@link CorrelationId} depending on whether <code>value</code> is non-null, otherwise an
     * {@link Optional#empty()} is returned
     */
    public static Optional<CorrelationId> optionalFrom(CharSequence value) {
        if (value == null) {
            return Optional.empty();
        } else {
            return Optional.of(CorrelationId.of(value));
        }
    }

    public static CorrelationId of(CharSequence value) {
        return new CorrelationId(value);
    }

    public static CorrelationId random() {
        return new CorrelationId(RandomIdGenerator.generate());
    }
}
