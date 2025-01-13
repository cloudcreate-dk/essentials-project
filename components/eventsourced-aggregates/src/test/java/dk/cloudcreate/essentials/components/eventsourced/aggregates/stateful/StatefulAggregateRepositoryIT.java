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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.Order;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreManagedUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.OffsetDateTime;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;

@Testcontainers
class StatefulAggregateRepositoryIT {
    public static final AggregateType ORDERS = AggregateType.of("Orders");

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest").withDatabaseName("event-store")
                                                                                                           .withUsername("test-user")
                                                                                                           .withPassword("secret-password");

    private Jdbi                               jdbi;
    private AggregateType                      aggregateType;
    private EventStoreManagedUnitOfWorkFactory unitOfWorkFactory;

    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;
    private PostgresqlAggregateSnapshotRepository                                   snapshotRepository;
    private StatefulAggregateRepository<OrderId, OrderEvent, Order>                 ordersRepository;
    private PostgresqlAggregateSnapshotRepository                                   snapshotRepositorySpy;

    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        aggregateType = ORDERS;
        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        var aggregateEventStreamConfigurationFactory = SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration(new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                                      IdentifierColumnType.UUID,
                                                                                                                                                      JSONColumnType.JSONB);
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                                     unitOfWorkFactory,
                                                                                                     new TestPersistableEventMapper(),
                                                                                                     aggregateEventStreamConfigurationFactory));

        snapshotRepository = new PostgresqlAggregateSnapshotRepository(eventStore,
                                                                       unitOfWorkFactory,
                                                                       aggregateEventStreamConfigurationFactory.jsonSerializer,
                                                                       AddNewAggregateSnapshotStrategy.updateWhenBehindByNumberOfEvents(2),
                                                                       AggregateSnapshotDeletionStrategy.keepALimitedNumberOfHistoricSnapshots(3));
        snapshotRepositorySpy = Mockito.spy(snapshotRepository);
        ordersRepository = StatefulAggregateRepository.from(eventStore,
                                                            ORDERS,
                                                            reflectionBasedAggregateRootFactory(),
                                                            Order.class,
                                                            snapshotRepositorySpy);
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
    }


    @Test
    void persisting_the_first_event_does_not_create_a_snapshot() {
        var orderId         = OrderId.random();
        var order           = new Order(orderId, CustomerId.random(), 1234);
        unitOfWorkFactory.usingUnitOfWork(() -> {
            ordersRepository.save(order);
        });
        Mockito.verify(snapshotRepositorySpy).aggregateUpdated(eq(order), any());
        var snapshotOptional = snapshotRepository.loadSnapshot(ORDERS,
                                                               orderId,
                                                               Order.class);
        assertThat(snapshotOptional).isEmpty();

        // And load it again
        var loadedOrder = unitOfWorkFactory.withUnitOfWork(() -> ordersRepository.load(orderId));

        assertThat(loadedOrder).usingRecursiveComparison()
                               .ignoringFieldsMatchingRegexes("invoker",
                                                              "eventOrderOfLastRehydratedEvent",
                                                              "hasBeenRehydrated")
                               .isEqualTo(order);
        Mockito.verify(snapshotRepositorySpy).loadSnapshot(eq(ORDERS), eq(orderId), eq(Order.class));
    }

    @Test
    void persisting_two_events_creates_a_snapshot() {
        var orderId         = OrderId.random();
        var order           = new Order(orderId, CustomerId.random(), 1234);
        order.addProduct(ProductId.random(), 10);
        var eventsToPersist = order.getUncommittedChanges();
        unitOfWorkFactory.usingUnitOfWork(() -> {
            ordersRepository.save(order);
        });
        Mockito.verify(snapshotRepositorySpy).aggregateUpdated(eq(order), any());
        var snapshotOptional = snapshotRepository.loadSnapshot(ORDERS,
                                                               orderId,
                                                               Order.class);
        assertThat(snapshotOptional).isPresent();
        assertThat((CharSequence) snapshotOptional.get().aggregateId).isEqualTo(orderId);
        assertThat(snapshotOptional.get().eventOrderOfLastIncludedEvent).isEqualTo(EventOrder.of(1));
        assertThat(snapshotOptional.get().aggregateImplType).isEqualTo(Order.class);
        assertThat((CharSequence) snapshotOptional.get().aggregateType).isEqualTo(ORDERS);
        assertThat(snapshotOptional.get().aggregateSnapshot).usingRecursiveComparison()
                                                            .ignoringFieldsMatchingRegexes("invoker")
                                                            .isEqualTo(order);


        // And load it again
        var loadedOrder = unitOfWorkFactory.withUnitOfWork(() -> ordersRepository.load(orderId));

        assertThat(loadedOrder).usingRecursiveComparison()
                               .ignoringFieldsMatchingRegexes("invoker",
                                                              "eventOrderOfLastRehydratedEvent",
                                                              "hasBeenRehydrated")
                               .isEqualTo(order);
        Mockito.verify(snapshotRepositorySpy).loadSnapshot(eq(ORDERS), eq(orderId), eq(Order.class));
    }

    static ObjectMapper createObjectMapper() {
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

    static class TestPersistableEventMapper implements PersistableEventMapper {
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
                                         new EventMetaData(),
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         null);
        }
    }
}