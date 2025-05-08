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

package dk.trustworks.essentials.types.springdata.jpa.converters;

import dk.trustworks.essentials.types.*;
import jakarta.persistence.AttributeConverter;

import java.time.*;

/**
 * Base implementation for all JPA {@link AttributeConverter}'s that can convert between a concrete {@link ZonedDateTimeType} sub-class
 * and a database {@link LocalDate} value.<br>
 * Example:
 * <pre>{@code
 * @Converter(autoApply = true)
 * public class DueDateAttributeConverter extends BaseZonedDateTimeTypeAttributeConverter<AccountId> {
 *     @Override
 *     protected Class<DueDate> getConcreteZonedDateTimeType() {
 *         return DueDate.class;
 *     }
 * }}</pre>
 *
 * @param <T> the concrete type of {@link ZonedDateTimeType} supported by this converter
 */
public abstract class BaseZonedDateTimeTypeAttributeConverter<T extends ZonedDateTimeType<T>> implements AttributeConverter<T, ZonedDateTime> {
    @Override
    public ZonedDateTime convertToDatabaseColumn(T attribute) {
        return attribute != null ? attribute.value() : null;
    }

    @Override
    public T convertToEntityAttribute(ZonedDateTime dbData) {
        if (dbData == null) return null;
        return SingleValueType.from(dbData, getConcreteZonedDateTimeType());
    }

    /**
     * Override this method to return the concrete {@link ZonedDateTimeType} sub-class supported by this converter
     */
    protected abstract Class<T> getConcreteZonedDateTimeType();
}
