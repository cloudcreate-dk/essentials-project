/*
 * Copyright 2021-2022 the original author or authors.
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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreManagedUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.reactive.EventHandler;
import dk.cloudcreate.essentials.types.LongRange;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.*;
import java.util.*;
import java.util.stream.*;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration.standardConfigurationUsingJackson;
import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardConfigurationUsingJackson;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MultiTenantPostgresqlEventStoreIT {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
    public static final AggregateType PRODUCTS  = AggregateType.of("Products");
    public static final AggregateType ORDERS    = AggregateType.of("Orders");


    private Jdbi                                                                    jdbi;
    private AggregateType                                                           aggregateType;
    private EventStoreManagedUnitOfWorkFactory                                      unitOfWorkFactory;
    private TestPersistableEventMapper                                              eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");

    private TenantId                       tenantId;
    private RecordingLocalEventBusConsumer recordingLocalEventBusConsumer;


    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new EventStoreSqlLogger());

        aggregateType = ORDERS;
        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        eventMapper = new TestPersistableEventMapper();
        var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                       unitOfWorkFactory,
                                                                                       eventMapper,
                                                                                       standardConfigurationUsingJackson(aggregateType_ -> aggregateType_ + "_events",
                                                                                                                         EventStreamTableColumnNames.defaultColumnNames(),
                                                                                                                         createObjectMapper(),
                                                                                                                         IdentifierColumnType.UUID,
                                                                                                                         JSONColumnType.JSONB,
                                                                                                                         new TenantSerializer.TenantIdSerializer()));
        persistenceStrategy.addAggregateEventStreamConfiguration(aggregateType,
                                                                 OrderId.class);

        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                persistenceStrategy);
        recordingLocalEventBusConsumer = new RecordingLocalEventBusConsumer();
        eventStore.localEventBus().addSyncSubscriber(recordingLocalEventBusConsumer);
        tenantId = TenantId.of(UUID.randomUUID().toString());
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        recordingLocalEventBusConsumer.reset();
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
    }

    @Test
    void append_stream_followed_by_fetchStream_with_open_EventOrder_range() {
        // Given
        var orderId    = OrderId.of("beed77fb-1115-1115-9c48-03ed5bfe8f89");
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        assertThat(eventStore.loadLastPersistedEventRelatedTo(aggregateType, orderId)).isEmpty();

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
        var persistedEventStream = eventStore.appendToStream(aggregateType,
                                                             orderId,
                                                             persistableEvents);
        assertThat(persistedEventStream.eventOrderRangeIncluded()).isEqualTo(LongRange.between(0, 2));
        assertThat(persistedEventStream.isPartialEventStream()).isTrue();
        assertThat((CharSequence) persistedEventStream.aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) persistedEventStream.aggregateType()).isEqualTo(aggregateType);
        var persistedEvents = persistedEventStream.eventList();

        assertAppendedEvents(orderId, persistableEvents, persistedEvents);
        unitOfWork.commit();
        assertAppendedEvents(orderId, persistableEvents, recordingLocalEventBusConsumer.beforeCommitPersistedEvents);
        assertAppendedEvents(orderId, persistableEvents, recordingLocalEventBusConsumer.afterCommitPersistedEvents);
        assertThat(recordingLocalEventBusConsumer.afterRollbackPersistedEvents).isEmpty();

        // Then
        unitOfWorkFactory.getOrCreateNewUnitOfWork();
        assertFetchedEvents(orderId, persistableEvents, eventStore.fetchStream(aggregateType, orderId, LongRange.from(1)));
        assertFetchedEvents(orderId, persistableEvents, eventStore.fetchStream(aggregateType, orderId, LongRange.from(1), tenantId));
        var allEvents = eventStore.fetchStream(aggregateType, orderId, tenantId);
        assertThat(allEvents).isPresent();
        assertThat(allEvents.get().eventList().size()).isEqualTo(3);
        assertThat(eventStore.fetchStream(aggregateType, orderId, TenantId.of("AnotherTenant"))).isEmpty();
    }

    private void assertFetchedEvents(OrderId orderId, List<OrderEvent> persistableEvents, Optional<AggregateEventStream<OrderId>> possibleEventStream) {
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
        assertThat(eventsLoaded.get(0).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(eventsLoaded.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(eventsLoaded.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(0).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(eventsLoaded.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(eventsLoaded.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(1));
        assertThat(eventsLoaded.get(0).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1115-1115-9c48-03ed5bfe8f89\", \"quantity\": 2, \"productId\": \"ProductId-1\"}");
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(eventsLoaded.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(eventsLoaded.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
        assertThat(eventsLoaded.get(0).tenant()).isEqualTo(Optional.of(tenantId));

        assertThat((CharSequence) eventsLoaded.get(1).eventId()).isNotNull();
        assertThat((CharSequence) eventsLoaded.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) eventsLoaded.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(eventsLoaded.get(1).eventOrder()).isEqualTo(EventOrder.of(2));
        assertThat(eventsLoaded.get(1).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(eventsLoaded.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(3));
        assertThat(eventsLoaded.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(eventsLoaded.get(1).event().getEventName()).isEmpty();
        assertThat(eventsLoaded.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductRemovedFromOrder.class)));
        assertThat(eventsLoaded.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductRemovedFromOrder.class).toString());
        assertThat(eventsLoaded.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(2));
        assertThat(eventsLoaded.get(1).event().getJson()).isEqualTo("{\"orderId\": \"beed77fb-1115-1115-9c48-03ed5bfe8f89\", \"productId\": \"ProductId-1\"}");
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(eventsLoaded.get(1).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(eventsLoaded.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(eventsLoaded.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(eventsLoaded.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(eventsLoaded.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
        assertThat(eventsLoaded.get(1).tenant()).isEqualTo(Optional.of(tenantId));
    }

    private void assertAppendedEvents(OrderId orderId, List<OrderEvent> persistableEvents, List<PersistedEvent> persistedEvents) {
        assertThat(persistedEvents.size()).isEqualTo(3);
        assertThat((CharSequence) persistedEvents.get(0).eventId()).isNotNull();
        assertThat((CharSequence) persistedEvents.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) persistedEvents.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(persistedEvents.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(persistedEvents.get(0).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(persistedEvents.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(persistedEvents.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(persistedEvents.get(0).event().getEventName()).isEmpty();
        assertThat(persistedEvents.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(persistedEvents.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(persistedEvents.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(0));
        assertThat(persistedEvents.get(0).event().getJson()).isEqualTo("{\"orderId\":\"beed77fb-1115-1115-9c48-03ed5bfe8f89\",\"orderingCustomerId\":\"Test-Customer-Id-15\",\"orderNumber\":1234}");
        assertThat(persistedEvents.get(0).metaData().getJson()).contains("\"Key1\":\"Value1\"");
        assertThat(persistedEvents.get(0).metaData().getJson()).contains("\"Key2\":\"Value2\"");
        assertThat(persistedEvents.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(persistedEvents.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(persistedEvents.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(persistedEvents.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
        assertThat(persistedEvents.get(0).tenant()).isEqualTo(Optional.of(tenantId));

        assertThat((CharSequence) persistedEvents.get(1).eventId()).isNotNull();
        assertThat((CharSequence) persistedEvents.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) persistedEvents.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(persistedEvents.get(1).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(persistedEvents.get(1).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(persistedEvents.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(persistedEvents.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(persistedEvents.get(1).event().getEventName()).isEmpty();
        assertThat(persistedEvents.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductAddedToOrder.class)));
        assertThat(persistedEvents.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductAddedToOrder.class).toString());
        assertThat(persistedEvents.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(1));
        assertThat(persistedEvents.get(1).event().getJson()).isEqualTo("{\"orderId\":\"beed77fb-1115-1115-9c48-03ed5bfe8f89\",\"productId\":\"ProductId-1\",\"quantity\":2}");
        assertThat(persistedEvents.get(1).metaData().getJson()).contains("\"Key1\":\"Value1\"");
        assertThat(persistedEvents.get(1).metaData().getJson()).contains("\"Key2\":\"Value2\"");
        assertThat(persistedEvents.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(persistedEvents.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(persistedEvents.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(persistedEvents.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
        assertThat(persistedEvents.get(1).tenant()).isEqualTo(Optional.of(tenantId));

        assertThat((CharSequence) persistedEvents.get(2).eventId()).isNotNull();
        assertThat((CharSequence) persistedEvents.get(2).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) persistedEvents.get(2).aggregateType()).isEqualTo(aggregateType);
        assertThat(persistedEvents.get(2).eventOrder()).isEqualTo(EventOrder.of(2));
        assertThat(persistedEvents.get(2).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(persistedEvents.get(2).globalEventOrder()).isEqualTo(GlobalEventOrder.of(3));
        assertThat(persistedEvents.get(2).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(persistedEvents.get(2).event().getEventName()).isEmpty();
        assertThat(persistedEvents.get(2).event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.ProductRemovedFromOrder.class)));
        assertThat(persistedEvents.get(2).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.ProductRemovedFromOrder.class).toString());
        assertThat(persistedEvents.get(2).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEvents.get(2));
        assertThat(persistedEvents.get(2).event().getJson()).isEqualTo("{\"orderId\":\"beed77fb-1115-1115-9c48-03ed5bfe8f89\",\"productId\":\"ProductId-1\"}");
        assertThat(persistedEvents.get(2).metaData().getJson()).contains("\"Key1\":\"Value1\"");
        assertThat(persistedEvents.get(2).metaData().getJson()).contains("\"Key2\":\"Value2\"");
        assertThat(persistedEvents.get(2).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(persistedEvents.get(2).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(persistedEvents.get(2).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(persistedEvents.get(2).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
        assertThat(persistedEvents.get(2).tenant()).isEqualTo(Optional.of(tenantId));
    }

    @Test
    void test_loadEventsByGlobalOrder() {
        // Add support for the Product aggregate
        eventStore.addAggregateEventStreamConfiguration(standardConfigurationUsingJackson(PRODUCTS,
                                                                                          createObjectMapper(),
                                                                                          AggregateIdSerializer.serializerFor(ProductId.class),
                                                                                          IdentifierColumnType.TEXT,
                                                                                          JSONColumnType.JSON,
                                                                                          new TenantSerializer.TenantIdSerializer()));

        var testEvents = createTestEvents();

        // Persist all test events
        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        testEvents.forEach((aggregateType, aggregatesAndEvents) -> {
            tenantId = TenantId.of(aggregateType + "-Tenant");
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

        unitOfWorkFactory.getOrCreateNewUnitOfWork();
        tenantId = TenantId.of(PRODUCTS + "-Tenant");
        // Then we can load all Product events in one go, but it will return empty because we persisted using a different tenant
        var persistedProductEventsStream = eventStore.loadEventsByGlobalOrder(PRODUCTS, LongRange.from(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue()), null, Optional.of(tenantId));
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
        // Switching to another tenant returns an empty result
        persistedProductEventsStream = eventStore.loadEventsByGlobalOrder(PRODUCTS, LongRange.from(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue()), null, Optional.of(TenantId.of("SomeTenant")));
        assertThat(persistedProductEventsStream).isNotNull();
        persistedProductEvents = persistedProductEventsStream.collect(Collectors.toList());
        assertThat(persistedProductEvents).isEmpty();

        // and all Order events in two batches
        tenantId = TenantId.of(ORDERS + "-Tenant");
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

        // Switch to a different tenant and ensure that no events are returned
        persistedOrdersEventsStream = eventStore.loadEventsByGlobalOrder(ORDERS,
                                                                         LongRange.from(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER.longValue(), totalNumberOfOrderEvents),
                                                                         null,
                                                                         Optional.of(TenantId.of("SomeTenant")));
        assertThat(persistedOrdersEventsStream).isNotNull();
        persistedOrderEvents = persistedOrdersEventsStream.collect(Collectors.toList());
        assertThat(persistedOrderEvents).isEmpty();
    }

    @Test
    void test_pollEvents() {
        // Add support for the Product aggregate
        eventStore.addAggregateEventStreamConfiguration(standardConfigurationUsingJackson(PRODUCTS,
                                                                                          createObjectMapper(),
                                                                                          AggregateIdSerializer.serializerFor(ProductId.class),
                                                                                          IdentifierColumnType.TEXT,
                                                                                          JSONColumnType.JSON,
                                                                                          new TenantSerializer.TenantIdSerializer()));
        var testEvents = createTestEvents();

        var productEventsReceived = new ArrayList<PersistedEvent>();
        var productsSubscriberId  = SubscriberId.of("ProductsSub1");
        var productEventsFlux = eventStore.pollEvents(PRODUCTS,
                                                      GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                      Optional.of(2),
                                                      Optional.of(Duration.ofMillis(100)),
                                                      Optional.of(TenantId.of("SomeTenant")),
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
                                                    Optional.of(Duration.ofMillis(100)),
                                                    Optional.of(TenantId.of(ORDERS + "-Tenant")),
                                                    Optional.of(ordersSubscriberId))
                                        .subscribe(e -> {
                                            System.out.println("Received Order event: " + e);
                                            orderEventsReceived.add(e);
                                        });

        // Persist all test events
        testEvents.forEach((aggregateType, aggregatesAndEvents) -> {
            tenantId = TenantId.of(aggregateType + "-Tenant");
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

        // Verify we didn't receive any product events
        assertThat(productEventsReceived).isEmpty();

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

    private class TestPersistableEventMapper implements PersistableEventMapper {
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
                                         tenantId);
        }
    }

    /**
     * Simple test in memory projector that just returns the underlying list of {@link PersistedEvent}'s
     */
    private static class InMemoryListProjector implements InMemoryProjector {
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

    private static class RecordingLocalEventBusConsumer implements EventHandler<PersistedEvents> {
        private final List<PersistedEvent> beforeCommitPersistedEvents  = new ArrayList<>();
        private final List<PersistedEvent> afterCommitPersistedEvents   = new ArrayList<>();
        private final List<PersistedEvent> afterRollbackPersistedEvents = new ArrayList<>();

        @Override
        public void handle(PersistedEvents persistedEvents) {
            if (persistedEvents.commitStage == CommitStage.BeforeCommit) {
                beforeCommitPersistedEvents.addAll(persistedEvents.events);
            } else if (persistedEvents.commitStage == CommitStage.AfterCommit) {
                afterCommitPersistedEvents.addAll(persistedEvents.events);
            } else {
                afterRollbackPersistedEvents.addAll(persistedEvents.events);
            }
        }

        private void reset() {
            beforeCommitPersistedEvents.clear();
            afterCommitPersistedEvents.clear();
            afterRollbackPersistedEvents.clear();
        }
    }
}