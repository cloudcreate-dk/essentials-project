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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.OrderId;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.Order;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AggregateSnapshotDeletionStrategyTest {
    private static List<AggregateSnapshot<OrderId, Order>> NO_EXISTING_SNAPSHOTS;
    private static List<AggregateSnapshot<OrderId, Order>> ONE_EXISTING_SNAPSHOT;
    private static List<AggregateSnapshot<OrderId, Order>> THREE_EXISTING_SNAPSHOTS;
    private static List<AggregateSnapshot<OrderId, Order>> FIVE_EXISTING_SNAPSHOTS;
    private static OrderId                                 ORDER_ID;

    @BeforeAll
    static void setupTestData() {
        ORDER_ID = OrderId.random();
        NO_EXISTING_SNAPSHOTS = List.of();
        ONE_EXISTING_SNAPSHOT = List.of(createOrderSnapshot(0));
        THREE_EXISTING_SNAPSHOTS = List.of(createOrderSnapshot(0),
                                           createOrderSnapshot(10),
                                           createOrderSnapshot(20));
        FIVE_EXISTING_SNAPSHOTS = List.of(createOrderSnapshot(0),
                                          createOrderSnapshot(10),
                                          createOrderSnapshot(20),
                                          createOrderSnapshot(30),
                                          createOrderSnapshot(40));
    }

    private static AggregateSnapshot<OrderId, Order> createOrderSnapshot(long eventOrder) {
        return new AggregateSnapshot<>(AggregateType.of("ORDERS"),
                                       ORDER_ID,
                                       Order.class,
                                       null,
                                       EventOrder.of(eventOrder));
    }

    @Test
    void test_keepALimitedNumberOfHistoricSnapshots() {
        var strategy = AggregateSnapshotDeletionStrategy.keepALimitedNumberOfHistoricSnapshots(3);
        assertThat(strategy.requiresExistingSnapshotDetailsToDetermineWhichAggregateSnapshotsToDelete()).isTrue();

        assertThat(strategy.resolveSnapshotsToDelete(NO_EXISTING_SNAPSHOTS)).isEmpty();
        assertThat(strategy.resolveSnapshotsToDelete(ONE_EXISTING_SNAPSHOT).collect(Collectors.toList())).isEqualTo(List.of());
        assertThat(strategy.resolveSnapshotsToDelete(THREE_EXISTING_SNAPSHOTS).collect(Collectors.toList())).isEqualTo(THREE_EXISTING_SNAPSHOTS.subList(0, 1));
        assertThat(strategy.resolveSnapshotsToDelete(FIVE_EXISTING_SNAPSHOTS).collect(Collectors.toList())).isEqualTo(FIVE_EXISTING_SNAPSHOTS.subList(0, 3));
    }

    @Test
    void test_keepAllHistoricSnapshots() {
        var strategy = AggregateSnapshotDeletionStrategy.keepAllHistoricSnapshots();
        assertThat(strategy.requiresExistingSnapshotDetailsToDetermineWhichAggregateSnapshotsToDelete()).isTrue();

        assertThat(strategy.resolveSnapshotsToDelete(NO_EXISTING_SNAPSHOTS)).isEmpty();
        assertThat(strategy.resolveSnapshotsToDelete(ONE_EXISTING_SNAPSHOT).collect(Collectors.toList())).isEqualTo(List.of());
        assertThat(strategy.resolveSnapshotsToDelete(THREE_EXISTING_SNAPSHOTS).collect(Collectors.toList())).isEqualTo(List.of());
        assertThat(strategy.resolveSnapshotsToDelete(FIVE_EXISTING_SNAPSHOTS).collect(Collectors.toList())).isEqualTo(List.of());
    }

    @Test
    void test_deleteAllHistoricSnapshots() {
        var strategy = AggregateSnapshotDeletionStrategy.deleteAllHistoricSnapshots();
        assertThat(strategy.requiresExistingSnapshotDetailsToDetermineWhichAggregateSnapshotsToDelete()).isFalse();

        assertThat(strategy.resolveSnapshotsToDelete(NO_EXISTING_SNAPSHOTS)).isEmpty();
        assertThat(strategy.resolveSnapshotsToDelete(ONE_EXISTING_SNAPSHOT).collect(Collectors.toList())).isEqualTo(ONE_EXISTING_SNAPSHOT);
        assertThat(strategy.resolveSnapshotsToDelete(THREE_EXISTING_SNAPSHOTS).collect(Collectors.toList())).isEqualTo(THREE_EXISTING_SNAPSHOTS);
        assertThat(strategy.resolveSnapshotsToDelete(FIVE_EXISTING_SNAPSHOTS).collect(Collectors.toList())).isEqualTo(FIVE_EXISTING_SNAPSHOTS);
    }

}