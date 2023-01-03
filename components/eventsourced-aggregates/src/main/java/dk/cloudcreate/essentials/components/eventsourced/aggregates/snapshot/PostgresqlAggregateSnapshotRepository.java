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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JSONSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.shared.collections.Lists;
import dk.cloudcreate.essentials.shared.reflection.Classes;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

@SuppressWarnings("unchecked")
/**
 * Postgresql specific version of {@link AggregateSnapshotRepository}
 */
public class PostgresqlAggregateSnapshotRepository implements AggregateSnapshotRepository {
    private static final Logger log                                    = LoggerFactory.getLogger(dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot.PostgresqlAggregateSnapshotRepository.class);
    public static final  String DEFAULT_AGGREGATE_SNAPSHOTS_TABLE_NAME = "aggregate_snapshots";

    private final ConfigurableEventStore<? extends AggregateEventStreamConfiguration> eventStore;
    private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork>       unitOfWorkFactory;
    private final String                                                              snapshotTableName;
    private final JSONSerializer                                                      jsonSerializer;
    private final AggregateSnapshotRowMapper                                          aggregateSnapshotWithSnapshotPayloadRowMapper;
    private final AggregateSnapshotRowMapper                                          aggregateSnapshotWithoutSnapshotPayloadRowMapper;
    private final AddNewAggregateSnapshotStrategy                                     addNewSnapshotStrategy;
    private final AggregateSnapshotDeletionStrategy                                   snapshotDeletionStrategy;

    /**
     * Create a new durable Postgresql version of the {@link PostgresqlAggregateSnapshotRepository}
     * that will persist {@link AggregateSnapshot}'s into the {@link #DEFAULT_AGGREGATE_SNAPSHOTS_TABLE_NAME} table<br>
     * Adding new {@link AggregateSnapshot}'s when it's behind by 10 events AND
     * Deleting All Historic {@link AggregateSnapshot}'s
     *
     * @param eventStore        the event store responsible for persisting aggregate event stream
     * @param unitOfWorkFactory unit of work factory for controlling transactions
     * @param jsonSerializer    JSON serializer that will be used to serialize Aggregate instances
     */
    public PostgresqlAggregateSnapshotRepository(ConfigurableEventStore<? extends AggregateEventStreamConfiguration> eventStore,
                                                 HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                                 JSONSerializer jsonSerializer) {
        this(eventStore,
             unitOfWorkFactory,
             Optional.empty(),
             jsonSerializer,
             AddNewAggregateSnapshotStrategy.updateWhenBehindByNumberOfEvents(10),
             AggregateSnapshotDeletionStrategy.deleteAllHistoricSnapshots());
    }

    /**
     * Create a new durable Postgresql version of the {@link PostgresqlAggregateSnapshotRepository}<br>
     * Adding new {@link AggregateSnapshot}'s when it's behind by 10 events AND
     * Deleting All Historic {@link AggregateSnapshot}'s
     *
     * @param eventStore        the event store responsible for persisting aggregate event stream
     * @param unitOfWorkFactory unit of work factory for controlling transactions
     * @param snapshotTableName the name of the table where {@link AggregateSnapshot}'s will be stored
     * @param jsonSerializer    JSON serializer that will be used to serialize Aggregate instances
     */
    public PostgresqlAggregateSnapshotRepository(ConfigurableEventStore<? extends AggregateEventStreamConfiguration> eventStore,
                                                 HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                                 String snapshotTableName,
                                                 JSONSerializer jsonSerializer) {
        this(eventStore,
             unitOfWorkFactory,
             Optional.ofNullable(snapshotTableName),
             jsonSerializer,
             AddNewAggregateSnapshotStrategy.updateWhenBehindByNumberOfEvents(10),
             AggregateSnapshotDeletionStrategy.deleteAllHistoricSnapshots());
    }

