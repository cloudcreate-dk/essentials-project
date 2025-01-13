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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreManagedUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.openjdk.jmh.annotations.*;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(10)
public class PostgresqlEventStoreBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    @State(Scope.Benchmark)
    public static class PerformanceTestState {

        @Param({"1", "5", "10", "20"})
        public  int                                                                     appendedEvents;
        private PostgreSQLContainer<?>                                                  postgreSQLContainer;
        private HikariDataSource                                                        ds;
        public  AggregateType                                                           aggregateType;
        public  EventStoreManagedUnitOfWorkFactory                                      unitOfWorkFactory;
        public  PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;
        public  AtomicLong                                                              orderNumber = new AtomicLong(0);

        @Setup(Level.Trial)
        public void trialSetUp() {
            System.out.println("Trial setup");
            postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
                    .withDatabaseName("event-store")
                    .withUsername("test-user")
                    .withPassword("secret-password");
            postgreSQLContainer.start();
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(postgreSQLContainer.getJdbcUrl());
            hikariConfig.setUsername(postgreSQLContainer.getUsername());
            hikariConfig.setPassword(postgreSQLContainer.getPassword());
            ds = new HikariDataSource(hikariConfig);
            var jdbi = Jdbi.create(ds);
            jdbi.installPlugin(new PostgresPlugin());
            jdbi.setSqlLogger(new SqlExecutionTimeLogger());

            aggregateType = AggregateType.of("Orders");
            unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
            var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                           unitOfWorkFactory,
                                                                                           new TestPersistableEventMapper(),
                                                                                           SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration(new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                                                                          IdentifierColumnType.UUID,
                                                                                                                                                                                          JSONColumnType.JSONB));
            eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                    persistenceStrategy);
            eventStore.addAggregateEventStreamConfiguration(aggregateType,
                                                            OrderId.class);
        }

        @TearDown(Level.Trial)
        public void trialTeardown() {
            System.out.println("Trial teardown");
            ds.close();
            postgreSQLContainer.stop();
        }
//
//        @Setup(Level.Invocation)
//        public void InvocationSetUp() {
//        }
//
//        @Setup(Level.Iteration)
//        public void IterationSetUp() {
//            System.out.println("Iteration setup: " + appendedEvents);
//        }
    }

    @Benchmark
    public void appendEvents(PerformanceTestState state) {
        state.unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            var orderId      = OrderId.random();
            var appendEvents = new ArrayList<OrderEvent>();
            appendEvents.add(new OrderEvent.OrderAdded(orderId,
                                                       CustomerId.random(),
                                                       state.orderNumber.incrementAndGet()));
            for (var i = 0; i < state.appendedEvents; i++) {
                appendEvents.add(new OrderEvent.ProductAddedToOrder(orderId,
                                                                    ProductId.random(),
                                                                    2));
            }
            state.eventStore.appendToStream(state.aggregateType,
                                            orderId,
                                            appendEvents);
        });
    }

    @Benchmark
    public void appendEventsAndLoadEvents(PerformanceTestState state) {
        var orderId = OrderId.random();
        state.unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            var appendEvents = new ArrayList<OrderEvent>();
            appendEvents.add(new OrderEvent.OrderAdded(orderId,
                                                       CustomerId.random(),
                                                       state.orderNumber.incrementAndGet()));
            for (var i = 0; i < state.appendedEvents; i++) {
                appendEvents.add(new OrderEvent.ProductAddedToOrder(orderId,
                                                                    ProductId.random(),
                                                                    2));
            }
            state.eventStore.appendToStream(state.aggregateType,
                                            orderId,
                                            appendEvents);
        });
        state.unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            var eventStream = state.eventStore.fetchStream(state.aggregateType,
                                                           orderId).get();
            var events = eventStream.events().map(persistedEvent -> persistedEvent.event().getJsonDeserialized().get()).collect(Collectors.toList());
            if (events.size() != 1 + state.appendedEvents) {
                throw new RuntimeException("Loaded " + events.size() + " but expected " + (1 + state.appendedEvents));
            }
        });
    }

    private static ObjectMapper createObjectMapper() {
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

    private static class TestPersistableEventMapper implements PersistableEventMapper {
        private final CorrelationId correlationId   = CorrelationId.random();
        private final EventId       causedByEventId = EventId.random();

        @Override
        public PersistableEvent map(Object aggregateId, AggregateEventStreamConfiguration aggregateEventStreamConfiguration, Object event, EventOrder eventOrder) {
            return PersistableEvent.from(EventId.random(),
                                         aggregateEventStreamConfiguration.aggregateType,
                                         aggregateId,
                                         EventTypeOrName.with(event.getClass()),
                                         event,
                                         eventOrder,
                                         EventRevision.of(1),
                                         EventMetaData.of("Key1", "Value1", "Key2", "Value2"),
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         null);
        }
    }
}
