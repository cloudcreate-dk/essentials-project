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

package dk.cloudcreate.essentials.types;

import java.time.*;
import java.time.chrono.Chronology;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalField;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Base class for all {@link SingleValueType}'s that encapsulate a {@link java.time.LocalDate}.<br>
 * Example concrete implementation of the {@link LocalDateType}:
 * <pre>{@code
 * public class DueDate extends LocalDateType<DueDate> {
 *     public DueDate(LocalDate value) {
 *         super(value);
 *     }
 *
 *     public static DueDate of(LocalDate value) {
 *         return new DueDate(value);
 *     }
 *
 *     public static DueDate now() {
 *         return new DueDate(LocalDate.now());
 *     }
 * }
 * }</pre>
 *
 * @param <CONCRETE_TYPE> The concrete {@link LocalDateType} implementation
 */
public abstract class LocalDateType<CONCRETE_TYPE extends LocalDateType<CONCRETE_TYPE>> implements JSR310SingleValueType<LocalDate, CONCRETE_TYPE> {
    private final LocalDate value;

    public LocalDateType(LocalDate value) {
        this.value = requireNonNull(value, "You must provide a value");
    }

    @Override
    public LocalDate value() {
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
        var that = (LocalDateType<?>) o;
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
     * Delegate for {@link LocalDate#get(TemporalField)}
     *
     * @param field
     * @return
     */
    public int get(TemporalField field) {
        return value.get(field);
    }

    /**
     * Delegate for {@link LocalDate#getLong(TemporalField)}
     *
     * @param field
     * @return
     */
    public long getLong(TemporalField field) {
        return value.getLong(field);
    }

    /**
     * Checks if this instant is after the specified local date.
     *
     * @param other the other local date to compare to
     * @return true if this instant is after the specified local date.
     */
    public boolean isAfter(LocalDateType<?> other) {
        return value.isAfter(other.value);
    }

    /**
     * Checks if this instant is before the specified local date.
     *
     * @param other the other local date to compare to
     * @return true if this instant is before the specified local date.
     */
    public boolean isBefore(LocalDateType<?> other) {
        return value.isBefore(other.value);
    }

    /**
     * Delegate for {@link LocalDate#getYear()}
     * @return
     */
    public int getYear() {
        return value.getYear();
    }

    /**
     * Delegate for {@link LocalDate#getMonthValue()}
     * @return
     */
    public int getMonthValue() {
        return value.getMonthValue();
    }

    /**
     * Delegate for {@link LocalDate#getMonth()}
     * @return
     */
    public Month getMonth() {
        return value.getMonth();
    }

    /**
     * Delegate for {@link LocalDate#getDayOfMonth()}
     * @return
     */
    public int getDayOfMonth() {
        return value.getDayOfMonth();
    }

    /**
     * Delegate for {@link LocalDate#getDayOfYear()}
     * @return
     */
    public int getDayOfYear() {
        return value.getDayOfYear();
    }

    /**
     * Delegate for {@link LocalDate#getDayOfWeek()}
     * @return
     */
    public DayOfWeek getDayOfWeek() {
        return value.getDayOfWeek();
    }

    /**
     * Delegate for {@link LocalDate#getChronology()}
     * @return
     */
    public Chronology getChronology() {
        return value.getChronology();
    }

    /**
     * Delegate for {@link LocalDate#toEpochDay()}
     * @return
     */
    public long toEpochDay() {
        return value.toEpochDay();
    }

    /**
     * Delegate for {@link LocalDate#format(DateTimeFormatter)}
     * @param formatter
     * @return
     */
    public String format(DateTimeFormatter formatter) {
        return value.format(formatter);
    }

    /**
     * Delegate for {@link LocalDate#toEpochSecond(LocalTime, ZoneOffset)}
     * @return
     */
    public long toEpochSecond(LocalTime time, ZoneOffset offset) {
        return value.toEpochSecond(time, offset);
    }


}
