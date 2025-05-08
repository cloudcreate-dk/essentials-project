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

package dk.trustworks.essentials.components.eventsourced.aggregates.snapshot;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

import java.util.*;

/**
 * Repository storing and updating Aggregate instance snapshots
 * @see PostgresqlAggregateSnapshotRepository
 * @see DelayedAddAndDeleteAggregateSnapshotDelegate
 */
public interface AggregateSnapshotRepository {
    /**
     * @param aggregateType         the aggregate type (determining the aggregate's event stream name)
     * @param aggregateId           the identifier for the aggregate instance
     * @param aggregateImplType     the concrete aggregate implementation type
     * @param <ID>                  the aggregate ID type
     * @param <AGGREGATE_IMPL_TYPE> the concrete aggregate implementation type
     * @return the aggregate snapshot wrapped in an {@link Optional} if a snapshot was found,
     * otherwise {@link Optional#empty()}
     */
    default <ID, AGGREGATE_IMPL_TYPE> Optional<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadSnapshot(AggregateType aggregateType,
                                                                                                        ID aggregateId,
                                                                                                        Class<AGGREGATE_IMPL_TYPE> aggregateImplType) {
        return loadSnapshot(aggregateType,
                            aggregateId,
                            EventOrder.MAX_EVENT_ORDER,
                            aggregateImplType);
    }

    /**
     * @param aggregateType                               the aggregate type (determining the aggregate's event stream name)
     * @param aggregateId                                 the identifier for the aggregate instance
     * @param withLastIncludedEventOrderLessThanOrEqualTo the snapshot returned must have {@link AggregateSnapshot#eventOrderOfLastIncludedEvent} that is less than or equal to this value
     * @param aggregateImplType                           the concrete aggregate implementation type
     * @param <ID>                                        the aggregate ID type
     * @param <AGGREGATE_IMPL_TYPE>                       the concrete aggregate implementation type
     * @return the aggregate snapshot wrapped in an {@link Optional} if a snapshot was found,
     * otherwise {@link Optional#empty()}
     */
    <ID, AGGREGATE_IMPL_TYPE> Optional<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadSnapshot(AggregateType aggregateType,
                                                                                                ID aggregateId,
                                                                                                EventOrder withLastIncludedEventOrderLessThanOrEqualTo,
                                                                                                Class<AGGREGATE_IMPL_TYPE> aggregateImplType);

    /**
     * Load all {@link AggregateSnapshot}'s related to the given aggregate instance
     *
     * @param aggregateType          the aggregate type (determining the aggregate's event stream name)
     * @param aggregateId            the identifier for the aggregate instance
     * @param aggregateImplType      the concrete aggregate implementation type
     * @param includeSnapshotPayload should the {@link AggregateSnapshot#aggregateSnapshot} be loaded?
     * @param <ID>                   the aggregate ID type
     * @param <AGGREGATE_IMPL_TYPE>  the concrete aggregate implementation type
     * @return list of all {@link AggregateSnapshot}'s in ascending {@link AggregateSnapshot#eventOrderOfLastIncludedEvent} order (the oldest snapshot first) related to the given aggregate instance
     */
    <ID, AGGREGATE_IMPL_TYPE> List<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadAllSnapshots(AggregateType aggregateType,
                                                                                                ID aggregateId,
                                                                                                Class<AGGREGATE_IMPL_TYPE> aggregateImplType,
                                                                                                boolean includeSnapshotPayload);


    /**
     * Callback from an Aggregate Repository to notify that the aggregate has been updated
     *
     * @param aggregate             the aggregate instance after the changes (in the form of events) has been marked as committed within the aggregate
     * @param persistedEvents       the aggregate changes (in the form of events) after they've been persisted
     * @param <ID>                  the aggregate ID type
     * @param <AGGREGATE_IMPL_TYPE> the concrete aggregate implementation type
     */
    <ID, AGGREGATE_IMPL_TYPE> void aggregateUpdated(AGGREGATE_IMPL_TYPE aggregate, AggregateEventStream<ID> persistedEvents);

    /**
     * Delete all snapshots for the given aggregate implementation type
     *
     * @param ofAggregateImplementationType the concrete aggregate implementation type that we want to delete all snapshots for
     * @param <AGGREGATE_IMPL_TYPE>         the concrete aggregate implementation type
     */
    <AGGREGATE_IMPL_TYPE> void deleteAllSnapshots(Class<AGGREGATE_IMPL_TYPE> ofAggregateImplementationType);

    /**
     * Delete all snapshots for the given aggregate instance with the given aggregate implementation type
     *
     * @param aggregateType                   the aggregate type (determining the aggregate's event stream name)
     * @param aggregateId                     the id of the aggregate that we want to delete all snapshots for
     * @param withAggregateImplementationType the concrete aggregate implementation type that we want to delete all snapshots for
     * @param <ID>                            the aggregate ID type
     * @param <AGGREGATE_IMPL_TYPE>           the concrete aggregate implementation type
     */
    <ID, AGGREGATE_IMPL_TYPE> void deleteSnapshots(AggregateType aggregateType, ID aggregateId, Class<AGGREGATE_IMPL_TYPE> withAggregateImplementationType);

    /**
     * Delete all snapshots with a given {@link AggregateSnapshot#eventOrderOfLastIncludedEvent} for the given aggregate instance, with the given aggregate implementation type
     *
     * @param aggregateType                   the aggregate type (determining the aggregate's event stream name)
     * @param aggregateId                     the id of the aggregate that we want to delete all snapshots for
     * @param withAggregateImplementationType the concrete aggregate implementation type that we want to delete all snapshots for
     * @param snapshotEventOrdersToDelete     The list of {@link AggregateSnapshot#eventOrderOfLastIncludedEvent} 's that should be deleted
     * @param <ID>                            the aggregate ID type
     * @param <AGGREGATE_IMPL_TYPE>           the concrete aggregate implementation type
     */
    <ID, AGGREGATE_IMPL_TYPE> void deleteSnapshots(AggregateType aggregateType,
                                                   ID aggregateId,
                                                   Class<AGGREGATE_IMPL_TYPE> withAggregateImplementationType,
                                                   List<EventOrder> snapshotEventOrdersToDelete);


}