    /**
     * Create a new durable Postgresql version of the {@link PostgresqlAggregateSnapshotRepository}
     *
     * @param eventStore               the event store responsible for persisting aggregate event stream
     * @param unitOfWorkFactory        unit of work factory for controlling transactions
     * @param snapshotTableName        the name of the table where {@link AggregateSnapshot}'s will be stored
     * @param jsonSerializer           JSON serializer that will be used to serialize Aggregate instances
     * @param addNewSnapshotStrategy   the strategy determining when a new {@link AggregateSnapshot} will be stored
     * @param snapshotDeletionStrategy the strategy determining when an existing {@link AggregateSnapshot} will be deleted
     */
    public PostgresqlAggregateSnapshotRepository(ConfigurableEventStore<? extends AggregateEventStreamConfiguration> eventStore,
                                                 HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                                 String snapshotTableName,
                                                 JSONSerializer jsonSerializer,
                                                 AddNewAggregateSnapshotStrategy addNewSnapshotStrategy,
                                                 AggregateSnapshotDeletionStrategy snapshotDeletionStrategy) {
        this(eventStore,
             unitOfWorkFactory,
             Optional.ofNullable(snapshotTableName),
             jsonSerializer,
             addNewSnapshotStrategy,
             snapshotDeletionStrategy);
    }

    /**
     * Create a new durable Postgresql version of the {@link PostgresqlAggregateSnapshotRepository}
     * that will persist {@link AggregateSnapshot}'s into the {@link #DEFAULT_AGGREGATE_SNAPSHOTS_TABLE_NAME} table
     *
     * @param eventStore               the event store responsible for persisting aggregate event stream
     * @param unitOfWorkFactory        unit of work factory for controlling transactions
     * @param jsonSerializer           JSON serializer that will be used to serialize Aggregate instances
     * @param addNewSnapshotStrategy   the strategy determining when a new {@link AggregateSnapshot} will be stored
     * @param snapshotDeletionStrategy the strategy determining when an existing {@link AggregateSnapshot} will be deleted
     */
    public PostgresqlAggregateSnapshotRepository(ConfigurableEventStore<? extends AggregateEventStreamConfiguration> eventStore,
                                                 HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                                 JSONSerializer jsonSerializer,
                                                 AddNewAggregateSnapshotStrategy addNewSnapshotStrategy,
                                                 AggregateSnapshotDeletionStrategy snapshotDeletionStrategy) {
        this(eventStore,
             unitOfWorkFactory,
             Optional.empty(),
             jsonSerializer,
             addNewSnapshotStrategy,
             snapshotDeletionStrategy);
    }

    /**
     * Create a new durable Postgresql version of the {@link PostgresqlAggregateSnapshotRepository}
     *
     * @param eventStore               the event store responsible for persisting aggregate event stream
     * @param unitOfWorkFactory        unit of work factory for controlling transactions
     * @param snapshotTableName        Optional name of the table where {@link AggregateSnapshot}'s will be stored -
     *                                 if {@link Optional#empty()} then {@link AggregateSnapshot}'s will persisted into
     *                                 the {@link #DEFAULT_AGGREGATE_SNAPSHOTS_TABLE_NAME} table
     * @param jsonSerializer           JSON serializer that will be used to serialize Aggregate instances
     * @param addNewSnapshotStrategy   the strategy determining when a new {@link AggregateSnapshot} will be stored
     * @param snapshotDeletionStrategy the strategy determining when an existing {@link AggregateSnapshot} will be deleted
     */
    public PostgresqlAggregateSnapshotRepository(ConfigurableEventStore<? extends AggregateEventStreamConfiguration> eventStore,
                                                 HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                                 Optional<String> snapshotTableName,
                                                 JSONSerializer jsonSerializer,
                                                 AddNewAggregateSnapshotStrategy addNewSnapshotStrategy,
                                                 AggregateSnapshotDeletionStrategy snapshotDeletionStrategy) {
        this.eventStore = requireNonNull(eventStore, "No eventStore instance provided");
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory instance provided");
        this.snapshotTableName = requireNonNull(snapshotTableName, "No snapshotTableName provided")
                .orElse(DEFAULT_AGGREGATE_SNAPSHOTS_TABLE_NAME);
        this.jsonSerializer = requireNonNull(jsonSerializer, "No jsonSerializer instance provided");
        this.addNewSnapshotStrategy = requireNonNull(addNewSnapshotStrategy, "No snapshotUpdateStrategy instance provided");
        this.snapshotDeletionStrategy = requireNonNull(snapshotDeletionStrategy, "No snapshotDeletionStrategy instance provided");
        aggregateSnapshotWithSnapshotPayloadRowMapper = new AggregateSnapshotRowMapper(true);
        aggregateSnapshotWithoutSnapshotPayloadRowMapper = new AggregateSnapshotRowMapper(false);
        initializeStorage();
    }

