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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.gap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.gap.PostgresqlEventStreamGapHandler.ResolveTransientGapsToPermanentGapsPromotionStrategy;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.observability.EventStoreSubscriptionObserver;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.trustworks.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.components.foundation.types.*;
import dk.trustworks.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.trustworks.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.trustworks.essentials.shared.functional.tuple.Pair;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.slf4j.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresqlEventStreamGapHandlerIT {
    private static final Logger                 log               = LoggerFactory.getLogger(PostgresqlEventStreamGapHandlerIT.class);
    public static final  AggregateType          ORDERS            = AggregateType.of("Orders");
    private static final List<GlobalEventOrder> NO_TRANSIENT_GAPS = List.of();

    private Jdbi                                                                    jdbi;
    private AggregateType                                                           aggregateType;
    private EventStoreUnitOfWorkFactory<EventStoreUnitOfWork>                       unitOfWorkFactory;
    private TestPersistableEventMapper                                              eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");


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
                                                                                       SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration(new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                                                                      IdentifierColumnType.UUID,
                                                                                                                                                                                      JSONColumnType.JSONB));
        persistenceStrategy.addAggregateEventStreamConfiguration(aggregateType,
                                                                 OrderId.class);
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                persistenceStrategy,
                                                Optional.empty(),
                                                eventStore -> new PostgresqlEventStreamGapHandler<>(eventStore,
                                                                                                    unitOfWorkFactory,
                                                                                                    Duration.ofMillis(1000),
                                                                                                    (forAggregateType, globalOrderQueryRange, allTransientGaps) -> {
                                                                                                        var numberOfGaps          = allTransientGaps.size();
                                                                                                        var numberOfGapsToInclude = Math.min(numberOfGaps, 2);
                                                                                                        return numberOfGapsToInclude > 0 ? allTransientGaps.subList(0, numberOfGapsToInclude)
                                                                                                                                                           .stream()
                                                                                                                                                           .map(Pair::_1)
                                                                                                                                                           .collect(Collectors.toList()) : NO_TRANSIENT_GAPS;
                                                                                                    },
                                                                                                    ResolveTransientGapsToPermanentGapsPromotionStrategy.thresholdBased(1)),
                                                new EventStoreSubscriptionObserver.NoOpEventStoreSubscriptionObserver());
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
    }

    @Test
    void test_transient_and_permanent_gap_handling() {
        var orderEventsReceived = new ArrayList<PersistedEvent>();
        var ordersSubscriberId  = SubscriberId.of("OrdersSub1");
        var orderEventsFlux = eventStore.pollEvents(ORDERS,
                                                    GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                    Optional.of(10),
                                                    Optional.of(Duration.ofMillis(100)),
                                                    Optional.empty(),
                                                    Optional.of(ordersSubscriberId))
                                        .subscribe(e -> {
                                            System.out.println("Received Order event: " + e);
                                            orderEventsReceived.add(e);
                                        });

        var testData   = createTestEvents();
        var orderId    = testData._1;
        var testEvents = testData._2;
        assertThat(testEvents.size()).isEqualTo(7);

        var firstEventPersisted        = new CountDownLatch(1);
        var eventsTwoToFourPersisted   = new CountDownLatch(1);
        var eventsSixAndSevenPersisted = new CountDownLatch(1);
        var eventsFiveRolledBack       = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(3);
        executor.execute(() -> {
            Thread.currentThread().setName("Append events 2-4");
            try {
                log.info("*** Waiting for Event 1 to be persisted and committed to the event store");
                firstEventPersisted.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            // Persist events (1 based) number 2-4 AFTER event number 1 has been persisted.
            // (event 5 will be persisted and rolled back and re-persisted to ensure global order 5 is marked as a permanent gap and the re-persisted event number 5 will be the last to be delivered)
            var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
            log.info("*** Appending events 2-4 out of 7 (1 based) to Stream");
            var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                 orderId,
                                                                 testEvents.subList(1, 4));
            assertThat((CharSequence) aggregateEventStream.aggregateId()).isEqualTo(orderId);
            assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
            assertThat(aggregateEventStream.eventList().size()).isEqualTo(3);
            log.info("*** Appending Events 2-4 resulted in (event-order, global-order): {}", aggregateEventStream.eventList().stream().map(persistedEvent -> "(" + persistedEvent.eventOrder() + ", " + persistedEvent.globalEventOrder() + ")").reduce((s, s2) -> s + ", " + s2).get());
            assertThat(aggregateEventStream.eventList().get(0).eventOrder().longValue()).isEqualTo(1);
            assertThat(aggregateEventStream.eventList().get(0).globalEventOrder().longValue()).isEqualTo(2);
            assertThat(aggregateEventStream.eventList().get(1).eventOrder().longValue()).isEqualTo(2);
            assertThat(aggregateEventStream.eventList().get(1).globalEventOrder().longValue()).isEqualTo(3);
            assertThat(aggregateEventStream.eventList().get(2).eventOrder().longValue()).isEqualTo(3);
            assertThat(aggregateEventStream.eventList().get(2).globalEventOrder().longValue()).isEqualTo(4);

            log.info("*** Committing Append of events 2-4 out of 7 (1 based)");
            unitOfWork.commit();

            eventsTwoToFourPersisted.countDown();
            Thread.currentThread().setName("Re-Append event 5");
            try {
                log.info("*** Waiting for Events 6-7 to be persisted and committed to the event store");
                eventsSixAndSevenPersisted.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            // Append event 5 again
            unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
            log.info("*** Appending events 5 out of 7 (1 based) to Stream");
            aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                             orderId,
                                                             testEvents.subList(4, 5));
            assertThat((CharSequence) aggregateEventStream.aggregateId()).isEqualTo(orderId);
            assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
            assertThat(aggregateEventStream.eventList().size()).isEqualTo(1);
            log.info("*** Appending Event 5 resulted in (event-order, global-order): {}", aggregateEventStream.events().map(persistedEvent -> "(" + persistedEvent.eventOrder() + ", " + persistedEvent.globalEventOrder() + ")").reduce((s, s2) -> s + ", " + s2).get());
            assertThat(aggregateEventStream.eventList().get(0).eventOrder().longValue()).isEqualTo(6);
            assertThat(aggregateEventStream.eventList().get(0).globalEventOrder().longValue()).isEqualTo(8);
            log.info("*** Committing Append of events 5 out of 7 (1 based)");
            unitOfWork.commit();
        });
        executor.execute(() -> {
            Thread.currentThread().setName("Append and rollback event 5");
            try {
                log.info("*** Waiting for Events 2-4 to be persisted to the event store");
                eventsTwoToFourPersisted.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            // Append event 5 but rollback it back to create a permanent gap
            var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
            log.info("*** Appending events 5 out of 7 (1 based) to Stream");
            var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                 orderId,
                                                                 testEvents.subList(4, 5));
            assertThat((CharSequence) aggregateEventStream.aggregateId()).isEqualTo(orderId);
            assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
            assertThat(aggregateEventStream.eventList().size()).isEqualTo(1);
            log.info("*** Appending Event 5 resulted in (event-order, global-order): {}", aggregateEventStream.events().map(persistedEvent -> "(" + persistedEvent.eventOrder() + ", " + persistedEvent.globalEventOrder() + ")").reduce((s, s2) -> s + ", " + s2).get());
            assertThat(aggregateEventStream.eventList().get(0).eventOrder().longValue()).isEqualTo(4);
            assertThat(aggregateEventStream.eventList().get(0).globalEventOrder().longValue()).isEqualTo(5);
            log.info("*** Rolling back Append of events 5 out of 7 (1 based)");
            unitOfWork.rollback();

            eventsFiveRolledBack.countDown();
        });
        executor.execute(() -> {
            Thread.currentThread().setName("Append events 6-7");
            try {
                log.info("*** Waiting for Event 5 to be appended and rolled back");
                eventsFiveRolledBack.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            // Persist events (1 based) number events 6-7
            var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
            log.info("*** Appending events 6-7 out of 7 (1 based) to Stream");
            var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                                 orderId,
                                                                 testEvents.subList(5, 7));
            assertThat((CharSequence) aggregateEventStream.aggregateId()).isEqualTo(orderId);
            assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
            assertThat(aggregateEventStream.eventList().size()).isEqualTo(2);
            log.info("*** Appending Events 6-7 resulted in (event-order, global-order): {}", aggregateEventStream.events().map(persistedEvent -> "(" + persistedEvent.eventOrder() + ", " + persistedEvent.globalEventOrder() + ")").reduce((s, s2) -> s + ", " + s2).get());
            assertThat(aggregateEventStream.eventList().get(0).eventOrder().longValue()).isEqualTo(4);
            assertThat(aggregateEventStream.eventList().get(0).globalEventOrder().longValue()).isEqualTo(6);
            assertThat(aggregateEventStream.eventList().get(1).eventOrder().longValue()).isEqualTo(5);
            assertThat(aggregateEventStream.eventList().get(1).globalEventOrder().longValue()).isEqualTo(7);
            log.info("*** Committing Append of events 6-7 out of 7 (1 based)");
            unitOfWork.commit();
            eventsSixAndSevenPersisted.countDown();
        });


        var unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        log.info("*** Appending event 1 out of 7 (1 based) to Stream");
        var aggregateEventStream = eventStore.appendToStream(aggregateType,
                                                             orderId,
                                                             testEvents.subList(0, 1));
        assertThat((CharSequence) aggregateEventStream.aggregateId()).isEqualTo(orderId);
        assertThat(aggregateEventStream.isPartialEventStream()).isTrue();
        assertThat(aggregateEventStream.eventList().size()).isEqualTo(1);
        log.info("*** Appending Event 1 resulted in (event-order, global-order): {}", aggregateEventStream.events().map(persistedEvent -> "(" + persistedEvent.eventOrder() + ", " + persistedEvent.globalEventOrder() + ")").reduce((s, s2) -> s + ", " + s2).get());
        assertThat(aggregateEventStream.eventList().get(0).eventOrder().longValue()).isEqualTo(0);
        assertThat(aggregateEventStream.eventList().get(0).globalEventOrder().longValue()).isEqualTo(1);
        log.info("*** Committing Append of event 1 out of 7 (1 based)");
        unitOfWork.commit();
        firstEventPersisted.countDown();

        // Verify we received all Order events
        Awaitility.waitAtMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> assertThat(orderEventsReceived.size()).isEqualTo(testEvents.size()));
        assertThat(orderEventsReceived.stream().filter(persistedEvent -> !persistedEvent.aggregateType().equals(ORDERS)).findAny()).isEmpty();
        assertThat(orderEventsReceived.stream()
                                      .map(persistedEvent -> persistedEvent.globalEventOrder().longValue())
                                      .collect(Collectors.toList()))
                .isEqualTo(List.of(1L, 2L, 3L, 4L, 6L, 7L, 8L));

        assertThat(eventStore.getEventStreamGapHandler()
                             .gapHandlerFor(ordersSubscriberId)
                             .getTransientGapsFor(ORDERS)).isEqualTo(List.of(GlobalEventOrder.of(5)));

        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() ->
                                         assertThat(eventStore.getEventStreamGapHandler()
                                                              .getPermanentGapsFor(ORDERS)
                                                              .collect(Collectors.toList())).isEqualTo(List.of(GlobalEventOrder.of(5))));
        assertThat(eventStore.getEventStreamGapHandler()
                             .gapHandlerFor(ordersSubscriberId)
                             .getTransientGapsFor(ORDERS)).isEmpty();

        orderEventsFlux.dispose();
    }

    private Pair<OrderId, List<? extends OrderEvent>> createTestEvents() {
        var orderId   = OrderId.random();
        var productId = ProductId.random();
        var orderEvents = List.of(
                new OrderEvent.OrderAdded(orderId, CustomerId.random(), 100),
                new OrderEvent.ProductAddedToOrder(orderId, productId, 3),
                new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), 1),
                new OrderEvent.ProductOrderQuantityAdjusted(orderId, productId, 10),
                new OrderEvent.ProductRemovedFromOrder(orderId, productId),
                new OrderEvent.ProductAddedToOrder(orderId, ProductId.random(), 1),
                new OrderEvent.OrderAccepted(orderId));
        return Pair.of(orderId, orderEvents);
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
        public PersistableEvent map(Object aggregateId,
                                    AggregateEventStreamConfiguration aggregateEventStreamConfiguration,
                                    Object event,
                                    EventOrder eventOrder) {
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