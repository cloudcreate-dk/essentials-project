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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.gap;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamConfiguration;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.trustworks.essentials.components.foundation.types.SubscriberId;
import dk.trustworks.essentials.types.LongRange;

import java.util.List;
import java.util.stream.Stream;

/**
 * {@link EventStreamGapHandler} that doesn't track or manage gaps
 * @param <CONFIG> The concrete {@link AggregateEventStreamConfiguration
 */
public final class NoEventStreamGapHandler<CONFIG extends AggregateEventStreamConfiguration> implements EventStreamGapHandler<CONFIG> {
    @Override
    public SubscriptionGapHandler gapHandlerFor(SubscriberId subscriberId) {
        return new NoSubscriptionGapHandler(subscriberId);
    }

    @Override
    public List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType) {
        return List.of();
    }

    @Override
    public List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType, LongRange resetForThisSpecificGlobalEventOrdersRange) {
        return List.of();
    }

    @Override
    public List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType, List<GlobalEventOrder> resetForTheseSpecificGlobalEventOrders) {
        return List.of();
    }

    @Override
    public Stream<GlobalEventOrder> getPermanentGapsFor(AggregateType aggregateType) {
        return Stream.empty();
    }

    private class NoSubscriptionGapHandler implements SubscriptionGapHandler {
        private SubscriberId subscriberId;

        public NoSubscriptionGapHandler(SubscriberId subscriberId) {
            this.subscriberId = subscriberId;
        }

        @Override
        public SubscriberId subscriberId() {
            return subscriberId;
        }

        @Override
        public List<GlobalEventOrder> findTransientGapsToIncludeInQuery(AggregateType aggregateType, LongRange globalOrderQueryRange) {
            return List.of();
        }

        @Override
        public void reconcileGaps(AggregateType aggregateType, LongRange globalOrderQueryRange, List<PersistedEvent> persistedEvents, List<GlobalEventOrder> transientGapsIncludedInQuery) {

        }

        @Override
        public List<GlobalEventOrder> resetTransientGapsFor(AggregateType aggregateType) {
            return List.of();
        }

        @Override
        public List<GlobalEventOrder> getTransientGapsFor(AggregateType aggregateType) {
            return List.of();
        }

        @Override
        public Stream<GlobalEventOrder> getPermanentGapsFor(AggregateType aggregateType) {
            return Stream.empty();
        }
    }
}
