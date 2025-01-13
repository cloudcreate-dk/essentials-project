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


import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LongRangeTest {

    @Test
    void test_empty_range() {
        var range = LongRange.EMPTY_RANGE;
        assertThat(range.isClosedRange()).isTrue();
        assertThat(range.isOpenRange()).isFalse();
        assertThat(range.covers(-1)).isFalse();
        assertThat(range.covers(0)).isTrue();
        assertThat(range.covers(1)).isFalse();
    }
    @Test
    void test_between() {
        var range = LongRange.between(-10, 10);

        assertThat(range.isClosedRange()).isTrue();
        assertThat(range.isOpenRange()).isFalse();
        assertThat(range.covers(-11)).isFalse();
        assertThat(range.covers(-10)).isTrue();
        assertThat(range.covers(0)).isTrue();
        assertThat(range.covers(10)).isTrue();
        assertThat(range.covers(11)).isFalse();
    }

    @Test
    void test_from_open_range() {
        var range = LongRange.from(0);

        assertThat(range.isClosedRange()).isFalse();
        assertThat(range.isOpenRange()).isTrue();
        assertThat(range.covers(-1)).isFalse();
        assertThat(range.covers(0)).isTrue();
        assertThat(range.covers(10)).isTrue();
        assertThat(range.covers(Long.MAX_VALUE)).isTrue();
    }

    @Test
    void test_from_closed_range() {
        var range = LongRange.from(1, 10);

        assertThat(range.isClosedRange()).isTrue();
        assertThat(range.isOpenRange()).isFalse();
        assertThat(range.covers(0)).isFalse();
        assertThat(range.covers(1)).isTrue();
        assertThat(range.covers(10)).isTrue();
        assertThat(range.covers(11)).isFalse();
        assertThat(range.covers(Long.MAX_VALUE)).isFalse();
    }


    @Test
    void test_only() {
        var range = LongRange.only(1);

        assertThat(range.isClosedRange()).isTrue();
        assertThat(range.isOpenRange()).isFalse();
        assertThat(range.covers(-1)).isFalse();
        assertThat(range.covers(0)).isFalse();
        assertThat(range.covers(1)).isTrue();
        assertThat(range.covers(2)).isFalse();
    }

    @Test
    void closed_range_stream() {
        var stream = LongRange.from(1, 5).stream();
        assertThat(stream.boxed().collect(Collectors.toList()))
                .isEqualTo(List.of(1L, 2L, 3L, 4L, 5L));
    }

    @Test
    void open_range_stream() {
        var stream = LongRange.from(1).stream();
        assertThat(stream.limit(10).boxed().collect(Collectors.toList()))
                .isEqualTo(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
    }
}