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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.api;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.SubscriptionResumePoint;
import dk.trustworks.essentials.components.foundation.types.SubscriberId;

import java.time.OffsetDateTime;

/**
 * <pre>
 * Represents a subscription to an aggregate type for a specific subscriber.
 * This class encapsulates the details of a subscription, including the unique subscriber ID,
 * the type of aggregate being subscribed to, the current global order of the subscription,
 * and the last time the subscription was updated.
 *
 * </pre>
 *
 * @param subscriberId        Unique identifier for the subscriber.
 * @param aggregateType       The type of aggregate being subscribed to.
 * @param currentGlobalOrder  The current global order position for the subscription.
 * @param lastUpdated         The timestamp of the last update to the subscription.
 */
public record ApiSubscription(
        SubscriberId subscriberId,
        AggregateType aggregateType,
        long currentGlobalOrder,
        OffsetDateTime lastUpdated
) {

    public static ApiSubscription from(SubscriptionResumePoint subscriptionResumePoint) {
        return new ApiSubscription(
                subscriptionResumePoint.getSubscriberId(),
                subscriptionResumePoint.getAggregateType(),
                subscriptionResumePoint.getResumeFromAndIncluding().longValue(),
                subscriptionResumePoint.getLastUpdated()
        );
    }

    @Override
    public String toString() {
        return "ApiSubscription{" +
                "subscriberId=" + subscriberId +
                ", aggregateType=" + aggregateType +
                ", currentGlobalOrder=" + currentGlobalOrder +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
