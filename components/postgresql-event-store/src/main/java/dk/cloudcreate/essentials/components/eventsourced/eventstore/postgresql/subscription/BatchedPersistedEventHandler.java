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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreSubscription;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;

import java.util.List;

/**
 * Handler for processing batches of {@link PersistedEvent}s. Implementations can
 * optimize processing by handling events in bulk rather than one at a time.
 * <p>
 * This handler is used by the {@link BatchedPersistedEventSubscriber}, which collects
 * events up to a specified batch size or maximum latency before invoking the handler.
 */
public interface BatchedPersistedEventHandler {
    /**
     * Handle a batch of {@link PersistedEvent}s. The batch is provided as an immutable
     * list of events in the order they were received.
     * <p>
     * This method is called within the context of a {@link UnitOfWork}
     * and is automatically retried for IO related exceptions according to the retry policy
     * configured in the {@link BatchedPersistedEventSubscriber}.
     * <p>
     * The method can return an integer indicating the number of events to request from
     * the event store after processing this batch. A value of 0 or negative means
     * to request a default of 1 event.
     *
     * @param events the batch of events to handle, guaranteed to be non-empty and in order
     * @return number of events to request next (0 or negative values will request 1 event)
     */
    int handleBatch(List<PersistedEvent> events);

    /**
     * Called when the subscription is reset to start from a specific {@link GlobalEventOrder}
     *
     * @param subscription                         the subscription that will be reset
     * @param subscribeFromAndIncludingGlobalOrder the global order to reset to
     */
    default void onResetFrom(EventStoreSubscription subscription, GlobalEventOrder subscribeFromAndIncludingGlobalOrder) {
        // Default is no-op
    }
}