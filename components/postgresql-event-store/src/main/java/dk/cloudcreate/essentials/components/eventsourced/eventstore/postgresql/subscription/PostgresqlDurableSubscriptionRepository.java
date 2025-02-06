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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.jdbi.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.IOExceptionUtil;
import dk.cloudcreate.essentials.components.foundation.postgresql.PostgresqlUtil;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.HandleAwareUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.*;

import java.time.*;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Postgresql version of the {@link DurableSubscriptionRepository}<br>
 * <u>Security:</u><br>
 * The user of this component can provide a {@code durableSubscriptionsTableName} which controls the table name where the component will store the durable resume points<br>
 * <strong>Note:</strong><br>
 * To support customization of storage table name, the {@code durableSubscriptionsTableName} will be directly used in constructing SQL statements
 * through string concatenation, which exposes the component to SQL injection attacks.<br>
 * <br>
 * <strong>Security Note:</strong><br>
 * It is the responsibility of the user of this component to sanitize the {@code durableSubscriptionsTableName}
 * to ensure the security of all the SQL statements generated by this component. The {@link PostgresqlDurableSubscriptionRepository} component will
 * call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
 * The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
 * <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
 * Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
 * <br>
 * It is highly recommended that the {@code durableSubscriptionsTableName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code durableSubscriptionsTableName} value.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
 * vulnerabilities, compromising the security and integrity of the database.</b>
 */
public final class PostgresqlDurableSubscriptionRepository implements DurableSubscriptionRepository {
    private static final Logger                          log                                      = LoggerFactory.getLogger(PostgresqlDurableSubscriptionRepository.class);
    /**
     * The default name for the table name that will store the durable resume points
     */
    public static final  String                          DEFAULT_DURABLE_SUBSCRIPTIONS_TABLE_NAME = "durable_subscriptions";
    private final        Jdbi                            jdbi;
    private final        String                          durableSubscriptionsTableName;
    private final        HandleAwareUnitOfWorkFactory<?> unitOfWorkFactory;
    private final        EventStore                      eventStore;

    /**
     * Create a {@link PostgresqlDurableSubscriptionRepository} using the default {@value #DEFAULT_DURABLE_SUBSCRIPTIONS_TABLE_NAME}
     *
     * @param jdbi       the jdbi instance
     * @param eventStore The {@link EventStore} that supplies events for subscriptions
     */
    public PostgresqlDurableSubscriptionRepository(Jdbi jdbi,
                                                   EventStore eventStore) {
        this(jdbi,
             eventStore,
             DEFAULT_DURABLE_SUBSCRIPTIONS_TABLE_NAME);
    }

