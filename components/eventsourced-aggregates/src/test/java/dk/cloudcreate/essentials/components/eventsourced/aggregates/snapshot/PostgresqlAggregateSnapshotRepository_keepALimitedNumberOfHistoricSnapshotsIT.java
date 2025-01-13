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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.Order;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateRepository;
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
import dk.cloudcreate.essentials.shared.collections.Lists;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresqlAggregateSnapshotRepository_keepALimitedNumberOfHistoricSnapshotsIT {
    public static final AggregateType ORDERS                               = AggregateType.of("Orders");
    public static final int           NUMBER_OF_EVENTS_BETWEEN_SNAPSHOTS   = 2;
    public static final int           NUMBER_OF_HISTORIC_SNAPSHOTS_TO_KEEP = 3;

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
                                                                       AddNewAggregateSnapshotStrategy.updateWhenBehindByNumberOfEvents(NUMBER_OF_EVENTS_BETWEEN_SNAPSHOTS),
                                                                       AggregateSnapshotDeletionStrategy.keepALimitedNumberOfHistoricSnapshots(NUMBER_OF_HISTORIC_SNAPSHOTS_TO_KEEP));


        eventStore.addAggregateEventStreamConfiguration(ORDERS,
                                                        OrderId.class);
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
        var eventsToPersist = order.getUncommittedChanges();
        unitOfWorkFactory.usingUnitOfWork(() -> {
            var eventStream = eventStore.appendToStream(ORDERS,
                                                        eventsToPersist.aggregateId,
                                                        eventsToPersist.eventOrderOfLastRehydratedEvent,
                                                        eventsToPersist.events);

            snapshotRepository.aggregateUpdated(order,
                                                eventStream);

            assertThat(snapshotRepository.loadSnapshot(ORDERS,
                                                       orderId,
                                                       Order.class)).isEmpty();
        });
    }

    @Test
    void persisting_2_events_creates_a_snapshot() {
        var orderId = OrderId.random();
        var order   = new Order(orderId, CustomerId.random(), 1234);
        order.addProduct(ProductId.random(), 10);
        var eventsToPersist = order.getUncommittedChanges();
        order.markChangesAsCommitted();
        unitOfWorkFactory.usingUnitOfWork(() -> {
            var eventStream = eventStore.appendToStream(ORDERS,
                                                        eventsToPersist.aggregateId,
                                                        eventsToPersist.eventOrderOfLastRehydratedEvent,
                                                        eventsToPersist.events);

            snapshotRepository.aggregateUpdated(order,
                                                eventStream);

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
        });
    }

    @Test
    void verify_that_multiple_snapshots_can_be_added_loaded_and_deleted_as_new_events_are_persisted() {
        var orderId         = OrderId.random();
        var order           = new Order(orderId, CustomerId.random(), 1234);
        var eventsToPersist = order.getUncommittedChanges();
        order.markChangesAsCommitted();
        unitOfWorkFactory.usingUnitOfWork(() -> {
            var eventStream = eventStore.appendToStream(ORDERS,
                                                        eventsToPersist.aggregateId,
                                                        eventsToPersist.eventOrderOfLastRehydratedEvent,
                                                        eventsToPersist.events);

            snapshotRepository.aggregateUpdated(order,
                                                eventStream);

            var snapshotOptional = snapshotRepository.loadSnapshot(ORDERS,
                                                                   orderId,
                                                                   Order.class);
            assertThat(snapshotOptional).isEmpty();
        });

        for (var round = 1; round <= 5; round++) {
            // Add 2 more events
            order.addProduct(ProductId.random(), round);
            order.addProduct(ProductId.random(), round);
            var numberOfPersistedEvents = round * 2 + 1; // 1 for the first event persisted above
            var roundAsString           = Integer.toString(round);
            unitOfWorkFactory.usingUnitOfWork(() -> {
                var newEventsToPersist = order.getUncommittedChanges();
                order.markChangesAsCommitted();
                var eventStream = eventStore.appendToStream(ORDERS,
                                                            newEventsToPersist.aggregateId,
                                                            newEventsToPersist.eventOrderOfLastRehydratedEvent,
                                                            newEventsToPersist.events);

                snapshotRepository.aggregateUpdated(order,
                                                    eventStream);

                var allSnapshots = snapshotRepository.loadAllSnapshots(ORDERS,
                                                                       orderId,
                                                                       Order.class,
                                                                       true);
                var snapshotsProduced         = numberOfPersistedEvents / NUMBER_OF_EVENTS_BETWEEN_SNAPSHOTS;
                var expectedNumberOfSnapshots = Math.min(NUMBER_OF_HISTORIC_SNAPSHOTS_TO_KEEP, snapshotsProduced);
                assertThat(allSnapshots).hasSize(expectedNumberOfSnapshots);
                for (var snapShotIndex = 0; snapShotIndex < allSnapshots.size(); snapShotIndex++) {
                    var lastSnapshot = (snapShotIndex == allSnapshots.size() - 1);
                    var snapshot     = allSnapshots.get(snapShotIndex);
                    assertThat(snapshot.eventOrderOfLastIncludedEvent)
                            .describedAs("Snapshots-Produced: %d, Round: %s, SnapshotIndex: %d", snapshotsProduced, roundAsString, snapShotIndex)
                            .isEqualTo(EventOrder.of(((snapShotIndex + 1) + (snapshotsProduced - allSnapshots.size())) * 2)); // EventOrder is 0 based, hence no +1
                    assertThat((CharSequence) snapshot.aggregateId)
                            .describedAs("Snapshots-Produced: %d, Round: %s, SnapshotIndex: %d", snapshotsProduced, roundAsString, snapShotIndex)
                            .isEqualTo(orderId);
                    assertThat(snapshot.aggregateImplType)
                            .describedAs("Snapshots-Produced: %d, Round: %s, SnapshotIndex: %d", snapshotsProduced, roundAsString, snapShotIndex)
                            .isEqualTo(Order.class);
                    assertThat((CharSequence) snapshot.aggregateType)
                            .describedAs("Snapshots-Produced: %d, Round: %s, SnapshotIndex: %d", snapshotsProduced, roundAsString, snapShotIndex)
                            .isEqualTo(ORDERS);
                    if (lastSnapshot) {
                        assertThat(snapshot.eventOrderOfLastIncludedEvent)
                                .describedAs("Snapshots-Produced: %d, Round: %s, SnapshotIndex: %d", snapshotsProduced, roundAsString, snapShotIndex)
                                .isEqualTo(Lists.last(eventStream.eventList()).get().eventOrder());
                        assertThat(snapshot.aggregateSnapshot)
                                .describedAs("Snapshots-Produced: %d, Round: %s, SnapshotIndex: %d", snapshotsProduced, roundAsString, snapShotIndex)
                                .usingRecursiveComparison()
                                .ignoringFieldsMatchingRegexes("invoker")
                                .isEqualTo(order);
                    } else {
                        assertThat(snapshot.aggregateSnapshot)
                                .describedAs("Snapshots-Produced: %d, Round: %s, SnapshotIndex: %d", snapshotsProduced, roundAsString, snapShotIndex)
                                .usingRecursiveComparison()
                                .ignoringFieldsMatchingRegexes("invoker")
                                .isNotEqualTo(order);
                    }
                }
            });
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
                                         new EventMetaData(),
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         null);
        }
    }

}