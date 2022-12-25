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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreManagedUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresqlAggregateSnapshotRepository_deleteAllHistoricSnapshotsIT {
    public static final AggregateType ORDERS = AggregateType.of("Orders");

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest").withDatabaseName("event-store")
                                                                                                           .withUsername("test-user")
                                                                                                           .withPassword("secret-password");

    private Jdbi                               jdbi;
    private AggregateType                      aggregateType;
    private EventStoreManagedUnitOfWorkFactory unitOfWorkFactory;

    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;
    private AggregateSnapshotRepository.PostgresqlAggregateSnapshotRepository       snapshotRepository;
    private StatefulAggregateRepository<OrderId, OrderEvent, Order>                 ordersRepository;
    private AggregateSnapshotRepository.PostgresqlAggregateSnapshotRepository       snapshotRepositorySpy;

    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new EventStoreSqlLogger());

        aggregateType = ORDERS;
        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        var aggregateEventStreamConfigurationFactory = SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfigurationUsingJackson(createObjectMapper(),
                                                                                                                                                                  IdentifierColumnType.UUID,
                                                                                                                                                                  JSONColumnType.JSONB);
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                                     unitOfWorkFactory,
                                                                                                     new TestPersistableEventMapper(),
                                                                                                     aggregateEventStreamConfigurationFactory));

        snapshotRepository = new AggregateSnapshotRepository.PostgresqlAggregateSnapshotRepository(eventStore,
                                                                                                   unitOfWorkFactory,
                                                                                                   aggregateEventStreamConfigurationFactory.jsonSerializer,
                                                                                                   AddNewAggregateSnapshotStrategy.updateOnEachAggregateUpdate(),
                                                                                                   AggregateSnapshotDeletionStrategy.deleteAllHistoricSnapshots());


        eventStore.addAggregateEventStreamConfiguration(ORDERS,
                                                        OrderId.class);
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
    }


    @Test
    void persisting_the_first_event_creates_a_snapshot() {
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
            assertThat(snapshotOptional).isPresent();
            assertThat((CharSequence) snapshotOptional.get().aggregateId).isEqualTo(orderId);
            assertThat(snapshotOptional.get().eventOrderOfLastIncludedEvent).isEqualTo(EventOrder.of(0));
            assertThat(snapshotOptional.get().aggregateImplType).isEqualTo(Order.class);
            assertThat((CharSequence) snapshotOptional.get().aggregateType).isEqualTo(ORDERS);
            assertThat(snapshotOptional.get().aggregateSnapshot).usingRecursiveComparison()
                                                                .ignoringFieldsMatchingRegexes("invoker")
                                                                .isEqualTo(order);
        });
    }

    @Test
    void verify_that_only_a_single_snapshot_is_stored_when_new_events_are_persisted() {
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
            assertThat(snapshotOptional).isPresent();
            assertThat((CharSequence) snapshotOptional.get().aggregateId).isEqualTo(orderId);
            assertThat(snapshotOptional.get().eventOrderOfLastIncludedEvent).isEqualTo(EventOrder.of(0));
            assertThat(snapshotOptional.get().aggregateImplType).isEqualTo(Order.class);
            assertThat((CharSequence) snapshotOptional.get().aggregateType).isEqualTo(ORDERS);
            assertThat(snapshotOptional.get().aggregateSnapshot).usingRecursiveComparison()
                                                                .ignoringFieldsMatchingRegexes("invoker")
                                                                .isEqualTo(order);

        });

        for (var round = 1; round <= 5; round++) {
            // Add 2 more events
            order.addProduct(ProductId.random(), round);
            order.addProduct(ProductId.random(), round);
            var lastPersistedEventsEventOrder = round * 2; // EventOrder is 0 based hence no + 1
            var roundAsString                 = Integer.toString(round);
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
                assertThat(allSnapshots).hasSize(1);
                var snapshot = allSnapshots.get(0);
                assertThat(snapshot.eventOrderOfLastIncludedEvent)
                        .describedAs("Round: %s", roundAsString)
                        .isEqualTo(EventOrder.of(lastPersistedEventsEventOrder));
                assertThat((CharSequence) snapshot.aggregateId)
                        .describedAs("Round: %s", roundAsString)
                        .isEqualTo(orderId);
                assertThat(snapshot.aggregateImplType)
                        .describedAs("Round: %s", roundAsString)
                        .isEqualTo(Order.class);
                assertThat((CharSequence) snapshot.aggregateType)
                        .describedAs("Round: %s", roundAsString)
                        .isEqualTo(ORDERS);
                assertThat(snapshot.aggregateSnapshot)
                        .describedAs("Round: %s", roundAsString)
                        .usingRecursiveComparison()
                        .ignoringFieldsMatchingRegexes("invoker")
                        .isEqualTo(order);
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