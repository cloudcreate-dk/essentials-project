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

package dk.cloudcreate.essentials.types.spring.web;

import dk.cloudcreate.essentials.types.*;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.util.NumberUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Set;

/**
 * {@link GenericConverter} that supports converting the following {@link SingleValueType} subtypes:
 * {@link CharSequenceType} to {@link String} and {@link String} to {@link CharSequenceType}<br>
 * {@link NumberType} to {@link Number} and {@link Number} to {@link NumberType}
 */
public final class SingleValueTypeConverter implements GenericConverter {
    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Set.of(new ConvertiblePair(String.class, CharSequenceType.class),
                      new ConvertiblePair(Number.class, NumberType.class),
                      new ConvertiblePair(String.class, NumberType.class),
                      new ConvertiblePair(String.class, JSR310SingleValueType.class));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        if (source instanceof SingleValueType) {
            return ((SingleValueType<?, ?>) source).value();
        } else if (LocalDateTimeType.class.isAssignableFrom(targetType.getType())) {
            return SingleValueType.fromObject(LocalDateTime.parse((String)source), (Class<SingleValueType<?, ?>>) targetType.getType());
        } else if (LocalDateType.class.isAssignableFrom(targetType.getType())) {
            return SingleValueType.fromObject(LocalDate.parse((String)source), (Class<SingleValueType<?, ?>>) targetType.getType());
        } else if (InstantType.class.isAssignableFrom(targetType.getType())) {
            return SingleValueType.fromObject(Instant.parse((String)source), (Class<SingleValueType<?, ?>>) targetType.getType());
        } else if (LocalTimeType.class.isAssignableFrom(targetType.getType())) {
            return SingleValueType.fromObject(LocalTime.parse((String)source), (Class<SingleValueType<?, ?>>) targetType.getType());
        } else if (OffsetDateTimeType.class.isAssignableFrom(targetType.getType())) {
            return SingleValueType.fromObject(OffsetDateTime.parse((String)source), (Class<SingleValueType<?, ?>>) targetType.getType());
        } else if (ZonedDateTimeType.class.isAssignableFrom(targetType.getType())) {
            return SingleValueType.fromObject(ZonedDateTime.parse(URLDecoder.decode((String)source, StandardCharsets.UTF_8)), (Class<SingleValueType<?, ?>>) targetType.getType());
        } else {
            if (source instanceof String && NumberType.class.isAssignableFrom(targetType.getType())) {
                Class<? extends Number> numberTargetType = NumberType.resolveNumberClass(targetType.getType());
                Number number = NumberUtils.parseNumber((String) source, numberTargetType);
                return SingleValueType.fromObject(number, (Class<SingleValueType<?, ?>>) targetType.getType());
            } else {
                return SingleValueType.fromObject(source, (Class<SingleValueType<?, ?>>) targetType.getType());
            }
        }
    }
}
