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

package dk.cloudcreate.essentials.components.boot.autoconfigure.postgresql.eventstore;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.boot.autoconfigure.postgresql.EssentialsComponentsConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.EventStoreInterceptor;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.micrometer.MicrometerTracingEventStoreInterceptor;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.SpringTransactionAwareEventStoreUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLockManager;
import dk.cloudcreate.essentials.components.foundation.messaging.MessageHandler;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.reactive.command.*;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.jdbi.v3.core.Jdbi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

/**
 * {@link PostgresqlEventStore} auto configuration<br>
 * <br>
 * <u><b>Security:</b></u><br>
 * If you in your own Spring Boot application choose to override the Beans defined by this starter,
 * then you need to check the component document to learn about the Security implications of each configuration.
 * <br>
 * Also see {@link EssentialsComponentsConfiguration} for security information related to common Essentials components.
 *
 * @see PostgresqlDurableSubscriptionRepository
 * @see SeparateTablePerAggregateTypePersistenceStrategy
 * @see SeparateTablePerAggregateTypeEventStreamConfigurationFactory
 * @see SeparateTablePerAggregateEventStreamConfiguration
 * @see EventStreamTableColumnNames
 * @see dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues
 * @see dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager
 * @see dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockStorage
 * @see dk.cloudcreate.essentials.components.foundation.postgresql.MultiTableChangeListener
 */
@AutoConfiguration
@ConditionalOnClass(name = "dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore")
@EnableConfigurationProperties(EssentialsEventStoreProperties.class)
public class EventStoreConfiguration {

    /**
     * The Local EventBus where the {@link EventStore} publishes {@link PersistedEvents} locally
     *
     * @param eventStoreUnitOfWorkFactory the {@link EventStoreUnitOfWorkFactory} that is required for the {@link EventStore} in order handle events associated with a given transaction
     * @param onErrorHandler              the error handler which will be called if any asynchronous subscriber/consumer fails to handle an event
     * @return the {@link EventStoreEventBus}
     */
    @Bean
    @ConditionalOnMissingBean
    public EventStoreEventBus eventStoreLocalEventBus(EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> eventStoreUnitOfWorkFactory,
                                                      Optional<OnErrorHandler> onErrorHandler) {
        return new EventStoreEventBus(eventStoreUnitOfWorkFactory,
                                      onErrorHandler);
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
                                                                       EssentialsEventStoreProperties properties) {
        return EventStoreSubscriptionManager.builder()
                                            .setEventStore(eventStore)
                                            .setFencedLockManager(fencedLockManager)
                                            .setDurableSubscriptionRepository(new PostgresqlDurableSubscriptionRepository(jdbi, eventStore.getUnitOfWorkFactory()))
                                            .setEventStorePollingBatchSize(properties.getSubscriptionManager().getEventStorePollingBatchSize())
                                            .setEventStorePollingInterval(properties.getSubscriptionManager().getEventStorePollingInterval())
                                            .setSnapshotResumePointsEvery(properties.getSubscriptionManager().getSnapshotResumePointsEvery())
                                            .build();
    }