    /**
     * Create a {@link PostgresqlDurableSubscriptionRepository} with a specific durableSubscriptionsTableName
     *
     * @param jdbi                          the jdbi instance
     * @param eventStore                    The {@link EventStore} that supplies events for subscriptions
     * @param durableSubscriptionsTableName the table name that will store the durable resume points<br>
     *                                      <strong>Note:</strong><br>
     *                                      To support customization of storage table name, the {@code durableSubscriptionsTableName} will be directly used in constructing SQL statements
     *                                      through string concatenation, which exposes the component to SQL injection attacks.<br>
     *                                      <br>
     *                                      <strong>Security Note:</strong><br>
     *                                      It is the responsibility of the user of this component to sanitize the {@code durableSubscriptionsTableName}
     *                                      to ensure the security of all the SQL statements generated by this component. The {@link PostgresqlDurableSubscriptionRepository} component will
     *                                      call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
     *                                      The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                                      However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
     *                                      <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
     *                                      Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
     *                                      Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
     *                                      <br>
     *                                      It is highly recommended that the {@code durableSubscriptionsTableName} value is only derived from a controlled and trusted source.<br>
     *                                      To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code durableSubscriptionsTableName} value.<br>
     *                                      <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
     *                                      vulnerabilities, compromising the security and integrity of the database.</b>
     */
    public PostgresqlDurableSubscriptionRepository(Jdbi jdbi,
                                                   EventStore eventStore,
                                                   String durableSubscriptionsTableName) {
        this.eventStore = requireNonNull(eventStore, "No eventStore instance provided");
        this.jdbi = requireNonNull(jdbi, "No Jdbi instance provided");
        this.unitOfWorkFactory = eventStore.getUnitOfWorkFactory();
        this.durableSubscriptionsTableName = requireNonNull(durableSubscriptionsTableName, "No durableSubscriptionsTableName provided").toLowerCase();
        PostgresqlUtil.checkIsValidTableOrColumnName(this.durableSubscriptionsTableName);

        log.info("Using durableSubscriptionsTableName: '{}'", this.durableSubscriptionsTableName);
        jdbi.registerArgument(new AggregateTypeArgumentFactory());
        jdbi.registerColumnMapper(new AggregateTypeColumnMapper());
        jdbi.registerArgument(new SubscriberIdArgumentFactory());
        jdbi.registerColumnMapper(new SubscriberIdColumnMapper());
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            uow.handle().execute("CREATE TABLE IF NOT EXISTS " + this.durableSubscriptionsTableName + " (\n" +
                                         "subscriber_id TEXT NOT NULL,\n" +
                                         "aggregate_type TEXT NOT NULL,\n" +
                                         "resume_from_and_including_global_eventorder bigint,\n" +
                                         "last_updated TIMESTAMP WITH TIME ZONE,\n" +
                                         "PRIMARY KEY (subscriber_id, aggregate_type))");
            log.info("Ensured '{}' table exists", this.durableSubscriptionsTableName);
        });
    }

    @Override
    public SubscriptionResumePoint createResumePoint(SubscriberId subscriberId,
                                                     AggregateType forAggregateType,
                                                     GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder) {
        requireNonNull(subscriberId, "No subscriberId value provided");
        requireNonNull(forAggregateType, "No forAggregateType value provided");
        requireNonNull(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder, "No onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder value provided");
        return unitOfWorkFactory.withUnitOfWork(uow -> {
            var subscribeFromGlobalEventOrder = eventStore.findLowestGlobalEventOrderPersisted(forAggregateType).map(lowestGlobalEventOrderPersisted -> {
                if (lowestGlobalEventOrderPersisted.isGreaterThan(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder)) {
                    log.info("[{}-{}] Was requested to start subscription from globalEventOrder {} but the lowestGlobalEventOrderPersisted was {}. Will start subscription from globalEventOrder: {}",
                             subscriberId,
                             forAggregateType,
                             onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                             lowestGlobalEventOrderPersisted,
                             lowestGlobalEventOrderPersisted);
                    return lowestGlobalEventOrderPersisted;
                } else {
                    return onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder;
                }
            }).orElse(onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);
            var now = OffsetDateTime.now(Clock.systemUTC());
            var rowsUpdated = uow.handle().createUpdate("INSERT INTO " + this.durableSubscriptionsTableName + " (\n" +
                                                                "aggregate_type, subscriber_id, resume_from_and_including_global_eventorder, last_updated)\n" +
                                                                "VALUES (:aggregate_type, :subscriber_id, :resume_from_and_including_global_eventorder, :last_updated) ON CONFLICT DO NOTHING")
                                 .bind("aggregate_type", forAggregateType)
                                 .bind("subscriber_id", subscriberId)
                                 .bind("resume_from_and_including_global_eventorder", subscribeFromGlobalEventOrder)
                                 .bind("last_updated", now)
                                 .execute();
            if (rowsUpdated == 0) {
                throw new IllegalStateException(msg("Cannot createResumePoint for subscriberId '{}' and aggregateType '{}' since a ResumePoint already exists",
                                                    subscriberId,
                                                    forAggregateType));
            } else {
                var subscriptionResumePoint = new SubscriptionResumePoint(subscriberId,
                                                                          forAggregateType,
                                                                          subscribeFromGlobalEventOrder,
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
        var rowsDeleted = unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createUpdate("DELETE FROM " + this.durableSubscriptionsTableName +
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
        var subscriptionResumePoint = unitOfWorkFactory.withUnitOfWork(uow -> uow.handle().createQuery("SELECT resume_from_and_including_global_eventorder, last_updated FROM " + this.durableSubscriptionsTableName +
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

        try {
            unitOfWorkFactory.usingUnitOfWork(uow -> {
                var now = OffsetDateTime.now(Clock.systemUTC());
                var preparedBatch = uow.handle().prepareBatch("UPDATE " + this.durableSubscriptionsTableName +
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
        } catch (Exception e) {
            if (IOExceptionUtil.isIOException(e)) {
                log.debug("Failed to save the {} ResumePoints", resumePoints.size(), e);
            } else {
                log.error("Failed to save the {} ResumePoints", resumePoints.size(), e);
            }
        }
    }
}
