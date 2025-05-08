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

package dk.trustworks.essentials.components.boot.autoconfigure.mongodb;

import org.springframework.core.convert.converter.*;

import java.util.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * {@link AdditionalConverters} class represents a collection of additional {@link Converter}/{@link GenericConverter}/etc.
 * that will be added to the {@link org.springframework.data.mongodb.core.convert.MongoCustomConversions}
 * that is created in {@link EssentialsComponentsConfiguration#mongoCustomConversions(Optional, Optional)}
 */
public final class AdditionalConverters {
    /**
     * {@link Converter}/{@link GenericConverter}/etc.
     */
    public final List<?> converters;

    public AdditionalConverters() {
        this(List.of());
    }

    /**
     * Constructs an instance of AdditionalGenericConverters with the given array of {@link Converter}/{@link GenericConverter}/etc. objects.
     *
     * @param converters an array of {@link Converter} objects to be added as additional converters
     */
    public AdditionalConverters(Object... converters) {
        this(List.of(converters));
    }

    /**
     * Constructs an instance of AdditionalGenericConverters with the given array of {@link Converter}/{@link GenericConverter}/etc. objects.
     *
     * @param converters a list of {@link Converter} objects to be added as additional converters (must not be null)
     */
    public AdditionalConverters(List<?> converters) {
        this.converters = requireNonNull(converters, "converters is null");
    }
}
