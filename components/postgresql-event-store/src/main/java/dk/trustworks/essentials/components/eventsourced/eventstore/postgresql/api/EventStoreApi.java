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
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;

import java.util.List;
import java.util.Optional;

/**
 * EventStoreApi serves as an interface to manage and query event store-related operations,
 * such as retrieving the highest persisted global event order or fetching all subscriptions.
 */
public interface EventStoreApi {

    /**
     * Retrieves the highest global event order that has been persisted in the event store
     * for the specified aggregate type. The result is encapsulated in an {@code Optional},
     * which will be empty if no events have been persisted for the given aggregate type.
     *
     * @param principal       the principal or identity querying the event store, typically
     *                        representing the authenticated user or system performing the action
     * @param aggregateType   the type of aggregate for which the highest persisted global event order
     *                        is being requested
     * @return an {@code Optional} containing the highest {@code GlobalEventOrder} if one exists
     *         for the specified aggregate type, or an empty {@code Optional} if no events exist
     */
    Optional<GlobalEventOrder> findHighestGlobalEventOrderPersisted(Object principal, AggregateType aggregateType);

    /**
     * Retrieves all subscriptions that are currently active in the system. Each subscription represents
     * a subscriber's subscription to an aggregate type, including details such as the subscriber ID,
     * the aggregate type, the current global order position, and the last updated timestamp.
     *
     * @param principal the principal or identity requesting the subscriptions, typically representing
     *                  the authenticated user or system performing the action
     * @return a list of {@code ApiSubscription} objects representing all active subscriptions in the system
     */
    List<ApiSubscription> findAllSubscriptions(Object principal);
}