    /**
     * Set up the strategy for how {@link AggregateType} event-streams should be persisted.
     *
     * @param jdbi                      the jdbi instance
     * @param unitOfWorkFactory         the {@link EventStoreUnitOfWorkFactory}
     * @param persistableEventMapper    the mapper from the raw Java Event's to {@link PersistableEvent}<br>
     * @param jsonEventSerializer       {@link JSONEventSerializer} responsible for serializing/deserializing the raw Java events to and from JSON
     * @param persistableEventEnrichers {@link PersistableEventEnricher}'s - which are called in sequence by the {@link SeparateTablePerAggregateTypePersistenceStrategy#persist(EventStoreUnitOfWork, AggregateType, Object, Optional, List)} after
     *                                  {@link PersistableEventMapper#map(Object, AggregateEventStreamConfiguration, Object, EventOrder)}
     *                                  has been called
     * @return the strategy for how {@link AggregateType} event-streams should be persisted
     */
    @Bean
    @ConditionalOnMissingBean
    public AggregateEventStreamPersistenceStrategy<SeparateTablePerAggregateEventStreamConfiguration> eventStorePersistenceStrategy(Jdbi jdbi,
                                                                                                                                    EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory,
                                                                                                                                    PersistableEventMapper persistableEventMapper,
                                                                                                                                    JSONEventSerializer jsonEventSerializer,
                                                                                                                                    EssentialsEventStoreProperties properties,
                                                                                                                                    List<PersistableEventEnricher> persistableEventEnrichers) {
        return new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                    unitOfWorkFactory,
                                                                    persistableEventMapper,
                                                                    SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration(jsonEventSerializer,
                                                                                                                                                                   properties.getIdentifierColumnType(),
                                                                                                                                                                   properties.getJsonColumnType()),
                                                                    persistableEventEnrichers);
    }

    /**
     * The configurable {@link EventStore} that allows us to persist and load Events associated with different {@link AggregateType}'s
     *
     * @param eventStoreUnitOfWorkFactory the {@link EventStoreUnitOfWorkFactory} that is required for the {@link EventStore} in order handle events associated with a given transaction
     * @param persistenceStrategy         the strategy for how {@link AggregateType} event-streams should be persisted.
     * @param eventStoreLocalEventBus     the Local EventBus where the {@link EventStore} publishes persisted event
     * @return the configurable {@link EventStore}
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore(EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> eventStoreUnitOfWorkFactory,
                                                                                                AggregateEventStreamPersistenceStrategy<SeparateTablePerAggregateEventStreamConfiguration> persistenceStrategy,
                                                                                                EventStoreEventBus eventStoreLocalEventBus,
                                                                                                EssentialsEventStoreProperties essentialsComponentsProperties,
                                                                                                List<EventStoreInterceptor> eventStoreInterceptors) {
        var configurableEventStore = new PostgresqlEventStore<>(eventStoreUnitOfWorkFactory,
                                                                persistenceStrategy,
                                                                Optional.of(eventStoreLocalEventBus),
                                                                eventStore -> essentialsComponentsProperties.isUseEventStreamGapHandler() ?
                                                                              new PostgresqlEventStreamGapHandler<>(eventStore, eventStoreUnitOfWorkFactory) :
                                                                              new NoEventStreamGapHandler<>());
        configurableEventStore.addEventStoreInterceptors(eventStoreInterceptors);
        return configurableEventStore;
    }

    @Bean
    @ConditionalOnProperty(prefix = "management.tracing", name = "enabled", havingValue = "true")
    public MicrometerTracingEventStoreInterceptor micrometerTracingEventStoreInterceptor(Optional<Tracer> tracer,
                                                                                         Optional<Propagator> propagator,
                                                                                         Optional<ObservationRegistry> observationRegistry,
                                                                                         EssentialsEventStoreProperties essentialsComponentsProperties) {
        return new MicrometerTracingEventStoreInterceptor(tracer.get(),
                                                          propagator.get(),
                                                          observationRegistry.get(),
                                                          essentialsComponentsProperties.isVerboseTracing());
    }

    /**
     * Create {@link EventProcessorDependencies} which encapsulates all the dependencies required by an instance of an {@link EventProcessor}
     *
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions<br>
     *                                      The  {@link EventStore} instance associated with the {@link EventStoreSubscriptionManager} is used to only queue a reference to
     *                                      the {@link PersistedEvent} and before the message is forwarded to the corresponding {@link MessageHandler} then we load the {@link PersistedEvent}'s
     *                                      payload and forward it to the {@link MessageHandler} annotated method
     * @param inboxes                       the {@link Inboxes} instance used to create an {@link Inbox}, with the name returned from {@link EventProcessor#getProcessorName()}.
     *                                      This {@link Inbox} is used for forwarding {@link PersistedEvent}'s received via {@link EventStoreSubscription}'s, because {@link EventStoreSubscription}'s
     *                                      doesn't handle message retry, etc.
     * @param commandBus                    The {@link CommandBus} where any {@link Handler} or {@link CmdHandler} annotated methods in the subclass of the {@link EventProcessor} will be registered
     * @param messageHandlerInterceptors    The {@link MessageHandlerInterceptor}'s that will intercept calls to the {@link MessageHandler} annotated methods.<br>
     * @return {@link EventProcessorDependencies}
     */
    @Bean
    @ConditionalOnMissingBean
    public EventProcessorDependencies eventProcessorDependencies(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                                                 Inboxes inboxes,
                                                                 DurableLocalCommandBus commandBus,
                                                                 List<MessageHandlerInterceptor> messageHandlerInterceptors) {
        return new EventProcessorDependencies(eventStoreSubscriptionManager,
                                              inboxes,
                                              commandBus,
                                              messageHandlerInterceptors);
    }

    /**
     * The {@link JSONEventSerializer} that handles both {@link EventStore} event/metadata serialization as well as {@link DurableQueues} message payload serialization and deserialization
     *
     * @param optionalEssentialsImmutableJacksonModule the optional {@link EssentialsImmutableJacksonModule}
     * @param additionalModules                        additional {@link Module}'s found in the {@link ApplicationContext}
     * @return the {@link JSONEventSerializer} responsible for serializing/deserializing the raw Java events to and from JSON
     */
    @Bean
    @ConditionalOnMissingBean
    public JSONEventSerializer jsonSerializer(Optional<EssentialsImmutableJacksonModule> optionalEssentialsImmutableJacksonModule,
                                              List<Module> additionalModules) {
        var objectMapperBuilder = JsonMapper.builder()
                                            .disable(MapperFeature.AUTO_DETECT_GETTERS)
                                            .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                                            .disable(MapperFeature.AUTO_DETECT_SETTERS)
                                            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                            .enable(MapperFeature.AUTO_DETECT_CREATORS)
                                            .enable(MapperFeature.AUTO_DETECT_FIELDS)
                                            .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                                            .addModule(new Jdk8Module())
                                            .addModule(new JavaTimeModule());

        additionalModules.forEach(objectMapperBuilder::addModule);

        optionalEssentialsImmutableJacksonModule.ifPresent(objectMapperBuilder::addModule);

        var objectMapper = objectMapperBuilder.build();
        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));

        return new JacksonJSONEventSerializer(objectMapper);
    }
}
