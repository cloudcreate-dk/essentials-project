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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.jdbi.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.*;

import java.time.*;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class PostgresqlDurableSubscriptionRepository implements DurableSubscriptionRepository {
    private static final Logger log                                      = LoggerFactory.getLogger(PostgresqlDurableSubscriptionRepository.class);
    /**
     * The default name for the table name that will store the durable resume points
     */
    public static final  String DEFAULT_DURABLE_SUBSCRIPTIONS_TABLE_NAME = "durable_subscriptions";
    private final        Jdbi   jdbi;
    private final        String durableSubscriptionsTableName;

    /**
     * Create a {@link PostgresqlDurableSubscriptionRepository} using the default {@value #DEFAULT_DURABLE_SUBSCRIPTIONS_TABLE_NAME}
     *
     * @param jdbi the jdbi instance
     */
    public PostgresqlDurableSubscriptionRepository(Jdbi jdbi) {
        this(jdbi,
             DEFAULT_DURABLE_SUBSCRIPTIONS_TABLE_NAME);
    }

    /**
     * Create a {@link PostgresqlDurableSubscriptionRepository} with a specific durableSubscriptionsTableName
     *
     * @param jdbi                          the jdbi instance
     * @param durableSubscriptionsTableName the table name that will store the durable resume points
     */
    public PostgresqlDurableSubscriptionRepository(Jdbi jdbi,
                                                   String durableSubscriptionsTableName) {
        this.jdbi = requireNonNull(jdbi, "No Jdbi instance provided");
        this.durableSubscriptionsTableName = requireNonNull(durableSubscriptionsTableName, "No durableSubscriptionsTableName provided");
        log.info("Using durableSubscriptionsTableName: '{}'", durableSubscriptionsTableName);
        jdbi.registerArgument(new AggregateTypeArgumentFactory());
        jdbi.registerColumnMapper(new AggregateTypeColumnMapper());
        jdbi.registerArgument(new SubscriberIdArgumentFactory());
        jdbi.registerColumnMapper(new SubscriberIdColumnMapper());
        jdbi.useTransaction(handle -> {
            var tablesCreated = handle.execute("CREATE TABLE IF NOT EXISTS " + durableSubscriptionsTableName + " (\n" +
                                                       "subscriber_id TEXT NOT NULL,\n" +
                                                       "aggregate_type TEXT NOT NULL,\n" +
                                                       "resume_from_and_including_global_eventorder bigint,\n" +
                                                       "last_updated TIMESTAMP WITH TIME ZONE,\n" +
                                                       "PRIMARY KEY (subscriber_id, aggregate_type))");
            if (tablesCreated == 1) {
                log.debug("Created table '{}'", durableSubscriptionsTableName);
            } else {
                log.debug("'{}' table already exists", durableSubscriptionsTableName);
            }
        });
    }

    @Override
    public SubscriptionResumePoint createResumePoint(SubscriberId subscriberId,
                                                     AggregateType forAggregateType,
                                                     GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder) {
        requireNonNull(subscriberId, "No subscriberId value provided");
        requireNonNull(forAggregateType, "No forAggregateType value provided");
        requireNonNull(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder, "No onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder value provided");
        return jdbi.inTransaction(handle -> {
            var now = OffsetDateTime.now(Clock.systemUTC());
            var rowsUpdated = handle.createUpdate("INSERT INTO " + durableSubscriptionsTableName + " (\n" +
                                                          "aggregate_type, subscriber_id, resume_from_and_including_global_eventorder, last_updated)\n" +
                                                          "VALUES (:aggregate_type, :subscriber_id, :resume_from_and_including_global_eventorder, :last_updated) ON CONFLICT DO NOTHING")
                                    .bind("aggregate_type", forAggregateType)
                                    .bind("subscriber_id", subscriberId)
                                    .bind("resume_from_and_including_global_eventorder", onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder)
                                    .bind("last_updated", now)
                                    .execute();
            if (rowsUpdated == 0) {
                throw new IllegalStateException(msg("Cannot createResumePoint for subscriberId '{}' and aggregateType '{}' since a ResumePoint already exists",
                                                    subscriberId,
                                                    forAggregateType));
            } else {
                var subscriptionResumePoint = new SubscriptionResumePoint(subscriberId,
                                                                          forAggregateType,
                                                                          onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                                                          now);
                log.debug("Created {}", subscriptionResumePoint);
                return subscriptionResumePoint;
            }
        });
    }

    @Override
    public void deleteResumePoint(SubscriberId subscriberId, AggregateType aggregateType) {
        requireNonNull(aggregateType, "No aggregateType value provided");
        requireNonNull(subscriberId, "No subscriberId value provided");
        var rowsDeleted = jdbi.inTransaction(handle -> handle.createUpdate("DELETE FROM " + durableSubscriptionsTableName +
                                                                                   " WHERE aggregate_type = :aggregate_type AND subscriber_id = :subscriber_id")
                                                             .bind("aggregate_type", aggregateType)
                                                             .bind("subscriber_id", subscriberId)
                                                             .execute());
        if (rowsDeleted == 1) {
            PostgresqlDurableSubscriptionRepository.log.debug("Deleted ResumePoint for subscriberId: '{}' and aggregateType '{}'", subscriberId, aggregateType);
        } else {
            PostgresqlDurableSubscriptionRepository.log.debug("Didn't find ResumePoint to Delete for subscriberId: '{}' and aggregateType '{}'", subscriberId, aggregateType);
        }
    }

    @Override
    public Optional<SubscriptionResumePoint> getResumePoint(SubscriberId subscriberId,
                                                            AggregateType forAggregateType) {
        requireNonNull(forAggregateType, "No forAggregateType value provided");
        requireNonNull(subscriberId, "No subscriberId value provided");
        var subscriptionResumePoint = jdbi.inTransaction(handle -> handle.createQuery("SELECT resume_from_and_including_global_eventorder, last_updated FROM " + durableSubscriptionsTableName +
                                                                                              " WHERE aggregate_type = :aggregate_type AND subscriber_id = :subscriber_id")
                                                                         .bind("aggregate_type", forAggregateType)
                                                                         .bind("subscriber_id", subscriberId)
                                                                         .map((rs, ctx) -> new SubscriptionResumePoint(subscriberId,
                                                                                                                       forAggregateType,
                                                                                                                       GlobalEventOrder.of(rs.getLong("resume_from_and_including_global_eventorder")),
                                                                                                                       rs.getObject("last_updated", OffsetDateTime.class)))
                                                                         .findOne());
        if (subscriptionResumePoint.isPresent()) {
            log.debug("Found {}", subscriptionResumePoint.get());
        } else {
            log.debug("Didn't find ResumePoint for subscriberId: '{}' and aggregateType '{}'", subscriberId, forAggregateType);
        }
        return subscriptionResumePoint;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public void saveResumePoints(Collection<SubscriptionResumePoint> resumePoints) {
        requireNonNull(resumePoints, "No resumePoints provided");
        if (resumePoints.isEmpty()) {
            log.trace("No resumePoints to save");
            return;
        } else {
            log.trace("Received {} ResumePoints to save: {}", resumePoints.size(), resumePoints);
        }

        jdbi.useTransaction(handle -> {
            var now = OffsetDateTime.now(Clock.systemUTC());
            var preparedBatch = handle.prepareBatch("UPDATE " + durableSubscriptionsTableName +
                                                            " SET resume_from_and_including_global_eventorder = :resume_from_and_including_global_eventorder, last_updated = :last_updated " +
                                                            " WHERE aggregate_type = :aggregate_type AND subscriber_id = :subscriber_id");

            // TODO: Optimize filtering to happen outside db trx
            resumePoints.forEach(subscriptionResumePoint -> {
                if (!subscriptionResumePoint.isChanged()) {
                    log.debug("Wont save Unchanged {}", subscriptionResumePoint);
                    return;
                }
                log.debug("Saving {}", subscriptionResumePoint);
                preparedBatch
                        .bind("aggregate_type", subscriptionResumePoint.getAggregateType())
                        .bind("subscriber_id", subscriptionResumePoint.getSubscriberId())
                        .bind("resume_from_and_including_global_eventorder", subscriptionResumePoint.getResumeFromAndIncluding())
                        .bind("last_updated", now)
                        .add();
            });

            var batchSize = preparedBatch.size();
            var rowsUpdated = Arrays.stream(preparedBatch.execute())
                                    .reduce(Integer::sum).orElse(0);
            resumePoints.forEach(subscriptionResumePoint -> {
                if (subscriptionResumePoint.isChanged()) {
                    subscriptionResumePoint.setLastUpdated(now);
                }
            });
            if (log.isTraceEnabled()) {
                log.trace("Saved {} resumePoints out of {} resulting in {} updated rows: {}",
                          batchSize,
                          resumePoints.size(),
                          rowsUpdated,
                          resumePoints);
            } else {
                log.debug("Saved {} resumePoints out of {} resulting in {} updated rows",
                          batchSize,
                          resumePoints.size(),
                          rowsUpdated);
            }
        });
    }
}
