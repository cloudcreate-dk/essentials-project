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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Repository responsible for persisting {@link SubscriptionResumePoint}'s maintained by the {@link EventStoreSubscriptionManager}
 *
 * @see PostgresqlDurableSubscriptionRepository
 */
public interface DurableSubscriptionRepository {

    /**
     * Create an initial Resume Point
     *
     * @param subscriberIdAggregateTypePair                           the combination of {@link SubscriberId} and {@link AggregateType} which uniquely define a resume point
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder the {@link GlobalEventOrder} resume point
     * @return the resume point
     * @throws IllegalStateException in case the resume point already exists. Consider using {@link #getOrCreateResumePoint(SubscriberId, AggregateType, GlobalEventOrder)}
     */
    default SubscriptionResumePoint createResumePoint(Pair<SubscriberId, AggregateType> subscriberIdAggregateTypePair,
                                                      GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder) {
        requireNonNull(subscriberIdAggregateTypePair, "No subscriberIdAggregateTypePair value provided");
        return createResumePoint(subscriberIdAggregateTypePair._1,
                                 subscriberIdAggregateTypePair._2,
                                 onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);
    }

    /**
     * Create an initial Resume Point for the combination of {@link SubscriberId} and {@link AggregateType} which uniquely define a resume point
     *
     * @param subscriberId                                            the subscriber id
     * @param forAggregateType                                        the aggregate type
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder the {@link GlobalEventOrder} resume point
     * @return the resume point
     * @throws IllegalStateException in case the resume point already exists. Consider using {@link #getOrCreateResumePoint(SubscriberId, AggregateType, GlobalEventOrder)}
     */
    SubscriptionResumePoint createResumePoint(SubscriberId subscriberId,
                                              AggregateType forAggregateType,
                                              GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);

    /**
     * Get Resume Point
     *
     * @param subscriberIdAggregateTypePair the combination of {@link SubscriberId} and {@link AggregateType} which uniquely define a resume point
     * @return an {@link Optional} with the resume point, or an {@link Optional#empty()} if the resume point doesn't exist
     */
    default Optional<SubscriptionResumePoint> getResumePoint(Pair<SubscriberId, AggregateType> subscriberIdAggregateTypePair) {
        requireNonNull(subscriberIdAggregateTypePair, "No subscriberIdAggregateTypePair value provided");
        return getResumePoint(subscriberIdAggregateTypePair._1,
                              subscriberIdAggregateTypePair._2);
    }

    /**
     * Get Resume Point for the combination of {@link SubscriberId} and {@link AggregateType} which uniquely define a resume point
     *
     * @param subscriberId     the subscriber id
     * @param forAggregateType the aggregate type
     * @return an {@link Optional} with the resume point, or an {@link Optional#empty()} if the resume point doesn't exist
     */
    Optional<SubscriptionResumePoint> getResumePoint(SubscriberId subscriberId,
                                                     AggregateType forAggregateType);

    /**
     * Delete a resume point
     *
     * @param resumePoint the resume point to delete
     */
    default void deleteResumePoint(SubscriptionResumePoint resumePoint) {
        requireNonNull(resumePoint, "No resumePoint provided");
        deleteResumePoint(resumePoint.getSubscriberId(),
                          resumePoint.getAggregateType());
    }

    /**
     * Delete the Resume Point for the combination of {@link SubscriberId} and {@link AggregateType} which uniquely define a resume point
     *
     * @param subscriberId  the subscriber id
     * @param aggregateType the aggregate type
     */
    void deleteResumePoint(SubscriberId subscriberId, AggregateType aggregateType);


    /**
     * Save the resume point if it's {@link SubscriptionResumePoint#isChanged()}
     *
     * @param resumePoint the resume point to save
     */
    default void saveResumePoint(SubscriptionResumePoint resumePoint) {
        requireNonNull(resumePoint, "No resumePoint provided");
        saveResumePoints(List.of(resumePoint));
    }

    /**
     * Batch save all the resume points. Only {@link SubscriptionResumePoint#isChanged()} Resume Points will saved
     *
     * @param resumePoints the resume points to save
     */
    void saveResumePoints(Collection<SubscriptionResumePoint> resumePoints);

    /**
     * Get or Create a Resume point for the combination of {@link SubscriberId} and {@link AggregateType} which uniquely define a resume point<br>
     * Logic is:<br>
     * {@link #getResumePoint(SubscriberId, AggregateType)} and if the resume point doesn't exist then {@link #createResumePoint(SubscriberId, AggregateType, GlobalEventOrder)}
     *
     * @param subscriberId                                            the subscriber id
     * @param forAggregateType                                        the aggregate type
     * @param onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder the {@link GlobalEventOrder} resume point that will be used if the resume didn't exist
     * @return the resume point
     */
    default SubscriptionResumePoint getOrCreateResumePoint(SubscriberId subscriberId,
                                                           AggregateType forAggregateType,
                                                           GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder) {
        return getResumePoint(subscriberId,
                              forAggregateType)
                .orElseGet(() -> createResumePoint(subscriberId,
                                                   forAggregateType,
                                                   onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder));
    }
}