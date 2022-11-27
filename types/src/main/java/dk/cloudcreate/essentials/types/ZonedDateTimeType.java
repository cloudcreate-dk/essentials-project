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
 * Base class for all {@link SingleValueType}'s that encapsulate a {@link ZonedDateTime}.<br>
 * Example concrete implementation of the {@link ZonedDateTimeType}:
 * <pre>{@code
 * public class TransactionTime extends ZonedDateTimeType<TransactionTime> {
 *     public TransactionTime(ZonedDateTime value) {
 *         super(value);
 *     }
 *
 *     public static TransactionTime of(ZonedDateTime value) {
 *         return new TransactionTime(value);
 *     }
 *
 *     public static TransactionTime now() {
 *         return new TransactionTime(ZonedDateTime.now());
 *     }
 * }
 * }</pre>
 *
 * @param <CONCRETE_TYPE> The concrete {@link ZonedDateTimeType} implementation
 */
public abstract class ZonedDateTimeType<CONCRETE_TYPE extends ZonedDateTimeType<CONCRETE_TYPE>> implements SingleValueType<ZonedDateTime, CONCRETE_TYPE> {
    private final ZonedDateTime value;

    public ZonedDateTimeType(ZonedDateTime value) {
        this.value = requireNonNull(value, "You must provide a value");
    }

    @Override
    public ZonedDateTime value() {
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
        var that = (ZonedDateTimeType<?>) o;
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
     * Delegate for {@link ZonedDateTime#get(TemporalField)}
     *
     * @param field
     * @return
     */
    public int get(TemporalField field) {
        return value.get(field);
    }

    /**
     * Delegate for {@link ZonedDateTime#getLong(TemporalField)}
     *
     * @param field
     * @return
     */
    public long getLong(TemporalField field) {
        return value.getLong(field);
    }

    /**
     * Delegate for {@link ZonedDateTime#getOffset()}
     * @return
     */
    public ZoneOffset getOffset() {
        return value.getOffset();
    }

    /**
     * Delegate for {@link ZonedDateTime#getZone()}
     * @return
     */
    public ZoneId getZone() {
        return value.getZone();
    }

    /**
     * Delegate for {@link ZonedDateTime#toLocalDateTime()}
     * @return
     */
    public LocalDateTime toLocalDateTime() {
        return value.toLocalDateTime();
    }

    /**
     * Delegate for {@link ZonedDateTime#getNano()}
     *
     * @return
     */
    public int getNano() {
        return value.getNano();
    }

    /**
     * Delegate for {@link ZonedDateTime#format(DateTimeFormatter)}
     * @param formatter
     * @return
     */
    public String format(DateTimeFormatter formatter) {
        return value.format(formatter);
    }

    /**
     * Delegate for {@link ZonedDateTime#toOffsetDateTime()}
     * @return
     */
    public OffsetDateTime toOffsetDateTime() {
        return value.toOffsetDateTime();
    }

    /**
     * Delegate for {@link ZonedDateTime#toInstant()}
     * @return
     */
    public Instant toInstant() {
        return value.toInstant();
    }

    /**
     * Delegate for {@link ZonedDateTime#toEpochSecond()}
     * @return
     */
    public long toEpochSecond() {
        return value.toEpochSecond();
    }

    /**
     * Checks if this instant is after the specified zoned date time.
     *
     * @param other the other zoned date time to compare to
     * @return true if this instant is after the specified zoned date time.
     */
    public boolean isAfter(ZonedDateTimeType<?> other) {
        return value.isAfter(other.value);
    }

    /**
     * Checks if this instant is before the specified zoned date time.
     *
     * @param other the other zoned date time to compare to
     * @return true if this instant is before the specified zoned date time.
     */
    public boolean isBefore(ZonedDateTimeType<?> other) {
        return value.isBefore(other.value);
    }

    /**
     * Delegate for {@link ZonedDateTime#toLocalDate()}
     * @return
     */
    public LocalDate toLocalDate() {
        return value.toLocalDate();
    }

    /**
     * Delegate for {@link ZonedDateTime#getYear()}
     * @return
     */
    public int getYear() {
        return value.getYear();
    }

    /**
     * Delegate for {@link ZonedDateTime#getMonthValue()}
     * @return
     */
    public int getMonthValue() {
        return value.getMonthValue();
    }

    /**
     * Delegate for {@link ZonedDateTime#getMonth()}
     * @return
     */
    public Month getMonth() {
        return value.getMonth();
    }

    /**
     * Delegate for {@link ZonedDateTime#getDayOfMonth()}
     * @return
     */
    public int getDayOfMonth() {
        return value.getDayOfMonth();
    }

    /**
     * Delegate for {@link ZonedDateTime#getDayOfYear()}
     * @return
     */
    public int getDayOfYear() {
        return value.getDayOfYear();
    }

    /**
     * Delegate for {@link ZonedDateTime#getDayOfWeek()}
     * @return
     */
    public DayOfWeek getDayOfWeek() {
        return value.getDayOfWeek();
    }

    /**
     * Delegate for {@link ZonedDateTime#toLocalTime()}
     * @return
     */
    public LocalTime toLocalTime() {
        return value.toLocalTime();
    }

    /**
     * Delegate for {@link ZonedDateTime#getHour()}
     * @return
     */
    public int getHour() {
        return value.getHour();
    }

    /**
     * Delegate for {@link ZonedDateTime#getMinute()}
     * @return
     */
    public int getMinute() {
        return value.getMinute();
    }

    /**
     * Delegate for {@link ZonedDateTime#getSecond()}
     * @return
     */
    public int getSecond() {
        return value.getSecond();
    }

}
