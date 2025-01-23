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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap.PostgresqlEventStreamGapHandler;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
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

import java.time.OffsetDateTime;
import java.util.*;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration;
import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
class FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStreamIT {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
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
                                                                                                    unitOfWorkFactory));
        eventStore.addAggregateEventStreamConfiguration(aggregateType,
                                                        AggregateIdSerializer.serializerFor(OrderId.class));
        eventStore.addEventStoreInterceptor(new FlushAndPublishPersistedEventsToEventBusRightAfterAppendToStream());
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
        
        // Verify the two events appended to the stream
        assertThat(order1AggregateStream.eventOrderRangeIncluded()).isEqualTo(LongRange.only(0));
        var aggregateStreamEventOrder1 = order1AggregateStream.eventList().get(0);
        assertThat((CharSequence) aggregateStreamEventOrder1.eventId()).isNotNull();
        assertThat((CharSequence) aggregateStreamEventOrder1.aggregateId()).isEqualTo(orderId1);
        assertThat((CharSequence) aggregateStreamEventOrder1.aggregateType()).isEqualTo(aggregateType);
        assertThat(aggregateStreamEventOrder1.eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(aggregateStreamEventOrder1.eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(aggregateStreamEventOrder1.globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(aggregateStreamEventOrder1.timestamp()).isBefore(OffsetDateTime.now());
        assertThat(aggregateStreamEventOrder1.event().getEventName()).isEmpty();
        assertThat(aggregateStreamEventOrder1.event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(aggregateStreamEventOrder1.event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(aggregateStreamEventOrder1.event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEventsOrder1.get(0));
        assertThat(aggregateStreamEventOrder1.event().getJson()).isEqualTo(
                "{\"orderId\":\"beed77fb-d911-1111-9c48-03ed5bfe8f89\",\"orderingCustomerId\":\"Test-Customer-Id-10\",\"orderNumber\":1234}");
        assertThat(aggregateStreamEventOrder1.metaData().getJson()).contains("\"Key1\":\"Value1\""); // Note formatting changed by postgresql
        assertThat(aggregateStreamEventOrder1.metaData().getJson()).contains("\"Key2\":\"Value2\""); // Note formatting changed by postgresql
        assertThat(aggregateStreamEventOrder1.metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(aggregateStreamEventOrder1.metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(aggregateStreamEventOrder1.causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(aggregateStreamEventOrder1.correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat(order2AggregateStream.eventOrderRangeIncluded()).isEqualTo(LongRange.only(0));
        var aggregateStreamEventOrder2 = order2AggregateStream.eventList().get(0);
        assertThat((CharSequence) aggregateStreamEventOrder2.eventId()).isNotNull();
        assertThat((CharSequence) aggregateStreamEventOrder2.aggregateId()).isEqualTo(orderId2);
        assertThat((CharSequence) aggregateStreamEventOrder2.aggregateType()).isEqualTo(aggregateType);
        assertThat(aggregateStreamEventOrder2.eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(aggregateStreamEventOrder2.eventRevision()).isEqualTo(EventRevision.of(3));
        assertThat(aggregateStreamEventOrder2.globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(aggregateStreamEventOrder2.timestamp()).isBefore(OffsetDateTime.now());
        assertThat(aggregateStreamEventOrder2.event().getEventName()).isEmpty();
        assertThat(aggregateStreamEventOrder2.event().getEventType()).isEqualTo(Optional.of(EventType.of(OrderEvent.OrderAdded.class)));
        assertThat(aggregateStreamEventOrder2.event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(OrderEvent.OrderAdded.class).toString());
        assertThat(aggregateStreamEventOrder2.event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(persistableEventsOrder2.get(0));
        assertThat(aggregateStreamEventOrder2.event().getJson()).isEqualTo(
                "{\"orderId\":\"ceed77fb-d911-9c48-1111-03ed5bfe8f89\",\"orderingCustomerId\":\"Test-Customer-Id2-10\",\"orderNumber\":4321}");
        assertThat(aggregateStreamEventOrder2.metaData().getJson()).contains("\"Key1\":\"Value1\""); // Note formatting changed by postgresql
        assertThat(aggregateStreamEventOrder2.metaData().getJson()).contains("\"Key2\":\"Value2\""); // Note formatting changed by postgresql
        assertThat(aggregateStreamEventOrder2.metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(aggregateStreamEventOrder2.metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(aggregateStreamEventOrder2.causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(aggregateStreamEventOrder2.correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        // And both events were published on the local events
        assertThat(recordingLocalEventBusConsumer.flushPersistedEvents).hasSize(2);
        assertThat((CharSequence) recordingLocalEventBusConsumer.flushPersistedEvents.get(0).eventId()).isEqualTo(aggregateStreamEventOrder1.eventId());
        assertThat((CharSequence) recordingLocalEventBusConsumer.flushPersistedEvents.get(1).eventId()).isEqualTo(aggregateStreamEventOrder2.eventId());
        
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents).isEmpty();
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents).isEmpty();
        assertThat(recordingLocalEventBusConsumer.afterRollbackPersistedEvents).isEmpty();
        recordingLocalEventBusConsumer.clear();

        unitOfWork.commit();

        // Then
        unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
        assertThat(recordingLocalEventBusConsumer.flushPersistedEvents).isEmpty();
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents).hasSize(2);
        assertThat((CharSequence) recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(0).eventId()).isEqualTo(aggregateStreamEventOrder1.eventId());
        assertThat((CharSequence) recordingLocalEventBusConsumer.beforeCommitPersistedEvents.get(1).eventId()).isEqualTo(aggregateStreamEventOrder2.eventId());
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents).hasSize(2);
        assertThat((CharSequence) recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(0).eventId()).isEqualTo(aggregateStreamEventOrder1.eventId());
        assertThat((CharSequence) recordingLocalEventBusConsumer.afterCommitPersistedEvents.get(1).eventId()).isEqualTo(aggregateStreamEventOrder2.eventId());
        assertThat(recordingLocalEventBusConsumer.afterRollbackPersistedEvents).isEmpty();

        // Check events still got stored
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
    }
    

    private static class RecordingLocalEventBusConsumer implements EventHandler {
        private final List<PersistedEvent> beforeCommitPersistedEvents  = new ArrayList<>();
        private final List<PersistedEvent> afterCommitPersistedEvents   = new ArrayList<>();
        private final List<PersistedEvent> afterRollbackPersistedEvents = new ArrayList<>();
        private final List<PersistedEvent> flushPersistedEvents = new ArrayList<>();

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