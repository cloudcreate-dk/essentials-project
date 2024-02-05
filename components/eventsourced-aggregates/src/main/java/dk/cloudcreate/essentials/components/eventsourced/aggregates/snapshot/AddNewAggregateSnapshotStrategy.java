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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateEventStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.collections.Lists;
import dk.cloudcreate.essentials.types.NumberType;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireTrue;

/**
 * Strategy for when an aggregate snapshot should be added
 */
public interface AddNewAggregateSnapshotStrategy {

    static AddNewAggregateSnapshotStrategy updateWhenBehindByNumberOfEvents(long numberOfEvents) {
        return new AddNewSnapshotWhenBehindByNumberOfEvents(numberOfEvents);
    }

    static AddNewAggregateSnapshotStrategy updateOnEachAggregateUpdate() {
        return new AddNewSnapshotWhenBehindByNumberOfEvents(1);
    }

    /**
     * Should a new aggregate snapshot be added based on
     *
     * @param aggregate                                        the aggregate that was just updated
     * @param persistedEvents                                  the events that was just persisted in relation to the aggregate
     * @param mostRecentlyStoredSnapshotLastIncludedEventOrder The most recent found {@link AggregateSnapshot#eventOrderOfLastIncludedEvent} for the given Aggregate instance (may be {@link Optional#empty()})
     * @return true if the aggregate snapshot should be added, otherwise false
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    <ID, AGGREGATE_IMPL_TYPE> boolean shouldANewAggregateSnapshotBeAdded(AGGREGATE_IMPL_TYPE aggregate,
                                                                         AggregateEventStream<ID> persistedEvents,
                                                                         Optional<EventOrder> mostRecentlyStoredSnapshotLastIncludedEventOrder);


    class AddNewSnapshotWhenBehindByNumberOfEvents implements AddNewAggregateSnapshotStrategy {
        private final long numberOfEventsBetweenAddingANewSnapshot;

        public AddNewSnapshotWhenBehindByNumberOfEvents(long numberOfEventsBetweenAddingANewSnapshot) {
            requireTrue(numberOfEventsBetweenAddingANewSnapshot >= 1, "numberOfEventsBetweenAddingANewSnapshot must be >= 1");
            this.numberOfEventsBetweenAddingANewSnapshot = numberOfEventsBetweenAddingANewSnapshot;
        }

        public long getNumberOfEventsBetweenAddingANewSnapshot() {
            return numberOfEventsBetweenAddingANewSnapshot;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AddNewSnapshotWhenBehindByNumberOfEvents that = (AddNewSnapshotWhenBehindByNumberOfEvents) o;
            return numberOfEventsBetweenAddingANewSnapshot == that.numberOfEventsBetweenAddingANewSnapshot;
        }

        @Override
        public int hashCode() {
            return Objects.hash(numberOfEventsBetweenAddingANewSnapshot);
        }

        @Override
        public String toString() {
            if (numberOfEventsBetweenAddingANewSnapshot == 1) {
                return "AddNewAggregateSnapshotOnEachAggregateUpdate";
            }
            return "AddNewSnapshotWhenBehindByNumberOfEvents(" +
                    +numberOfEventsBetweenAddingANewSnapshot +
                    ')';
        }

        @Override
        public <ID, AGGREGATE_IMPL_TYPE> boolean shouldANewAggregateSnapshotBeAdded(AGGREGATE_IMPL_TYPE aggregate,
                                                                                    AggregateEventStream<ID> persistedEvents,
                                                                                    Optional<EventOrder> mostRecentlyStoredSnapshotLastIncludedEventOrder) {

            return (Lists.last(persistedEvents.eventList()).get().eventOrder().longValue() - mostRecentlyStoredSnapshotLastIncludedEventOrder.map(NumberType::longValue).orElse(-1L) >= numberOfEventsBetweenAddingANewSnapshot);
        }
    }
}
