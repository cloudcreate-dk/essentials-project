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

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.DurableSubscriptionRepository;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.trustworks.essentials.shared.security.EssentialsSecurityProvider;

import java.util.List;
import java.util.Optional;

import static dk.trustworks.essentials.shared.security.EssentialsSecurityRoles.ESSENTIALS_ADMIN;
import static dk.trustworks.essentials.shared.security.EssentialsSecurityRoles.SUBSCRIPTION_READER;
import static dk.trustworks.essentials.shared.security.EssentialsSecurityValidator.validateHasAnyEssentialsSecurityRoles;

/**
 * The DefaultEventStoreApi class is a concrete implementation of the EventStoreApi interface,
 * providing methods to interact with the event store and manage event-related data.
 * This implementation enforces security through role validation and provides functionality
 * to retrieve event and subscription information.
 */
public class DefaultEventStoreApi implements EventStoreApi {

    private final EssentialsSecurityProvider essentialsSecurityProvider;
    private final EventStore eventStore;
    private final DurableSubscriptionRepository durableSubscriptionRepository;

    public DefaultEventStoreApi(EssentialsSecurityProvider essentialsSecurityProvider,
                                EventStore eventStore,
                                DurableSubscriptionRepository durableSubscriptionRepository) {
        this.essentialsSecurityProvider = essentialsSecurityProvider;
        this.eventStore = eventStore;
        this.durableSubscriptionRepository = durableSubscriptionRepository;
    }

    private void validateSubscriptionReaderRoles(Object principal) {
        validateHasAnyEssentialsSecurityRoles(essentialsSecurityProvider, principal, SUBSCRIPTION_READER, ESSENTIALS_ADMIN);
    }

    @Override
    public Optional<GlobalEventOrder> findHighestGlobalEventOrderPersisted(Object principal, AggregateType aggregateType) {
        validateSubscriptionReaderRoles(principal);
        return eventStore.getUnitOfWorkFactory().withUnitOfWork(uow -> {
            return eventStore.findHighestGlobalEventOrderPersisted(aggregateType);
        });
    }

    @Override
    public List<ApiSubscription> findAllSubscriptions(Object principal) {
        validateSubscriptionReaderRoles(principal);
        return durableSubscriptionRepository.findAllResumePoints().stream().map(ApiSubscription::from).toList();
    }
}
