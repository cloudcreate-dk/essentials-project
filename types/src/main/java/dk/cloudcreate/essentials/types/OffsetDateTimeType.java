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
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalField;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Base class for all {@link SingleValueType}'s that encapsulate a {@link OffsetDateTime}.<br>
 * Example concrete implementation of the {@link OffsetDateTimeType}:
 * <pre>{@code
 * public class TransferTime extends OffsetDateTimeType<TransferTime> {
 *     public TransferTime(OffsetDateTime value) {
 *         super(value);
 *     }
 *
 *     public static TransferTime of(OffsetDateTime value) {
 *         return new TransferTime(value);
 *     }
 *
 *     public static TransferTime now() {
 *         return new TransferTime(OffsetDateTime.now());
 *     }
 * }
 * }</pre>
 *
 * @param <CONCRETE_TYPE> The concrete {@link OffsetDateTimeType} implementation
 */
public abstract class OffsetDateTimeType<CONCRETE_TYPE extends OffsetDateTimeType<CONCRETE_TYPE>> implements JSR310SingleValueType<OffsetDateTime, CONCRETE_TYPE> {
    private final OffsetDateTime value;

    public OffsetDateTimeType(OffsetDateTime value) {
        this.value = requireNonNull(value, "You must provide a value");
    }

    @Override
    public OffsetDateTime value() {
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
        var that = (OffsetDateTimeType<?>) o;
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
     * Delegate for {@link OffsetDateTime#get(TemporalField)}
     *
     * @param field
     * @return
     */
    public int get(TemporalField field) {
        return value.get(field);
    }

    /**
     * Delegate for {@link OffsetDateTime#getLong(TemporalField)}
     *
     * @param field
     * @return
     */
    public long getLong(TemporalField field) {
        return value.getLong(field);
    }

    /**
     * Delegate for {@link OffsetDateTime#getOffset()}
     * @return
     */
    public ZoneOffset getOffset() {
        return value.getOffset();
    }

    /**
     * Delegate for {@link OffsetDateTime#toLocalDateTime()}
     * @return
     */
    public LocalDateTime toLocalDateTime() {
        return value.toLocalDateTime();
    }

    /**
     * Delegate for {@link OffsetDateTime#getNano()}
     *
     * @return
     */
    public int getNano() {
        return value.getNano();
    }

    /**
     * Delegate for {@link OffsetDateTime#format(DateTimeFormatter)}
     * @param formatter
     * @return
     */
    public String format(DateTimeFormatter formatter) {
        return value.format(formatter);
    }

    /**
     * Delegate for {@link OffsetDateTime#toOffsetTime()}
     * @return
     */
    public OffsetTime toOffsetTime() {
        return value.toOffsetTime();
    }

    /**
     * Delegate for {@link OffsetDateTime#toZonedDateTime()}
     * @return
     */
    public ZonedDateTime toZonedDateTime() {
        return value.toZonedDateTime();
    }

    /**
     * Delegate for {@link OffsetDateTime#toInstant()}
     * @return
     */
    public Instant toInstant() {
        return value.toInstant();
    }

    /**
     * Delegate for {@link OffsetDateTime#toEpochSecond()}
     * @return
     */
    public long toEpochSecond() {
        return value.toEpochSecond();
    }

    /**
     * Checks if this instant is after the specified offset date time.
     *
     * @param other the other offset date time to compare to
     * @return true if this instant is after the specified offset date time.
     */
    public boolean isAfter(OffsetDateTimeType<?> other) {
        return value.isAfter(other.value);
    }

    /**
     * Checks if this instant is before the specified offset date time.
     *
     * @param other the other offset date time to compare to
     * @return true if this instant is before the specified offset date time.
     */
    public boolean isBefore(OffsetDateTimeType<?> other) {
        return value.isBefore(other.value);
    }

    /**
     * Delegate for {@link OffsetDateTime#toLocalDate()}
     * @return
     */
    public LocalDate toLocalDate() {
        return value.toLocalDate();
    }

    /**
     * Delegate for {@link OffsetDateTime#getYear()}
     * @return
     */
    public int getYear() {
        return value.getYear();
    }

    /**
     * Delegate for {@link OffsetDateTime#getMonthValue()}
     * @return
     */
    public int getMonthValue() {
        return value.getMonthValue();
    }

    /**
     * Delegate for {@link OffsetDateTime#getMonth()}
     * @return
     */
    public Month getMonth() {
        return value.getMonth();
    }

    /**
     * Delegate for {@link OffsetDateTime#getDayOfMonth()}
     * @return
     */
    public int getDayOfMonth() {
        return value.getDayOfMonth();
    }

    /**
     * Delegate for {@link OffsetDateTime#getDayOfYear()}
     * @return
     */
    public int getDayOfYear() {
        return value.getDayOfYear();
    }

    /**
     * Delegate for {@link OffsetDateTime#getDayOfWeek()}
     * @return
     */
    public DayOfWeek getDayOfWeek() {
        return value.getDayOfWeek();
    }

    /**
     * Delegate for {@link OffsetDateTime#toLocalTime()}
     * @return
     */
    public LocalTime toLocalTime() {
        return value.toLocalTime();
    }

    /**
     * Delegate for {@link OffsetDateTime#getHour()}
     * @return
     */
    public int getHour() {
        return value.getHour();
    }

    /**
     * Delegate for {@link OffsetDateTime#getMinute()}
     * @return
     */
    public int getMinute() {
        return value.getMinute();
    }

    /**
     * Delegate for {@link OffsetDateTime#getSecond()}
     * @return
     */
    public int getSecond() {
        return value.getSecond();
    }
}

