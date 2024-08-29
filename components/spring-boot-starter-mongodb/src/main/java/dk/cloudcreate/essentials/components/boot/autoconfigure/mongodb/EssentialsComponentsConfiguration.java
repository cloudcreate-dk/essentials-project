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

package dk.cloudcreate.essentials.components.boot.autoconfigure.mongodb;


import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.*;
import dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockManager;
import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.components.foundation.lifecycle.*;
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
import dk.cloudcreate.essentials.types.CharSequenceType;
import dk.cloudcreate.essentials.types.springdata.mongo.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.*;
import org.springframework.core.convert.converter.*;
import org.springframework.data.mongodb.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.*;
import java.util.function.Function;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * MongoDB focused Essentials Components auto configuration<br>
 * <br>
 * <u><b>Security:</b></u><br>
 * If you in your own Spring Boot application choose to override the Beans defined by this starter,
 * then you need to check the component document to learn about the Security implications of each configuration.
 * <br>
 * <u>{@link MongoFencedLockManager}</u><br>
 * To support customization of storage collection name, the {@code fencedLocksCollectionName} will be directly used as Collection name,
 * which exposes the component to the risk of malicious input.<br>
 * <br>
 * <strong>Security Note:</strong><br>
 * It is the responsibility of the user of this component to sanitize the {@code fencedLocksCollectionName}
 * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage} component, used by {@link MongoFencedLockManager} will
 * call the {@link dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
 * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
 * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
 * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
 * <br>
 * It is highly recommended that the {@code fencedLocksCollectionName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code fencedLocksCollectionName} value.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
 * <br>
 * <u>{@link MongoDurableQueues}</u><br>
 * To support customization of storage collection name, the {@link MongoDurableQueues#getSharedQueueCollectionName()} will be directly used as Collection name,
 * which exposes the component to the risk of malicious input.<br>
 * <br>
 * <strong>Security Note:</strong><br>
 * It is the responsibility of the user of this component to sanitize the {@code sharedQueueCollectionName}
 * to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. The {@link MongoDurableQueues}, will
 * call the {@link dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} method to validate the collection name as a first line of defense.<br>
 * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link dk.cloudcreate.essentials.components.foundation.mongo.MongoUtil#checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc..<br>
 * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
 * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
 * <br>
 * It is highly recommended that the {@code sharedQueueCollectionName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the {@code sharedQueueCollectionName} value.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.</b>
 * @see dk.cloudcreate.essentials.components.queue.springdata.mongodb.MongoDurableQueues
 * @see dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockManager
 * @see dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo.MongoFencedLockStorage
 */
@AutoConfiguration
@EnableConfigurationProperties(EssentialsComponentsProperties.class)
public class EssentialsComponentsConfiguration {

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
    public DurableQueuesMicrometerInterceptor durableQueuesMicrometerInterceptor(Optional<MeterRegistry> meterRegistry,
                                                                                 EssentialsComponentsProperties properties) {
        return new DurableQueuesMicrometerInterceptor(meterRegistry.get(), properties.getTracingProperties().getModuleTag());
    }

    /**
     * Auto-registers any {@link CommandHandler} with the single {@link CommandBus} bean found<br>
     * AND auto-registers any {@link EventHandler} with all {@link EventBus} beans foound
     *
     * @return the {@link ReactiveHandlersBeanPostProcessor} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public static ReactiveHandlersBeanPostProcessor reactiveHandlersBeanPostProcessor() {
        return new ReactiveHandlersBeanPostProcessor();
    }


    /**
     * Essential Jackson module which adds support for serializing and deserializing any Essentials types (note: Map keys still needs to be explicitly defined - see doc)
     *
     * @return the Essential Jackson module which adds support for serializing and deserializing any Essentials types
     */
    @Bean
    @ConditionalOnMissingBean
    public EssentialTypesJacksonModule essentialJacksonModule() {
        return new EssentialTypesJacksonModule();
    }

    /**
     * Essential Immutable Jackson module which adds support for serializing and deserializing objects with no {@link JsonCreator} or a default constructor
     *
     * @return the Essential Immutable Jackson module which adds support for serializing and deserializing objects with no {@link JsonCreator} or a default constructor
     */
    @Bean
    @ConditionalOnClass(name = "org.objenesis.ObjenesisStd")
    @ConditionalOnProperty(prefix = "essentials", name = "immutable-jackson-module-enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public EssentialsImmutableJacksonModule essentialsImmutableJacksonModule() {
        return new EssentialsImmutableJacksonModule();
    }

    /**
     * Essential Jackson module which adds support for serializing and deserializing objects with semantic types
     *
     * @return the Essential Jackson module which adds support for serializing and deserializing objects with semantic types
     */
    @Bean
    @ConditionalOnMissingBean
    public EssentialTypesJacksonModule essentialsJacksonModule() {
        return new EssentialTypesJacksonModule();
    }

    @Bean
    @ConditionalOnMissingBean
    public SingleValueTypeRandomIdGenerator registerIdGenerator() {
        return new SingleValueTypeRandomIdGenerator();
    }

    /**
     * Creates a {@link MongoCustomConversions} bean  (using <code>SpringDataJavaTimeCodecs</code>)
     * with optional additional char sequence types supported and generic converters.<br>
     * Registers <code>new SingleValueTypeConverter(LockName.class, QueueEntryId.class, QueueName.class)))</code> with the {@link MongoCustomConversions}
     * as {@link LockName}, {@link QueueEntryId} and {@link QueueName} are required by {@link FencedLockManager} and {@link DurableQueues}
     * and any additional {@link CharSequenceType}'s provided in the optional {@link AdditionalCharSequenceTypesSupported} parameter.<br>
     * Through the optional {@link AdditionalConverters} parameter it supports easily registration of additional {@link Converter}/{@link GenericConverter}'s/etc<br>
     * <br>
     * Example Spring Config:
     * <pre>{@code
     * @Bean
     * AdditionalCharSequenceTypesSupported additionalCharSequenceTypesSupported() {
     *     return new AdditionalCharSequenceTypesSupported(OrderId.class);
     * }
     *
     * @Bean
     * AdditionalConverters additionalGenericConverters() {
     *     return new AdditionalConverters(Jsr310Converters.StringToDurationConverter.INSTANCE,
     *                                     Jsr310Converters.DurationToStringConverter.INSTANCE);
     * }
     * }</pre>
     *
     * @param optionalAdditionalCharSequenceTypesSupported An optional additional concrete {@link CharSequenceType}'s that will be registered with the {@link SingleValueTypeConverter}
     *                                                     being added to the returned {@link MongoCustomConversions}
     * @param optionalAdditionalGenericConverters          An optional additional {@link Converter}/{@link GenericConverter}'s/etc. that will be
     *                                                     added to the returned  {@link MongoCustomConversions}
     * @return the {@link MongoCustomConversions}
     */
    @Bean
    @ConditionalOnMissingBean
    public MongoCustomConversions mongoCustomConversions(Optional<AdditionalCharSequenceTypesSupported> optionalAdditionalCharSequenceTypesSupported,
                                                         Optional<AdditionalConverters> optionalAdditionalGenericConverters) {
        return MongoCustomConversions.create(mongoConverterConfigurationAdapter -> {
            mongoConverterConfigurationAdapter.useSpringDataJavaTimeCodecs();
            List<Class<? extends CharSequenceType<?>>> allCharSequenceTypesSupported = new ArrayList<>(List.of(LockName.class, QueueEntryId.class, QueueName.class));
            optionalAdditionalCharSequenceTypesSupported.ifPresent(additionalCharSequenceTypesSupported -> allCharSequenceTypesSupported.addAll(additionalCharSequenceTypesSupported.charSequenceTypes));

            List<Object> allGenericConverters = new ArrayList<>(List.of(new SingleValueTypeConverter(allCharSequenceTypesSupported)));
            optionalAdditionalGenericConverters.ifPresent(additionalGenericConverters -> allGenericConverters.addAll(additionalGenericConverters.converters));
            mongoConverterConfigurationAdapter.registerConverters(allGenericConverters);
        });
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
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} for coordinating {@link UnitOfWork}/Transactions
     * @param eventBus          the {@link EventBus} where {@link FencedLockEvents} are published
     * @param properties        the auto configure properties
     * @return The {@link MongoFencedLockManager}
     */
    @Bean
    @ConditionalOnMissingBean
    public FencedLockManager fencedLockManager(MongoTemplate mongoTemplate,
                                               SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                                               EventBus eventBus,
                                               EssentialsComponentsProperties properties) {
        return MongoFencedLockManager.builder()
                                     .setMongoTemplate(mongoTemplate)
                                     .setUnitOfWorkFactory(unitOfWorkFactory)
                                     .setLockTimeOut(properties.getFencedLockManager().getLockTimeOut())
                                     .setLockConfirmationInterval(properties.getFencedLockManager().getLockConfirmationInterval())
                                     .setFencedLocksCollectionName(properties.getFencedLockManager().getFencedLocksCollectionName())
                                     .setEventBus(eventBus)
                                     .buildAndStart();
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
     * {@link JSONSerializer} responsible for serializing/deserializing the raw Java events to and from JSON
     * (including handling {@link DurableQueues} message payload serialization and deserialization)
     *
     * @param additionalModules                        additional {@link Module}'s found in the {@link ApplicationContext}
     * @return the {@link JSONSerializer} responsible for serializing/deserializing the raw Java events to and from JSON
     */
    @Bean
    @ConditionalOnMissingClass("dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JSONEventSerializer")
    @ConditionalOnMissingBean
    public JSONSerializer jsonSerializer(List<Module> additionalModules) {
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

        var objectMapper = objectMapperBuilder.build();
        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));

        return new JacksonJSONSerializer(objectMapper);
    }

    /**
     * The {@link LifecycleManager} that handles starting and stopping life cycle beans
     *
     * @param properties the auto configure properties
     * @return the {@link LifecycleManager}
     */
    @Bean
    @ConditionalOnMissingBean
    public LifecycleManager lifecycleController(EssentialsComponentsProperties properties) {
        return new DefaultLifecycleManager(properties.getLifeCycles().isStartLifeCycles());
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.devtools.restart.RestartScope")
    public SpringBootDevToolsClassLoaderChangeContextRefreshedListener contextRefreshedListener(JSONSerializer jsonSerializer) {
        return new SpringBootDevToolsClassLoaderChangeContextRefreshedListener(jsonSerializer);
    }

    private static class SpringBootDevToolsClassLoaderChangeContextRefreshedListener {
        private static final Logger log = LoggerFactory.getLogger(SpringBootDevToolsClassLoaderChangeContextRefreshedListener.class);
        private final JacksonJSONSerializer jacksonJSONSerializer;

        public SpringBootDevToolsClassLoaderChangeContextRefreshedListener(JSONSerializer jsonSerializer) {
            requireNonNull(jsonSerializer, "No jsonSerializer provided");
            this.jacksonJSONSerializer = jsonSerializer instanceof JacksonJSONSerializer ? (JacksonJSONSerializer) jsonSerializer : null;
        }

        @EventListener
        public void handleContextRefresh(ContextRefreshedEvent event) {
            if (jacksonJSONSerializer != null) {
                log.info("Updating the '{}'s internal ObjectMapper's ClassLoader to {} from {}",
                         jacksonJSONSerializer.getClass().getSimpleName(),
                         event.getApplicationContext().getClassLoader(),
                         jacksonJSONSerializer.getObjectMapper().getTypeFactory().getClassLoader()
                        );
                jacksonJSONSerializer.getObjectMapper().setTypeFactory(TypeFactory.defaultInstance().withClassLoader(event.getApplicationContext().getClassLoader()));
            }
        }
    }
}
