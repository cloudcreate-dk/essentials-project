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

package dk.trustworks.essentials.components.eventsourced.aggregates.stateful;

import dk.trustworks.essentials.components.eventsourced.aggregates.Aggregate;
import dk.trustworks.essentials.components.eventsourced.aggregates.snapshot.AggregateSnapshotRepository;
import dk.trustworks.essentials.components.eventsourced.aggregates.stateful.classic.AggregateRoot;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.types.LongRange;

import java.util.Optional;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;
import static dk.trustworks.essentials.shared.MessageFormatter.msg;

/**
 * {@link Aggregate} specific {@link InMemoryProjector}<br>
 * Note: An in memory projection is never associated with a {@link UnitOfWork} and any changes to the aggregate
 * won't automatically be persisted. Use the {@link StatefulAggregateRepository} for transactional usage.
 */
public class StatefulAggregateInMemoryProjector implements InMemoryProjector {
    private final StatefulAggregateInstanceFactory      aggregateRootInstanceFactory;
    private final Optional<AggregateSnapshotRepository> aggregateSnapshotRepository;

    public StatefulAggregateInMemoryProjector(StatefulAggregateInstanceFactory aggregateRootInstanceFactory) {
        this(aggregateRootInstanceFactory,
             null);
    }

    /**
     * @param aggregateRootInstanceFactory factory for creating aggregate root instances
     * @param aggregateSnapshotRepository  optional (may be null) {@link AggregateSnapshotRepository}
     */
    public StatefulAggregateInMemoryProjector(StatefulAggregateInstanceFactory aggregateRootInstanceFactory,
                                              AggregateSnapshotRepository aggregateSnapshotRepository) {
        this.aggregateRootInstanceFactory = requireNonNull(aggregateRootInstanceFactory, "No aggregateRootFactory instance provided");
        this.aggregateSnapshotRepository = Optional.ofNullable(aggregateSnapshotRepository);
    }

    @Override
    public boolean supports(Class<?> projectionType) {
        return AggregateRoot.class.isAssignableFrom(requireNonNull(projectionType, "No aggregateType provided"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <ID, PROJECTION> Optional<PROJECTION> projectEvents(AggregateType aggregateType, ID aggregateId, Class<PROJECTION> projectionType, EventStore eventStore) {
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(aggregateId, "No aggregateId provided");
        requireNonNull(projectionType, "No aggregateType provided");
        requireNonNull(eventStore, "No eventStore instance provided");
        if (!supports(projectionType)) {
            throw new IllegalArgumentException(msg("The provided aggregateType '{}' isn't supported", projectionType.getName()));
        }

        // Check for aggregate snapshot
        var aggregateSnapshot = aggregateSnapshotRepository.flatMap(repository -> repository.loadSnapshot(aggregateType,
                                                                                                          aggregateId,
                                                                                                          projectionType));
        return aggregateSnapshot.map(snapshot -> {
            var possibleEventStream = eventStore.fetchStream(aggregateType,
                                                             aggregateId,
                                                             LongRange.from(snapshot.eventOrderOfLastIncludedEvent.increment().longValue()));
            possibleEventStream.ifPresent(eventStream -> ((AggregateRoot<ID, ?, ?>)snapshot.aggregateSnapshot).rehydrate(eventStream));
            return Optional.of(snapshot.aggregateSnapshot);
        }).orElseGet(() -> {
            var aggregate = (StatefulAggregate<ID, ?, ?>) aggregateRootInstanceFactory.create(aggregateId, projectionType);
            var possibleEventStream = eventStore.fetchStream(aggregateType,
                                                             aggregateId);

            return (Optional<PROJECTION>) possibleEventStream.map(aggregate::rehydrate);
        });
    }
}
