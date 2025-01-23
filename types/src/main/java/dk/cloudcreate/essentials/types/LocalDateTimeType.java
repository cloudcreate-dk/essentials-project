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

package dk.cloudcreate.essentials.types;

import java.time.*;
import java.time.chrono.Chronology;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalField;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Base class for all {@link SingleValueType}'s that encapsulate a {@link LocalDateTime}.<br>
 * Example concrete implementation of the {@link LocalDateTimeType}:
 * <pre>{@code
 * public class Created extends LocalDateTimeType<Created> {
 *     public Created(LocalDateTime value) {
 *         super(value);
 *     }
 *
 *     public static Created of(LocalDateTime value) {
 *         return new Created(value);
 *     }
 *
 *     public static Created now() {
 *         return new Created(LocalDateTime.now());
 *     }
 * }
 * }</pre>
 *
 * @param <CONCRETE_TYPE> The concrete {@link LocalDateTimeType} implementation
 */
public abstract class LocalDateTimeType<CONCRETE_TYPE extends LocalDateTimeType<CONCRETE_TYPE>> implements JSR310SingleValueType<LocalDateTime, CONCRETE_TYPE> {
    private final LocalDateTime value;

    public LocalDateTimeType(LocalDateTime value) {
        this.value = requireNonNull(value, "You must provide a value");
    }

    @Override
    public LocalDateTime value() {
        return value;
    }

    @Override
    public int compareTo(CONCRETE_TYPE o) {
        return this.value.compareTo(o.value());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (LocalDateTimeType<?>) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }

    /**
     * Delegate for {@link LocalDateTime#get(TemporalField)}
     *
     * @param field
     * @return
     */
    public int get(TemporalField field) {
        return value.get(field);
    }

    /**
     * Delegate for {@link LocalDateTime#getLong(TemporalField)}
     *
     * @param field
     * @return
     */
    public long getLong(TemporalField field) {
        return value.getLong(field);
    }

    /**
     * Delegate for {@link LocalDateTime#toEpochSecond(ZoneOffset)}
     *
     * @return
     */
    public long toEpochSecond(ZoneOffset zoneOffset) {
        return value.toEpochSecond(zoneOffset);
    }

    /**
     * Delegate for {@link LocalDateTime#getNano()}
     *
     * @return
     */
    public int getNano() {
        return value.getNano();
    }

    /**
     * Checks if this instant is after the specified local date time.
     *
     * @param other the other local date time to compare to
     * @return true if this instant is after the specified local date time.
     */
    public boolean isAfter(LocalDateTimeType<?> other) {
        return value.isAfter(other.value);
    }

    /**
     * Checks if this instant is before the specified local date time.
     *
     * @param other the other local date time to compare to
     * @return true if this instant is before the specified local date time.
     */
    public boolean isBefore(LocalDateTimeType<?> other) {
        return value.isBefore(other.value);
    }

    /**
     * Delegate for {@link LocalDateTime#format(DateTimeFormatter)}
     * @param formatter
     * @return
     */
    public String format(DateTimeFormatter formatter) {
        return value.format(formatter);
    }

    /**
     * Delegate for {@link LocalDateTime#toLocalDate()}
     * @return
     */
    public LocalDate toLocalDate() {
        return value.toLocalDate();
    }

    /**
     * Delegate for {@link LocalDateTime#getYear()}
     * @return
     */
    public int getYear() {
        return value.getYear();
    }

    /**
     * Delegate for {@link LocalDateTime#getMonthValue()}
     * @return
     */
    public int getMonthValue() {
        return value.getMonthValue();
    }

    /**
     * Delegate for {@link LocalDateTime#getMonth()}
     * @return
     */
    public Month getMonth() {
        return value.getMonth();
    }

    /**
     * Delegate for {@link LocalDateTime#getDayOfMonth()}
     * @return
     */
    public int getDayOfMonth() {
        return value.getDayOfMonth();
    }

    /**
     * Delegate for {@link LocalDateTime#getDayOfYear()}
     * @return
     */
    public int getDayOfYear() {
        return value.getDayOfYear();
    }

    /**
     * Delegate for {@link LocalDateTime#getDayOfWeek()}
     * @return
     */
    public DayOfWeek getDayOfWeek() {
        return value.getDayOfWeek();
    }

    /**
     * Delegate for {@link LocalDateTime#toLocalTime()}
     * @return
     */
    public LocalTime toLocalTime() {
        return value.toLocalTime();
    }

    /**
     * Delegate for {@link LocalDateTime#getHour()}
     * @return
     */
    public int getHour() {
        return value.getHour();
    }

    /**
     * Delegate for {@link LocalDateTime#getMinute()}
     * @return
     */
    public int getMinute() {
        return value.getMinute();
    }

    /**
     * Delegate for {@link LocalDateTime#getSecond()}
     * @return
     */
    public int getSecond() {
        return value.getSecond();
    }

    /**
     * Delegate for {@link LocalDateTime#getChronology()}
     * @return
     */
    public Chronology getChronology() {
        return value.getChronology();
    }
}
