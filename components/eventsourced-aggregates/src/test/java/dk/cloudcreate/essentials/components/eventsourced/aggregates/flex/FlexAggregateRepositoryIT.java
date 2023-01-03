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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.flex;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.reactive.EventHandler;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import reactor.core.Disposable;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
public class FlexAggregateRepositoryIT {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
    public static final AggregateType ORDERS    = AggregateType.of("Orders");

    private Jdbi                                                                    jdbi;
    private AggregateType                                                           aggregateType;
    private EventStoreUnitOfWorkFactory<EventStoreUnitOfWork>                       unitOfWorkFactory;
    private TestPersistableEventMapper                                              eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest").withDatabaseName("event-store")
                                                                                                           .withUsername("test-user")
                                                                                                           .withPassword("secret-password");

    private FlexAggregateRepository<OrderId, Order> ordersRepository;
    private RecordingLocalEventBusConsumer          recordingLocalEventBusConsumer;
    private Disposable                              persistedEventFlux;
    private List<PersistedEvent>                    asynchronousOrderEventsReceived;

    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        aggregateType = ORDERS;
        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        eventMapper = new TestPersistableEventMapper();
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                                     unitOfWorkFactory,
                                                                                                     eventMapper,
                                                                                                     SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfigurationUsingJackson(createObjectMapper(),
                                                                                                                                                                                                                IdentifierColumnType.UUID,
                                                                                                                                                                                                                JSONColumnType.JSONB)));
        recordingLocalEventBusConsumer = new RecordingLocalEventBusConsumer();
        eventStore.localEventBus().addSyncSubscriber(recordingLocalEventBusConsumer);

        ordersRepository = FlexAggregateRepository.from(eventStore,
                                                        ORDERS,
                                                        unitOfWorkFactory,
                                                        OrderId.class,
                                                        Order.class);

        asynchronousOrderEventsReceived = new ArrayList<>();
        persistedEventFlux = eventStore.pollEvents(ORDERS,
                                                   GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                   Optional.empty(),
                                                   Optional.of(Duration.ofMillis(100)),
                                                   Optional.empty(),
                                                   Optional.empty())
                                       .subscribe(event -> asynchronousOrderEventsReceived.add(event));
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();

        if (persistedEventFlux != null) {
            persistedEventFlux.dispose();
        }
    }

    @DisplayName("Verify we can persist an Aggregate and load it again")
    @Test
    void persistAndLoadAggregate() {
        // Given
        var orderId    = OrderId.of("0784e5b6-9b27-4236-8797-480c117b0599");
        var productId  = ProductId.of("7397830b-270c-4d0b-838b-dfa0b4d9d116");
        var customerId = CustomerId.of("bc049499-f338-46a5-85fe-26d4da26faff");

        unitOfWorkFactory.usingUnitOfWork(unitOfWorkController -> {
            var eventsToPersist = Order.createNewOrder(orderId, customerId, 123);

            eventsToPersist = eventsToPersist.append(new Order().rehydrate(eventsToPersist).addProduct(productId, 10));
            ordersRepository.persist(eventsToPersist);
        });
        and:
        // Events persisted were published on the Local EventBus
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(2);
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(0).event().getEventTypeOrName().getValue()).isEqualTo(Order.OrderAdded.class.getName());
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(1).aggregateId()).isEqualTo(orderId);
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(1).event().getEventTypeOrName().getValue()).isEqualTo(Order.ProductAddedToOrder.class.getName());
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(2);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(0).event().getEventTypeOrName().getValue()).isEqualTo(Order.OrderAdded.class.getName());
        assertThat((CharSequence) recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(1).aggregateId()).isEqualTo(orderId);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(1).event().getEventTypeOrName().getValue()).isEqualTo(Order.ProductAddedToOrder.class.getName());

        Awaitility.waitAtMost(Duration.ofMillis(2000))
                  .untilAsserted(() -> assertThat(asynchronousOrderEventsReceived.size()).isEqualTo(2));

        // And The events received are the same
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents)
                .isEqualTo(recordingLocalEventBusConsumer.afterCommitPersistedEvents);
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents)
                .isEqualTo(asynchronousOrderEventsReceived);

        // And the events contains everything expected
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).eventId()).isNotNull();
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(asynchronousOrderEventsReceived.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(asynchronousOrderEventsReceived.get(0).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(asynchronousOrderEventsReceived.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(asynchronousOrderEventsReceived.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventName()).isEmpty();
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(Order.OrderAdded.class)));
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(Order.OrderAdded.class).toString());
        assertThat(asynchronousOrderEventsReceived.get(0).event().getJson()).isEqualTo("{\"orderId\": \"0784e5b6-9b27-4236-8797-480c117b0599\", \"orderNumber\": 123, \"orderingCustomerId\": \"bc049499-f338-46a5-85fe-26d4da26faff\"}");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(asynchronousOrderEventsReceived.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(asynchronousOrderEventsReceived.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) asynchronousOrderEventsReceived.get(1).eventId()).isNotNull();
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(asynchronousOrderEventsReceived.get(1).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(asynchronousOrderEventsReceived.get(1).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(asynchronousOrderEventsReceived.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(asynchronousOrderEventsReceived.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(asynchronousOrderEventsReceived.get(1).event().getEventName()).isEmpty();
        assertThat(asynchronousOrderEventsReceived.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(Order.ProductAddedToOrder.class)));
        assertThat(asynchronousOrderEventsReceived.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(Order.ProductAddedToOrder.class).toString());
        assertThat(asynchronousOrderEventsReceived.get(1).event().getJson()).isEqualTo("{\"orderId\": \"0784e5b6-9b27-4236-8797-480c117b0599\", \"quantity\": 10, \"productId\": \"7397830b-270c-4d0b-838b-dfa0b4d9d116\"}");
        assertThat(asynchronousOrderEventsReceived.get(1).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(asynchronousOrderEventsReceived.get(1).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(asynchronousOrderEventsReceived.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(asynchronousOrderEventsReceived.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(asynchronousOrderEventsReceived.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(asynchronousOrderEventsReceived.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        // When
        var result = unitOfWorkFactory.withUnitOfWork(uow -> ordersRepository.load(orderId));

        // Then
        assertThat(result.aggregateId().toString()).isEqualTo(orderId.toString());
        assertThat(result.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(result.eventOrderOfLastRehydratedEvent()).isEqualTo(EventOrder.of(1));
    }

    @DisplayName("Load with correct expectedLatestEventOrder")
    @Test
    void loadBasedOnExpectedLatestEventOrder() {
        // Given
        var orderId   = OrderId.random();
        var productId = ProductId.random();

        unitOfWorkFactory.usingUnitOfWork(unitOfWorkController -> {
            var eventsToPersist = Order.createNewOrder(orderId, CustomerId.random(), 123);
            eventsToPersist = eventsToPersist.append(new Order().rehydrate(eventsToPersist).addProduct(productId, 10));
            eventsToPersist = eventsToPersist.append(new Order().rehydrate(eventsToPersist).accept());
            ordersRepository.persist(eventsToPersist);
        });

        // When
        var result = unitOfWorkFactory.withUnitOfWork(uow -> ordersRepository.load(orderId, 2L));

        // Then
        assertThat(result.aggregateId().toString()).isEqualTo(orderId.toString());
        assertThat(result.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(result.accepted).isTrue();
        assertThat(result.eventOrderOfLastRehydratedEvent()).isEqualTo(EventOrder.of(2));
    }

    @DisplayName("Load with wrong expectedLatestEventOrder")
    @Test
    void loadBasedOnWrongExpectedLatestEventOrderValue() {
        // Given
        var orderId   = OrderId.random();
        var productId = ProductId.random();

        unitOfWorkFactory.usingUnitOfWork(unitOfWorkController -> {
            var eventsToPersist = Order.createNewOrder(orderId, CustomerId.random(), 123);

            eventsToPersist = eventsToPersist.append(new Order().rehydrate(eventsToPersist).addProduct(productId, 10));
            ordersRepository.persist(eventsToPersist);
        });

        // When
        assertThatThrownBy(() -> unitOfWorkFactory.withUnitOfWork(uow -> ordersRepository.load(orderId, 0L)))
                .isInstanceOf(UnitOfWorkException.class)
                .cause().isInstanceOf(OptimisticAggregateLoadException.class);
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
                                         META_DATA,
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         null);
        }
    }

    private static class RecordingLocalEventBusConsumer implements EventHandler {
        private final List<PersistedEvent> beforeCommitPersistedEvents  = new ArrayList<>();
        private final List<PersistedEvent> afterCommitPersistedEvents   = new ArrayList<>();
        private final List<PersistedEvent> afterRollbackPersistedEvents = new ArrayList<>();

        @Override
        public void handle(Object event) {
            var persistedEvents = (PersistedEvents) event;
            if (persistedEvents.commitStage == CommitStage.BeforeCommit) {
                beforeCommitPersistedEvents.addAll(persistedEvents.events);
            } else if (persistedEvents.commitStage == CommitStage.AfterCommit) {
                afterCommitPersistedEvents.addAll(persistedEvents.events);
            } else {
                afterRollbackPersistedEvents.addAll(persistedEvents.events);
            }
        }

        private void clear() {
            beforeCommitPersistedEvents.clear();
            afterCommitPersistedEvents.clear();
            afterRollbackPersistedEvents.clear();
        }
    }

    private ObjectMapper createObjectMapper() {
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

}
