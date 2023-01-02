/*
 * Copyright 2021-2022 the original author or authors.
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalField;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Base class for all {@link SingleValueType}'s that encapsulate a {@link java.time.LocalTime}.<br>
 * Example concrete implementation of the {@link LocalTimeType}:
 * <pre>{@code
 * public class TimeOfDay extends LocalTimeType<TimeOfDay> {
 *     public TimeOfDay(LocalTime value) {
 *         super(value);
 *     }
 *
 *     public static TimeOfDay of(LocalTime value) {
 *         return new TimeOfDay(value);
 *     }
 *
 *     public static TimeOfDay now() {
 *         return new TimeOfDay(LocalTime.now());
 *     }
 * }
 * }</pre>
 *
 * @param <CONCRETE_TYPE> The concrete {@link LocalTimeType} implementation
 */
public abstract class LocalTimeType<CONCRETE_TYPE extends LocalTimeType<CONCRETE_TYPE>> implements JSR310SingleValueType<LocalTime, CONCRETE_TYPE> {
    private final LocalTime value;

    public LocalTimeType(LocalTime value) {
        this.value = requireNonNull(value, "You must provide a value");
    }

    @Override
    public LocalTime value() {
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
        var that = (LocalTimeType<?>) o;
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
     * Delegate for {@link LocalTime#get(TemporalField)}
     *
     * @param field
     * @return
     */
    public int get(TemporalField field) {
        return value.get(field);
    }

    /**
     * Delegate for {@link LocalTime#getLong(TemporalField)}
     *
     * @param field
     * @return
     */
    public long getLong(TemporalField field) {
        return value.getLong(field);
    }

    /**
     * Delegate for {@link LocalTime#getNano()}
     *
     * @return
     */
    public int getNano() {
        return value.getNano();
    }

    /**
     * Checks if this instant is after the specified local time.
     *
     * @param other the other local time to compare to
     * @return true if this instant is after the specified local time.
     */
    public boolean isAfter(LocalTimeType<?> other) {
        return value.isAfter(other.value);
    }

    /**
     * Checks if this instant is before the specified local time.
     *
     * @param other the other local time to compare to
     * @return true if this instant is before the specified local time.
     */
    public boolean isBefore(LocalTimeType<?> other) {
        return value.isBefore(other.value);
    }

    /**
     * Delegate for {@link LocalTime#getHour()}
     * @return
     */
    public int getHour() {
        return value.getHour();
    }

    /**
     * Delegate for {@link LocalTime#getMinute()}
     * @return
     */
    public int getMinute() {
        return value.getMinute();
    }

    /**
     * Delegate for {@link LocalTime#getSecond()}
     * @return
     */
    public int getSecond() {
        return value.getSecond();
    }

    /**
     * Delegate for {@link LocalTime#format(DateTimeFormatter)}
     * @param formatter
     * @return
     */
    public String format(DateTimeFormatter formatter) {
        return value.format(formatter);
    }

    /**
     * Delegate for {@link LocalTime#toSecondOfDay()}
     * @return
     */
    public int toSecondOfDay() {
        return value.toSecondOfDay();
    }

    /**
     * Delegate for {@link LocalTime#toNanoOfDay()}
     * @return
     */
    public long toNanoOfDay() {
        return value.toNanoOfDay();
    }

    /**
     * Delegate for {@link LocalTime#toEpochSecond(LocalDate, ZoneOffset)}
     * @param date
     * @param offset
     * @return
     */
    public long toEpochSecond(LocalDate date, ZoneOffset offset) {
        return value.toEpochSecond(date, offset);
    }
}
