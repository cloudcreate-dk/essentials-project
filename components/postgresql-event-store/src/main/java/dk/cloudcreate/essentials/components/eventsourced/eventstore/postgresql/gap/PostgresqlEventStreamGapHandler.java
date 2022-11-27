/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.jdbi.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;
import dk.cloudcreate.essentials.types.LongRange;
import org.slf4j.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static dk.cloudcreate.essentials.shared.FailFast.*;

/**
 * Postgresql specific version of the {@link EventStreamGapHandler}, which will maintain per {@link SubscriberId} transient gaps
 * and permanent gaps (across all subscribers) in the {@link #TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME} and {@link #PERMANENT_GAPS_TABLE_NAME}
 *
 * @param <CONFIG> The concrete {@link AggregateEventStreamConfiguration}
 */
public class PostgresqlEventStreamGapHandler<CONFIG extends AggregateEventStreamConfiguration> implements EventStreamGapHandler<CONFIG> {
    private static final Logger                                               log                                  = LoggerFactory.getLogger(PostgresqlEventStreamGapHandler.class);
    public static final  String                                               TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME = "transient_subscriber_gaps";
    private static final String                                               TRANSIENT_SUBSCRIBER_GAPS_INDEX_NAME = "transient_subscriber_gaps_index";
    public static final  String                                               PERMANENT_GAPS_TABLE_NAME            = "permanent_gaps";
    public static final  List<GlobalEventOrder>                               NO_GAPS                              = List.of();
    private final        PostgresqlEventStore<CONFIG>                         postgresqlEventStore;
    private final        EventStoreUnitOfWorkFactory<EventStoreUnitOfWork>    unitOfWorkFactory;
    private final        ResolveTransientGapsToIncludeInQueryStrategy         resolveTransientGapsToIncludeInQueryStrategy;
    private final        ResolveTransientGapsToPermanentGapsPromotionStrategy resolveTransientGapsToPermanentGapsPromotionStrategy;
    private              long                                                 refreshTransientGapsFromStorageEverySeconds;

    /**
     * Default configuration that includes the earliest 10 transient gaps and which will promote transient gaps to permanent gaps after 120 seconds.
     *
     * @param postgresqlEventStore the postgresql event store
     * @param unitOfWorkFactory    the unit of work factory that coordinates the event store {@link UnitOfWork}
     */
    public PostgresqlEventStreamGapHandler(PostgresqlEventStore<CONFIG> postgresqlEventStore,
                                           EventStoreUnitOfWorkFactory unitOfWorkFactory) {
        this(postgresqlEventStore,
             unitOfWorkFactory,
             Duration.ofSeconds(60),
             (forAggregateType, globalOrderQueryRange, allTransientGaps) -> {
                 var numberOfGaps          = allTransientGaps.size();
                 var numberOfGapsToInclude = Math.min(numberOfGaps, 2);
                 return numberOfGapsToInclude > 0 ? allTransientGaps.subList(0, numberOfGapsToInclude)
                                                                    .stream()
                                                                    .map(Pair::_1)
                                                                    .collect(Collectors.toList()) : NO_GAPS;
             },
             ResolveTransientGapsToPermanentGapsPromotionStrategy.thresholdBased(120));
    }

