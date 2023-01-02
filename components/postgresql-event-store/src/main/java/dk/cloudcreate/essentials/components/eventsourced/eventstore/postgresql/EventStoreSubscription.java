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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.SubscriptionResumePoint;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.types.*;

import java.util.Optional;

public interface EventStoreSubscription extends Lifecycle {
    /**
     * the unique id for the subscriber
     *
     * @return the unique id for the subscriber
     */
    SubscriberId subscriberId();

    /**
     * the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     *
     * @return the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     */
    AggregateType aggregateType();

    void unsubscribe();

    /**
     * Reset the subscription point.<br>
     *
     * @param subscribeFromAndIncludingGlobalOrder this {@link GlobalEventOrder} will become the starting point in the
     *                                             EventStream associated with the <code>aggregateType</code>
     */
    void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder);

    /**
     * Get the subscriptions resume point (if supported by the subscription)
     *
     * @return the subscriptions resume point
     */
    Optional<SubscriptionResumePoint> currentResumePoint();

    /**
     * If {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     */
    Optional<Tenant> onlyIncludeEventsForTenant();

    /**
     */
    boolean isActive();
}
