/*
 * Copyright 2021-2023 the original author or authors.
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

import dk.cloudcreate.essentials.types.dates.*;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

public class DateTest {
    @Test
    void Created_LocalDateTimeType() {
        var now          = LocalDateTime.now();
        var created      = Created.of(now);
        var createdLater = Created.of(now.plusSeconds(1));

        // Value
        assertThat(created.value()).isEqualTo(now);

        // Equals
        assertThat(created).isEqualTo(Created.of(now));
        assertThat(created).isNotEqualTo(createdLater);

        // HashCode
        assertThat(created.hashCode()).isEqualTo(Created.of(now).hashCode());
        assertThat(created.hashCode()).isNotEqualTo(createdLater.hashCode());

        // ToString
        assertThat(created.toString()).isEqualTo(now.toString());

        // Comparable
        assertThat(created.compareTo(Created.of(now))).isZero();
        assertThat(created.compareTo(createdLater)).isLessThan(0);
        assertThat(createdLater.compareTo(created)).isGreaterThan(0);
    }

    @Test
    void DueDate_LocalDateType() {
        var now          = LocalDate.now();
        var dueDate      = DueDate.of(now);
        var dueDateLater = DueDate.of(now.plusDays(1));

        // Value
        assertThat(dueDate.value()).isEqualTo(now);

        // Equals
        assertThat(dueDate).isEqualTo(DueDate.of(now));
        assertThat(dueDate).isNotEqualTo(dueDateLater);

        // HashCode
        assertThat(dueDate.hashCode()).isEqualTo(DueDate.of(now).hashCode());
        assertThat(dueDate.hashCode()).isNotEqualTo(dueDateLater.hashCode());

        // ToString
        assertThat(dueDate.toString()).isEqualTo(now.toString());

        // Comparable
        assertThat(dueDate.compareTo(DueDate.of(now))).isZero();
        assertThat(dueDate.compareTo(dueDateLater)).isLessThan(0);
        assertThat(dueDateLater.compareTo(dueDate)).isGreaterThan(0);
    }

    @Test
    void LastUpdated_InstantType() {
        var now              = Instant.now();
        var lastUpdated      = LastUpdated.of(now);
        var lastUpdatedLater = LastUpdated.of(now.plusSeconds(1));

        // Value
        assertThat(lastUpdated.value()).isEqualTo(now);

        // Equals
        assertThat(lastUpdated).isEqualTo(LastUpdated.of(now));
        assertThat(lastUpdated).isNotEqualTo(lastUpdatedLater);

        // HashCode
        assertThat(lastUpdated.hashCode()).isEqualTo(LastUpdated.of(now).hashCode());
        assertThat(lastUpdated.hashCode()).isNotEqualTo(lastUpdatedLater.hashCode());

        // ToString
        assertThat(lastUpdated.toString()).isEqualTo(now.toString());

        // Comparable
        assertThat(lastUpdated.compareTo(LastUpdated.of(now))).isZero();
        assertThat(lastUpdated.compareTo(lastUpdatedLater)).isLessThan(0);
        assertThat(lastUpdatedLater.compareTo(lastUpdated)).isGreaterThan(0);
    }

    @Test
    void TimeOfDay_LocalTimeType() {
        var now            = LocalTime.now();
        var timeOfDay      = TimeOfDay.of(now);
        var timeOfDayLater = TimeOfDay.of(now.plusSeconds(1));

        // Value
        assertThat(timeOfDay.value()).isEqualTo(now);

        // Equals
        assertThat(timeOfDay).isEqualTo(TimeOfDay.of(now));
        assertThat(timeOfDay).isNotEqualTo(timeOfDayLater);

        // HashCode
        assertThat(timeOfDay.hashCode()).isEqualTo(TimeOfDay.of(now).hashCode());
        assertThat(timeOfDay.hashCode()).isNotEqualTo(timeOfDayLater.hashCode());

        // ToString
        assertThat(timeOfDay.toString()).isEqualTo(now.toString());

        // Comparable
        assertThat(timeOfDay.compareTo(TimeOfDay.of(now))).isZero();
        assertThat(timeOfDay.compareTo(timeOfDayLater)).isLessThan(0);
        assertThat(timeOfDayLater.compareTo(timeOfDay)).isGreaterThan(0);
    }

    @Test
    void TransactionTime_ZonedDateTimeType() {
        var now                  = ZonedDateTime.now();
        var transactionTime      = TransactionTime.of(now);
        var transactionTimeLater = TransactionTime.of(now.plusSeconds(1));

        // Value
        assertThat(transactionTime.value()).isEqualTo(now);

        // Equals
        assertThat(transactionTime).isEqualTo(TransactionTime.of(now));
        assertThat(transactionTime).isNotEqualTo(transactionTimeLater);

        // HashCode
        assertThat(transactionTime.hashCode()).isEqualTo(TransactionTime.of(now).hashCode());
        assertThat(transactionTime.hashCode()).isNotEqualTo(transactionTimeLater.hashCode());

        // ToString
        assertThat(transactionTime.toString()).isEqualTo(now.toString());

        // Comparable
        assertThat(transactionTime.compareTo(TransactionTime.of(now))).isZero();
        assertThat(transactionTime.compareTo(transactionTimeLater)).isLessThan(0);
        assertThat(transactionTimeLater.compareTo(transactionTime)).isGreaterThan(0);
    }

    @Test
    void TransferTime_OffsetDateTimeType() {
        var now               = OffsetDateTime.now();
        var transferTime      = TransferTime.of(now);
        var transferTimeLater = TransferTime.of(now.plusSeconds(1));

        // Value
        assertThat(transferTime.value()).isEqualTo(now);

        // Equals
        assertThat(transferTime).isEqualTo(TransferTime.of(now));
        assertThat(transferTime).isNotEqualTo(transferTimeLater);

        // HashCode
        assertThat(transferTime.hashCode()).isEqualTo(TransferTime.of(now).hashCode());
        assertThat(transferTime.hashCode()).isNotEqualTo(transferTimeLater.hashCode());

        // ToString
        assertThat(transferTime.toString()).isEqualTo(now.toString());

        // Comparable
        assertThat(transferTime.compareTo(TransferTime.of(now))).isZero();
        assertThat(transferTime.compareTo(transferTimeLater)).isLessThan(0);
        assertThat(transferTimeLater.compareTo(transferTime)).isGreaterThan(0);
    }
}
