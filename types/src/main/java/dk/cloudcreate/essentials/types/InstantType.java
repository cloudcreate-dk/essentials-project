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

import java.time.Instant;
import java.time.temporal.TemporalField;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Base class for all {@link SingleValueType}'s that encapsulate a {@link Instant}.<br>
 * Example concrete implementation of the {@link InstantType}:
 * <pre>{@code
 * public class LastUpdated extends InstantType<LastUpdated> {
 *     public LastUpdated(Instant value) {
 *         super(value);
 *     }
 *
 *     public static LastUpdated of(Instant value) {
 *         return new LastUpdated(value);
 *     }
 *
 *     public static LastUpdated now() {
 *         return new LastUpdated(Instant.now());
 *     }
 * }
 * }</pre>
 *
 * @param <CONCRETE_TYPE> The concrete {@link InstantType} implementation
 */
public abstract class InstantType<CONCRETE_TYPE extends InstantType<CONCRETE_TYPE>> implements JSR310SingleValueType<Instant, CONCRETE_TYPE> {
    private final Instant value;

    public InstantType(Instant value) {
        this.value = requireNonNull(value, "You must provide a value");
    }

    @Override
    public Instant value() {
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
        var that = (InstantType<?>) o;
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
     * Delegate for {@link Instant#get(TemporalField)}
     *
     * @param field
     * @return
     */
    public int get(TemporalField field) {
        return value.get(field);
    }

    /**
     * Delegate for {@link Instant#getLong(TemporalField)}
     *
     * @param field
     * @return
     */
    public long getLong(TemporalField field) {
        return value.getLong(field);
    }

    /**
     * Delegate for {@link Instant#getEpochSecond()}
     *
     * @return
     */
    public long getEpochSecond() {
        return value.getEpochSecond();
    }

    /**
     * Delegate for {@link Instant#getNano()}
     *
     * @return
     */
    public int getNano() {
        return value.getNano();
    }

    /**
     * Delegate for {@link Instant#toEpochMilli()}
     *
     * @return
     */
    public long toEpochMilli() {
        return value.toEpochMilli();
    }

    /**
     * Checks if this instant is after the specified instant.
     *
     * @param other the other instant to compare to
     * @return true if this instant is after the specified instant
     */
    public boolean isAfter(InstantType<?> other) {
        return value.isAfter(other.value);
    }

    /**
     * Checks if this instant is before the specified instant.
     *
     * @param other the other instant to compare to
     * @return true if this instant is before the specified instant
     */
    public boolean isBefore(InstantType<?> other) {
        return value.isBefore(other.value);
    }


}