    /**
     * @param postgresqlEventStore                                 the postgresql event store
     * @param unitOfWorkFactory                                    the unit of work factory that coordinates the event store {@link UnitOfWork}
     * @param refreshTransientGapsFromStorageInterval              how often should transient gaps be refreshed from database. This is a simple low overhead effort to keep local caches of transient gaps
     *                                                             eventually in sync across multiple nodes. Any new transient gaps detected, resolved/deleted are always reflected in the underlying database which is the ultimate
     *                                                             source of truth. If an event stream subscriber, managed through the {@link EventStoreSubscriptionManager} is using an exclusive (i.e. single node) subscription then
     *                                                             the local transient gap will always be in sync with the database. For other subscriptions forms, the local cache will be eventually consistent with the database. This will
     *                                                             mean that certain nodes may try to include a transient gap AFTER another node have promoted the gap to be a permanent gap (and the same applies if a permanent gap is removed
     *                                                             using e.g. {@link EventStreamGapHandler#resetPermanentGapsFor(AggregateType)})
     * @param resolveTransientGapsToIncludeInQueryStrategy         strategy that determines how many and which gaps, among all the transient gaps detected for the given subscriber,
     *                                                             should be included from {@link SubscriptionGapHandler#findTransientGapsToIncludeInQuery(AggregateType, LongRange)}
     *                                                             (which is called from {@link PostgresqlEventStore#pollEvents(AggregateType, long, Optional, Optional, Optional, Optional)})
     * @param resolveTransientGapsToPermanentGapsPromotionStrategy strategy for when the {@link PostgresqlEventStreamGapHandler} will promote a transient gap to a permanent gap
     */
    public PostgresqlEventStreamGapHandler(PostgresqlEventStore<CONFIG> postgresqlEventStore,
                                           EventStoreUnitOfWorkFactory unitOfWorkFactory,
                                           Duration refreshTransientGapsFromStorageInterval,
                                           ResolveTransientGapsToIncludeInQueryStrategy resolveTransientGapsToIncludeInQueryStrategy,
                                           ResolveTransientGapsToPermanentGapsPromotionStrategy resolveTransientGapsToPermanentGapsPromotionStrategy) {
        this.postgresqlEventStore = requireNonNull(postgresqlEventStore, "No postgresqlEventStore provided");
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory provided");
        this.refreshTransientGapsFromStorageEverySeconds = requireNonNull(refreshTransientGapsFromStorageInterval, "No refreshTransientGapsFromStorageInterval provided").toSeconds();
        this.resolveTransientGapsToIncludeInQueryStrategy = requireNonNull(resolveTransientGapsToIncludeInQueryStrategy, "No resolveTransientGapsToIncludeInQuery provided");
        this.resolveTransientGapsToPermanentGapsPromotionStrategy = requireNonNull(resolveTransientGapsToPermanentGapsPromotionStrategy, "No resolveTransientGapsToPermanentGapsPromotionStrategy provided");
        createGapHandlingTablesAndIndexes();
    }

