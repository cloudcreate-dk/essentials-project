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

package dk.cloudcreate.essentials.types.springdata.jpa.converters;

import dk.cloudcreate.essentials.types.*;

import javax.persistence.AttributeConverter;
import java.sql.Date;

/**
 * Base implementation for all JPA {@link AttributeConverter}'s that can convert between a concrete {@link LocalDateType} sub-class
 * and a database {@link java.time.LocalDate} value.<br>
 * Example:
 * <pre>{@code
 * @Converter(autoApply = true)
 * public class DueDateAttributeConverter extends BaseLocalDateTypeAttributeConverter<AccountId> {
 *     @Override
 *     protected Class<DueDate> getConcreteLocalDateType() {
 *         return DueDate.class;
 *     }
 * }}</pre>
 *
 * @param <T> the concrete type of {@link LocalDateType} supported by this converter
 */
public abstract class BaseLocalDateTypeAttributeConverter<T extends LocalDateType<T>> implements AttributeConverter<T, Date> {
    @Override
    public Date convertToDatabaseColumn(T attribute) {
        return attribute != null ? Date.valueOf(attribute.value()) : null;
    }

    @Override
    public T convertToEntityAttribute(Date dbData) {
        if (dbData == null) return null;
        return SingleValueType.from(dbData.toLocalDate(), getConcreteLocalDateType());
    }

    /**
     * Override this method to return the concrete {@link LocalDateType} sub-class supported by this converter
     */
    protected abstract Class<T> getConcreteLocalDateType();
}
