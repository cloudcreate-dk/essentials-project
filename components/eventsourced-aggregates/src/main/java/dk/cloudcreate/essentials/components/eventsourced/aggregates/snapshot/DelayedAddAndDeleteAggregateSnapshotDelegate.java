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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.collections.Lists;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;


/**
 * Delegating {@link AggregateSnapshotRepository} which directly delegates all operations to the provided <code>delegateRepository</code>,
 * except for {@link AggregateSnapshotRepository#aggregateUpdated(Object, AggregateEventStream)}, {@link AggregateSnapshotRepository#deleteSnapshots(AggregateType, Object, Class, List)}, which are performed asynchronously in the background.<br>
 * This ensures that expensive update/clean-up for aggregate snapshots in the database don't affect aggregate persistence performance.
 */
public class DelayedAddAndDeleteAggregateSnapshotDelegate implements AggregateSnapshotRepository {
    private static final Logger log = LoggerFactory.getLogger(DelayedAddAndDeleteAggregateSnapshotDelegate.class);

    private final AggregateSnapshotRepository delegateRepository;

    public static AggregateSnapshotRepository delegateTo(AggregateSnapshotRepository delegateRepository) {
        return new DelayedAddAndDeleteAggregateSnapshotDelegate(delegateRepository);
    }

    public DelayedAddAndDeleteAggregateSnapshotDelegate(AggregateSnapshotRepository delegateRepository) {
        this.delegateRepository = requireNonNull(delegateRepository, "No delegateRepository provided");
        log.info("Delegating to {}", delegateRepository);
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> Optional<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadSnapshot(AggregateType aggregateType, ID aggregateId, Class<AGGREGATE_IMPL_TYPE> aggregateImplType) {
        return delegateRepository.loadSnapshot(aggregateType, aggregateId, aggregateImplType);
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> Optional<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadSnapshot(AggregateType aggregateType, ID aggregateId, EventOrder withLastIncludedEventOrderLessThanOrEqualTo, Class<AGGREGATE_IMPL_TYPE> aggregateImplType) {
        return delegateRepository.loadSnapshot(aggregateType, aggregateId, withLastIncludedEventOrderLessThanOrEqualTo, aggregateImplType);
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> List<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadAllSnapshots(AggregateType aggregateType, ID aggregateId, Class<AGGREGATE_IMPL_TYPE> aggregateImplType, boolean includeSnapshotPayload) {
        return delegateRepository.loadAllSnapshots(aggregateType, aggregateId, aggregateImplType, includeSnapshotPayload);
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> void aggregateUpdated(AGGREGATE_IMPL_TYPE aggregate, AggregateEventStream<ID> persistedEvents) {
        CompletableFuture.runAsync(() -> {
            log.debug("[{}:{}] Delegating aggregateUpdated for '{}' and last_included_event_order {}",
                      persistedEvents.aggregateType(),
                      persistedEvents.aggregateId(),
                      aggregate.getClass().getName(),
                      Lists.last(persistedEvents.eventList()).get().eventOrder());
            delegateRepository.aggregateUpdated(aggregate, persistedEvents);
        });
    }

    @Override
    public <AGGREGATE_IMPL_TYPE> void deleteAllSnapshots(Class<AGGREGATE_IMPL_TYPE> ofAggregateImplementationType) {
        delegateRepository.deleteAllSnapshots(ofAggregateImplementationType);
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> void deleteSnapshots(AggregateType aggregateType, ID aggregateId, Class<AGGREGATE_IMPL_TYPE> withAggregateImplementationType) {
        delegateRepository.deleteSnapshots(aggregateType, aggregateId, withAggregateImplementationType);
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> void deleteSnapshots(AggregateType aggregateType, ID aggregateId, Class<AGGREGATE_IMPL_TYPE> withAggregateImplementationType, List<EventOrder> snapshotEventOrdersToDelete) {
        CompletableFuture.runAsync(() -> {
            log.debug("[{}:{}] Delegating deleteSnapshots for '{}' and snapshotEventOrdersToDelete: {}",
                      aggregateType,
                      aggregateId,
                      withAggregateImplementationType.getName(),
                      snapshotEventOrdersToDelete);
            delegateRepository.deleteSnapshots(aggregateType, aggregateId, withAggregateImplementationType, snapshotEventOrdersToDelete);
        });

    }
}
