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

package dk.cloudcreate.essentials.types.springdata.jpa.converters;

import dk.cloudcreate.essentials.types.*;
import jakarta.persistence.AttributeConverter;

import java.time.*;

/**
 * Base implementation for all JPA {@link AttributeConverter}'s that can convert between a concrete {@link InstantType} sub-class
 * and a database {@link LocalDate} value.<br>
 * Example:
 * <pre>{@code
 * @Converter(autoApply = true)
 * public class DueDateAttributeConverter extends BaseInstantTypeAttributeConverter<AccountId> {
 *     @Override
 *     protected Class<DueDate> getConcreteInstantType() {
 *         return DueDate.class;
 *     }
 * }}</pre>
 *
 * @param <T> the concrete type of {@link InstantType} supported by this converter
 */
public abstract class BaseInstantTypeAttributeConverter<T extends InstantType<T>> implements AttributeConverter<T, Instant> {
    @Override
    public Instant convertToDatabaseColumn(T attribute) {
        return attribute != null ? attribute.value() : null;
    }

    @Override
    public T convertToEntityAttribute(Instant dbData) {
        if (dbData == null) return null;
        return SingleValueType.from(dbData, getConcreteInstantType());
    }

    /**
     * Override this method to return the concrete {@link InstantType} sub-class supported by this converter
     */
    protected abstract Class<T> getConcreteInstantType();
}