    private void createGapHandlingTablesAndIndexes() {

        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            unitOfWork.handle().getJdbi().registerArgument(new AggregateTypeArgumentFactory());
            unitOfWork.handle().getJdbi().registerColumnMapper(new AggregateTypeColumnMapper());
            unitOfWork.handle().getJdbi().registerArgument(new SubscriberIdArgumentFactory());
            unitOfWork.handle().getJdbi().registerColumnMapper(new SubscriberIdColumnMapper());
            var numberOfChanges = unitOfWork.handle().execute("CREATE TABLE IF NOT EXISTS " + TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME + " (\n" +
                                                                      "   subscriber_id text NOT NULL,\n" +
                                                                      "   aggregate_type text NOT NULL,\n" +
                                                                      "   gap_global_event_order bigint NOT NULL\n," +
                                                                      "   first_discovered TIMESTAMP WITH TIME ZONE NOT NULL\n," +
                                                                      "   PRIMARY KEY (subscriber_id, aggregate_type, gap_global_event_order)\n" +
                                                                      ")");
            log.info("{} '{}'", numberOfChanges == 1 ? "Created table: " : "Table already exists: ", TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME);

            numberOfChanges = unitOfWork.handle().execute("CREATE INDEX IF NOT EXISTS " + TRANSIENT_SUBSCRIBER_GAPS_INDEX_NAME + " ON \n" +
                                                                  TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME + "(subscriber_id, aggregate_type)");
            log.info("{} '{}'", numberOfChanges == 1 ? "Created index: " : "Index already exists: ", TRANSIENT_SUBSCRIBER_GAPS_INDEX_NAME);

            numberOfChanges = unitOfWork.handle().execute("CREATE TABLE IF NOT EXISTS " + PERMANENT_GAPS_TABLE_NAME + " (\n" +
                                                                  "   aggregate_type text NOT NULL,\n" +
                                                                  "   gap_global_event_order bigint NOT NULL\n," +
                                                                  "   added_timestamp TIMESTAMP WITH TIME ZONE NOT NULL," +
                                                                  "   PRIMARY KEY (aggregate_type, gap_global_event_order)\n" +
                                                                  ")");
            log.info("{} '{}'", numberOfChanges == 1 ? "Created table: " : "Table already exists: ", PERMANENT_GAPS_TABLE_NAME);
        });
    }

    @Override
    public SubscriptionGapHandler gapHandlerFor(SubscriberId subscriberId) {
        return new PostgresqlSubscriptionGapHandler(subscriberId);
    }

    @Override
    public List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType) {
        var globalEventOrdersRemoved = unitOfWorkFactory.withUnitOfWork(unitOfWork ->
                                                                                unitOfWork.handle().createQuery("DELETE FROM " + PERMANENT_GAPS_TABLE_NAME + " WHERE aggregate_type = :aggregate_type RETURNING gap_global_event_order")
                                                                                          .bind("aggregate_type", requireNonNull(aggregateType, "No aggregateType provided"))
                                                                                          .mapTo(GlobalEventOrder.class)
                                                                                          .list());
        log.info("[{}] Removed {} Permanent Gap(s) with GlobalEventOrder: {}",
                 aggregateType,
                 globalEventOrdersRemoved.size(),
                 globalEventOrdersRemoved);
        return globalEventOrdersRemoved;
    }


    @Override
    public List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType, LongRange resetForThisSpecificGlobalEventOrdersRange) {
        requireNonNull(resetForThisSpecificGlobalEventOrdersRange, "No resetForThisSpecificGlobalEventOrdersRange provided");
        var globalEventOrdersRemoved = unitOfWorkFactory.withUnitOfWork(unitOfWork ->
                                                                        {
                                                                            var sql = "DELETE FROM " + PERMANENT_GAPS_TABLE_NAME + " WHERE aggregate_type = :aggregate_type RETURNING gap_global_event_order\n";
                                                                            if (resetForThisSpecificGlobalEventOrdersRange.isOpenRange()) {
                                                                                sql += " AND gap_global_event_order >= :gap_global_event_order_from_and_including";
                                                                            } else {
                                                                                sql += " AND gap_global_event_order >= :gap_global_event_order_from_and_including AND gap_global_event_order <= :gap_global_event_order_to_and_including";
                                                                            }
                                                                            sql += "  RETURNING gap_global_event_order";
                                                                            var update = unitOfWork.handle().createQuery(sql)
                                                                                                   .bind("aggregate_type", requireNonNull(aggregateType, "No aggregateType provided"));
                                                                            if (resetForThisSpecificGlobalEventOrdersRange.isOpenRange()) {
                                                                                update.bind("gap_global_event_order_from_and_including", resetForThisSpecificGlobalEventOrdersRange.fromInclusive);
                                                                            } else {
                                                                                update.bind("gap_global_event_order_from_and_including", resetForThisSpecificGlobalEventOrdersRange.fromInclusive);
                                                                                update.bind("gap_global_event_order_to_and_including", resetForThisSpecificGlobalEventOrdersRange.toInclusive);
                                                                            }
                                                                            return update
                                                                                    .mapTo(GlobalEventOrder.class)
                                                                                    .list();
                                                                        });
        log.info("[{}] Removed {} Permanent Gap(s), according to reset range {}, with GlobalEventOrder: {}",
                 aggregateType,
                 globalEventOrdersRemoved.size(),
                 resetForThisSpecificGlobalEventOrdersRange,
                 globalEventOrdersRemoved);

        return globalEventOrdersRemoved;
    }

    @Override
    public List<GlobalEventOrder> resetPermanentGapsFor(AggregateType aggregateType, List<GlobalEventOrder> resetForTheseSpecificGlobalEventOrders) {
        requireNonNull(resetForTheseSpecificGlobalEventOrders, "No resetForTheseSpecificGlobalEventOrders provided");
        if (resetForTheseSpecificGlobalEventOrders.isEmpty()) {
            return resetForTheseSpecificGlobalEventOrders;
        }

        var globalEventOrdersRemoved = unitOfWorkFactory.withUnitOfWork(unitOfWork ->
                                                                        {
                                                                            var sql = "DELETE FROM " + PERMANENT_GAPS_TABLE_NAME + " WHERE aggregate_type = :aggregate_type RETURNING gap_global_event_order\n" +
                                                                                    " AND gap_global_event_order IN (<globalEventOrders>)\n" +
                                                                                    "  RETURNING gap_global_event_order";
                                                                            return unitOfWork.handle().createQuery(sql)
                                                                                             .bind("aggregate_type", requireNonNull(aggregateType, "No aggregateType provided"))
                                                                                             .bindList("globalEventOrders", resetForTheseSpecificGlobalEventOrders)
                                                                                             .mapTo(GlobalEventOrder.class)
                                                                                             .list();
                                                                        });
        log.info("[{}] Removed {} Permanent Gap(s), according to reset list {}, with GlobalEventOrder: {}",
                 aggregateType,
                 globalEventOrdersRemoved.size(),
                 resetForTheseSpecificGlobalEventOrders,
                 globalEventOrdersRemoved);

        return globalEventOrdersRemoved;
    }

    @Override
    public Stream<GlobalEventOrder> getPermanentGapsFor(AggregateType aggregateType) {
        return getPermanentGapsAsLongFor(aggregateType)
                .map(GlobalEventOrder::of);
    }

    private Stream<Long> getPermanentGapsAsLongFor(AggregateType aggregateType) {
        return unitOfWorkFactory.withUnitOfWork(unitOfWork ->
                                                        unitOfWork.handle().createQuery("SELECT gap_global_event_order FROM " + PERMANENT_GAPS_TABLE_NAME + " WHERE aggregate_type = :aggregate_type")
                                                                  .bind("aggregate_type", requireNonNull(aggregateType, "No aggregateType provided"))
                                                                  .mapTo(Long.class)
                                                                  .stream());
    }

    private class PostgresqlSubscriptionGapHandler implements SubscriptionGapHandler {
        private final SubscriberId                                                               subscriberId;
        private       OffsetDateTime                                                             transientGapsLastRefreshedFromStorage;
        private       ConcurrentMap<AggregateType, List<Pair<GlobalEventOrder, OffsetDateTime>>> allTransientGaps = new ConcurrentHashMap<>();

        public PostgresqlSubscriptionGapHandler(SubscriberId subscriberId) {
            this.subscriberId = requireNonNull(subscriberId, "No subscriberId provided");
        }

        @Override
        public SubscriberId subscriberId() {
            return subscriberId;
        }

        @Override
        public List<GlobalEventOrder> findTransientGapsToIncludeInQuery(AggregateType aggregateType, LongRange globalOrderQueryRange) {
            requireNonNull(aggregateType, "No aggregateType provided");
            requireNonNull(globalOrderQueryRange, "No globalOrderQueryRange provided");

            // Ensure all transient gaps for this aggregate type is loaded
            getTransientGapsFor(aggregateType);
            var transientGapsFor = allTransientGaps.get(aggregateType);
            if (transientGapsFor.isEmpty()) {
                return NO_GAPS;
            } else {
                return resolveTransientGapsToIncludeInQueryStrategy.resolveTransientGaps(aggregateType,
                                                                                         globalOrderQueryRange,
                                                                                         Collections.unmodifiableList(transientGapsFor));
            }
        }

        @Override
        public void reconcileGaps(AggregateType aggregateType, LongRange globalOrderQueryRange, List<PersistedEvent> persistedEvents, List<GlobalEventOrder> transientGapsIncludedInQuery) {
            requireNonNull(aggregateType, "No aggregateType provided");
            requireNonNull(globalOrderQueryRange, "No globalOrderQueryRange provided");
            requireNonNull(persistedEvents, "No persistedEvents provided");
            requireNonNull(transientGapsIncludedInQuery, "No transientGaps provided");

            log.debug("[{}] Reconciling '{}' Gaps for query with globalOrderQueryRange: {},  persistedEvents size: {} and transientGaps size: {}",
                      subscriberId,
                      aggregateType,
                      globalOrderQueryRange,
                      persistedEvents.size(),
                      transientGapsIncludedInQuery.size());

            // Resolve existing Transient Gaps
            var resolvedTransientGaps = persistedEvents.stream().map(PersistedEvent::globalEventOrder)
                                                       .filter(transientGapsIncludedInQuery::contains)
                                                       .collect(Collectors.toList());
            var findTransientGapsThatWereResolved = !transientGapsIncludedInQuery.isEmpty() && !persistedEvents.isEmpty();
            if (findTransientGapsThatWereResolved) {
                deleteTransientGaps(aggregateType,
                                    resolvedTransientGaps);
            }

            // New Transient Gaps
            if (!persistedEvents.isEmpty()) {
                var persistedEventGlobalOrders      = persistedEvents.stream().map(persistedEvent -> persistedEvent.globalEventOrder().longValue()).collect(Collectors.toList());
                var maxGlobalOrderOfPersistedEvents = persistedEventGlobalOrders.stream().max(Long::compareTo).get();
                var newTransientGapsToAdd = LongRange.between(globalOrderQueryRange.fromInclusive,
                                                              maxGlobalOrderOfPersistedEvents)
                                                     .stream()
                                                     .boxed()
                                                     .filter(globalEventOrder -> !persistedEventGlobalOrders.contains(globalEventOrder))
                                                     .map(GlobalEventOrder::of)
                                                     .collect(Collectors.toList());
                // Verify if the transient gap is already marked permanent by another subscriber (permanent gaps are defined across subscribers per aggregate type)
                var permanentGapsAmongTheNewTransientGaps = getPermanentGapsFor(aggregateType).filter(newTransientGapsToAdd::contains).collect(Collectors.toList());
                if (permanentGapsAmongTheNewTransientGaps.size() > 0) {
                    log.debug("[{}] Removed {} permanent gaps among the newly discovered transient gaps for {}: {}",
                              subscriberId,
                              permanentGapsAmongTheNewTransientGaps.size(),
                              aggregateType,
                              permanentGapsAmongTheNewTransientGaps);
                    newTransientGapsToAdd.removeAll(permanentGapsAmongTheNewTransientGaps);
                }
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Detected {} New Transient '{}' gaps: {} based on persisted events: {}",
                              subscriberId,
                              newTransientGapsToAdd.size(),
                              aggregateType,
                              newTransientGapsToAdd,
                              persistedEventGlobalOrders);
                }
                addNewTransientGaps(aggregateType, newTransientGapsToAdd);
            }

            // Promote Transient Gaps to Permanent Gaps
            log.trace("[{}] Looking for Transient '{}' gaps that can be promoted to Permanent Gaps. All Transient Gaps: {}",
                      subscriberId,
                      aggregateType,
                      allTransientGaps);
            var promotableTransientGaps = resolveTransientGapsToPermanentGapsPromotionStrategy.resolveTransientGapsReadyToBePromotedToPermanentGaps(aggregateType,
                                                                                                                                                    allTransientGaps.get(aggregateType));
            promoteTransientGapsToPermanentGaps(aggregateType,
                                                promotableTransientGaps);
        }

        private void promoteTransientGapsToPermanentGaps(AggregateType aggregateType, List<GlobalEventOrder> promotableTransientGaps) {
            if (promotableTransientGaps.isEmpty()) return;

            var unitOfWork = unitOfWorkFactory.getRequiredUnitOfWork();
            deleteTransientGaps(aggregateType, promotableTransientGaps);

            var now = now();
            var preparedBatch = unitOfWork.handle().prepareBatch("INSERT INTO " + PERMANENT_GAPS_TABLE_NAME + "\n" +
                                                                         "(aggregate_type, gap_global_event_order, added_timestamp) " +
                                                                         "VALUES (:aggregate_type, :gap_global_event_order, :added_timestamp) " +
                                                                         "ON CONFLICT DO NOTHING");

            for (var permanentGap : promotableTransientGaps) {
                preparedBatch
                        .bind("aggregate_type", aggregateType)
                        .bind("gap_global_event_order", permanentGap)
                        .bind("added_timestamp", now)
                        .add();
            }
            var rowsUpdated = Arrays.stream(preparedBatch.execute())
                                    .reduce(Integer::sum).orElse(0);
            if (rowsUpdated == promotableTransientGaps.size()) {
                log.debug("[{}] Promoted {} Transient '{}' Gaps to be Permanent Gaps: {}",
                          subscriberId,
                          promotableTransientGaps.size(),
                          aggregateType,
                          promotableTransientGaps);
            } else {
                log.debug("[{}] Promoted {} out of {} Transient '{}' Gaps to be Permanent Gaps: {}",
                          subscriberId,
                          rowsUpdated,
                          promotableTransientGaps.size(),
                          aggregateType,
                          promotableTransientGaps);
            }
        }

        private void addNewTransientGaps(AggregateType aggregateType, List<GlobalEventOrder> newTransientGapsToAdd) {
            if (newTransientGapsToAdd.isEmpty()) return;

            var unitOfWork = unitOfWorkFactory.getRequiredUnitOfWork();
            var now        = now();

            var gaps = internalGetTransientGapsFor(aggregateType);
            gaps.addAll(newTransientGapsToAdd.stream()
                                             .map(globalEventOrder -> Pair.of(globalEventOrder,
                                                                              now))
                                             .collect(Collectors.toList()));

            var preparedBatch = unitOfWork.handle().prepareBatch("INSERT INTO " + TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME + "\n" +
                                                                         "(subscriber_id, aggregate_type, gap_global_event_order, first_discovered) " +
                                                                         "VALUES (:subscriber_id, :aggregate_type, :gap_global_event_order, :first_discovered) " +
                                                                         "ON CONFLICT DO NOTHING");
            for (var transientGap : newTransientGapsToAdd) {
                preparedBatch
                        .bind("subscriber_id", subscriberId)
                        .bind("aggregate_type", aggregateType)
                        .bind("gap_global_event_order", transientGap)
                        .bind("first_discovered", now)
                        .add();
            }
            var rowsUpdated = Arrays.stream(preparedBatch.execute())
                                    .reduce(Integer::sum).orElse(0);
            if (rowsUpdated == newTransientGapsToAdd.size()) {
                log.debug("[{}] Added {} New Transient '{}' Gaps {}\nAll Transient '{}' Gaps: {}",
                          subscriberId,
                          newTransientGapsToAdd.size(),
                          aggregateType,
                          newTransientGapsToAdd,
                          aggregateType,
                          allTransientGaps);
            } else {
                log.warn("[{}] Added {} out of {} new Transient '{}' Gaps. " +
                                 "Do you have multiple instances of the same subscriber '{}' running without using exclusive subscriptions?\n" +
                                 "New Transient Gaps to add: {}",
                         subscriberId,
                         rowsUpdated,
                         newTransientGapsToAdd.size(),
                         aggregateType,
                         subscriberId,
                         allTransientGaps);
            }
        }

        private void deleteTransientGaps(AggregateType aggregateType, List<GlobalEventOrder> resolvedTransientGaps) {
            if (resolvedTransientGaps.isEmpty()) return;

            var unitOfWork = unitOfWorkFactory.getRequiredUnitOfWork();
            var gaps       = internalGetTransientGapsFor(aggregateType);
            allTransientGaps.put(aggregateType,
                                 gaps.stream()
                                     .filter(gap -> !resolvedTransientGaps.contains(gap._1))
                                     .collect(Collectors.toList()));

            var numOfRowsChanges = unitOfWork.handle().createUpdate("DELETE FROM " + TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME + "\n" +
                                                                            "    WHERE aggregate_type = :aggregate_type and gap_global_event_order IN (<resolveTransientGaps>)")
                                             .bind("aggregate_type", requireNonNull(aggregateType, "No aggregateType provided"))
                                             .bindList("resolveTransientGaps", resolvedTransientGaps)
                                             .execute();
            if (numOfRowsChanges != resolvedTransientGaps.size()) {
                log.warn("[{}] Wanted to delete {} resolved Transient '{}' gaps, but was only able to delete {} transient gaps.\n" +
                                 "Do you have multiple instances of the same subscriber '{}' running without using exclusive subscriptions?\n" +
                                 "Resolved Transient Gaps to delete: {}",
                         subscriberId,
                         resolvedTransientGaps.size(),
                         aggregateType,
                         numOfRowsChanges,
                         subscriberId,
                         resolvedTransientGaps);
            } else {
                log.debug("[{}] Deleted {} resolved Transient '{}' gaps. " +
                                  "Resolved Transient Gaps deleted: {}\n" +
                                  "All Transient '{}' Gaps: {}",
                          subscriberId,
                          resolvedTransientGaps.size(),
                          aggregateType,
                          resolvedTransientGaps,
                          aggregateType,
                          allTransientGaps);
            }
        }

        private List<Pair<GlobalEventOrder, OffsetDateTime>> internalGetTransientGapsFor(AggregateType aggregateType) {
            requireNonNull(aggregateType, "No aggregateType provided");
            var gaps = allTransientGaps.get(aggregateType);
            if (gaps == null || gaps.isEmpty() || transientGapsLastRefreshedFromStorage == null || ChronoUnit.SECONDS.between(transientGapsLastRefreshedFromStorage, now()) >= refreshTransientGapsFromStorageEverySeconds) {
                transientGapsLastRefreshedFromStorage = now();
                allTransientGaps.put(aggregateType, unitOfWorkFactory.getRequiredUnitOfWork()
                                                                     .handle()
                                                                     .createQuery("SELECT gap_global_event_order, first_discovered FROM " + TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME + "\n" +
                                                                                          "    WHERE aggregate_type = :aggregate_type and subscriber_id = :subscriber_id\n" +
                                                                                          "    ORDER BY gap_global_event_order ASC")
                                                                     .bind("aggregate_type", requireNonNull(aggregateType, "No aggregateType provided"))
                                                                     .bind("subscriber_id", subscriberId)
                                                                     .map((rs, ctx) -> Pair.of(GlobalEventOrder.of(rs.getLong("gap_global_event_order")),
                                                                                               rs.getObject("first_discovered", OffsetDateTime.class)))
                                                                     .list());
            }
            return allTransientGaps.computeIfAbsent(aggregateType, aggregateType_ -> new ArrayList<>());
        }

        @Override
        public List<GlobalEventOrder> resetTransientGapsFor(AggregateType aggregateType) {
            return unitOfWorkFactory.withUnitOfWork(unitOfWork ->
                                                            unitOfWork.handle()
                                                                      .createQuery("DELETE FROM " + TRANSIENT_SUBSCRIBER_GAPS_TABLE_NAME + "\n" +
                                                                                           "    WHERE aggregate_type = :aggregate_type and subscriber_id = :subscriber_id\n" +
                                                                                           "    RETURNING gap_global_event_order")
                                                                      .bind("aggregate_type", requireNonNull(aggregateType, "No aggregateType provided"))
                                                                      .bind("subscriber_id", subscriberId)
                                                                      .mapTo(GlobalEventOrder.class)
                                                                      .list()
                                                   );
        }

        @Override
        public List<GlobalEventOrder> getTransientGapsFor(AggregateType aggregateType) {
            return unitOfWorkFactory.withUnitOfWork(unitOfWork ->
                                                            internalGetTransientGapsFor(aggregateType)
                                                                    .stream()
                                                                    .map(Pair::_1)
                                                                    .collect(Collectors.toList()));
        }

        @Override
        public Stream<GlobalEventOrder> getPermanentGapsFor(AggregateType aggregateType) {
            return unitOfWorkFactory.getRequiredUnitOfWork()
                                    .handle()
                                    .createQuery("SELECT gap_global_event_order FROM " + PERMANENT_GAPS_TABLE_NAME + "\n" +
                                                         "    WHERE aggregate_type = :aggregate_type\n" +
                                                         "    ORDER BY gap_global_event_order ASC")
                                    .bind("aggregate_type", requireNonNull(aggregateType, "No aggregateType provided"))
                                    .mapTo(GlobalEventOrder.class)
                                    .stream();
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o.getClass().equals(PostgresqlSubscriptionGapHandler.class))) return false;
            PostgresqlSubscriptionGapHandler that = (PostgresqlSubscriptionGapHandler) o;
            return subscriberId.equals(that.subscriberId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subscriberId);
        }

        @Override
        public String toString() {
            return "PostgresqlSubscriptionGapHandler{" +
                    "subscriberId=" + subscriberId +
                    ", transientGapsLastRefreshedFromStorage=" + transientGapsLastRefreshedFromStorage +
                    ", transientGaps(#" + allTransientGaps.size() + ")=" + allTransientGaps +
                    '}';
        }
    }

    /**
     * Strategy the allows user of the {@link PostgresqlEventStreamGapHandler} to determine which and how many transient gaps to include in
     * a given call to the {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)} during {@link EventStore#pollEvents(AggregateType, long, Optional, Optional, Optional, Optional)}
     */
    @FunctionalInterface
    public interface ResolveTransientGapsToIncludeInQueryStrategy {
        /**
         * Based on the <code>globalOrderQueryRange</code> resolve which transient gaps that should be included in the {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Tenant)}
         *
         * @param forAggregateType      the aggregate type we want to resolve gaps for
         * @param globalOrderQueryRange the global order query range being used when querying for new events
         * @param allTransientGaps      all the currently known transient gaps ({@link Pair#_1} is the Gap {@link GlobalEventOrder}
         *                              and {@link Pair#_2} is the gaps firstDiscoveredTimestamp)
         * @return a list of {@link GlobalEventOrder} gaps (can be null or empty if no transient gaps exists for this subscriber)
         */
        List<GlobalEventOrder> resolveTransientGaps(AggregateType forAggregateType, LongRange globalOrderQueryRange, List<Pair<GlobalEventOrder, OffsetDateTime>> allTransientGaps);
    }

    /**
     * Strategy the allows user of the {@link PostgresqlEventStreamGapHandler} to determine when
     * a transient gap is promoted to a permanent gap
     *
     * @see #thresholdBased(int)
     */
    @FunctionalInterface
    public interface ResolveTransientGapsToPermanentGapsPromotionStrategy {
        /**
         * Determine which transient gaps should be promoted to permanent gaps.<br>
         * The simplest solution is to use a time based approach where any transient gap that has existed for longer than the specified
         * Jdbi <code>handle.getConfig(SqlStatements.class).setQueryTimeout(seconds);</code> or Spring <code>PlatformTransactionManager#setDefaultTimeout(seconds)</code>
         * is promoted to permanent gaps - see {@link #thresholdBased(int)}
         *
         * @param forAggregateType the aggregate type we want to determine which transient gaps should be promoted to permanent gaps
         * @param allTransientGaps all the currently known transient gaps ({@link Pair#_1} is the Gap {@link GlobalEventOrder}
         *                         and {@link Pair#_2} is the gaps firstDiscoveredTimestamp)
         * @return a list of {@link GlobalEventOrder} transient gaps that will be promoted to permanent gaps
         */
        List<GlobalEventOrder> resolveTransientGapsReadyToBePromotedToPermanentGaps(AggregateType forAggregateType, List<Pair<GlobalEventOrder, OffsetDateTime>> allTransientGaps);

        /**
         * Default strategy where the time between the transient gaps firstDiscoveredTimestamp ({@link Pair#_2}) and now is larger than
         * <code>permanentGapThresholdInSeconds</code> then the transient gap is promoted to a permanent gap
         *
         * @param permanentGapThresholdInSeconds if the time between the transient gaps firstDiscoveredTimestamp ({@link Pair#_2}) and <b>now</b> is larger than
         *                                       <code>permanentGapThresholdInSeconds</code> then a transient gap is promoted to a permanent gap
         * @return the default threshold based strategy
         */
        static ResolveTransientGapsToPermanentGapsPromotionStrategy thresholdBased(int permanentGapThresholdInSeconds) {
            requireTrue(permanentGapThresholdInSeconds > 0, "permanentGapThresholdInSeconds must be > 0");
            return (forAggregateType, allTransientGaps) -> {
                var now = now();
                return allTransientGaps.stream()
                                       .filter(globalEventOrderFirstDiscoveredTimestampPair -> {
                                           var secondsBetween = ChronoUnit.SECONDS.between(globalEventOrderFirstDiscoveredTimestampPair._2, now);
                                           if (log.isTraceEnabled()) {
                                               log.trace("{} seconds since '{}' Transient Gap with GlobalOrder {} was first discovered",
                                                         secondsBetween,
                                                         forAggregateType,
                                                         globalEventOrderFirstDiscoveredTimestampPair._1);
                                           }
                                           return secondsBetween >
                                                   permanentGapThresholdInSeconds;
                                       })
                                       .map(Pair::_1)
                                       .collect(Collectors.toList());

            };
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(Clock.systemUTC());
    }
}
