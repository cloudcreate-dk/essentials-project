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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.decider;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.decider.DeciderTest.GuessingGameEvent;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot.AggregateSnapshotRepository;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.*;
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
import java.util.stream.*;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfigurationUsingJackson;
import static dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule.createObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Event-Sourced, {@link Decider} based Mastermind Digit Guessing Game integration test,
 * using the {@link CommandHandler#deciderBasedCommandHandler(ConfigurableEventStore,
 * AggregateType, Class, AggregateIdResolver, AggregateIdResolver, AggregateSnapshotRepository, Class, Decider)} which is backed by the {@link EventStore}.
 * <p>
 * This event-sourced Mastermind game presents players with a challenge to decipher a secret code made up of user-defined digits,
 * typically ranging from 1 to 9.<br>
 * The code's length is variable, offering diverse gameplay scenarios.
 * <p>
 * The player submits guesses of varying lengths, receiving feedback on whether the guess is too short, too long, or the correct length.<br>
 * Additional feedback, such as the number of correct digits and their placement, can be incorporated.
 * <p>
 * The goal is to strategically decipher the secret code within a set maximum number of guesses.<br>
 * Successfully guessing the secret before reaching the limit results in {@link GuessingGameEvent.GameWon},
 * while exceeding the allowed guesses leads to {@link GuessingGameEvent.GameLost}.
 *
 * <p>
 * Note:<br>
 * <pre>
 * This example/test is inspired by this <a href="https://dev.to/jakub_zalas/functional-event-sourcing-example-in-kotlin-3245">article</a>.<br>
 * The example in the referenced article and its code <a href="https://github.com/jakzal/mastermind/tree/main">MasterMind</a>
 * are released under this <a href="https://github.com/jakzal/mastermind/blob/main/LICENSE">MIT license</a>:
 *
 * Copyright (c) 2015 Jakub Zalas <jakub@zalas.pl>
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * </pre>
 *
 * @see DeciderTest
 */
@Testcontainers
class DeciderBasedCommandHandlerIT {
    public static final AggregateType                                                           GAMES = AggregateType.of("Games");
    private             Jdbi                                                                    jdbi;
    private             EventStoreUnitOfWorkFactory<EventStoreUnitOfWork>                       unitOfWorkFactory;
    private             TestPersistableEventMapper                                              eventMapper;
    private             PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");

    private ObjectMapper                   objectMapper;
    private RecordingLocalEventBusConsumer recordingLocalEventBusConsumer;
    private Disposable                     persistedEventFlux;
    private List<PersistedEvent>           asynchronousGameEventsReceived;

    private CommandHandler<DeciderTest.GuessingGameCommand, DeciderTest.GuessingGameEvent, DeciderTest.GuessingGameError> commandHandler;

