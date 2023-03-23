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

package dk.cloudcreate.essentials.components.boot.autoconfigure.postgresql;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.EventStoreInterceptor;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.SpringTransactionAwareEventStoreUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventTypeOrName;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLockManager;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import org.jdbi.v3.core.Jdbi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfigurationUsingJackson;

/**
 * {@link PostgresqlEventStore} auto configuration
 */
@AutoConfiguration
@ConditionalOnClass(name = "dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore")
@EnableConfigurationProperties(EssentialsComponentsProperties.class)
public class EventStoreConfiguration {

    /**
     * The Local EventBus where the {@link EventStore} publishes {@link PersistedEvents} locally
     *
     * @param eventStoreUnitOfWorkFactory the {@link EventStoreUnitOfWorkFactory} that is required for the {@link EventStore} in order handle events associated with a given transaction
     * @return the {@link EventStoreEventBus}
     */
    @Bean
    @ConditionalOnMissingBean
    public EventStoreEventBus eventStoreLocalEventBus(EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> eventStoreUnitOfWorkFactory) {
        return new EventStoreEventBus(eventStoreUnitOfWorkFactory);
    }

    /**
     * Default {@link PersistableEventMapper} which maps from the raw Java Event's to {@link PersistableEvent}<br>
     * The {@link PersistableEventMapper} adds additional information such as:
     * event-id, event-type, event-order, event-timestamp, event-meta-data, correlation-id, tenant-id for each persisted event at a cross-functional level.
     *
     * @return the {@link PersistableEventMapper} to use for all Events
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistableEventMapper persistableEventMapper() {
        return (aggregateId, aggregateTypeConfiguration, event, eventOrder) ->
                PersistableEvent.builder()
                                .setEvent(event)
                                .setAggregateType(aggregateTypeConfiguration.aggregateType)
                                .setAggregateId(aggregateId)
                                .setEventTypeOrName(EventTypeOrName.with(event.getClass()))
                                .setEventOrder(eventOrder)
                                .build();
    }

    /**
     * Define the {@link EventStoreUnitOfWorkFactory} which is required for the {@link EventStore}
     * in order handle events associated with a given transaction.<br>
     * The {@link SpringTransactionAwareEventStoreUnitOfWorkFactory} supports joining {@link UnitOfWork}'s
     * with the underlying Spring managed Transaction (i.e. supports methods annotated with @Transactional)
     *
     * @param jdbi               the jdbi instance
     * @param transactionManager the Spring Transactional manager as we allow Spring to demarcate the transaction
     * @return The {@link EventStoreUnitOfWorkFactory}
     */
    @Bean
    @ConditionalOnMissingBean
    public EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> eventStoreUnitOfWorkFactory(Jdbi jdbi,
                                                                                                   PlatformTransactionManager transactionManager) {
        return new SpringTransactionAwareEventStoreUnitOfWorkFactory(jdbi, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventStoreSubscriptionManager eventStoreSubscriptionManager(EventStore eventStore,
                                                                       FencedLockManager fencedLockManager,
                                                                       Jdbi jdbi,
                                                                       EssentialsComponentsProperties properties) {
        return EventStoreSubscriptionManager.builder()
                                            .setEventStore(eventStore)
                                            .setFencedLockManager(fencedLockManager)
                                            .setDurableSubscriptionRepository(new PostgresqlDurableSubscriptionRepository(jdbi))
                                            .setEventStorePollingBatchSize(properties.getEventStore().getSubscriptionManager().getEventStorePollingBatchSize())
                                            .setEventStorePollingInterval(properties.getEventStore().getSubscriptionManager().getEventStorePollingInterval())
                                            .setSnapshotResumePointsEvery(properties.getEventStore().getSubscriptionManager().getSnapshotResumePointsEvery())
                                            .build();
    }

    /**
     * Setup the strategy for how {@link AggregateType} event-streams should be persisted.
     *
     * @param jdbi                            the jdbi instance
     * @param unitOfWorkFactory               the {@link EventStoreUnitOfWorkFactory}
     * @param persistableEventMapper          the mapper from the raw Java Event's to {@link PersistableEvent}<br>
     * @param essentialComponentsObjectMapper {@link ObjectMapper} responsible for serializing/deserializing the raw Java events to and from JSON
     * @return the strategy for how {@link AggregateType} event-streams should be persisted
     */
    @Bean
    @ConditionalOnMissingBean
    public SeparateTablePerAggregateTypePersistenceStrategy eventStorePersistenceStrategy(Jdbi jdbi,
                                                                                          EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory,
                                                                                          PersistableEventMapper persistableEventMapper,
                                                                                          ObjectMapper essentialComponentsObjectMapper,
                                                                                          EssentialsComponentsProperties properties) {
        return new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                    unitOfWorkFactory,
                                                                    persistableEventMapper,
                                                                    standardSingleTenantConfigurationUsingJackson(essentialComponentsObjectMapper,
                                                                                                                  properties.getEventStore().getIdentifierColumnType(),
                                                                                                                  properties.getEventStore().getJsonColumnType()));
    }

    /**
     * The configurable {@link EventStore} that allows us to persist and load Events associated with different {@link AggregateType}øs
     *
     * @param eventStoreUnitOfWorkFactory the {@link EventStoreUnitOfWorkFactory} that is required for the {@link EventStore} in order handle events associated with a given transaction
     * @param persistenceStrategy         the strategy for how {@link AggregateType} event-streams should be persisted.
     * @param eventStoreLocalEventBus     the Local EventBus where the {@link EventStore} publishes persisted event
     * @return the configurable {@link EventStore}
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore(EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> eventStoreUnitOfWorkFactory,
                                                                                                SeparateTablePerAggregateTypePersistenceStrategy persistenceStrategy,
                                                                                                EventStoreEventBus eventStoreLocalEventBus,
                                                                                                EssentialsComponentsProperties essentialsComponentsProperties,
                                                                                                List<EventStoreInterceptor> eventStoreInterceptors) {
        var configurableEventStore = new PostgresqlEventStore<>(eventStoreUnitOfWorkFactory,
                                                                persistenceStrategy,
                                                                Optional.of(eventStoreLocalEventBus),
                                                                eventStore -> essentialsComponentsProperties.getEventStore().isUseEventStreamGapHandler() ?
                                                                              new PostgresqlEventStreamGapHandler<>(eventStore, eventStoreUnitOfWorkFactory) :
                                                                              new NoEventStreamGapHandler<>());
        configurableEventStore.addEventStoreInterceptors(eventStoreInterceptors);
        return configurableEventStore;
    }
}