    private void initializeStorage() {
        var rowsUpdated = unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().execute("CREATE TABLE IF NOT EXISTS " + snapshotTableName + " (\n" +
                                                                                               "aggregate_impl_type TEXT NOT NULL,\n" +
                                                                                               "aggregate_id TEXT NOT NULL,\n" +
                                                                                               "aggregate_type TEXT NOT NULL,\n" +
                                                                                               "last_included_event_order bigint NOT NULL,\n" +
                                                                                               "snapshot JSONB NOT NULL,\n" +
                                                                                               "created_ts TIMESTAMP WITH TIME ZONE NOT NULL,\n" +
                                                                                               "statistics JSONB,\n" +
                                                                                               "PRIMARY KEY (aggregate_impl_type, " +
                                                                                               "             aggregate_id," +
                                                                                               "             last_included_event_order)\n" +
                                                                                               ")"));
        if (rowsUpdated == 1) {
            log.info("Created aggregate snapshot table '{}'", snapshotTableName);
        } else {
            log.debug("Aggregate snapshot table already exists: '{}'", snapshotTableName);
        }
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> Optional<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadSnapshot(AggregateType aggregateType,
                                                                                                       ID aggregateId,
                                                                                                       EventOrder withLastIncludedEventOrderLessThanOrEqualTo,
                                                                                                       Class<AGGREGATE_IMPL_TYPE> aggregateImplType) {
        requireNonNull(aggregateType, "No aggregateType supplied");
        requireNonNull(aggregateId, "No aggregateId supplied");
        requireNonNull(withLastIncludedEventOrderLessThanOrEqualTo, "No withLastIncludedEventOrderLessThanOrEqualTo supplied");
        requireNonNull(aggregateImplType, "No aggregateImplType supplied");
        var config                = eventStore.getAggregateEventStreamConfiguration(aggregateType);
        var serializedAggregateId = config.aggregateIdSerializer.serialize(aggregateId);
        return unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createQuery("SELECT * FROM " + snapshotTableName +
                                                                                        " WHERE aggregate_impl_type = :aggregate_impl_type AND " +
                                                                                        "aggregate_id = :aggregate_id AND " +
                                                                                        "last_included_event_order <= :last_included_event_order")
                                                          .bind("aggregate_impl_type", aggregateImplType.getName())
                                                          .bind("aggregate_id", serializedAggregateId)
                                                          .bind("last_included_event_order", withLastIncludedEventOrderLessThanOrEqualTo)
                                                          .map(aggregateSnapshotWithSnapshotPayloadRowMapper)
                                                          .map(snapshot -> (AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>) snapshot)
                                                          .findOne());
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> void aggregateUpdated(AGGREGATE_IMPL_TYPE aggregate, AggregateEventStream<ID> persistedEvents) {
        requireNonNull(aggregate, "No aggregate instance supplied");
        requireNonNull(persistedEvents, "No persistedEvents stream supplied");
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            var aggregateType         = persistedEvents.aggregateType();
            var config                = eventStore.getAggregateEventStreamConfiguration(aggregateType);
            var serializedAggregateId = config.aggregateIdSerializer.serialize(persistedEvents.aggregateId());
            var aggregateImplType     = aggregate.getClass().getName();

            var mostRecentlyStoredSnapshotLastIncludedEventOrder = findMostRecentLastIncludedEventOrderFor(serializedAggregateId, aggregateImplType, uow);
            if (shouldWeAddANewAggregateSnapshot(aggregate, persistedEvents, aggregateType, aggregateImplType, mostRecentlyStoredSnapshotLastIncludedEventOrder)) {
                deleteHistoricSnapShotsIfNecessary(aggregate, persistedEvents, uow, aggregateType, serializedAggregateId, aggregateImplType);

                var lastAppliedEventOrder = Lists.last(persistedEvents.eventList()).get().eventOrder();
                var rowsUpdated = uow.handle().createUpdate("INSERT INTO " + snapshotTableName + "(\n" +
                                                                    "aggregate_impl_type, aggregate_id, aggregate_type, last_included_event_order, snapshot, created_ts" +
                                                                    "\n) VALUES (\n" +
                                                                    ":aggregate_impl_type, :aggregate_id, :aggregate_type, :last_included_event_order, :snapshot::jsonb, :created_ts" +
                                                                    "\n) ON CONFLICT DO NOTHING")
                                     .bind("aggregate_impl_type", aggregateImplType)
                                     .bind("aggregate_id", serializedAggregateId)
                                     .bind("aggregate_type", aggregateType.value())
                                     .bind("last_included_event_order", lastAppliedEventOrder.longValue())
                                     .bind("snapshot", jsonSerializer.serialize(aggregate))
                                     .bind("created_ts", OffsetDateTime.now(Clock.systemUTC()))
                                     .execute();

                if (rowsUpdated == 1) {
                    log.debug("[{}:{}] Updated Aggregate Snapshot for '{}' and last_included_event_order {}",
                              aggregateType,
                              persistedEvents.aggregateId(),
                              aggregateImplType,
                              lastAppliedEventOrder);
                } else {
                    log.debug("[{}:{}] No rows updated when trying to update Aggregate Snapshot for '{}' and last_included_event_order {}",
                              aggregateType,
                              persistedEvents.aggregateId(),
                              aggregateImplType,
                              lastAppliedEventOrder);
                }
            }
        });
    }

    private <ID, AGGREGATE_IMPL_TYPE> boolean shouldWeAddANewAggregateSnapshot(AGGREGATE_IMPL_TYPE aggregate, AggregateEventStream<ID> persistedEvents, AggregateType aggregateType, String aggregateImplType, Optional<EventOrder> mostRecentlyStoredSnapshotLastIncludedEventOrder) {
        if (addNewSnapshotStrategy.shouldANewAggregateSnapshotBeAdded(aggregate, persistedEvents, mostRecentlyStoredSnapshotLastIncludedEventOrder)) {
            if (log.isDebugEnabled()) {
                log.debug("[{}:{}] {} strategy determined to ADD a new Aggregate Snapshot for '{}' based on mostRecentlyStoredSnapshotLastIncludedEventOrder {} and persistedEvents->eventOrders: {}",
                          aggregateType,
                          persistedEvents.aggregateId(),
                          addNewSnapshotStrategy,
                          aggregateImplType,
                          mostRecentlyStoredSnapshotLastIncludedEventOrder,
                          persistedEvents.eventList().stream().map(persistedEvent -> persistedEvent.eventOrder().longValue()).collect(Collectors.toList()));
            }
            return true;
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[{}:{}] {} strategy determined NOT to add a new Aggregate Snapshot for '{}' based on mostRecentlyStoredSnapshotLastIncludedEventOrder {} and persistedEvents->eventOrders: {}",
                          aggregateType,
                          persistedEvents.aggregateId(),
                          addNewSnapshotStrategy,
                          aggregateImplType,
                          mostRecentlyStoredSnapshotLastIncludedEventOrder,
                          persistedEvents.eventList().stream().map(persistedEvent -> persistedEvent.eventOrder().longValue()).collect(Collectors.toList()));
            }
            return false;
        }
    }

    protected Optional<EventOrder> findMostRecentLastIncludedEventOrderFor(String serializedAggregateId,
                                                                           String aggregateImplType,
                                                                           HandleAwareUnitOfWork uow) {
        return uow.handle().createQuery("SELECT coalesce(MAX(last_included_event_order), -1) FROM " + snapshotTableName +
                                                " WHERE aggregate_impl_type = :aggregate_impl_type AND " +
                                                "aggregate_id = :aggregate_id")
                  .bind("aggregate_impl_type", aggregateImplType)
                  .bind("aggregate_id", serializedAggregateId)
                  .mapTo(EventOrder.class)
                  .findOne();
    }

    private <ID, AGGREGATE_IMPL_TYPE> void deleteHistoricSnapShotsIfNecessary(AGGREGATE_IMPL_TYPE aggregate,
                                                                              AggregateEventStream<ID> persistedEvents,
                                                                              HandleAwareUnitOfWork uow,
                                                                              AggregateType aggregateType,
                                                                              String serializedAggregateId,
                                                                              String aggregateImplType) {
        if (snapshotDeletionStrategy.requiresExistingSnapshotDetailsToDetermineWhichAggregateSnapshotsToDelete()) {
            var existingSnapshots = loadAllSnapshots(serializedAggregateId,
                                                     aggregateImplType,
                                                     false,
                                                     uow);
            if (log.isDebugEnabled()) {
                log.debug("[{}:{}] Found {} {}'s Aggregate-Snapshots with eventOrderOfLastIncludedEvent: {}",
                          aggregateType,
                          persistedEvents.aggregateId(),
                          existingSnapshots.size(),
                          aggregateImplType,
                          existingSnapshots.stream().map(snapshot -> snapshot.eventOrderOfLastIncludedEvent.longValue()).collect(Collectors.toList()));
            }

            if (!existingSnapshots.isEmpty()) {
                var snapshotEventOrdersToDeleteStream = snapshotDeletionStrategy.resolveSnapshotsToDelete(existingSnapshots);
                var eventOrdersToDelete               = snapshotEventOrdersToDeleteStream.map(snapshot -> snapshot.eventOrderOfLastIncludedEvent).collect(Collectors.toList());
                if (!eventOrdersToDelete.isEmpty()) {
                    log.debug("[{}:{}] Will delete {} Historic {}'s Aggregate-Snapshots with eventOrderOfLastIncludedEvent: {}",
                              aggregateType,
                              persistedEvents.aggregateId(),
                              eventOrdersToDelete.size(),
                              aggregateImplType,
                              eventOrdersToDelete);

                    deleteSnapshots(aggregateType,
                                    persistedEvents.aggregateId(),
                                    aggregate.getClass(),
                                    eventOrdersToDelete);
                }
            }
        } else {
            deleteSnapshots(aggregateType,
                            persistedEvents.aggregateId(),
                            aggregate.getClass());
        }
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> List<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadAllSnapshots(AggregateType aggregateType,
                                                                                                       ID aggregateId,
                                                                                                       Class<AGGREGATE_IMPL_TYPE> aggregateImplType,
                                                                                                       boolean includeSnapshotPayload) {
        requireNonNull(aggregateType, "No aggregateType supplied");
        requireNonNull(aggregateId, "No aggregateId supplied");
        requireNonNull(aggregateImplType, "No aggregateImplType supplied");
        var config                = eventStore.getAggregateEventStreamConfiguration(aggregateType);
        var serializedAggregateId = config.aggregateIdSerializer.serialize(aggregateId);

        return unitOfWorkFactory.withUnitOfWork(uow -> loadAllSnapshots(serializedAggregateId,
                                                                        aggregateImplType.getName(),
                                                                        includeSnapshotPayload,
                                                                        uow));
    }

    protected <ID, AGGREGATE_IMPL_TYPE> List<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> loadAllSnapshots(String serializedAggregateId,
                                                                                                          String aggregateImplType,
                                                                                                          boolean includeSnapshotPayload,
                                                                                                          HandleAwareUnitOfWork uow) {
        var selectColumns = includeSnapshotPayload ? "*" : "aggregate_impl_type, aggregate_id, aggregate_type, last_included_event_order, created_ts, statistics";
        return uow.handle().createQuery("SELECT " + selectColumns + " FROM " + snapshotTableName + " WHERE " +
                                                "aggregate_impl_type = :aggregate_impl_type AND aggregate_id = :aggregate_id " +
                                                "ORDER BY last_included_event_order ASC")
                  .bind("aggregate_impl_type", aggregateImplType)
                  .bind("aggregate_id", serializedAggregateId)
                  .map(includeSnapshotPayload ? aggregateSnapshotWithSnapshotPayloadRowMapper : aggregateSnapshotWithoutSnapshotPayloadRowMapper)
                  .map(snapshot -> (AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>) snapshot)
                  .list();
    }

    @Override
    public <AGGREGATE_IMPL_TYPE> void deleteAllSnapshots(Class<AGGREGATE_IMPL_TYPE> ofAggregateImplementationType) {
        requireNonNull(ofAggregateImplementationType, "No ofAggregateImplementationType supplied");
        var rowsUpdated = unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createUpdate("DELETE FROM " + snapshotTableName +
                                                                                                    " WHERE aggregate_impl_type = :aggregate_impl_type")
                                                                     .bind("aggregate_impl_type", ofAggregateImplementationType.getName())
                                                                     .execute());
        log.debug("Deleted {} historic snapshots related to Aggregate implementation type '{}'",
                  rowsUpdated,
                  ofAggregateImplementationType.getName());
    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> void deleteSnapshots(AggregateType aggregateType, ID aggregateId, Class<AGGREGATE_IMPL_TYPE> withAggregateImplementationType) {
        requireNonNull(aggregateType, "No aggregateType supplied");
        requireNonNull(aggregateId, "No aggregateId supplied");
        requireNonNull(withAggregateImplementationType, "No withAggregateImplementationType supplied");

        var config                = eventStore.getAggregateEventStreamConfiguration(aggregateType);
        var serializedAggregateId = config.aggregateIdSerializer.serialize(aggregateId);

        var rowsUpdated = unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createUpdate("DELETE FROM " + snapshotTableName +
                                                                                                    " WHERE aggregate_impl_type = :aggregate_impl_type AND " +
                                                                                                    "aggregate_id = :aggregate_id")
                                                                     .bind("aggregate_impl_type", withAggregateImplementationType.getName())
                                                                     .bind("aggregate_id", serializedAggregateId)
                                                                     .execute());
        log.debug("Deleted {} historic snapshots related to Aggregate '{}' with id '{}'",
                  rowsUpdated,
                  withAggregateImplementationType.getName(),
                  aggregateId);

    }

    @Override
    public <ID, AGGREGATE_IMPL_TYPE> void deleteSnapshots(AggregateType aggregateType,
                                                          ID aggregateId,
                                                          Class<AGGREGATE_IMPL_TYPE> withAggregateImplementationType,
                                                          List<EventOrder> snapshotEventOrdersToDelete) {
        requireNonNull(aggregateType, "No aggregateType supplied");
        requireNonNull(aggregateId, "No aggregateId supplied");
        requireNonNull(withAggregateImplementationType, "No withAggregateImplementationType supplied");
        requireNonEmpty(snapshotEventOrdersToDelete, "snapshotEventOrdersToDelete may not be null or empty");

        var config                = eventStore.getAggregateEventStreamConfiguration(aggregateType);
        var serializedAggregateId = config.aggregateIdSerializer.serialize(aggregateId);
        var rowsUpdated = unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createUpdate("DELETE FROM " + snapshotTableName +
                                                                                                    " WHERE aggregate_impl_type = :aggregate_impl_type AND " +
                                                                                                    "aggregate_id = :aggregate_id AND last_included_event_order IN (<snapshotEventOrdersToDelete>)")
                                                                     .bind("aggregate_impl_type", withAggregateImplementationType.getName())
                                                                     .bind("aggregate_id", serializedAggregateId)
                                                                     .bindList("snapshotEventOrdersToDelete", snapshotEventOrdersToDelete.stream().map(eventOrder -> eventOrder.longValue()).collect(Collectors.toList()))
                                                                     .execute());
        log.debug("Deleted {} historic snapshots related to Aggregate '{}' with id '{}' and snapshotEventOrdersToDelete: {}",
                  rowsUpdated,
                  withAggregateImplementationType.getName(),
                  aggregateId,
                  snapshotEventOrdersToDelete);
    }


    private class AggregateSnapshotRowMapper implements RowMapper<AggregateSnapshot> {
        private final boolean resultSetContainsSnapshotPayload;

        public AggregateSnapshotRowMapper(boolean resultSetContainsSnapshotPayload) {
            this.resultSetContainsSnapshotPayload = resultSetContainsSnapshotPayload;
        }

        @Override
        public AggregateSnapshot map(ResultSet rs, StatementContext ctx) throws SQLException {
            var aggregateType     = AggregateType.of(rs.getString("aggregate_type"));
            var config            = eventStore.getAggregateEventStreamConfiguration(aggregateType);
            var aggregateImplType = Classes.forName(rs.getString("aggregate_impl_type"));


            var aggregateId     = config.aggregateIdSerializer.deserialize(rs.getString("aggregate_id"));
            var snapshotPayload = deserializeSnapshot(rs, aggregateId, aggregateImplType);
            return new AggregateSnapshot(aggregateType,
                                         aggregateId,
                                         aggregateImplType,
                                         snapshotPayload,
                                         EventOrder.of(rs.getLong("last_included_event_order")));
        }

        private Object deserializeSnapshot(ResultSet rs, Object aggregateId, Class<?> aggregateImplType) throws SQLException {
            try {
                return resultSetContainsSnapshotPayload ? jsonSerializer.deserialize(rs.getString("snapshot"), aggregateImplType) : null;
            } catch (Exception e) {
                log.error(msg("Failed to deserialize '{}' with id '{}'", aggregateImplType, aggregateId), e);
                return new BrokenSnapshot(e);
            }
        }
    }
}
