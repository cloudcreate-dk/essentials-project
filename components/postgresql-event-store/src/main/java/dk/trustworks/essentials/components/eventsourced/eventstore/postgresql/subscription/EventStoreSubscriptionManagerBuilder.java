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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.trustworks.essentials.components.foundation.fencedlock.*;

import java.time.Duration;

public final class EventStoreSubscriptionManagerBuilder {
    private EventStore                    eventStore;
    private int                           eventStorePollingBatchSize = 100;
    private Duration                      eventStorePollingInterval  = Duration.ofMillis(500);
    private FencedLockManager             fencedLockManager;
    private Duration                      snapshotResumePointsEvery  = Duration.ofSeconds(1);
    private DurableSubscriptionRepository durableSubscriptionRepository;
    private boolean startLifeCycles = true;

    /**
     * @param eventStore the event store that the created {@link EventStoreSubscriptionManager} can manage event subscriptions against
     * @return this builder
     */
    public EventStoreSubscriptionManagerBuilder setEventStore(EventStore eventStore) {
        this.eventStore = eventStore;
        return this;
    }

    /**
     * @param eventStorePollingBatchSize how many events should The {@link EventStore} maximum return when polling for events - default 100
     * @return this builder
     */
    public EventStoreSubscriptionManagerBuilder setEventStorePollingBatchSize(int eventStorePollingBatchSize) {
        this.eventStorePollingBatchSize = eventStorePollingBatchSize;
        return this;
    }

    /**
     * @param eventStorePollingInterval how often should the {@link EventStore} be polled for new events - default 500 ms
     * @return this builder
     */
    public EventStoreSubscriptionManagerBuilder setEventStorePollingInterval(Duration eventStorePollingInterval) {
        this.eventStorePollingInterval = eventStorePollingInterval;
        return this;
    }

    /**
     * @param fencedLockManager the {@link FencedLockManager} that will be used to acquire a {@link FencedLock} for exclusive asynchronous subscriptions
     * @return this builder
     */
    public EventStoreSubscriptionManagerBuilder setFencedLockManager(FencedLockManager fencedLockManager) {
        this.fencedLockManager = fencedLockManager;
        return this;
    }

    /**
     * @param snapshotResumePointsEvery How often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock)
     *                                  subscribers have their {@link SubscriptionResumePoint} saved - default: every 1 second
     * @return this builder
     */
    public EventStoreSubscriptionManagerBuilder setSnapshotResumePointsEvery(Duration snapshotResumePointsEvery) {
        this.snapshotResumePointsEvery = snapshotResumePointsEvery;
        return this;
    }

    /**
     * @param durableSubscriptionRepository The repository responsible for persisting {@link SubscriptionResumePoint}
     * @return this builder
     */
    public EventStoreSubscriptionManagerBuilder setDurableSubscriptionRepository(DurableSubscriptionRepository durableSubscriptionRepository) {
        this.durableSubscriptionRepository = durableSubscriptionRepository;
        return this;
    }

    public EventStoreSubscriptionManagerBuilder setStartLifeCycles(boolean startLifeCycles) {
        this.startLifeCycles = startLifeCycles;
        return this;
    }

    public EventStoreSubscriptionManager.DefaultEventStoreSubscriptionManager build() {
        return new EventStoreSubscriptionManager.DefaultEventStoreSubscriptionManager(eventStore,
                                                                                      eventStorePollingBatchSize,
                                                                                      eventStorePollingInterval,
                                                                                      fencedLockManager,
                                                                                      snapshotResumePointsEvery,
                                                                                      durableSubscriptionRepository,
                                                                                      startLifeCycles);
    }
}