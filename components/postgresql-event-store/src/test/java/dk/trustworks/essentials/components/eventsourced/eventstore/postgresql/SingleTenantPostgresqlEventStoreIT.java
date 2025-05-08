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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.gap.PostgresqlEventStreamGapHandler;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.observability.EventStoreSubscriptionObserver;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.trustworks.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.components.foundation.types.*;
import dk.trustworks.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.trustworks.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.trustworks.essentials.reactive.EventHandler;
import dk.trustworks.essentials.types.*;
import org.awaitility.Awaitility;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import reactor.core.publisher.Flux;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.*;

import static dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration;
import static dk.trustworks.essentials.shared.FailFast.requireNonNull;
import static dk.trustworks.essentials.shared.MessageFormatter.msg;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SingleTenantPostgresqlEventStoreIT {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
    public static final AggregateType PRODUCTS  = AggregateType.of("Products");
    public static final AggregateType ORDERS    = AggregateType.of("Orders");

    private Jdbi                                                                    jdbi;
    private AggregateType                                                           aggregateType;
    private EventStoreUnitOfWorkFactory<EventStoreUnitOfWork>                       unitOfWorkFactory;
    private TestPersistableEventMapper                                              eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?>         postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");
    private       RecordingLocalEventBusConsumer recordingLocalEventBusConsumer;


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
        var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                       unitOfWorkFactory,
                                                                                       eventMapper,
                                                                                       standardSingleTenantConfiguration(aggregateType_ -> aggregateType_ + "_events",
                                                                                                                         EventStreamTableColumnNames.defaultColumnNames(),
                                                                                                                         new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                         IdentifierColumnType.UUID,
                                                                                                                         JSONColumnType.JSONB));
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                persistenceStrategy,
                                                Optional.empty(),
                                                eventStore -> new PostgresqlEventStreamGapHandler<>(eventStore,
                                                                                                    unitOfWorkFactory),
                                                new EventStoreSubscriptionObserver.NoOpEventStoreSubscriptionObserver());
        eventStore.addAggregateEventStreamConfiguration(aggregateType,
                                                        AggregateIdSerializer.serializerFor(OrderId.class));
        recordingLocalEventBusConsumer = new RecordingLocalEventBusConsumer();
        eventStore.localEventBus()
                  .addSyncSubscriber(recordingLocalEventBusConsumer);
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
    }

    @Test
    void test_resolveGlobalEventOrderSequenceName() {
        var unitOfWork   = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var sequenceName = eventStore.getPersistenceStrategy().resolveGlobalEventOrderSequenceName(unitOfWork, ORDERS);
        assertThat(sequenceName).isPresent();
        assertThat(sequenceName).hasValue("public.orders_events_global_order_seq");
    }

    @Test
    void persisting_events_related_to_two_different_aggregates_in_one_unitofwork() {
        // Given
        var orderId1     = OrderId.of("beed77fb-d911-1111-9c48-03ed5bfe8f89");
        var customerId1  = CustomerId.of("Test-Customer-Id-10");
        var orderNumber1 = 1234;
        var orderId2     = OrderId.of("ceed77fb-d911-9c48-1111-03ed5bfe8f89");
        var customerId2  = CustomerId.of("Test-Customer-Id2-10");
        var orderNumber2 = 4321;

        // When
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var persistableEventsOrder1 = List.of(new OrderEvent.OrderAdded(orderId1,
                                                                        customerId1,
                                                                        orderNumber1));
        var order1AggregateStream = eventStore.startStream(aggregateType,
                                                           orderId1,
                                                           persistableEventsOrder1);

        var persistableEventsOrder2 = List.of(new OrderEvent.OrderAdded(orderId2,
                                                                        customerId2,
                                                                        orderNumber2));
        var order2AggregateStream = eventStore.startStream(aggregateType,
                                                           orderId2,
                                                           persistableEventsOrder2);
        unitOfWork.commit();

        // Then
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var lastPersistedEventOrder1 = eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId1);
        assertThat(lastPersistedEventOrder1).isPresent();
        assertThat((CharSequence) lastPersistedEventOrder1.get().eventId()).isNotNull();
        assertThat((CharSequence) lastPersistedEventOrder1.get().aggregateId()).isEqualTo(orderId1);
        assertThat((CharSequence) lastPersistedEventOrder1.get().aggregateType()).isEqualTo(aggregateType);
        assertThat(lastPersistedEventOrder1.get().eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(lastPersistedEventOrder1.get().eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(lastPersistedEventOrder1.get().globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(lastPersistedEventOrder1.get().timestamp()).isBefore(OffsetDateTime.now());
        assertThat(lastPersistedEventOrder1.get().event().getEventName()).isEmpty();
        assertThat(lastPersistedEventOrder1.get().event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(lastPersistedEventOrder1.get().event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(lastPersistedEventOrder1.get().event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEventsOrder1.get(0));
        assertThat(lastPersistedEventOrder1.get().event().getJson()).isEqualTo(
                "{\"orderId\": \"beed77fb-d911-1111-9c48-03ed5bfe8f89\", \"orderNumber\": 1234, \"orderingCustomerId\": \"Test-Customer-Id-10\"}"); // Note formatting changed by postgresql
        assertThat(lastPersistedEventOrder1.get().metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(lastPersistedEventOrder1.get().metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(lastPersistedEventOrder1.get().metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(lastPersistedEventOrder1.get().metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(lastPersistedEventOrder1.get().causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(lastPersistedEventOrder1.get().correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        var lastPersistedEventOrder2 = eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId2);
        assertThat(lastPersistedEventOrder2).isPresent();
        assertThat((CharSequence) lastPersistedEventOrder2.get().eventId()).isNotNull();
        assertThat((CharSequence) lastPersistedEventOrder2.get().aggregateId()).isEqualTo(orderId2);
        assertThat((CharSequence) lastPersistedEventOrder2.get().aggregateType()).isEqualTo(aggregateType);
        assertThat(lastPersistedEventOrder2.get().eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(lastPersistedEventOrder2.get().eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(lastPersistedEventOrder2.get().globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(lastPersistedEventOrder2.get().timestamp()).isBefore(OffsetDateTime.now());
        assertThat(lastPersistedEventOrder2.get().event().getEventName()).isEmpty();
        assertThat(lastPersistedEventOrder2.get().event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(lastPersistedEventOrder2.get().event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(lastPersistedEventOrder2.get().event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEventsOrder2.get(0));
        assertThat(lastPersistedEventOrder2.get().event().getJson()).isEqualTo(
                "{\"orderId\": \"ceed77fb-d911-9c48-1111-03ed5bfe8f89\", \"orderNumber\": 4321, \"orderingCustomerId\": \"Test-Customer-Id2-10\"}"); // Note formatting changed by postgresql
        assertThat(lastPersistedEventOrder2.get().metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(lastPersistedEventOrder2.get().metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(lastPersistedEventOrder2.get().metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(lastPersistedEventOrder2.get().metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(lastPersistedEventOrder2.get().causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(lastPersistedEventOrder2.get().correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        // And both events were published on the local events
        assertThat(recordingLocalEventBusConsumer.flushPersistedEvents).isEmpty();
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(2);
        assertThat((CharSequence) recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(0).eventId()).isEqualTo(lastPersistedEventOrder1.get().eventId());
        assertThat((CharSequence) recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(1).eventId()).isEqualTo(lastPersistedEventOrder2.get().eventId());

        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(2);
        assertThat((CharSequence) recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(0).eventId()).isEqualTo(lastPersistedEventOrder1.get().eventId());
        assertThat((CharSequence) recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(1).eventId()).isEqualTo(lastPersistedEventOrder2.get().eventId());

        assertThat(recordingLocalEventBusConsumer.afterRollbackPersistedEvents.size()).isEqualTo(0);
    }

    @Test
    void loadLastPersistedEventRelatedTo__before_and_after_persisting_an_event() {
        // Given
        var orderId    = OrderId.of("beed77fb-d911-1111-9c48-03ed5bfe8f89");
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        assertThat(eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId)).isEmpty();

        // When
        var customerId = CustomerId.of("Test-Customer-Id-10");
        var persistableEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                                  customerId,
                                                                  1234));
        eventStore.startStream(aggregateType,
                               orderId,
                               persistableEvents);

        // Then
        var lastPersistedEvent = eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId);
        assertThat(lastPersistedEvent).isPresent();
        assertThat((CharSequence) lastPersistedEvent.get().eventId()).isNotNull();
        assertThat((CharSequence) lastPersistedEvent.get().aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) lastPersistedEvent.get().aggregateType()).isEqualTo(aggregateType);
        assertThat(lastPersistedEvent.get().eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(lastPersistedEvent.get().eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(lastPersistedEvent.get().globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(lastPersistedEvent.get().timestamp()).isBefore(OffsetDateTime.now());
        assertThat(lastPersistedEvent.get().event().getEventName()).isEmpty();
        assertThat(lastPersistedEvent.get().event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(lastPersistedEvent.get().event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(lastPersistedEvent.get().event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(0));
        assertThat(lastPersistedEvent.get().event().getJson()).isEqualTo(
                "{\"orderId\": \"beed77fb-d911-1111-9c48-03ed5bfe8f89\", \"orderNumber\": 1234, \"orderingCustomerId\": \"Test-Customer-Id-10\"}"); // Note formatting changed by postgresql
        assertThat(lastPersistedEvent.get().metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(lastPersistedEvent.get().metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(lastPersistedEvent.get().metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(lastPersistedEvent.get().metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(lastPersistedEvent.get().causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(lastPersistedEvent.get().correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void loadLastPersistedEventRelatedTo_before_and_after_persisting_three_event() {
        // Given
        var orderId    = OrderId.of("beed77fb-1111-1111-9c48-03ed5bfe8f89");
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        assertThat(eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId)).isEmpty();

        // When
        var customerId = CustomerId.of("Test-Customer-Id-11");
        var persistableEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                                  customerId,
                                                                  1234),
                                        new OrderEvent.ProductAddedToOrder(orderId,
                                                                           ProductId.of("ProductId-1"),
                                                                           2),
                                        new OrderEvent.ProductRemovedFromOrder(orderId,
                                                                               ProductId.of("ProductId-1")));
        eventStore.appendToStream(aggregateType,
                                  orderId,
                                  persistableEvents);

        // Then
        var lastPersistedEvent = eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId);
        assertThat(lastPersistedEvent).isPresent();
        assertThat((CharSequence) lastPersistedEvent.get().eventId()).isNotNull();
        assertThat((CharSequence) lastPersistedEvent.get().aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) lastPersistedEvent.get().aggregateType()).isEqualTo(aggregateType);
        assertThat(lastPersistedEvent.get().eventOrder()).isEqualTo(EventOrder.of(2));
        assertThat(lastPersistedEvent.get().eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(lastPersistedEvent.get().globalEventOrder()).isEqualTo(GlobalEventOrder.of(3));
        assertThat(lastPersistedEvent.get().timestamp()).isBefore(OffsetDateTime.now());
        assertThat(lastPersistedEvent.get().event().getEventName()).isEmpty();
        assertThat(lastPersistedEvent.get().event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductRemovedFromOrder.class)));
        assertThat(lastPersistedEvent.get().event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductRemovedFromOrder.class).toString());
        assertThat(lastPersistedEvent.get().event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(2));
        assertThat(lastPersistedEvent.get().event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1111-1111-9c48-03ed5bfe8f89\", \"productId\": \"ProductId-1\"}"); // Note formatting changed by postgresql
        assertThat(lastPersistedEvent.get().metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(lastPersistedEvent.get().metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(lastPersistedEvent.get().metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(lastPersistedEvent.get().metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(lastPersistedEvent.get().causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(lastPersistedEvent.get().correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void loadEvent() {
        // Given
        var orderId    = OrderId.of("beed77fb-1112-1111-9c48-03ed5bfe8f89");
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        assertThat(eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId)).isEmpty();

        // When
        var customerId = CustomerId.of("Test-Customer-Id-12");
        var persistableEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                                  customerId,
                                                                  1234),
                                        new OrderEvent.ProductAddedToOrder(orderId,
                                                                           ProductId.of("ProductId-1"),
                                                                           2),
                                        new OrderEvent.ProductRemovedFromOrder(orderId,
                                                                               ProductId.of("ProductId-1")));
        var persistedEventStream = eventStore.appendToStream(aggregateType,
                                                             orderId,
                                                             persistableEvents);

        // Then
        var specificEvent = eventStore.loadEvent(aggregateType, persistedEventStream.eventList().get(1).eventId());
        assertThat(specificEvent).isPresent();
        assertThat((CharSequence) specificEvent.get().eventId()).isNotNull();
        assertThat((CharSequence) specificEvent.get().aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) specificEvent.get().aggregateType()).isEqualTo(aggregateType);
        assertThat(specificEvent.get().eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(specificEvent.get().eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(specificEvent.get().globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(specificEvent.get().timestamp()).isBefore(OffsetDateTime.now());
        assertThat(specificEvent.get().event().getEventName()).isEmpty();
        assertThat(specificEvent.get().event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(specificEvent.get().event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(specificEvent.get().event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(1));
        assertThat(specificEvent.get().event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1112-1111-9c48-03ed5bfe8f89\", \"quantity\": 2, \"productId\": \"ProductId-1\"}"); // Note formatting changed by postgresql
        assertThat(specificEvent.get().metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(specificEvent.get().metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(specificEvent.get().metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(specificEvent.get().metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(specificEvent.get().causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(specificEvent.get().correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void fetchStream_with_open_EventOrder_range() {
        // Given
        var orderId    = OrderId.of("beed77fb-1112-1112-9c48-03ed5bfe8f89");
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        assertThat(eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId)).isEmpty();

        // When
        var customerId = CustomerId.of("Test-Customer-Id-13");
        var persistableEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                                  customerId,
                                                                  1234),
                                        new OrderEvent.ProductAddedToOrder(orderId,
                                                                           ProductId.of("ProductId-1"),
                                                                           2),
                                        new OrderEvent.ProductRemovedFromOrder(orderId,
                                                                               ProductId.of("ProductId-1")));
        var persistedEventStream = eventStore.appendToStream(aggregateType,
                                                             orderId,
                                                             persistableEvents);

        // Then
        var possibleEventStream = eventStore.fetchStream(aggregateType, orderId, LongRange.from(1));
        assertThat(possibleEventStream).isPresent();
        var loadedEventStream = possibleEventStream.get();
        assertThat((CharSequence) loadedEventStream.aggregateId()).isEqualTo(orderId);
        assertThat(loadedEventStream.isPartialEventStream()).isTrue();
        assertThat(loadedEventStream.eventOrderRangeIncluded()).isEqualTo(LongRange.from(1));
        assertThat((CharSequence) loadedEventStream.aggregateType()).isEqualTo(aggregateType);
        var eventsLoaded = loadedEventStream.events().collect(Collectors.toList());
        assertThat(eventsLoaded.size()).isEqualTo(2);
        assertThat((CharSequence) eventsLoaded.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(0).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(eventsLoaded.get(0).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsLoaded.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(eventsLoaded.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(0).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(eventsLoaded.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(eventsLoaded.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(1));
        assertThat(eventsLoaded.get(0).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1112-1112-9c48-03ed5bfe8f89\", \"quantity\": 2, \"productId\": \"ProductId-1\"}"); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) eventsLoaded.get(1).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(1).eventOrder()).isEqualTo(EventOrder.of(2));
        assertThat(eventsLoaded.get(1).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsLoaded.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(3));
        assertThat(eventsLoaded.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(1).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductRemovedFromOrder.class)));
        assertThat(eventsLoaded.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductRemovedFromOrder.class).toString());
        assertThat(eventsLoaded.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(2));
        assertThat(eventsLoaded.get(1).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1112-1112-9c48-03ed5bfe8f89\", \"productId\": \"ProductId-1\"}"); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void fetchStream_with_closed_EventOrder_range() {
        // Given
        var orderId    = OrderId.of("beed77fb-1113-1113-9c48-03ed5bfe8f89");
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        assertThat(eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId)).isEmpty();

        // When
        var customerId = CustomerId.of("Test-Customer-Id-14");
        var persistableEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                                  customerId,
                                                                  1234),
                                        new OrderEvent.ProductAddedToOrder(orderId,
                                                                           ProductId.of("ProductId-1"),
                                                                           2),
                                        new OrderEvent.ProductRemovedFromOrder(orderId,
                                                                               ProductId.of("ProductId-1")));
        var persistedEventStream = eventStore.appendToStream(aggregateType,
                                                             orderId,
                                                             persistableEvents);

        // Then
        var possibleEventStream = eventStore.fetchStream(aggregateType, orderId, LongRange.between(0, 1));
        assertThat(possibleEventStream).isPresent();
        var loadedEventStream = possibleEventStream.get();
        assertThat((CharSequence) loadedEventStream.aggregateId()).isEqualTo(orderId);
        assertThat(loadedEventStream.isPartialEventStream()).isTrue();
        assertThat(loadedEventStream.eventOrderRangeIncluded()).isEqualTo(LongRange.between(0, 1));
        assertThat((CharSequence) loadedEventStream.aggregateType()).isEqualTo(aggregateType);
        var eventsLoaded = loadedEventStream.events().collect(Collectors.toList());
        assertThat(eventsLoaded.size()).isEqualTo(2);

        assertThat((CharSequence) eventsLoaded.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(eventsLoaded.get(0).eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(eventsLoaded.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(eventsLoaded.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(0).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(eventsLoaded.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(eventsLoaded.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(0));
        assertThat(eventsLoaded.get(0).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1113-1113-9c48-03ed5bfe8f89\", \"orderNumber\": 1234, \"orderingCustomerId\": \"Test-Customer-Id-14\"}"); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) eventsLoaded.get(1).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(1).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(eventsLoaded.get(1).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsLoaded.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(eventsLoaded.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(1).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(eventsLoaded.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(eventsLoaded.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(1));
        assertThat(eventsLoaded.get(1).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1113-1113-9c48-03ed5bfe8f89\", \"quantity\": 2, \"productId\": \"ProductId-1\"}"); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void persist_and_load_a_single_event() {
        // Given
        var orderId    = OrderId.of("beed77fb-d911-480f-9c48-03ed5bfe8f89");
        var customerId = CustomerId.of("Test-Customer-Id");
        var persistableEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                                  customerId,
                                                                  1234));
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();

        // When
        var persistedEventsStream = eventStore.appendToStream(aggregateType,
                                                              orderId,
                                                              persistableEvents);
        unitOfWork.commit();

        // Then
        assertThat(persistedEventsStream).isNotNull();
        assertThat((CharSequence) persistedEventsStream.aggregateId()).isEqualTo(orderId);
        assertThat(persistedEventsStream.isPartialEventStream()).isTrue();
        var eventsPersisted = persistedEventsStream.eventList();
        assertThat(eventsPersisted.size()).isEqualTo(1);
        assertThat((CharSequence) eventsPersisted.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsPersisted.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsPersisted.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsPersisted.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(eventsPersisted.get(0).eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(eventsPersisted.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(eventsPersisted.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsPersisted.get(0).timestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(eventsPersisted.get(0).event().getEventName()).isEmpty();
        assertThat(eventsPersisted.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(eventsPersisted.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(eventsPersisted.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(0));
        assertThat(eventsPersisted.get(0).event().getJson()).isEqualTo("{\"orderId\":\"beed77fb-d911-480f-9c48-03ed5bfe8f89\",\"orderingCustomerId\":\"Test-Customer-Id\",\"orderNumber\":1234}");
        assertThat(eventsPersisted.get(0).metaData().getJson()).contains("\"Key1\":\"Value1\"");
        assertThat(eventsPersisted.get(0).metaData().getJson()).contains("\"Key2\":\"Value2\"");
        assertThat(eventsPersisted.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsPersisted.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsPersisted.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsPersisted.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        // Load events again
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var loadedEventStream = eventStore.fetchStream(aggregateType,
                                                       orderId).get();
        assertThat(loadedEventStream).isNotNull();
        assertThat(loadedEventStream.eventOrderRangeIncluded()).isEqualTo(LongRange.from(0));
        assertThat((CharSequence) loadedEventStream.aggregateId()).isEqualTo(orderId);
        assertThat(loadedEventStream.isPartialEventStream()).isFalse();
        assertThat((CharSequence) loadedEventStream.aggregateType()).isEqualTo(aggregateType);
        var eventsLoaded = loadedEventStream.events().collect(Collectors.toList());
        assertThat(eventsLoaded.size()).isEqualTo(1);
        assertThat((CharSequence) eventsLoaded.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(eventsLoaded.get(0).eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(eventsLoaded.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(eventsLoaded.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(0).timestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(eventsLoaded.get(0).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(eventsLoaded.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(eventsLoaded.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(0));
        assertThat(eventsLoaded.get(0).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-d911-480f-9c48-03ed5bfe8f89\", \"orderNumber\": 1234, \"orderingCustomerId\": \"Test-Customer-Id\"}"); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        // Check equals and hashCode work
        assertThat(eventsLoaded.get(0)).isEqualTo(eventsPersisted.get(0));
        assertThat(eventsLoaded.get(0).hashCode()).isEqualTo(eventsPersisted.get(0).hashCode());
    }

    @Test
    void append_events_to_existing_aggregate_eventstream() {
        // Given
        var orderId    = OrderId.of("beed77fb-d911-480f-9c48-03ed5bfeffff");
        var customerId = CustomerId.of("Test-Customer-Id-2");
        var initialEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                              customerId,
                                                              1234));
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();

        var highestGlobalEventOrderPersisted = eventStore.getPersistenceStrategy().findHighestGlobalEventOrderPersisted(unitOfWork, aggregateType);
        assertThat(highestGlobalEventOrderPersisted).isEmpty();
        var lowestGlobalEventOrderPersisted = eventStore.getPersistenceStrategy().findLowestGlobalEventOrderPersisted(unitOfWork, aggregateType);
        assertThat(lowestGlobalEventOrderPersisted).isEmpty();

        // When
        var persistedEventsStream = eventStore.appendToStream(aggregateType,
                                                              orderId,
                                                              initialEvents);
        unitOfWork.commit();

        // Then
        assertThat(persistedEventsStream).isNotNull();
        assertThat((CharSequence) persistedEventsStream.aggregateId()).isEqualTo(orderId);
        assertThat(persistedEventsStream.isPartialEventStream()).isTrue();
        var eventsPersisted = persistedEventsStream.eventList();
        assertThat(eventsPersisted.size()).isEqualTo(1);
        assertThat((CharSequence) eventsPersisted.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsPersisted.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsPersisted.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsPersisted.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(eventsPersisted.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));

        // Append to Stream
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var appendEvents = List.of(new OrderEvent.ProductAddedToOrder(orderId,
                                                                      ProductId.of("ProductId-1"),
                                                                      2),
                                   new OrderEvent.ProductRemovedFromOrder(orderId,
                                                                          ProductId.of("ProductId-1")));

        var appendedEventStream = eventStore.appendToStream(aggregateType,
                                                            orderId,
                                                            appendEvents);
        assertThat(appendedEventStream).isNotNull();
        assertThat((CharSequence) appendedEventStream.aggregateId()).isEqualTo(orderId);
        assertThat(appendedEventStream.isPartialEventStream()).isTrue();
        assertThat(appendedEventStream.eventOrderRangeIncluded()).isEqualTo(LongRange.from(1, 2));
        assertThat((CharSequence) appendedEventStream.aggregateType()).isEqualTo(aggregateType);
        var eventsAppended = appendedEventStream.events().collect(Collectors.toList());
        assertThat(eventsAppended.size()).isEqualTo(2);
        assertThat((CharSequence) eventsAppended.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsAppended.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsAppended.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsAppended.get(0).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(eventsAppended.get(0).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsAppended.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(eventsAppended.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsAppended.get(0).event().getEventName()).isEmpty();
        assertThat(eventsAppended.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(eventsAppended.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(eventsAppended.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(appendEvents.get(0));
        assertThat(eventsAppended.get(0).event().getJson()).isEqualTo("{\"orderId\":\"beed77fb-d911-480f-9c48-03ed5bfeffff\",\"productId\":\"ProductId-1\",\"quantity\":2}");
        assertThat(eventsAppended.get(0).metaData().getJson()).contains("\"Key1\":\"Value1\"");
        assertThat(eventsAppended.get(0).metaData().getJson()).contains("\"Key2\":\"Value2\"");
        assertThat(eventsAppended.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsAppended.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsAppended.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsAppended.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) eventsAppended.get(1).eventId()).isNotNull();
        assertThat((CharSequence) eventsAppended.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsAppended.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsAppended.get(1).eventOrder()).isEqualTo(EventOrder.of(2));
        assertThat(eventsAppended.get(1).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsAppended.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(3));
        assertThat(eventsAppended.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsAppended.get(1).event().getEventName()).isEmpty();
        assertThat(eventsAppended.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductRemovedFromOrder.class)));
        assertThat(eventsAppended.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductRemovedFromOrder.class).toString());
        assertThat(eventsAppended.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(appendEvents.get(1));
        assertThat(eventsAppended.get(1).event().getJson()).isEqualTo("{\"orderId\":\"beed77fb-d911-480f-9c48-03ed5bfeffff\",\"productId\":\"ProductId-1\"}");
        assertThat(eventsAppended.get(1).metaData().getJson()).contains("\"Key1\":\"Value1\"");
        assertThat(eventsAppended.get(1).metaData().getJson()).contains("\"Key2\":\"Value2\"");
        assertThat(eventsAppended.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsAppended.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsAppended.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsAppended.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
        unitOfWork.commit();

        // And load events again
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var loadedEventStream = eventStore.fetchStream(aggregateType,
                                                       orderId).get();
        assertThat(loadedEventStream).isNotNull();
        assertThat((CharSequence) loadedEventStream.aggregateId()).isEqualTo(orderId);
        assertThat(loadedEventStream.isPartialEventStream()).isFalse();
        assertThat(appendedEventStream.eventOrderRangeIncluded()).isEqualTo(LongRange.between(1, 2));
        assertThat((CharSequence) loadedEventStream.aggregateType()).isEqualTo(aggregateType);
        var eventsLoaded = loadedEventStream.events().collect(Collectors.toList());
        assertThat(eventsLoaded.size()).isEqualTo(3);
        assertThat((CharSequence) eventsLoaded.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(eventsLoaded.get(0).eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(eventsLoaded.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(eventsLoaded.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(0).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(eventsLoaded.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(eventsLoaded.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(initialEvents.get(0));
        assertThat(eventsLoaded.get(0).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-d911-480f-9c48-03ed5bfeffff\", \"orderNumber\": 1234, \"orderingCustomerId\": \"Test-Customer-Id-2\"}"); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) eventsLoaded.get(1).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(1).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(eventsLoaded.get(1).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsLoaded.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(eventsLoaded.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(1).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(eventsLoaded.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(eventsLoaded.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(appendEvents.get(0));
        assertThat(eventsLoaded.get(1).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-d911-480f-9c48-03ed5bfeffff\", \"quantity\": 2, \"productId\": \"ProductId-1\"}");
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(eventsLoaded.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) eventsLoaded.get(2).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(2).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(2).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(2).eventOrder()).isEqualTo(EventOrder.of(2));
        assertThat(eventsLoaded.get(2).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsLoaded.get(2).globalEventOrder()).isEqualTo(GlobalEventOrder.of(3));
        assertThat(eventsLoaded.get(2).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(2).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(2).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductRemovedFromOrder.class)));
        assertThat(eventsLoaded.get(2).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductRemovedFromOrder.class).toString());
        assertThat(eventsLoaded.get(2).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(appendEvents.get(1));
        assertThat(eventsLoaded.get(2).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-d911-480f-9c48-03ed5bfeffff\", \"productId\": \"ProductId-1\"}");
        assertThat(eventsLoaded.get(2).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(eventsLoaded.get(2).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(eventsLoaded.get(2).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(2).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(2).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(2).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        // Verify lowest and highest globalorder persisted after all events are persisted
        highestGlobalEventOrderPersisted = eventStore.getPersistenceStrategy().findHighestGlobalEventOrderPersisted(unitOfWork, aggregateType);
        assertThat(highestGlobalEventOrderPersisted).isPresent();
        assertThat(highestGlobalEventOrderPersisted.get()).isEqualTo(GlobalEventOrder.of(3));

        lowestGlobalEventOrderPersisted = eventStore.getPersistenceStrategy().findLowestGlobalEventOrderPersisted(unitOfWork, aggregateType);
        assertThat(lowestGlobalEventOrderPersisted).isPresent();
        assertThat(lowestGlobalEventOrderPersisted.get()).isEqualTo(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER);
    }

    @Test
    void append_events_to_with_overlapping_event_order() {
        // Given
        var orderId    = OrderId.of("beed77fb-d911-480f-9c48-03ed5bfe1111");
        var customerId = CustomerId.of("Test-Customer-Id-3");
        var initialEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                              customerId,
                                                              1234),
                                    new OrderEvent.ProductAddedToOrder(orderId,
                                                                       ProductId.of("ProductId-0"),
                                                                       1));
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();

        // When
        var persistedEventsStream = eventStore.appendToStream(aggregateType,
                                                              orderId,
                                                              initialEvents);
        unitOfWork.commit();

        // Then
        assertThat(persistedEventsStream).isNotNull();
        assertThat((CharSequence) persistedEventsStream.aggregateId()).isEqualTo(orderId);
        assertThat(persistedEventsStream.isPartialEventStream()).isTrue();
        assertThat(persistedEventsStream.eventOrderRangeIncluded()).isEqualTo(LongRange.between(0, 1));
        var eventsPersisted = persistedEventsStream.eventList();
        assertThat(eventsPersisted.size()).isEqualTo(2);
        assertThat((CharSequence) eventsPersisted.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsPersisted.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsPersisted.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsPersisted.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(eventsPersisted.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));

        assertThat((CharSequence) eventsPersisted.get(1).eventId()).isNotNull();
        assertThat((CharSequence) eventsPersisted.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsPersisted.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsPersisted.get(1).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(eventsPersisted.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));

        // Append Initial Event to Stream with overlapping event order
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var appendInitialEvent = List.of(new OrderEvent.ProductAddedToOrder(orderId,
                                                                      ProductId.of("ProductId-1"),
                                                                      2));
        assertThatThrownBy(() -> eventStore.appendToStream(aggregateType,
                                                           orderId,
                                                           EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED, // -1
                                                           appendInitialEvent))
                .isExactlyInstanceOf(OptimisticAppendToStreamException.class)
                .hasMessageContaining("Event-Order range [0]");
        unitOfWork.rollback();

        // Append Initial Event to Stream with overlapping event order
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var appendSecondEvent = List.of(new OrderEvent.ProductAddedToOrder(orderId,
                                                                            ProductId.of("ProductId-1"),
                                                                            2));
        assertThatThrownBy(() -> eventStore.appendToStream(aggregateType,
                                                           orderId,
                                                           EventOrder.FIRST_EVENT_ORDER, // 0
                                                           appendSecondEvent))
                .isExactlyInstanceOf(OptimisticAppendToStreamException.class)
                .hasMessageContaining("Event-Order range [1]");
        unitOfWork.rollback();

        // Append Events to Stream with overlapping event order
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var appendEvents = List.of(new OrderEvent.ProductAddedToOrder(orderId,
                                                                      ProductId.of("ProductId-1"),
                                                                      2),
                                   new OrderEvent.ProductRemovedFromOrder(orderId,
                                                                          ProductId.of("ProductId-1")));
        assertThatThrownBy(() -> eventStore.appendToStream(aggregateType,
                                                           orderId,
                                                           EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED, // -1
                                                           appendEvents))
                .isExactlyInstanceOf(OptimisticAppendToStreamException.class)
                .hasMessageContaining("Event-Order range [0..1]");
        unitOfWork.rollback();

        // And load events again and ensure that no events were persisted
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var loadedEventStream = eventStore.fetchStream(aggregateType,
                                                       orderId).get();
        assertThat(loadedEventStream).isNotNull();
        assertThat((CharSequence) loadedEventStream.aggregateId()).isEqualTo(orderId);
        assertThat(loadedEventStream.isPartialEventStream()).isFalse();
        assertThat(loadedEventStream.eventOrderRangeIncluded()).isEqualTo(LongRange.from(0));
        assertThat((CharSequence) loadedEventStream.aggregateType()).isEqualTo(aggregateType);
        var eventsLoaded = loadedEventStream.events().toList();
        assertThat(eventsLoaded.size()).isEqualTo(2);
        assertThat((CharSequence) eventsLoaded.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(eventsLoaded.get(0).eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(eventsLoaded.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(eventsLoaded.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(0).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(eventsLoaded.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(eventsLoaded.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(initialEvents.get(0));
        assertThat(eventsLoaded.get(0).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-d911-480f-9c48-03ed5bfe1111\", \"orderNumber\": 1234, \"orderingCustomerId\": \"Test-Customer-Id-3\"}"); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) eventsLoaded.get(1).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(1).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(eventsLoaded.get(1).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsLoaded.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(eventsLoaded.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(1).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(eventsLoaded.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(eventsLoaded.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(initialEvents.get(1));
        assertThat(eventsLoaded.get(1).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-d911-480f-9c48-03ed5bfe1111\", \"quantity\": 1, \"productId\": \"ProductId-0\"}"); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsLoaded.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void test_inMemory_Projection() {
        // Given
        var orderId = OrderId.of("beed77fb-1115-1115-9c48-03ed5bfe8f89");
        unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var projection = eventStore.inMemoryProjection(aggregateType,
                                                       orderId,
                                                       List.class,
                                                       new InMemoryListProjector());
        assertThat(projection).isEmpty();

        // When
        var customerId = CustomerId.of("Test-Customer-Id-15");
        var persistableEvents = List.of(new OrderEvent.OrderAdded(orderId,
                                                                  customerId,
                                                                  1234),
                                        new OrderEvent.ProductAddedToOrder(orderId,
                                                                           ProductId.of("ProductId-1"),
                                                                           2),
                                        new OrderEvent.ProductRemovedFromOrder(orderId,
                                                                               ProductId.of("ProductId-1")));
        eventStore.appendToStream(aggregateType,
                                  orderId,
                                  persistableEvents);

        projection = eventStore.inMemoryProjection(aggregateType,
                                                   orderId,
                                                   List.class,
                                                   new InMemoryListProjector());

        var eventsInProjection = (List<PersistedEvent>) projection.get();

        assertThat(eventsInProjection.size()).isEqualTo(3);
        assertThat((CharSequence) eventsInProjection.get(0).eventId()).isNotNull();
        assertThat((CharSequence) eventsInProjection.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsInProjection.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsInProjection.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(eventsInProjection.get(0).eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(eventsInProjection.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(eventsInProjection.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsInProjection.get(0).event().getEventName()).isEmpty();
        assertThat(eventsInProjection.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(eventsInProjection.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(eventsInProjection.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(0));
        assertThat(eventsInProjection.get(0).event().getJson()).isEqualTo(
                "{\"orderId\": \"beed77fb-1115-1115-9c48-03ed5bfe8f89\", \"orderNumber\": 1234, \"orderingCustomerId\": \"Test-Customer-Id-15\"}"); // Note formatting changed by postgresql
        assertThat(eventsInProjection.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\""); // Note formatting changed by postgresql
        assertThat(eventsInProjection.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\""); // Note formatting changed by postgresql
        assertThat(eventsInProjection.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsInProjection.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsInProjection.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsInProjection.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) eventsInProjection.get(1).eventId()).isNotNull();
        assertThat((CharSequence) eventsInProjection.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsInProjection.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsInProjection.get(1).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(eventsInProjection.get(1).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsInProjection.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(eventsInProjection.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsInProjection.get(1).event().getEventName()).isEmpty();
        assertThat(eventsInProjection.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(eventsInProjection.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(eventsInProjection.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(1));
        assertThat(eventsInProjection.get(1).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1115-1115-9c48-03ed5bfe8f89\", \"quantity\": 2, \"productId\": \"ProductId-1\"}");
        assertThat(eventsInProjection.get(1).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(eventsInProjection.get(1).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(eventsInProjection.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsInProjection.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsInProjection.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsInProjection.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) eventsInProjection.get(2).eventId()).isNotNull();
        assertThat((CharSequence) eventsInProjection.get(2).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsInProjection.get(2).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsInProjection.get(2).eventOrder()).isEqualTo(EventOrder.of(2));
        assertThat(eventsInProjection.get(2).eventRevision()).isEqualTo(EventRevision.of(2));
        assertThat(eventsInProjection.get(2).globalEventOrder()).isEqualTo(GlobalEventOrder.of(3));
        assertThat(eventsInProjection.get(2).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsInProjection.get(2).event().getEventName()).isEmpty();
        assertThat(eventsInProjection.get(2).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductRemovedFromOrder.class)));
        assertThat(eventsInProjection.get(2).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductRemovedFromOrder.class).toString());
        assertThat(eventsInProjection.get(2).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(2));
        assertThat(eventsInProjection.get(2).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1115-1115-9c48-03ed5bfe8f89\", \"productId\": \"ProductId-1\"}");
        assertThat(eventsInProjection.get(2).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(eventsInProjection.get(2).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(eventsInProjection.get(2).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsInProjection.get(2).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsInProjection.get(2).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsInProjection.get(2).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void test_loadEventsByGlobalOrder() {
        // Add support for the Product aggregate
        eventStore.addAggregateEventStreamConfiguration(SeparateTablePerAggregateEventStreamConfiguration.standardSingleTenantConfiguration(PRODUCTS,
                                                                                                                                            new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                            AggregateIdSerializer.serializerFor(ProductId.class),
                                                                                                                                            IdentifierColumnType.TEXT,
                                                                                                                                            JSONColumnType.JSON));
        var testEvents = createTestEvents();

        // Persist all test events
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        testEvents.forEach((aggregateType, aggregatesAndEvents) -> {
            aggregatesAndEvents.forEach((aggregateId, events) -> {
                System.out.println(msg("Persisting {} {} events related to aggregate id {}",
                                       events.size(),
                                       aggregateType,
                                       aggregateId));
                var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                     aggregateId,
                                                                     events);
                assertThat(aggregateEventStream.aggregateId()).isEqualTo(aggregateId);
                assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
                assertThat(aggregateEventStream.eventList().size()).isEqualTo(events.size());
            });
        });
        unitOfWork.commit();

        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        // Then we can load all Product events in one go
        var persistedProductEventsStream = eventStore.loadEventsByGlobalOrder(PRODUCTS, LongRange.from(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue()));
        assertThat(persistedProductEventsStream).isNotNull();
        var persistedProductEvents = persistedProductEventsStream.collect(Collectors.toList());
        var totalNumberOfProductEvents = testEvents.get(PRODUCTS)
                                                   .values()
                                                   .stream()
                                                   .map(List::size)
                                                   .reduce(Integer::sum)
                                                   .get();
        System.out.println("Total number of Product Events: " + totalNumberOfProductEvents);
        assertThat(persistedProductEvents.size()).isEqualTo(totalNumberOfProductEvents);
        // Verify we only have Product related events
        assertThat(persistedProductEvents.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(PRODUCTS)).findAny()).isEmpty();
        assertThat(persistedProductEvents.stream()
                                         .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                         .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfProductEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));

        // and all Order events in two batches
        var totalNumberOfOrderEvents = testEvents.get(ORDERS)
                                                 .values()
                                                 .stream()
                                                 .map(List::size)
                                                 .reduce(Integer::sum)
                                                 .get();
        System.out.println("Total number of Order Events: " + totalNumberOfOrderEvents);

        // Check we can split the number of order events in two
        assertThat(totalNumberOfOrderEvents % 2).isEqualTo(0);
        var batchSize      = totalNumberOfOrderEvents / 2;
        var allOrderEvents = new ArrayList<PersistedEvent>();
        // First batch
        var persistedOrdersEventsStream = eventStore.loadEventsByGlobalOrder(ORDERS, LongRange.from(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(), batchSize));
        assertThat(persistedOrdersEventsStream).isNotNull();
        var persistedOrderEvents = persistedOrdersEventsStream.collect(Collectors.toList());
        assertThat(persistedOrderEvents.size()).isEqualTo(batchSize);
        allOrderEvents.addAll(persistedOrderEvents);
        // Second batch
        persistedOrdersEventsStream = eventStore.loadEventsByGlobalOrder(ORDERS, LongRange.from(persistedOrderEvents.get(batchSize - 1).globalEventOrder().longValue() + 1, batchSize));
        assertThat(persistedOrdersEventsStream).isNotNull();
        persistedOrderEvents = persistedOrdersEventsStream.collect(Collectors.toList());
        assertThat(persistedOrderEvents.size()).isEqualTo(batchSize);
        allOrderEvents.addAll(persistedOrderEvents);
        // Third batch is empty
        persistedOrdersEventsStream = eventStore.loadEventsByGlobalOrder(ORDERS, LongRange.from(persistedOrderEvents.get(batchSize - 1).globalEventOrder().longValue() + 1, batchSize));
        assertThat(persistedOrdersEventsStream).isNotNull();
        persistedOrderEvents = persistedOrdersEventsStream.collect(Collectors.toList());
        assertThat(persistedOrderEvents).isEmpty();

        // Verify we only have Order related events
        assertThat(allOrderEvents.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(ORDERS)).findAny()).isEmpty();
        assertThat(allOrderEvents.size()).isEqualTo(totalNumberOfOrderEvents);
        assertThat(allOrderEvents.stream()
                                 .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                 .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfOrderEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));

        // Verify we can load a range plus additional globalEventOrder entries (including one that doesn't exist in the event store)
        var globalEventOrdersFound = eventStore.loadEventsByGlobalOrder(ORDERS, GlobalEventOrder.rangeFrom(GlobalEventOrder.of(10), 10), GlobalEventOrder.of(3, 5, 6, 45, 72, 345))
                                               .map(event -> event.globalEventOrder().toString())
                                               .reduce((s, s2) -> s + "," + s2)
                                               .get();
        assertThat(globalEventOrdersFound).isEqualTo("3,5,6,10,11,12,13,14,15,16,17,18,19,45,72");

        globalEventOrdersFound = eventStore.loadEventsByGlobalOrder(ORDERS, GlobalEventOrder.rangeBetween(GlobalEventOrder.of(10), GlobalEventOrder.of(20)), GlobalEventOrder.of(3, 5, 6, 45, 72, 345))
                                           .map(event -> event.globalEventOrder().toString())
                                           .reduce((s, s2) -> s + "," + s2)
                                           .get();
        assertThat(globalEventOrdersFound).isEqualTo("3,5,6,10,11,12,13,14,15,16,17,18,19,20,45,72");
    }

    @Test
    void test_loadEvents() {
        // Add support for the Product aggregate
        eventStore.addAggregateEventStreamConfiguration(SeparateTablePerAggregateEventStreamConfiguration.standardSingleTenantConfiguration(PRODUCTS,
                                                                                                                                            new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                            AggregateIdSerializer.serializerFor(ProductId.class),
                                                                                                                                            IdentifierColumnType.TEXT,
                                                                                                                                            JSONColumnType.JSON));
        var testEvents = createTestEvents();

        // Persist all test events
        var unitOfWork                      = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        var eventsPersistedPerAggregateType = new HashMap<AggregateType, List<PersistedEvent>>();
        testEvents.forEach((aggregateType, aggregatesAndEvents) -> {
            aggregatesAndEvents.forEach((aggregateId, events) -> {
                System.out.println(msg("Persisting {} {} events related to aggregate id {}",
                                       events.size(),
                                       aggregateType,
                                       aggregateId));
                var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                     aggregateId,
                                                                     events);
                assertThat(aggregateEventStream.aggregateId()).isEqualTo(aggregateId);
                assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
                assertThat(aggregateEventStream.eventList().size()).isEqualTo(events.size());

                // Remember the events persisted
                var eventsPersistedForThisAggregateType = eventsPersistedPerAggregateType.computeIfAbsent(aggregateType, _aggregateType -> new ArrayList<>());
                eventsPersistedForThisAggregateType.addAll(aggregateEventStream.eventList());
            });
        });
        unitOfWork.commit();

        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();

        var allPersistedProductEvents = eventsPersistedPerAggregateType.get(PRODUCTS);
        var allPersistedOrderEvents   = eventsPersistedPerAggregateType.get(ORDERS);

        // Verify we can load all PRODUCT events persisted
        var allLoadedProductEvents = eventStore.loadEvents(PRODUCTS, allPersistedProductEvents.stream().map(PersistedEvent::eventId).toList());
        assertThat(allLoadedProductEvents).hasSize(allPersistedProductEvents.size());
        assertThat(allPersistedProductEvents).containsAll(allLoadedProductEvents);
        // Verify we can load all partial set of PRODUCT events persisted
        var partialListOfPersistedProductIds = allPersistedProductEvents.stream().map(PersistedEvent::eventId).limit(4).toList();
        var partialLoadedProductEvents       = eventStore.loadEvents(PRODUCTS, partialListOfPersistedProductIds);
        assertThat(partialLoadedProductEvents).hasSize(4);
        assertThat(partialLoadedProductEvents.stream().map(PersistedEvent::eventId).toList()).containsAll(partialListOfPersistedProductIds);
        assertThat(allPersistedProductEvents).containsAll(partialLoadedProductEvents);
        // Verify we can NOT load PRODUCT events using persisted ORDER event-ids
        var listExpectedToBeEmpty = eventStore.loadEvents(PRODUCTS, allPersistedOrderEvents.stream().map(PersistedEvent::eventId).limit(4).toList());
        assertThat(listExpectedToBeEmpty).isEmpty();

        // Verify we can load all ORDER events persisted
        var allLoadedOrderEvents = eventStore.loadEvents(ORDERS, allPersistedOrderEvents.stream().map(PersistedEvent::eventId).toList());
        assertThat(allLoadedOrderEvents).hasSize(allPersistedOrderEvents.size());
        assertThat(allPersistedOrderEvents).containsAll(allLoadedOrderEvents);
        // Verify we can load all partial set of ORDER events persisted
        var partialListOfPersistedOrderIds = allPersistedOrderEvents.stream().map(PersistedEvent::eventId).limit(10).toList();
        var partialLoadedOrderEvents       = eventStore.loadEvents(ORDERS, partialListOfPersistedOrderIds);
        assertThat(partialLoadedOrderEvents).hasSize(10);
        assertThat(partialLoadedOrderEvents.stream().map(PersistedEvent::eventId).toList()).containsAll(partialListOfPersistedOrderIds);
        assertThat(allPersistedOrderEvents).containsAll(partialLoadedOrderEvents);
        // Verify we can NOT load ORDER events using persisted PRODUCT event-ids
        listExpectedToBeEmpty = eventStore.loadEvents(ORDERS, allPersistedProductEvents.stream().map(PersistedEvent::eventId).limit(4).toList());
        assertThat(listExpectedToBeEmpty).isEmpty();
    }

    private void testSimpleEventPolling(Function<SubscriberId, Flux<PersistedEvent>> productsFluxSupplier,
                                        Function<SubscriberId, Flux<PersistedEvent>> ordersFluxSupplier) {
        requireNonNull(productsFluxSupplier);
        requireNonNull(ordersFluxSupplier);
        // Add support for the Product aggregate
        eventStore.addAggregateEventStreamConfiguration(SeparateTablePerAggregateEventStreamConfiguration.standardSingleTenantConfiguration(PRODUCTS,
                                                                                                                                            new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                            AggregateIdSerializer.serializerFor(ProductId.class),
                                                                                                                                            IdentifierColumnType.TEXT,
                                                                                                                                            JSONColumnType.JSON));
        var testEvents = createTestEvents();

        var productEventsReceived = new ArrayList<PersistedEvent>();
        var productsSubscriberId  = SubscriberId.of("ProductsSub1");
        var productEventsFlux = productsFluxSupplier.apply(productsSubscriberId)
                                                    .subscribe(e -> {
                                                        System.out.println("Received Product event: " + e);
                                                        productEventsReceived.add(e);
                                                    });
        var orderEventsReceived = new ArrayList<PersistedEvent>();
        var ordersSubscriberId  = SubscriberId.of("OrdersSub1");
        var orderEventsFlux = ordersFluxSupplier.apply(ordersSubscriberId)
                                                .subscribe(e -> {
                                                    System.out.println("Received Order event: " + e);
                                                    orderEventsReceived.add(e);
                                                });

        // Persist all test events
        testEvents.forEach((aggregateType, aggregatesAndEvents) -> {
            aggregatesAndEvents.forEach((aggregateId, events) -> {
                var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
                System.out.println(msg("Persisting {} {} events related to aggregate id {}",
                                       events.size(),
                                       aggregateType,
                                       aggregateId));
                var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                     aggregateId,
                                                                     events);
                assertThat(aggregateEventStream.aggregateId()).isEqualTo(aggregateId);
                assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
                assertThat(aggregateEventStream.eventList().size()).isEqualTo(events.size());
                unitOfWork.commit();
            });
        });

        // Verify we received all product events
        var totalNumberOfProductEvents = testEvents.get(PRODUCTS)
                                                   .values()
                                                   .stream()
                                                   .map(List::size)
                                                   .reduce(Integer::sum)
                                                   .get();
        System.out.println("Total number of Product Events: " + totalNumberOfProductEvents);
        Awaitility.waitAtMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> assertThat(productEventsReceived.size()).isEqualTo(totalNumberOfProductEvents));
        // Verify we only have Product related events
        assertThat(productEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(PRODUCTS)).findAny()).isEmpty();
        assertThat(productEventsReceived.stream()
                                        .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                        .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfProductEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));

        // Verify we received all Order events
        var totalNumberOfOrderEvents = testEvents.get(ORDERS)
                                                 .values()
                                                 .stream()
                                                 .map(List::size)
                                                 .reduce(Integer::sum)
                                                 .get();
        System.out.println("Total number of Order Events: " + totalNumberOfOrderEvents);
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(orderEventsReceived.size()).isEqualTo(totalNumberOfOrderEvents));
        assertThat(orderEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(ORDERS)).findAny()).isEmpty();
        assertThat(orderEventsReceived.stream()
                                      .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                      .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfOrderEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));

        assertThat(eventStore.getEventStreamGapHandler()
                             .getPermanentGapsFor(ORDERS)).isEmpty();
        assertThat(eventStore.getEventStreamGapHandler()
                             .getPermanentGapsFor(PRODUCTS)).isEmpty();
        assertThat(eventStore.getEventStreamGapHandler()
                             .gapHandlerFor(productsSubscriberId)
                             .getTransientGapsFor(PRODUCTS)).isEmpty();
        assertThat(eventStore.getEventStreamGapHandler()
                             .gapHandlerFor(ordersSubscriberId)
                             .getTransientGapsFor(ORDERS)).isEmpty();

        productEventsFlux.dispose();
        orderEventsFlux.dispose();
    }

    @Test
    void test_pollEvents_with_rate_limit() {
        testSimpleEventPolling(productsSubscriberId -> eventStore.pollEvents(PRODUCTS,
                                                                             GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                                             Optional.of(2),
                                                                             Optional.of(Duration.ofMillis(100)),
                                                                             Optional.empty(),
                                                                             Optional.of(productsSubscriberId))
                                                                 .limitRate(10),
                               ordersSubscriberId -> eventStore.pollEvents(ORDERS,
                                                                           GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                                           Optional.of(10),
                                                                           Optional.of(Duration.ofMillis(100)),
                                                                           Optional.empty(),
                                                                           Optional.of(ordersSubscriberId))
                                                               .limitRate(10));
    }

    @Test
    void test_pollEvents_with_unbounded_rate() {
        testSimpleEventPolling(productsSubscriberId -> eventStore.pollEvents(PRODUCTS,
                                                                             GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                                             Optional.of(2),
                                                                             Optional.of(Duration.ofMillis(100)),
                                                                             Optional.empty(),
                                                                             Optional.of(productsSubscriberId)),
                               ordersSubscriberId -> eventStore.pollEvents(ORDERS,
                                                                           GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                                           Optional.of(10),
                                                                           Optional.of(Duration.ofMillis(100)),
                                                                           Optional.empty(),
                                                                           Optional.of(ordersSubscriberId)));
    }

    @Test
    void test_unboundedPollForEvents() {
        testSimpleEventPolling(productsSubscriberId -> eventStore.unboundedPollForEvents(PRODUCTS,
                                                                                         GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                                                         Optional.of(2),
                                                                                         Optional.of(Duration.ofMillis(100)),
                                                                                         Optional.empty(),
                                                                                         Optional.of(productsSubscriberId)),
                               ordersSubscriberId -> eventStore.unboundedPollForEvents(ORDERS,
                                                                                       GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                                                       Optional.of(10),
                                                                                       Optional.of(Duration.ofMillis(100)),
                                                                                       Optional.empty(),
                                                                                       Optional.of(ordersSubscriberId)));
    }

    @Test
    void test_pollEvents_with_pauses() throws InterruptedException {
        // Add support for the Product aggregate
        eventStore.addAggregateEventStreamConfiguration(SeparateTablePerAggregateEventStreamConfiguration.standardSingleTenantConfiguration(PRODUCTS,
                                                                                                                                            new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                            AggregateIdSerializer.serializerFor(ProductId.class),
                                                                                                                                            IdentifierColumnType.TEXT,
                                                                                                                                            JSONColumnType.JSON));

        var productEventsReceived = new ArrayList<PersistedEvent>();
        var productsSubscriberId  = SubscriberId.of("ProductsSub1");
        var productEventsFlux = eventStore.pollEvents(PRODUCTS,
                                                      GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                      Optional.of(2),
                                                      Optional.of(Duration.ofMillis(50)),
                                                      Optional.empty(),
                                                      Optional.of(productsSubscriberId))
                                          .subscribe(e -> {
                                              System.out.println("Received Product event: " + e);
                                              productEventsReceived.add(e);
                                          });
        var orderEventsReceived = new ArrayList<PersistedEvent>();
        var ordersSubscriberId  = SubscriberId.of("OrdersSub1");
        var orderEventsFlux = eventStore.pollEvents(ORDERS,
                                                    GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                    Optional.of(10),
                                                    Optional.of(Duration.ofMillis(50)),
                                                    Optional.empty(),
                                                    Optional.of(ordersSubscriberId))
                                        .subscribe(e -> {
                                            System.out.println("Received Order event: " + e);
                                            orderEventsReceived.add(e);
                                        });
        System.out.println("----> Pausing");

        Thread.sleep(5000);
        System.out.println("------------------ PERSISTING EVENTS --------------------------");

        // Persist all test events
        System.out.println("----> Starting persisting all events");
        var index      = new AtomicInteger();
        var testEvents = createTestEvents();
        testEvents.forEach((aggregateType, aggregatesAndEvents) -> {
            aggregatesAndEvents.forEach((aggregateId, events) -> {
                var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
                System.out.println(msg("Persisting {} {} events related to aggregate id {}",
                                       events.size(),
                                       aggregateType,
                                       aggregateId));
                var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                     aggregateId,
                                                                     events);
                assertThat(aggregateEventStream.aggregateId()).isEqualTo(aggregateId);
                assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
                assertThat(aggregateEventStream.eventList().size()).isEqualTo(events.size());
                unitOfWork.commit();
                if (testEvents.size() / 2 == index.incrementAndGet()) {
                    try {
                        System.out.println("----> Pause during persist events");
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
            });
        });

        System.out.println("----> Done persisting all events");
        // Verify we received all product events
        var totalNumberOfProductEvents = testEvents.get(PRODUCTS)
                                                   .values()
                                                   .stream()
                                                   .map(List::size)
                                                   .reduce(Integer::sum)
                                                   .get();
        System.out.println("Total number of Product Events: " + totalNumberOfProductEvents);
        Awaitility.waitAtMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> assertThat(productEventsReceived.size()).isEqualTo(totalNumberOfProductEvents));
        // Verify we only have Product related events
        assertThat(productEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(PRODUCTS)).findAny()).isEmpty();
        assertThat(productEventsReceived.stream()
                                        .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                        .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfProductEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));

        // Verify we received all Order events
        var totalNumberOfOrderEvents = testEvents.get(ORDERS)
                                                 .values()
                                                 .stream()
                                                 .map(List::size)
                                                 .reduce(Integer::sum)
                                                 .get();
        System.out.println("Total number of Order Events: " + totalNumberOfOrderEvents);
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(orderEventsReceived.size()).isEqualTo(totalNumberOfOrderEvents));
        assertThat(orderEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(ORDERS)).findAny()).isEmpty();
        assertThat(orderEventsReceived.stream()
                                      .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                      .collect(Collectors.toList()))
                .isEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                  totalNumberOfOrderEvents)
                                     .boxed()
                                     .collect(Collectors.toList()));

        assertThat(eventStore.getEventStreamGapHandler()
                             .getPermanentGapsFor(ORDERS)).isEmpty();
        assertThat(eventStore.getEventStreamGapHandler()
                             .getPermanentGapsFor(PRODUCTS)).isEmpty();
        assertThat(eventStore.getEventStreamGapHandler()
                             .gapHandlerFor(productsSubscriberId)
                             .getTransientGapsFor(PRODUCTS)).isEmpty();
        assertThat(eventStore.getEventStreamGapHandler()
                             .gapHandlerFor(ordersSubscriberId)
                             .getTransientGapsFor(ORDERS)).isEmpty();

        System.out.println("----> Pause after comparing published events");
        Thread.sleep(5000);


        productEventsFlux.dispose();
        orderEventsFlux.dispose();
    }

    @Test
    void test_pollEvents_with_large_gaps() throws InterruptedException {
        var orderEventsReceived = new ArrayList<PersistedEvent>();
        var ordersSubscriberId  = SubscriberId.of("OrdersSub1");
        var batchSize           = 10;
        var orderEventsFlux = eventStore.pollEvents(ORDERS,
                                                    GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                    Optional.of(batchSize),
                                                    Optional.of(Duration.ofMillis(50)),
                                                    Optional.empty(),
                                                    Optional.of(ordersSubscriberId))
                                        .subscribe(e -> {
                                            System.out.println("Received Order event: " + e);
                                            orderEventsReceived.add(e);
                                        });
        System.out.println("------------------ PERSISTING ALL EVENTS --------------------------");

        // Persist all test events
        var index                = new AtomicInteger();
        var expectedNumberOfGaps = new AtomicInteger();
        var aggregatesAndEvents  = createTestEvents().get(ORDERS);
        aggregatesAndEvents.forEach((aggregateId, events) -> {
            var currentIndex                                = index.get();
            var rollbackAddingEventsToForceAnEventStreamGap = (currentIndex % batchSize == 0);
            if (rollbackAddingEventsToForceAnEventStreamGap) {
                System.out.println("---> failAddingEventsToForceAnEventStreamGap for aggregate '" + aggregateId + "': " + rollbackAddingEventsToForceAnEventStreamGap);
            }

            var attempt = 0;
            do {
                attempt++;
                var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
                System.out.println(msg("---> Persisting {} {} events related to aggregate id '{}'",
                                       events.size(),
                                       aggregateType,
                                       aggregateId));
                var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                     aggregateId,
                                                                     events);
                assertThat(aggregateEventStream.aggregateId()).isEqualTo(aggregateId);
                assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
                assertThat(aggregateEventStream.eventList().size()).isEqualTo(events.size());
                if (rollbackAddingEventsToForceAnEventStreamGap && attempt < 10) {
                    System.out.println("---> Rolling back " + events.size() + " events in order to create event stream gap related to aggregate '" + aggregateId + "'");
                    expectedNumberOfGaps.addAndGet(events.size());
                    unitOfWork.rollback();
                } else {
                    System.out.println("---> Committing related to aggregate '" + aggregateId + "'");
                    if (expectedNumberOfGaps.get() > 0) {
                        System.out.println("Permanent gaps " + expectedNumberOfGaps.get());
                    }
                    unitOfWork.commit();
                }
            } while (rollbackAddingEventsToForceAnEventStreamGap && attempt < 10);
            index.incrementAndGet();
        });

        System.out.println("----> Done persisting all events");
        var totalNumberOfOrderEvents = aggregatesAndEvents.values()
                                                          .stream()
                                                          .map(List::size)
                                                          .reduce(Integer::sum)
                                                          .get();
        System.out.println("# Total number of Order Events: " + totalNumberOfOrderEvents);
        System.out.println("# Total number of expected Order Events gaps: " + expectedNumberOfGaps.get());
        Awaitility.waitAtMost(Duration.ofSeconds(30))
                  .untilAsserted(() -> assertThat(orderEventsReceived.size()).isEqualTo(totalNumberOfOrderEvents));

        var persistedEventsGlobalOrders = orderEventsReceived.stream()
                                                             .map(persistedEvent -> persistedEvent.globalEventOrder().longValue()).collect(Collectors.toList());
        System.out.println("# Persisted Order Events GlobalOrders: " + persistedEventsGlobalOrders);
        assertThat(persistedEventsGlobalOrders).doesNotHaveDuplicates();
        assertThat(persistedEventsGlobalOrders)
                .isNotEqualTo(LongStream.rangeClosed(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(),
                                                     totalNumberOfOrderEvents)
                                        .boxed()
                                        .collect(Collectors.toList()));


        var transientGapsFor = eventStore.getEventStreamGapHandler()
                                         .gapHandlerFor(ordersSubscriberId)
                                         .getTransientGapsFor(ORDERS);
        System.out.println("# GlobalOrders Gaps for Order Events      : " + transientGapsFor);
        assertThat(transientGapsFor).isNotEmpty();
        assertThat(transientGapsFor.size()).isEqualTo(expectedNumberOfGaps.get());
        var transientGapsGlobalOrders = transientGapsFor.stream().map(NumberType::longValue).collect(Collectors.toList());
        assertThat(transientGapsGlobalOrders).doesNotContainAnyElementsOf(persistedEventsGlobalOrders);

        // Gaps are not permanent yet
        assertThat(eventStore.getEventStreamGapHandler().getPermanentGapsFor(ORDERS)).isEmpty();

        orderEventsFlux.dispose();
    }

    private Map<AggregateType, Map<?, List<?>>> createTestEvents() {
        var eventsPerAggregateType = new HashMap<AggregateType, Map<?, List<?>>>();

        // Products
        var aggregateType       = PRODUCTS;
        var aggregatesAndEvents = new HashMap<Object, List<?>>();
        for (int i = 0; i < 10; i++) {
            var productId = ProductId.random();
            if (i % 2 == 0) {
                aggregatesAndEvents.put(productId, List.of(new ProductEvent.ProductAdded(productId),
                                                           new ProductEvent.ProductDiscontinued(productId)));
            } else {
                aggregatesAndEvents.put(productId, List.of(new ProductEvent.ProductAdded(productId)));
            }
        }
        eventsPerAggregateType.put(aggregateType, aggregatesAndEvents);

        // Orders
        aggregateType = ORDERS;
        aggregatesAndEvents = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            var orderId = OrderId.random();
            if (i % 2 == 0) {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i),
                                                         new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), i)));
            } else if (i % 3 == 0) {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i),
                                                         new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), i),
                                                         new OrderEvent.ProductOrderQuantityAdjusted(orderId, ProductId.random(), i - 1)));
            } else if (i % 5 == 0) {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i),
                                                         new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), i),
                                                         new OrderEvent.ProductOrderQuantityAdjusted(orderId, ProductId.random(), i - 1),
                                                         new OrderEvent.ProductRemovedFromOrder(orderId, ProductId.random()),
                                                         new OrderEvent.OrderAccepted(orderId)));
            } else {
                aggregatesAndEvents.put(orderId, List.of(new OrderEvent.OrderAdded(orderId, CustomerId.random(), i)));
            }
        }
        eventsPerAggregateType.put(aggregateType, aggregatesAndEvents);
        return eventsPerAggregateType;
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
                                         null, // Leave reading the EventRevision to the PersistableEvent's from method
                                         META_DATA,
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         null);
        }
    }

    /**
     * Simple test in memory projector that just returns the underlying list of {@link PersistedEvent}'s
     */
    private class InMemoryListProjector implements InMemoryProjector {
        @Override
        public boolean supports(Class<?> projectionType) {
            return List.class.isAssignableFrom(projectionType);
        }

        @Override
        public <ID, PROJECTION> Optional<PROJECTION> projectEvents(AggregateType aggregateType,
                                                                   ID aggregateId,
                                                                   Class<PROJECTION> projectionType,
                                                                   EventStore eventStore) {
            var eventStream = eventStore.fetchStream(aggregateType,
                                                     aggregateId);
            return (Optional<PROJECTION>) eventStream.map(actualEventStream -> actualEventStream.eventList());
        }
    }

    private static class RecordingLocalEventBusConsumer implements EventHandler {
        private final List<PersistedEvent> beforeCommitPersistedEvents  = new ArrayList<>();
        private final List<PersistedEvent> afterCommitPersistedEvents   = new ArrayList<>();
        private final List<PersistedEvent> afterRollbackPersistedEvents = new ArrayList<>();
        private final List<PersistedEvent> flushPersistedEvents         = new ArrayList<>();

        @Override
        public void handle(Object event) {
            var persistedEvents = (PersistedEvents) event;
            if (persistedEvents.commitStage == CommitStage.Flush) {
                flushPersistedEvents.addAll(persistedEvents.events);
            } else if (persistedEvents.commitStage == CommitStage.BeforeCommit) {
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
            flushPersistedEvents.clear();
        }
    }
}