    private DeciderTest.GuessingGameId      gameId;
    private DeciderTest.Secret              secret;
    private int                             maxAttempts;
    private Set<DeciderTest.Digit>          allowedDigits;
    private DeciderTest.GuessingGameDecider decider;

    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        objectMapper = createObjectMapper();
        eventMapper = new TestPersistableEventMapper();
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                                     unitOfWorkFactory,
                                                                                                     eventMapper,
                                                                                                     standardSingleTenantConfigurationUsingJackson(objectMapper,
                                                                                                                                                   IdentifierColumnType.TEXT,
                                                                                                                                                   JSONColumnType.JSONB)));
        recordingLocalEventBusConsumer = new RecordingLocalEventBusConsumer();
        eventStore.localEventBus().addSyncSubscriber(recordingLocalEventBusConsumer);

        decider = new DeciderTest.GuessingGameDecider();
        commandHandler = CommandHandler.deciderBasedCommandHandler(eventStore,
                                                                   GAMES,
                                                                   DeciderTest.GuessingGameId.class,
                                                                   cmd -> Optional.of(cmd.gameId()),
                                                                   event -> Optional.of(event.gameId()),
                                                                   null,
                                                                   DeciderTest.GuessingGameState.class,
                                                                   decider);


        asynchronousGameEventsReceived = new ArrayList<>();
        persistedEventFlux = eventStore.pollEvents(GAMES,
                                                   GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                   Optional.empty(),
                                                   Optional.of(Duration.ofMillis(100)),
                                                   Optional.empty(),
                                                   Optional.empty())
                                       .subscribe(event -> asynchronousGameEventsReceived.add(event));

        gameId = DeciderTest.GuessingGameId.random();
        secret = new DeciderTest.Secret(4, 5, 8, 9);
        maxAttempts = 12;
        allowedDigits = Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9).map(DeciderTest.Digit::of).collect(Collectors.toSet());
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();

        if (persistedEventFlux != null) {
            persistedEventFlux.dispose();
        }
    }

    @Test
    void starting_a_game() {
        // When and then
        var expectedGameStartedEvent = new DeciderTest.GuessingGameEvent.GameStarted(gameId,
                                                                                     secret,
                                                                                     maxAttempts,
                                                                                     allowedDigits);
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            commandHandler.handle(new DeciderTest.GuessingGameCommand.JoinGame(gameId,
                                                                               secret,
                                                                               maxAttempts,
                                                                               allowedDigits))
                          .shouldSucceedWith(expectedGameStartedEvent);
        });

        // Assert event published
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(1);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofMillis(2000))
                  .untilAsserted(() -> assertThat(asynchronousGameEventsReceived.size()).isEqualTo(1));

        assertThat((CharSequence) asynchronousGameEventsReceived.get(0).eventId()).isNotNull();
        assertThat((CharSequence) asynchronousGameEventsReceived.get(0).aggregateId()).isEqualTo(gameId);
        assertThat((CharSequence) asynchronousGameEventsReceived.get(0).aggregateType()).isEqualTo(GAMES);
        assertThat(asynchronousGameEventsReceived.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(asynchronousGameEventsReceived.get(0).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(asynchronousGameEventsReceived.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(asynchronousGameEventsReceived.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(asynchronousGameEventsReceived.get(0).event().getEventName()).isEmpty();
        assertThat(asynchronousGameEventsReceived.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(DeciderTest.GuessingGameEvent.GameStarted.class)));
        assertThat(asynchronousGameEventsReceived.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(DeciderTest.GuessingGameEvent.GameStarted.class).toString());
        assertThat(asynchronousGameEventsReceived.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(expectedGameStartedEvent);
        assertThat(asynchronousGameEventsReceived.get(0).event().getJson()).isNotEmpty();
        assertThat(asynchronousGameEventsReceived.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(asynchronousGameEventsReceived.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(asynchronousGameEventsReceived.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(asynchronousGameEventsReceived.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(TestPersistableEventMapper.META_DATA));
        assertThat(asynchronousGameEventsReceived.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(asynchronousGameEventsReceived.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void make_a_guess() {
        // Given
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            eventStore.startStream(GAMES,
                                   gameId,
                                   new DeciderTest.GuessingGameEvent.GameStarted(gameId,
                                                                                 secret,
                                                                                 maxAttempts,
                                                                                 allowedDigits));

        });
        // Given event was persisted and published
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(1);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofMillis(2000))
                  .untilAsserted(() -> assertThat(asynchronousGameEventsReceived.size()).isEqualTo(1));
        // Reset to assert
        asynchronousGameEventsReceived.clear();
        recordingLocalEventBusConsumer.clear();

        // When and then
        var guess = new DeciderTest.Guess(1, 2, 3, 4);
        var expectedGuessMadeEvent = new DeciderTest.GuessingGameEvent.GuessMade(gameId,
                                                                                 guess,
                                                                                 1);
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            commandHandler.handle(new DeciderTest.GuessingGameCommand.MakeGuess(gameId,
                                                                                guess))
                          .shouldSucceedWith(expectedGuessMadeEvent);
        });

        // Assert event published
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(1);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofMillis(2000))
                  .untilAsserted(() -> assertThat(asynchronousGameEventsReceived.size()).isEqualTo(1));

        assertThat((CharSequence) asynchronousGameEventsReceived.get(0).eventId()).isNotNull();
        assertThat((CharSequence) asynchronousGameEventsReceived.get(0).aggregateId()).isEqualTo(gameId);
        assertThat((CharSequence) asynchronousGameEventsReceived.get(0).aggregateType()).isEqualTo(GAMES);
        assertThat(asynchronousGameEventsReceived.get(0).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(asynchronousGameEventsReceived.get(0).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(asynchronousGameEventsReceived.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(asynchronousGameEventsReceived.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(asynchronousGameEventsReceived.get(0).event().getEventName()).isEmpty();
        assertThat(asynchronousGameEventsReceived.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(DeciderTest.GuessingGameEvent.GuessMade.class)));
        assertThat(asynchronousGameEventsReceived.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(DeciderTest.GuessingGameEvent.GuessMade.class).toString());
        assertThat(asynchronousGameEventsReceived.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(expectedGuessMadeEvent);
        assertThat(asynchronousGameEventsReceived.get(0).event().getJson()).isNotEmpty();
        assertThat(asynchronousGameEventsReceived.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(asynchronousGameEventsReceived.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(asynchronousGameEventsReceived.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(asynchronousGameEventsReceived.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(TestPersistableEventMapper.META_DATA));
        assertThat(asynchronousGameEventsReceived.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(asynchronousGameEventsReceived.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    @Test
    void game_can_no_longer_be_played_once_won() {
        // Given
        var guess = secret.toGuess();
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            eventStore.startStream(GAMES,
                                   gameId,
                                   new DeciderTest.GuessingGameEvent.GameStarted(gameId,
                                                                                 secret,
                                                                                 maxAttempts,
                                                                                 allowedDigits),
                                   new DeciderTest.GuessingGameEvent.GuessMade(gameId,
                                                                               guess,
                                                                               1),
                                   new DeciderTest.GuessingGameEvent.GameWon(gameId, 1));

        });
        // Given event was persisted and published
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(3);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(3);
        Awaitility.waitAtMost(Duration.ofMillis(2000))
                  .untilAsserted(() -> assertThat(asynchronousGameEventsReceived.size()).isEqualTo(3));
        // Reset to assert
        asynchronousGameEventsReceived.clear();
        recordingLocalEventBusConsumer.clear();

        // When and then
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            commandHandler.handle(new DeciderTest.GuessingGameCommand.MakeGuess(gameId,
                                                                                guess))
                          .shouldFailWith(new DeciderTest.GuessingGameError.GameFinishedError.GameAlreadyWon(gameId));
        });

        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(0);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(0);
        Awaitility.await()
                  .during(Duration.ofMillis(1990))
                  .atMost(Duration.ofSeconds(2000))
                  .until(() -> asynchronousGameEventsReceived.isEmpty());
    }

    // ------------------------ Supporting classes ---------------------------

    private static class TestPersistableEventMapper implements PersistableEventMapper {
        public static final EventMetaData META_DATA       = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
        private final       CorrelationId correlationId   = CorrelationId.random();
        private final       EventId       causedByEventId = EventId.random();

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
}
