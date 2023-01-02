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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.OrderId;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.Order;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.types.EventId;
import dk.cloudcreate.essentials.types.LongRange;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AddNewAggregateSnapshotStrategyTest {

    private static OrderId ORDER_ID;
    private static Order   AGGREGATE;


    @BeforeAll
    static void setupTestData() {
        ORDER_ID = OrderId.random();
        AGGREGATE = new Order(ORDER_ID); //Not used actively by any strategies at the moment
    }


    @Test
    void test_updateWhenBehindByNumberOfEvents_with_no_previous_snapshots() {
        var strategy = AddNewAggregateSnapshotStrategy.updateWhenBehindByNumberOfEvents(2);

        var testData = new TestEventStreams(0);

        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.onePersistedEvent,
                                                               Optional.empty())).isFalse();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.twoPersistedEvents,
                                                               Optional.empty())).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.threePersistedEvents,
                                                               Optional.empty())).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fourPersistedEvents,
                                                               Optional.empty())).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fivePersistedEvents,
                                                               Optional.empty())).isTrue();
    }

    @Test
    void test_updateWhenBehindByNumberOfEvents_with_a_previous_persisted_snapshot() {
        var strategy = AddNewAggregateSnapshotStrategy.updateWhenBehindByNumberOfEvents(2);

        var testData = new TestEventStreams(1);

        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.onePersistedEvent,
                                                               Optional.of(EventOrder.of(0)))).isFalse();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.twoPersistedEvents,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.threePersistedEvents,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fourPersistedEvents,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fivePersistedEvents,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
    }

    @Test
    void test_updateWhenBehindByNumberOfEvents_with_larger_gap_and_previous_persisted_snapshot() {
        var strategy = AddNewAggregateSnapshotStrategy.updateWhenBehindByNumberOfEvents(4);

        var testData = new TestEventStreams(5);

        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.onePersistedEvent,
                                                               Optional.of(EventOrder.of(4)))).isFalse();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.twoPersistedEvents,
                                                               Optional.of(EventOrder.of(4)))).isFalse();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.threePersistedEvents,
                                                               Optional.of(EventOrder.of(4)))).isFalse();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fourPersistedEvents,
                                                               Optional.of(EventOrder.of(4)))).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fivePersistedEvents,
                                                               Optional.of(EventOrder.of(4)))).isTrue();
    }

    @Test
    void test_updateOnEachAggregateUpdate_with_no_previous_snapshots() {
        var strategy = AddNewAggregateSnapshotStrategy.updateOnEachAggregateUpdate();

        var testData = new TestEventStreams(0);

        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.onePersistedEvent,
                                                               Optional.empty())).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.twoPersistedEvents,
                                                               Optional.empty())).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.threePersistedEvents,
                                                               Optional.empty())).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fourPersistedEvents,
                                                               Optional.empty())).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fivePersistedEvents,
                                                               Optional.empty())).isTrue();
    }

    @Test
    void test_updateOnEachAggregateUpdate_with_a_previous_persisted_snapshot() {
        var strategy = AddNewAggregateSnapshotStrategy.updateOnEachAggregateUpdate();

        var testData = new TestEventStreams(1);

        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.onePersistedEvent,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.twoPersistedEvents,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.threePersistedEvents,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fourPersistedEvents,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
        assertThat(strategy.shouldANewAggregateSnapshotBeAdded(AGGREGATE,
                                                               testData.fivePersistedEvents,
                                                               Optional.of(EventOrder.of(0)))).isTrue();
    }


    static class TestEventStreams {
        private final AggregateEventStream<OrderId> onePersistedEvent;
        private final AggregateEventStream<OrderId> twoPersistedEvents;
        private final AggregateEventStream<OrderId> threePersistedEvents;
        private final AggregateEventStream<OrderId> fourPersistedEvents;
        private final AggregateEventStream<OrderId> fivePersistedEvents;

        public TestEventStreams(long fromAndIncludingEventOrder) {
            onePersistedEvent = createAggregateStream(LongRange.only(fromAndIncludingEventOrder),
                                                      Stream.of(createPersistedEvent(fromAndIncludingEventOrder)));
            twoPersistedEvents = createAggregateStream(LongRange.between(fromAndIncludingEventOrder, fromAndIncludingEventOrder + 1),
                                                       Stream.of(createPersistedEvent(fromAndIncludingEventOrder),
                                                                 createPersistedEvent(fromAndIncludingEventOrder + 1)));
            threePersistedEvents = createAggregateStream(LongRange.between(fromAndIncludingEventOrder, fromAndIncludingEventOrder + 2),
                                                         Stream.of(createPersistedEvent(fromAndIncludingEventOrder),
                                                                   createPersistedEvent(fromAndIncludingEventOrder + 1),
                                                                   createPersistedEvent(fromAndIncludingEventOrder + 2)));
            fourPersistedEvents = createAggregateStream(LongRange.between(fromAndIncludingEventOrder, fromAndIncludingEventOrder + 3),
                                                        Stream.of(createPersistedEvent(fromAndIncludingEventOrder),
                                                                  createPersistedEvent(fromAndIncludingEventOrder + 1),
                                                                  createPersistedEvent(fromAndIncludingEventOrder + 2),
                                                                  createPersistedEvent(fromAndIncludingEventOrder + 3)));
            fivePersistedEvents = createAggregateStream(LongRange.between(fromAndIncludingEventOrder, fromAndIncludingEventOrder + 4),
                                                        Stream.of(createPersistedEvent(fromAndIncludingEventOrder),
                                                                  createPersistedEvent(fromAndIncludingEventOrder + 1),
                                                                  createPersistedEvent(fromAndIncludingEventOrder + 2),
                                                                  createPersistedEvent(fromAndIncludingEventOrder + 3),
                                                                  createPersistedEvent(fromAndIncludingEventOrder + 4)));
        }


        private AggregateEventStream<OrderId> createAggregateStream(LongRange eventOrderRangeIncluded,
                                                                    Stream<PersistedEvent> persistedEvents) {
            return AggregateEventStream.of(mock(AggregateEventStreamConfiguration.class),
                                           ORDER_ID,
                                           eventOrderRangeIncluded,
                                           persistedEvents);
        }

        private PersistedEvent createPersistedEvent(long eventOrder) {

            return PersistedEvent.from(EventId.random(),
                                       AggregateType.of("ORDERS"),
                                       ORDER_ID,
                                       mock(EventJSON.class),
                                       EventOrder.of(eventOrder),
                                       EventRevision.of(1),
                                       GlobalEventOrder.of(100 + eventOrder),
                                       mock(EventMetaDataJSON.class),
                                       OffsetDateTime.now(Clock.systemUTC()),
                                       Optional.empty(),
                                       Optional.empty(),
                                       Optional.empty());
        }
    }
}