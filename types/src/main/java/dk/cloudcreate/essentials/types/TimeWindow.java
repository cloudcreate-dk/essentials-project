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

import java.time.Instant;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Represents a time window that covers a time period between two timestamps
 * ({@link #fromInclusive}/{@link #toExclusive}), where the <code>{@link #toExclusive}</code> is optional.<br>
 * If <code>toExclusive</code> is <code>null</code>, then the {@link TimeWindow} covers the time-period of <code>[{@link #fromInclusive};∞[</code>, otherwise
 * the {@link TimeWindow} covers the time-period: <code>[{@link #fromInclusive};{@link #toExclusive}[</code>
 */
public class TimeWindow {
    /**
     * The start of the time period that this {@link TimeWindow} covers. This timestamp is inclusive in the time window
     */
    public final Instant fromInclusive;
    /**
     * The optional end of the time period that this {@link TimeWindow} covers. This timestamp is exclusive in the time window
     */
    public final Instant toExclusive;

    /**
     * Create a new {@link TimeWindow} between two timestamps ({@link #fromInclusive}/{@link #toExclusive}), where the <code>{@link #toExclusive}</code> is optional.<br>
     * This creates a {@link TimeWindow} which covers the time-period: <code>[{@link #fromInclusive};{@link #toExclusive}[</code><br>
     * If <code>toExclusive</code> is <code>null</code>, then an open time-period is created: <code>[{@link #fromInclusive};∞[</code>
     *
     * @param fromInclusive the from and inclusive timestamp
     * @param toExclusive   the optional to and exclusive timestamp
     * @throws IllegalArgumentException if toExclusive is equal to or before fromInclusive
     */
    public TimeWindow(Instant fromInclusive, Instant toExclusive) {
        this.fromInclusive = requireNonNull(fromInclusive, "No fromInclusive value provided");
        this.toExclusive = toExclusive;
        if (fromInclusive.equals(toExclusive)) {
            throw new IllegalArgumentException(msg("fromInclusive '{}' is the SAME as toExclusive '{}'",
                                                   fromInclusive,
                                                   toExclusive));
        }
        if (toExclusive != null && fromInclusive.isAfter(toExclusive)) {
            throw new IllegalArgumentException(msg("fromInclusive '{}' is AFTER toExclusive '{}'",
                                                   fromInclusive,
                                                   toExclusive));
        }
    }

    /**
     * Create a new {@link TimeWindow} covering an open time-period: <code>[fromInclusive;∞[</code>
     *
     * @param fromInclusive the from and inclusive timestamp
     */
    public TimeWindow(Instant fromInclusive) {
        this.fromInclusive = requireNonNull(fromInclusive, "No fromInclusive value provided");
        this.toExclusive = null;
    }

    /**
     * Create a new {@link TimeWindow} between two timestamps, where the <code>toExclusive</code> is optional.<br>
     * This creates a {@link TimeWindow} which covers the time-period: <code>[fromInclusive;toExclusive[</code><br>
     * If <code>toExclusive</code> is <code>null</code>, then an open time-period is created: <code>[fromInclusive;∞[</code>
     *
     * @param fromInclusive the from and inclusive timestamp
     * @param toExclusive   the optional to and exclusive timestamp
     */
    public static TimeWindow between(Instant fromInclusive, Instant toExclusive) {
        return new TimeWindow(fromInclusive, toExclusive);
    }

    /**
     * Create a new {@link TimeWindow} covering an open time-period: <code>[fromInclusive;∞[</code>
     *
     * @param fromInclusive the from and inclusive timestamp
     */
    public static TimeWindow from(Instant fromInclusive) {
        return new TimeWindow(fromInclusive);
    }

    /**
     * Is the {@link TimeWindow} open, i.e. does it cover <code>[fromInclusive;∞[</code> (i.e. if {@link #toExclusive} is null)?<br>
     *
     * @return true if {@link #toExclusive} is null, otherwise false
     */
    public boolean isOpenTimeWindow() {
        return toExclusive == null;
    }

    /**
     * Is the {@link TimeWindow} closed, i.e. is {@link #toExclusive} is NOT null?<br>
     *
     * @return true if {@link #toExclusive} is NOT null, otherwise false
     */
    public boolean isClosedTimeWindow() {
        return toExclusive != null;
    }

    /**
     * Does the {@link TimeWindow} cover the specified timestamp?
     *
     * @param timestamp the timestamp we want to check if the {@link TimeWindow} covers.<br>
     *                  If the <code>timestamp</code> is null, then we return true if the {@link TimeWindow#isOpenTimeWindow()}, otherwise we return false.
     * @return true if the {@link TimeWindow} covers the timestamp, otherwise false<br>
     * If the <code>timestamp</code> is null, then we return true if the {@link TimeWindow#isOpenTimeWindow()}, otherwise we return false.
     */
    public boolean covers(Instant timestamp) {
        if (timestamp == null) {
            return isOpenTimeWindow();
        }

        if (timestamp.isBefore(fromInclusive)) {
            return false;
        }

        if (isOpenTimeWindow()) {
            return true;
        } else {
            return timestamp.isBefore(toExclusive);
        }
    }

    /**
     * The start of the time period that this {@link TimeWindow} covers. This timestamp is inclusive in the time window
     */
    Instant getFromInclusive() {
        return fromInclusive;
    }

    /**
     * The optional end of the time period that this {@link TimeWindow} covers. This timestamp is exclusive in the time window
     */
    public Instant getToExclusive() {
        return toExclusive;
    }

    /**
     * Close an open or closed {@link TimeWindow} (see {@link #isClosedTimeWindow()}/{@link #isOpenTimeWindow()})
     *
     * @param toExclusive the new {@link #toExclusive} timestamp
     * @return a new {@link TimeWindow} instance with {@link #fromInclusive} from this {@link TimeWindow} and
     * {@link #toExclusive} from the <code>toExclusive</code> parameter
     * @throws IllegalArgumentException if toExclusive is equal to or before fromInclusive
     */
    public TimeWindow close(Instant toExclusive) {
        return TimeWindow.between(fromInclusive, toExclusive);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeWindow that = (TimeWindow) o;
        return fromInclusive.equals(that.fromInclusive) && Objects.equals(toExclusive, that.toExclusive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromInclusive, toExclusive);
    }

    @Override
    public String toString() {
        return "TimeWindow{" +
                "fromInclusive=" + fromInclusive +
                ", toExclusive=" + toExclusive +
                '}';
    }
}
