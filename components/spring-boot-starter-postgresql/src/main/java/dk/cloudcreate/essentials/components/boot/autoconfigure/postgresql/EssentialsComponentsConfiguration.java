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


import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.jdbi.SpringTransactionAwareJdbiUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.reactive.command.interceptor.CommandBusInterceptor;
import dk.cloudcreate.essentials.reactive.spring.ReactiveHandlersBeanPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.slf4j.*;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.*;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.*;

/**
 * Postgresql focused Essentials Components auto configuration
 */
@AutoConfiguration
@EnableConfigurationProperties(EssentialsComponentsProperties.class)
public class EssentialsComponentsConfiguration implements ApplicationListener<ApplicationContextEvent>, ApplicationContextAware {
    public static final Logger log = LoggerFactory.getLogger(EssentialsComponentsConfiguration.class);

    private ApplicationContext     applicationContext;
    private boolean                closed;
    private Map<String, Lifecycle> lifeCycleBeans;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Bean
    @ConditionalOnProperty(prefix = "management.tracing", name = "enabled", havingValue = "true")
    public DurableQueuesMicrometerTracingInterceptor durableQueuesMicrometerTracingInterceptor(Optional<Tracer> tracer,
                                                                                               Optional<Propagator> propagator,
                                                                                               Optional<ObservationRegistry> observationRegistry,
                                                                                               EssentialsComponentsProperties properties) {
        return new DurableQueuesMicrometerTracingInterceptor(tracer.get(),
                                                             propagator.get(),
                                                             observationRegistry.get(),
                                                             properties.getDurableQueues().isVerboseTracing());
    }

    @Bean
    @ConditionalOnProperty(prefix = "management.tracing", name = "enabled", havingValue = "true")
    public DurableQueuesMicrometerInterceptor durableQueuesMicrometerInterceptor(Optional<MeterRegistry> meterRegistry) {
        return new DurableQueuesMicrometerInterceptor(meterRegistry.get());
    }

    /**
     * Auto-registers any {@link CommandHandler} with the single {@link CommandBus} bean found<br>
     * AND auto-registers any {@link EventHandler} with all {@link EventBus} beans foound
     *
     * @return the {@link ReactiveHandlersBeanPostProcessor} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveHandlersBeanPostProcessor reactiveHandlersBeanPostProcessor() {
        return new ReactiveHandlersBeanPostProcessor();
    }


    /**
     * Essential Jackson module which adds support for serializing and deserializing any Essentials types (note: Map keys still needs to be explicitly defined - see doc)
     *
     * @return the Essential Jackson module which adds support for serializing and deserializing any Essentials types
     */
    @Bean
    @ConditionalOnMissingBean
    public com.fasterxml.jackson.databind.Module essentialJacksonModule() {
        return new EssentialTypesJacksonModule();
    }

    /**
     * Essential Immutable Jackson module which adds support for serializing and deserializing objects with no {@link JsonCreator} or a default constructor
     *
     * @return the Essential Immutable Jackson module which adds support for serializing and deserializing objects with no {@link JsonCreator} or a default constructor
     */
    @Bean
    @ConditionalOnClass(name = "org.objenesis.ObjenesisStd")
    @ConditionalOnMissingBean
    public EssentialsImmutableJacksonModule essentialsImmutableJacksonModule() {
        return new EssentialsImmutableJacksonModule();
    }

