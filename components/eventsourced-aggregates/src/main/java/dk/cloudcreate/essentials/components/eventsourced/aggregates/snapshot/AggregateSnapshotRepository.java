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

/**
 * Repository storing and updating Aggregate instance snapshots
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

    @SuppressWarnings("unchecked")
    class PostgresqlAggregateSnapshotRepository implements AggregateSnapshotRepository {
        private static final Logger log                                    = LoggerFactory.getLogger(PostgresqlAggregateSnapshotRepository.class);
        public static final  String DEFAULT_AGGREGATE_SNAPSHOTS_TABLE_NAME = "aggregate_snapshots";

        private final ConfigurableEventStore<? extends AggregateEventStreamConfiguration> eventStore;
        private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork>       unitOfWorkFactory;
        private final String                                                              snapshotTableName;
        private final JSONSerializer                                                      jsonSerializer;
        private final AggregateSnapshotRowMapper                                          aggregateSnapshotWithSnapshotPayloadRowMapper;
        private final AggregateSnapshotRowMapper                                          aggregateSnapshotWithoutSnapshotPayloadRowMapper;
        private final AddNewAggregateSnapshotStrategy                                     addNewSnapshotStrategy;
        private final AggregateSnapshotDeletionStrategy                                   snapshotDeletionStrategy;

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


                var snapshotPayload = resultSetContainsSnapshotPayload ? jsonSerializer.deserialize(rs.getString("snapshot"), aggregateImplType) : null;
                return new AggregateSnapshot(aggregateType,
                                             config.aggregateIdSerializer.deserialize(rs.getString("aggregate_id")),
                                             aggregateImplType,
                                             snapshotPayload,
                                             EventOrder.of(rs.getLong("last_included_event_order")));
            }
        }
    }


}
