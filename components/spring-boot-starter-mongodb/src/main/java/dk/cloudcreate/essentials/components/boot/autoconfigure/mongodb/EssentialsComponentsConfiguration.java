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

package dk.cloudcreate.essentials.components.boot.autoconfigure.mongodb;


import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.*;
import dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockManager;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.foundation.reactive.command.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.queue.springdata.mongodb.MongoDurableQueues;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.reactive.command.interceptor.CommandBusInterceptor;
import dk.cloudcreate.essentials.reactive.spring.ReactiveHandlersBeanPostProcessor;
import dk.cloudcreate.essentials.types.springdata.mongo.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.*;
import org.springframework.beans.BeansException;
import org.springframework.boot.actuate.autoconfigure.tracing.ConditionalOnEnabledTracing;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.*;
import org.springframework.data.mongodb.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.*;

import java.util.*;
import java.util.function.Function;

/**
 * MongoDB focused Essentials Components auto configuration
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
    @ConditionalOnEnabledTracing
    public DurableQueuesMicrometerTracingInterceptor durableQueuesMicrometerTracingInterceptor(Tracer tracer,
                                                                                               Propagator propagator,
                                                                                               ObservationRegistry observationRegistry,
                                                                                               EssentialsComponentsProperties properties) {
        return new DurableQueuesMicrometerTracingInterceptor(tracer,
                                                             propagator,
                                                             observationRegistry,
                                                             properties.getDurableQueues().isVerboseTracing());
    }

    @Bean
    @ConditionalOnEnabledTracing
    public DurableQueuesMicrometerInterceptor durableQueuesMicrometerInterceptor(MeterRegistry meterRegistry) {
        return new DurableQueuesMicrometerInterceptor(meterRegistry);
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
    public com.fasterxml.jackson.databind.Module essentialsImmutableJacksonModule() {
        return new EssentialsImmutableJacksonModule();
    }

    @Bean
    @ConditionalOnMissingBean
    public SingleValueTypeRandomIdGenerator registerIdGenerator() {
        return new SingleValueTypeRandomIdGenerator();
    }

    /**
     * Registers <code>new SingleValueTypeConverter(LockName.class, QueueEntryId.class, QueueName.class)))</code> with the {@link MongoCustomConversions}
     * as {@link LockName}, {@link QueueEntryId} and {@link QueueName} are required by {@link FencedLockManager} and {@link DurableQueues}
     * @return the {@link MongoCustomConversions}
     */
    @Bean
    @ConditionalOnMissingBean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new SingleValueTypeConverter(LockName.class, QueueEntryId.class, QueueName.class)));
    }

    /**
     * Provides the {@link MongoTransactionManager} required for the {@link SpringMongoTransactionAwareUnitOfWorkFactory}
     *
     * @param databaseFactory the database factory
     * @return the {@link MongoTransactionManager}
     */
    @Bean
    @ConditionalOnMissingBean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        TransactionOptions transactionOptions = TransactionOptions.builder()
                                                                  .readConcern(ReadConcern.SNAPSHOT)
                                                                  .writeConcern(WriteConcern.ACKNOWLEDGED)
                                                                  .build();
        return new MongoTransactionManager(databaseFactory, transactionOptions);
    }

    /**
     * Define the {@link SpringMongoTransactionAwareUnitOfWorkFactory}<br>
     * The {@link SpringMongoTransactionAwareUnitOfWorkFactory} supports joining {@link UnitOfWork}'s
     * with the underlying Spring managed Transaction (i.e. supports methods annotated with @Transactional)
     *
     * @param transactionManager the Spring Mongo specific Transactional manager as we allow Spring to demarcate the transaction
     * @param databaseFactory    the Spring databaseFactory
     * @return The {@link SpringMongoTransactionAwareUnitOfWorkFactory}
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory(MongoTransactionManager transactionManager,
                                                                          MongoDatabaseFactory databaseFactory) {
        return new SpringMongoTransactionAwareUnitOfWorkFactory(transactionManager, databaseFactory);
    }

    /**
     * The {@link MongoFencedLockManager} that coordinates distributed locks
     *
     * @param mongoTemplate     the {@link MongoTemplate}
     * @param mongoConverter    the {@link MongoConverter}
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} for coordinating {@link UnitOfWork}/Transactions
     * @param eventBus          the {@link EventBus} where {@link FencedLockEvents} are published
     * @param properties        the auto configure properties
     * @return The {@link MongoFencedLockManager}
     */
    @Bean
    @ConditionalOnMissingBean
    public FencedLockManager fencedLockManager(MongoTemplate mongoTemplate,
                                               MongoConverter mongoConverter,
                                               SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                                               EventBus eventBus,
                                               EssentialsComponentsProperties properties) {
        return MongoFencedLockManager.builder()
                                     .setMongoTemplate(mongoTemplate)
                                     .setMongoConverter(mongoConverter)
                                     .setUnitOfWorkFactory(unitOfWorkFactory)
                                     .setLockTimeOut(properties.getFencedLockManager().getLockTimeOut())
                                     .setLockConfirmationInterval(properties.getFencedLockManager().getLockConfirmationInterval())
                                     .setFencedLocksCollectionName(properties.getFencedLockManager().getFencedLocksCollectionName())
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
    public JSONSerializer jsonSerializer(ObjectMapper essentialComponentsObjectMapper) {
        return new JacksonJSONSerializer(essentialComponentsObjectMapper);
    }

    /**
     * The {@link MongoDurableQueues} that handles messaging and supports the {@link Inboxes}/{@link Outboxes} implementations
     *
     * @param mongoTemplate     the {@link MongoTemplate}
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory}
     * @param jsonSerializer    the {@link JSONSerializer} responsible for serializing Message payloads
     * @param properties        the auto configure properties
     * @return the {@link MongoDurableQueues}
     */
    @Bean
    @ConditionalOnMissingBean
    public DurableQueues durableQueues(MongoTemplate mongoTemplate,
                                       SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                                       JSONSerializer jsonSerializer,
                                       EssentialsComponentsProperties properties,
                                       List<DurableQueuesInterceptor> durableQueuesInterceptors) {
        Function<ConsumeFromQueue, QueuePollingOptimizer> pollingOptimizerFactory =
                consumeFromQueue -> new QueuePollingOptimizer.SimpleQueuePollingOptimizer(consumeFromQueue,
                                                                                          (long) (consumeFromQueue.getPollingInterval().toMillis() *
                                                                                                  properties.getDurableQueues()
                                                                                                            .getPollingDelayIntervalIncrementFactor()),
                                                                                          properties.getDurableQueues()
                                                                                                    .getMaxPollingInterval()
                                                                                                    .toMillis()
                );
        MongoDurableQueues durableQueues;
        if (properties.getDurableQueues().getTransactionalMode() == TransactionalMode.FullyTransactional) {
            durableQueues = new MongoDurableQueues(mongoTemplate,
                                                   unitOfWorkFactory,
                                                   jsonSerializer,
                                                   properties.getDurableQueues().getSharedQueueCollectionName(),
                                                   pollingOptimizerFactory);
        } else {
            durableQueues = new MongoDurableQueues(mongoTemplate,
                                                   properties.getDurableQueues().getMessageHandlingTimeout(),
                                                   jsonSerializer,
                                                   properties.getDurableQueues().getSharedQueueCollectionName(),
                                                   pollingOptimizerFactory);
        }
        durableQueues.addInterceptors(durableQueuesInterceptors);
        return durableQueues;
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
    @ConditionalOnMissingBean
    public EventBus eventBus(Optional<OnErrorHandler> onErrorHandler) {
        return new LocalEventBus("default", onErrorHandler);
    }

    /**
     * {@link ObjectMapper} responsible for serializing/deserializing the raw Java events to and from JSON
     *
     * @return the {@link ObjectMapper} responsible for serializing/deserializing the raw Java events to and from JSON
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper essentialComponentsObjectMapper() {
        var objectMapper = JsonMapper.builder()
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
                                     .addModule(new EssentialTypesJacksonModule())
                                     .addModule(new EssentialsImmutableJacksonModule())
                                     .build();

        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return objectMapper;
    }

    /**
     * Callback to ensure Essentials components implementing {@link Lifecycle} are started
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