    /**
     * {@link Jdbi} is the JDBC API used by the all the Postgresql specific components such as
     * PostgresqlEventStore, {@link PostgresqlFencedLockManager} and {@link PostgresqlDurableQueues}
     *
     * @param dataSource the Spring managed datasource
     * @return the {@link Jdbi} instance
     */
    @Bean
    @ConditionalOnMissingBean
    public Jdbi jdbi(DataSource dataSource) {
        var jdbi = Jdbi.create(new TransactionAwareDataSourceProxy(dataSource));
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());
        return jdbi;
    }

    /**
     * Define the {@link SpringTransactionAwareJdbiUnitOfWorkFactory}, but only if an EventStore specific variant isn't on the classpath.<br>
     * The {@link SpringTransactionAwareJdbiUnitOfWorkFactory} supports joining {@link UnitOfWork}'s
     * with the underlying Spring managed Transaction (i.e. supports methods annotated with @Transactional)
     *
     * @param jdbi               the jdbi instance
     * @param transactionManager the Spring Transactional manager as we allow Spring to demarcate the transaction
     * @return The {@link SpringTransactionAwareJdbiUnitOfWorkFactory}
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.SpringTransactionAwareEventStoreUnitOfWorkFactory")
    public HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory(Jdbi jdbi,
                                                                                           PlatformTransactionManager transactionManager) {
        return new SpringTransactionAwareJdbiUnitOfWorkFactory(jdbi, transactionManager);
    }

    /**
     * The {@link PostgresqlFencedLockManager} that coordinates distributed locks
     *
     * @param jdbi              the jbdi instance
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} for coordinating {@link UnitOfWork}/Transactions
     * @param eventBus          the {@link EventBus} where {@link FencedLockEvents} are published
     * @param properties        the auto configure properties
     * @return The {@link PostgresqlFencedLockManager}
     */
    @Bean
    @ConditionalOnMissingBean
    public FencedLockManager fencedLockManager(Jdbi jdbi,
                                               HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                               EventBus eventBus,
                                               EssentialsComponentsProperties properties) {
        return PostgresqlFencedLockManager.builder()
                                          .setJdbi(jdbi)
                                          .setUnitOfWorkFactory(unitOfWorkFactory)
                                          .setLockTimeOut(properties.getFencedLockManager().getLockTimeOut())
                                          .setLockConfirmationInterval(properties.getFencedLockManager().getLockConfirmationInterval())
                                          .setFencedLocksTableName(properties.getFencedLockManager().getFencedLocksTableName())
                                          .setEventBus(eventBus)
                                          .buildAndStart();
    }

    /**
     * The {@link JSONSerializer} that handles {@link DurableQueues} message payload serialization and deserialization
     *
     * @param essentialComponentsObjectMapper the {@link ObjectMapper} responsible for serializing Messages
     * @return the {@link JSONSerializer}
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer")
    public JSONSerializer jsonSerializer(ObjectMapper essentialComponentsObjectMapper) {
        return new JacksonJSONSerializer(essentialComponentsObjectMapper);
    }


    /**
     * The {@link PostgresqlDurableQueues} that handles messaging and supports the {@link Inboxes}/{@link Outboxes} implementations
     *
     * @param unitOfWorkFactory                the {@link UnitOfWorkFactory}
     * @param jsonSerializer                   the {@link JSONSerializer} responsible for serializing Message payloads
     * @param optionalMultiTableChangeListener the optional {@link MultiTableChangeListener}
     * @param properties                       the auto configure properties
     * @return the {@link PostgresqlDurableQueues}
     */
    @Bean
    @ConditionalOnMissingBean
    public DurableQueues durableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                       JSONSerializer jsonSerializer,
                                       Optional<MultiTableChangeListener<TableChangeNotification>> optionalMultiTableChangeListener,
                                       EssentialsComponentsProperties properties,
                                       List<DurableQueuesInterceptor> durableQueuesInterceptors) {
        var durableQueues = PostgresqlDurableQueues.builder()
                                                   .setUnitOfWorkFactory(unitOfWorkFactory)
                                                   .setMessageHandlingTimeout(properties.getDurableQueues().getMessageHandlingTimeout())
                                                   .setTransactionalMode(properties.getDurableQueues().getTransactionalMode())
                                                   .setJsonSerializer(jsonSerializer)
                                                   .setSharedQueueTableName(properties.getDurableQueues().getSharedQueueTableName())
                                                   .setMultiTableChangeListener(optionalMultiTableChangeListener.orElse(null))
                                                   .setQueuePollingOptimizerFactory(consumeFromQueue -> new QueuePollingOptimizer.SimpleQueuePollingOptimizer(consumeFromQueue,
                                                                                                                                                              (long) (consumeFromQueue.getPollingInterval().toMillis() *
                                                                                                                                                                      properties.getDurableQueues()
                                                                                                                                                                                .getPollingDelayIntervalIncrementFactor()),
                                                                                                                                                              properties.getDurableQueues()
                                                                                                                                                                        .getMaxPollingInterval()
                                                                                                                                                                        .toMillis()
                                                   )).build();
        durableQueues.addInterceptors(durableQueuesInterceptors);
        return durableQueues;
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiTableChangeListener<TableChangeNotification> multiTableChangeListener(Jdbi jdbi,
                                                                                      ObjectMapper essentialComponentsObjectMapper,
                                                                                      EventBus eventBus,
                                                                                      EssentialsComponentsProperties properties) {
        return new MultiTableChangeListener<>(jdbi,
                                              properties.getMultiTableChangeListener().getPollingInterval(),
                                              essentialComponentsObjectMapper,
                                              eventBus);
    }

    /**
     * The {@link Inboxes} instance using the provided {@link DurableQueues} implementation for message durability and retry
     *
     * @param durableQueues     the {@link DurableQueues} implementation responsible for message durability and retry
     * @param fencedLockManager the distributed locks manager for controlling message consumption across different nodes
     * @return the {@link Inboxes} instance using the provided {@link DurableQueues} implementation for message durability and retry
     */
    @Bean
    @ConditionalOnMissingBean
    public Inboxes inboxes(DurableQueues durableQueues,
                           FencedLockManager fencedLockManager) {
        return Inboxes.durableQueueBasedInboxes(durableQueues,
                                                fencedLockManager);
    }

    /**
     * The {@link Outboxes} instance using the provided {@link DurableQueues} implementation for message durability and retry
     *
     * @param durableQueues     the {@link DurableQueues} implementation responsible for message durability and retry
     * @param fencedLockManager the distributed locks manager for controlling message consumption across different nodes
     * @return the {@link Outboxes} instance using the provided {@link DurableQueues} implementation for message durability and retry
     */
    @Bean
    @ConditionalOnMissingBean
    public Outboxes outboxes(DurableQueues durableQueues,
                             FencedLockManager fencedLockManager) {
        return Outboxes.durableQueueBasedOutboxes(durableQueues,
                                                  fencedLockManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public DurableLocalCommandBus commandBus(DurableQueues durableQueues,
                                             UnitOfWorkFactory<? extends UnitOfWork> unitOfWorkFactory,
                                             Optional<QueueName> optionalCommandQueueName,
                                             Optional<RedeliveryPolicy> optionalCommandQueueRedeliveryPolicy,
                                             Optional<SendAndDontWaitErrorHandler> optionalSendAndDontWaitErrorHandler,
                                             List<CommandBusInterceptor> commandBusInterceptors) {
        var durableCommandBusBuilder = DurableLocalCommandBus.builder()
                                                             .setDurableQueues(durableQueues);
        optionalCommandQueueName.ifPresent(durableCommandBusBuilder::setCommandQueueName);
        optionalCommandQueueRedeliveryPolicy.ifPresent(durableCommandBusBuilder::setCommandQueueRedeliveryPolicy);
        optionalSendAndDontWaitErrorHandler.ifPresent(durableCommandBusBuilder::setSendAndDontWaitErrorHandler);
        durableCommandBusBuilder.addInterceptors(commandBusInterceptors);
        if (commandBusInterceptors.stream().noneMatch(commandBusInterceptor -> UnitOfWorkControllingCommandBusInterceptor.class.isAssignableFrom(commandBusInterceptor.getClass()))) {
            durableCommandBusBuilder.addInterceptors(new UnitOfWorkControllingCommandBusInterceptor(unitOfWorkFactory));
        }
        return durableCommandBusBuilder.build();
    }

    /**
     * Configure the {@link EventBus} to use for all event handlers
     *
     * @param onErrorHandler the error handler which will be called if any asynchronous subscriber/consumer fails to handle an event
     * @return the {@link EventBus} to use for all event handlers
     */
    @Bean
    @ConditionalOnMissingClass("dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.EventStoreEventBus")
    @ConditionalOnMissingBean
    public EventBus eventBus(Optional<OnErrorHandler> onErrorHandler) {
        return new LocalEventBus("default", onErrorHandler);
    }

    /**
     * {@link ObjectMapper} responsible for serializing/deserializing the raw Java events to and from JSON
     *
     * @param optionalEssentialsImmutableJacksonModule the optional {@link EssentialsImmutableJacksonModule}
     * @param additionalModules additional {@link Module}'s found in the {@link ApplicationContext}
     * @return the {@link ObjectMapper} responsible for serializing/deserializing the raw Java events to and from JSON
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper essentialComponentsObjectMapper(Optional<EssentialsImmutableJacksonModule> optionalEssentialsImmutableJacksonModule,
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
                                            .addModule(new JavaTimeModule())
                                            .addModule(new EssentialTypesJacksonModule());

        additionalModules.forEach(objectMapperBuilder::addModule);

        optionalEssentialsImmutableJacksonModule.ifPresent(essentialsImmutableJacksonModule -> {
            objectMapperBuilder.addModule(new EssentialsImmutableJacksonModule());
        });

        var objectMapper = objectMapperBuilder.build();
        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return objectMapper;
    }

    /**
     * Callback to ensure Essentials components implementing {@link dk.cloudcreate.essentials.components.foundation.Lifecycle} are started
     *
     * @param event
     */
    @Override
    public void onApplicationEvent(ApplicationContextEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            log.info(event.getClass().getSimpleName());
            closed = false;
            lifeCycleBeans = applicationContext.getBeansOfType(Lifecycle.class);
            lifeCycleBeans.forEach((beanName, lifecycleBean) -> {
                if (!lifecycleBean.isStarted()) {
                    log.info("Starting {} bean '{}' of type '{}'", dk.cloudcreate.essentials.components.foundation.Lifecycle.class.getSimpleName(), beanName, lifecycleBean.getClass().getName());
                    lifecycleBean.start();
                }
            });

            var callbacks = applicationContext.getBeansOfType(JdbiConfigurationCallback.class).values();
            if (!callbacks.isEmpty()) {
                var jdbi = applicationContext.getBean(Jdbi.class);
                callbacks.forEach(configureJdbiCallback -> {
                    log.info("Calling {}: {}",
                             JdbiConfigurationCallback.class.getSimpleName(),
                             configureJdbiCallback.getClass().getName());
                    configureJdbiCallback.configure(jdbi);
                });
            }
        } else if (event instanceof ContextClosedEvent) {
            log.info("{} - has Context already been closed: {}", event.getClass().getSimpleName(), closed);
            if (!closed) {
                lifeCycleBeans.forEach((beanName, lifecycleBean) -> {
                    if (lifecycleBean.isStarted()) {
                        log.info("Stopping {} bean '{}' of type '{}'", Lifecycle.class.getSimpleName(), beanName, lifecycleBean.getClass().getName());
                        lifecycleBean.stop();
                    }
                });
                closed = true;
            }
        }
    }
}
