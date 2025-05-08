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

package dk.trustworks.essentials.types;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class TimeWindowTest {
    @Test
    void create_an_open_TimeWindow() {
        var now        = Instant.now();
        var timeWindow = TimeWindow.from(now);

        assertThat(timeWindow.isOpenTimeWindow()).isTrue();
        assertThat(timeWindow.isClosedTimeWindow()).isFalse();
        assertThat(timeWindow.fromInclusive).isEqualTo(now);
        assertThat(timeWindow.toExclusive).isNull();

        assertThat(timeWindow.covers(now.minusNanos(1))).isFalse();
        assertThat(timeWindow.covers(now)).isTrue();
        assertThat(timeWindow.covers(now.plusNanos(1))).isTrue();
        assertThat(timeWindow.covers(null)).isTrue();
    }

    @Test
    void test_open_TimeWindow_equals_and_hashcode() {
        var now         = Instant.now();
        var timeWindow1 = TimeWindow.from(now);
        var timeWindow2 = TimeWindow.from(now);

        assertThat(timeWindow1).isEqualTo(timeWindow2);
        assertThat(timeWindow1.hashCode()).isEqualTo(timeWindow2.hashCode());
    }

    @Test
    void create_a_closed_TimeWindow() {
        var from        = Instant.now();
        var toExclusive = from.plusSeconds(100);
        var timeWindow  = TimeWindow.between(from, toExclusive);

        assertThat(timeWindow.isClosedTimeWindow()).isTrue();
        assertThat(timeWindow.isOpenTimeWindow()).isFalse();
        assertThat(timeWindow.fromInclusive).isEqualTo(from);
        assertThat(timeWindow.toExclusive).isEqualTo(toExclusive);

        // From
        assertThat(timeWindow.covers(from.minusNanos(1))).isFalse();
        assertThat(timeWindow.covers(from)).isTrue();
        assertThat(timeWindow.covers(from.plusNanos(1))).isTrue();

        // To
        assertThat(timeWindow.covers(toExclusive.minusNanos(1))).isTrue();
        assertThat(timeWindow.covers(toExclusive)).isFalse();
        assertThat(timeWindow.covers(toExclusive.plusNanos(1))).isFalse();
        assertThat(timeWindow.covers(null)).isFalse();
    }

    @Test
    void test_closed_TimeWindow_equals_and_hashcode() {
        var from        = Instant.now();
        var toExclusive = from.plusSeconds(100);
        var timeWindow1 = TimeWindow.between(from, toExclusive);
        var timeWindow2 = TimeWindow.between(from, toExclusive);

        assertThat(timeWindow1).isEqualTo(timeWindow2);
        assertThat(timeWindow1.hashCode()).isEqualTo(timeWindow2.hashCode());
    }

    @Test
    void close_an_open_TimeWindow() {
        var from           = Instant.now();
        var openTimeWindow = TimeWindow.from(from);

        var toExclusive      = from.plusSeconds(10);
        var closedTimeWindow = openTimeWindow.close(toExclusive);

        assertThat(closedTimeWindow.isClosedTimeWindow()).isTrue();
        assertThat(closedTimeWindow.isOpenTimeWindow()).isFalse();
        assertThat(closedTimeWindow.fromInclusive).isEqualTo(from);
        assertThat(closedTimeWindow.toExclusive).isEqualTo(toExclusive);

        // From
        assertThat(closedTimeWindow.covers(from.minusNanos(1))).isFalse();
        assertThat(closedTimeWindow.covers(from)).isTrue();
        assertThat(closedTimeWindow.covers(from.plusNanos(1))).isTrue();

        // To
        assertThat(closedTimeWindow.covers(toExclusive.minusNanos(1))).isTrue();
        assertThat(closedTimeWindow.covers(toExclusive)).isFalse();
        assertThat(closedTimeWindow.covers(toExclusive.plusNanos(1))).isFalse();
        assertThat(closedTimeWindow.covers(null)).isFalse();

        // equals
        assertThat(closedTimeWindow).isNotEqualTo(openTimeWindow);
    }

    @Test
    void close_an_open_TimeWindow_with_null_toExclusive_return_an_identical_TimeWindow() {
        var from           = Instant.now();
        var openTimeWindow = TimeWindow.from(from);

        var toExclusive         = from.plusSeconds(10);
        var stillOpenTimeWindow = openTimeWindow.close(null);

        assertThat(stillOpenTimeWindow.isOpenTimeWindow()).isTrue();
        assertThat(stillOpenTimeWindow.isClosedTimeWindow()).isFalse();
        assertThat(stillOpenTimeWindow).isEqualTo(openTimeWindow);
    }

    @Test
    void close_an_already_closed_TimeWindow() {
        var from                    = Instant.now();
        var toExclusive             = from.plusSeconds(100);
        var alreadyClosedTimeWindow = TimeWindow.between(from, toExclusive);

        var nextToExclusive  = toExclusive.plusSeconds(10);
        var closedTimeWindow = alreadyClosedTimeWindow.close(nextToExclusive);

        assertThat(closedTimeWindow.isClosedTimeWindow()).isTrue();
        assertThat(closedTimeWindow.isOpenTimeWindow()).isFalse();
        assertThat(closedTimeWindow.fromInclusive).isEqualTo(from);
        assertThat(closedTimeWindow.toExclusive).isEqualTo(nextToExclusive);

        // From
        assertThat(closedTimeWindow.covers(from.minusNanos(1))).isFalse();
        assertThat(closedTimeWindow.covers(from)).isTrue();
        assertThat(closedTimeWindow.covers(from.plusNanos(1))).isTrue();

        // To
        assertThat(closedTimeWindow.covers(nextToExclusive.minusNanos(1))).isTrue();
        assertThat(closedTimeWindow.covers(nextToExclusive)).isFalse();
        assertThat(closedTimeWindow.covers(nextToExclusive.plusNanos(1))).isFalse();
        assertThat(closedTimeWindow.covers(null)).isFalse();

        // equals
        assertThat(closedTimeWindow).isNotEqualTo(alreadyClosedTimeWindow);
    }

    @Test
    void create_a_closed_TimeWindow_where_toExclusive_is_invalid() {
        var from = Instant.now();

        // from and to is the same
        assertThatThrownBy(() -> TimeWindow.between(from, from))
                .isInstanceOf(IllegalArgumentException.class);

        // to is before from
        assertThatThrownBy(() -> TimeWindow.between(from, from.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}