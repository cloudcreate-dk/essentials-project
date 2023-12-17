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


import dk.cloudcreate.essentials.components.foundation.types.RandomIdGenerator;
import dk.cloudcreate.essentials.shared.collections.Streams;
import dk.cloudcreate.essentials.types.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.*;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.decider.HandlerResult.*;
import static dk.cloudcreate.essentials.shared.FailFast.*;

/**
 * Event-Sourced, {@link Decider} based Mastermind Digit Guessing Game unit test
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
 * This example/test is inspired by this <a href="https://dev.to/jakub_zalas/functional-event-sourcing-example-in-kotlin-3245">article</a>.<br>
 * The examples in the referenced article and its code <a href="https://github.com/jakzal/mastermind/tree/main">MasterMind</a>
 * are released under <a href="https://github.com/jakzal/mastermind/blob/main/LICENSE">MIT license</a>:
 * <pre>
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
 * @see DeciderBasedCommandHandlerIT
 */
class DeciderTest {
    private GuessingGameDecider decider;
    private GuessingGameId      gameId;
    private Secret              secret;
    private int                 maxAttempts;
    private Set<Digit>          allowedDigits;

    @BeforeEach
    void setup() {
        decider = new GuessingGameDecider();
        gameId = GuessingGameId.random();
        secret = new Secret(4, 5, 8, 9);
        maxAttempts = 12;
        allowedDigits = Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9).map(Digit::of).collect(Collectors.toSet());
    }

    @Test
    void starting_a_game() {
        decider.handle(new GuessingGameCommand.JoinGame(gameId,
                                                        secret,
                                                        maxAttempts,
                                                        allowedDigits),
                       decider.initialState())
               .shouldSucceedWith(new GuessingGameEvent.GameStarted(gameId,
                                                                    secret,
                                                                    maxAttempts,
                                                                    allowedDigits));
    }

    @Test
    void make_a_guess() {
        // Given
        var givenGame = game(new GuessingGameEvent.GameStarted(gameId,
                                                               secret,
                                                               maxAttempts,
                                                               allowedDigits));
        // When
        var guess = new Guess(1, 2, 3, 4);
        var result = decider.handle(new GuessingGameCommand.MakeGuess(gameId,
                                                                      guess),
                                    givenGame);

        // Then
        result.shouldSucceedWith(new GuessingGameEvent.GuessMade(gameId,
                                                                 guess,
                                                                 1));
    }

    @Test
    void make_a_guess_with_the_correct_digits() {
        // Given
        var givenGame = game(new GuessingGameEvent.GameStarted(gameId,
                                                               secret,
                                                               maxAttempts,
                                                               allowedDigits));
        // When
        var guess = new Guess(9, 4, 8, 5);
        var result = decider.handle(new GuessingGameCommand.MakeGuess(gameId,
                                                                      guess),
                                    givenGame);

        // Then
        result.shouldSucceedWith(new GuessingGameEvent.GuessMade(gameId,
                                                                 guess,
                                                                 1));
    }

    @Test
    void game_is_won_when_secret_is_guessed() {
        // Given
        var givenGame = game(new GuessingGameEvent.GameStarted(gameId,
                                                               secret,
                                                               maxAttempts,
                                                               allowedDigits));
        // When
        var guess = secret.toGuess();
        var result = decider.handle(new GuessingGameCommand.MakeGuess(gameId,
                                                                      guess),
                                    givenGame);

        // Then
        result.shouldSucceedWith(new GuessingGameEvent.GuessMade(gameId,
                                                                 guess,
                                                                 1),
                                 new GuessingGameEvent.GameWon(gameId, 1));
    }

    @Test
    void game_can_no_longer_be_played_once_won() {
        // Given
        var guess = secret.toGuess();
        var givenGame = game(new GuessingGameEvent.GameStarted(gameId,
                                                               secret,
                                                               maxAttempts,
                                                               allowedDigits),
                             new GuessingGameEvent.GuessMade(gameId,
                                                             guess,
                                                             1),
                             new GuessingGameEvent.GameWon(gameId, 1));
        // When
        var result = decider.handle(new GuessingGameCommand.MakeGuess(gameId,
                                                                      guess),
                                    givenGame);

        // Then
        result.shouldFailWith(new GuessingGameError.GameFinishedError.GameAlreadyWon(gameId));
    }

    private GuessingGameState game(GuessingGameEvent... events) {
        return game(List.of(events));
    }

    private GuessingGameState game(List<GuessingGameEvent> givenEvents) {
        return StateEvolver.applyEvents(decider,
                                        decider.initialState(),
                                        givenEvents.stream());
    }


    // -------------------------- Guessing Game Decider --------------------------
    static class GuessingGameDecider implements Decider<GuessingGameCommand, GuessingGameEvent, GuessingGameError, GuessingGameState> {
        @Override
        public HandlerResult<GuessingGameError, GuessingGameEvent> handle(GuessingGameCommand cmd, GuessingGameState gameState) {
            return gameState.handle(cmd, gameState);
        }

        @Override
        public GuessingGameState initialState() {
            return new GuessingGameState.NotStartedGame();
        }

        @Override
        public GuessingGameState applyEvent(GuessingGameState gameState, GuessingGameEvent gameEvent) {
            return gameState.applyEvent(gameState, gameEvent);
        }
    }

    // -------------------------- State --------------------------
    public interface GuessingGameState extends Handler<GuessingGameCommand, GuessingGameEvent, GuessingGameError, GuessingGameState>, StateEvolver<GuessingGameState, GuessingGameEvent> {
        class NotStartedGame implements GuessingGameState {
            @Override
            public HandlerResult<GuessingGameError, GuessingGameEvent> handle(GuessingGameCommand cmd, GuessingGameState game) {
                if (cmd instanceof GuessingGameCommand.JoinGame) {
                    var joinGame = (GuessingGameCommand.JoinGame) cmd;
                    return events(new GuessingGameEvent.GameStarted(joinGame.gameId,
                                                                    joinGame.secret,
                                                                    joinGame.maxAttempts,
                                                                    joinGame.allowedDigits));
                }
                return error(new GuessingGameError.GuessError.GameNotStarted(cmd.gameId()));
            }

            @Override
            public GuessingGameState applyEvent(GuessingGameState game, GuessingGameEvent gameEvent) {
                if (gameEvent instanceof GuessingGameEvent.GameStarted) {
                    var gameStarted = (GuessingGameEvent.GameStarted) gameEvent;
                    return new StartedGameState(gameStarted.secret,
                                                gameStarted.allowedDigits,
                                                gameStarted.maxAttempts);
                }
                return game;
            }
        }

        final class StartedGameState implements GuessingGameState {
            private final Secret     secret;
            private final Set<Digit> allowedDigits;
            private final int        maxAttempts;
            int attempts;

            public StartedGameState(Secret secret,
                                    Set<Digit> allowedDigits,
                                    int maxAttempts) {
                this.secret = secret;
                this.allowedDigits = allowedDigits;
                this.maxAttempts = maxAttempts;
            }

            boolean isGuessInvalid(Guess guess) {
                return !allowedDigits.containsAll(guess.digits());
            }

            boolean isGuessTooShort(Guess guess) {
                return guess.length() < secret.length();
            }

            boolean isGuessTooLong(Guess guess) {
                return guess.length() > secret.length();
            }

            List<Digit> findHits(Guess guess) {
                return Streams.zipOrderedAndEqualSizedStreams(secret.digits().stream(),
                                                              guess.digits().stream(),
                                                              DigitPair::new)
                              .filter(DigitPair::matches)
                              .map(DigitPair::guess)
                              .collect(Collectors.toList());
            }

            @Override
            public HandlerResult<GuessingGameError, GuessingGameEvent> handle(GuessingGameCommand cmd, GuessingGameState game) {
                if (cmd instanceof GuessingGameCommand.MakeGuess) {
                    var makeGuess = (GuessingGameCommand.MakeGuess) cmd;
                    if (isGuessTooShort(makeGuess.guess)) {
                        return error(new GuessingGameError.GuessError.GuessTooShort(makeGuess.gameId, makeGuess.guess, secret.length()));
                    }
                    if (isGuessTooLong(makeGuess.guess)) {
                        return error(new GuessingGameError.GuessError.GuessTooLong(makeGuess.gameId, makeGuess.guess, secret.length()));
                    }
                    if (isGuessInvalid(makeGuess.guess)) {
                        return error(new GuessingGameError.GuessError.InvalidDigitInGuess(makeGuess.gameId, makeGuess.guess, allowedDigits));
                    }

                    List<GuessingGameEvent> events = new ArrayList<>();
                    events.add(new GuessingGameEvent.GuessMade(makeGuess.gameId,
                                                               makeGuess.guess,
                                                               attempts + 1));
                    if (findHits(makeGuess.guess).size() == secret.length() && Objects.equals(makeGuess.guess().digits, secret.digits)) {
                        events.add(new GuessingGameEvent.GameWon(makeGuess.gameId, attempts + 1));
                    } else if (attempts + 1 == maxAttempts) {
                        events.add(new GuessingGameEvent.GameLost(makeGuess.gameId, maxAttempts));
                    }
                    return events(events);
                }
                return error(new GuessingGameError.GuessError.GameNotStarted(cmd.gameId()));
            }

            @Override
            public GuessingGameState applyEvent(GuessingGameState game, GuessingGameEvent gameEvent) {
                if (gameEvent instanceof GuessingGameEvent.GuessMade) {
                    attempts++;
                } else if (gameEvent instanceof GuessingGameEvent.GameWon) {
                    return new GameWonState(secret,
                                            maxAttempts,
                                            attempts);
                } else if (gameEvent instanceof GuessingGameEvent.GameLost) {
                    return new GameLostState(secret,
                                             maxAttempts);
                }
                return game;
            }
        }

        class GameWonState implements GuessingGameState {
            public final Secret secret;
            public final int    maxAttempts;
            public final int    attempts;

            public GameWonState(Secret secret, int maxAttempts, int attempts) {
                this.secret = secret;
                this.maxAttempts = maxAttempts;
                this.attempts = attempts;
            }

            @Override
            public HandlerResult<GuessingGameError, GuessingGameEvent> handle(GuessingGameCommand cmd, GuessingGameState game) {
                return error(new GuessingGameError.GameFinishedError.GameAlreadyWon(cmd.gameId()));
            }

            @Override
            public GuessingGameState applyEvent(GuessingGameState game, GuessingGameEvent gameEvent) {
                return game;
            }
        }

        class GameLostState implements GuessingGameState {
            public final Secret secret;
            public final int    maxAttempts;

            public GameLostState(Secret secret, int maxAttempts) {
                this.secret = secret;
                this.maxAttempts = maxAttempts;
            }

            @Override
            public HandlerResult<GuessingGameError, GuessingGameEvent> handle(GuessingGameCommand cmd, GuessingGameState game) {
                return error(new GuessingGameError.GameFinishedError.GameAlreadyLost(cmd.gameId()));
            }

            @Override
            public GuessingGameState applyEvent(GuessingGameState game, GuessingGameEvent gameEvent) {
                return game;
            }
        }
    }

    // -------------------------- Commands --------------------------
    public interface GuessingGameCommand {
        GuessingGameId gameId();

        final class JoinGame implements GuessingGameCommand {
            public final GuessingGameId gameId;
            public final Secret         secret;
            public final int            maxAttempts;
            public final Set<Digit>     allowedDigits;

            public JoinGame(GuessingGameId gameId,
                            Secret secret,
                            int maxAttempts,
                            Set<Digit> allowedDigits) {
                this.gameId = requireNonNull(gameId, "No gameId provided");
                this.secret = requireNonNull(secret, "No secret provided");
                this.maxAttempts = maxAttempts;
                requireTrue(maxAttempts > 0, "You must specify > 0 maxAttempts");
                this.allowedDigits = requireNonEmpty(allowedDigits, "No allowedDigits provided");
            }

            @Override
            public GuessingGameId gameId() {
                return gameId;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof JoinGame)) return false;
                JoinGame joinGame = (JoinGame) o;
                return maxAttempts == joinGame.maxAttempts && Objects.equals(gameId, joinGame.gameId) && Objects.equals(secret, joinGame.secret) && Objects.equals(allowedDigits, joinGame.allowedDigits);
            }

            @Override
            public int hashCode() {
                return Objects.hash(gameId, secret, maxAttempts, allowedDigits);
            }

            @Override
            public String toString() {
                return "JoinGame{" +
                        "gameId=" + gameId +
                        ", secret=" + secret +
                        ", maxAttempts=" + maxAttempts +
                        ", allowedDigits=" + allowedDigits +
                        '}';
            }
        }

        final class MakeGuess implements GuessingGameCommand {
            public final GuessingGameId gameId;
            public final Guess          guess;

            public MakeGuess(GuessingGameId gameId,
                             Guess guess) {
                this.gameId = requireNonNull(gameId, "No gameId provided");
                this.guess = requireNonNull(guess, "No guess provided");
            }

            @Override
            public GuessingGameId gameId() {
                return gameId;
            }


            public Guess guess() {
                return guess;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof MakeGuess)) return false;
                MakeGuess makeGuess = (MakeGuess) o;
                return Objects.equals(gameId, makeGuess.gameId) && Objects.equals(guess, makeGuess.guess);
            }

            @Override
            public int hashCode() {
                return Objects.hash(gameId, guess);
            }

            @Override
            public String toString() {
                return "MakeGuess{" +
                        "gameId=" + gameId +
                        ", guess=" + guess +
                        '}';
            }
        }
    }

    // -------------------------- Events --------------------------
    public interface GuessingGameEvent {
        GuessingGameId gameId();

        final class GameStarted implements GuessingGameEvent {
            public final GuessingGameId gameId;
            public final Secret         secret;
            public final int            maxAttempts;
            public final Set<Digit>     allowedDigits;

            public GameStarted(GuessingGameId gameId,
                               Secret secret,
                               int maxAttempts,
                               Set<Digit> allowedDigits) {

                this.gameId = gameId;
                this.secret = secret;
                this.maxAttempts = maxAttempts;
                this.allowedDigits = allowedDigits;
            }

            @Override
            public GuessingGameId gameId() {
                return gameId;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof GameStarted)) return false;
                GameStarted that = (GameStarted) o;
                return maxAttempts == that.maxAttempts && Objects.equals(gameId, that.gameId) && Objects.equals(secret, that.secret) && Objects.equals(allowedDigits, that.allowedDigits);
            }

            @Override
            public int hashCode() {
                return Objects.hash(gameId, secret, maxAttempts, allowedDigits);
            }

            @Override
            public String toString() {
                return "GameStarted{" +
                        "gameId=" + gameId +
                        ", secret=" + secret +
                        ", maxAttempts=" + maxAttempts +
                        ", allowedDigits=" + allowedDigits +
                        '}';
            }
        }

        final class GuessMade implements GuessingGameEvent {
            public final GuessingGameId gameId;
            public final Guess          guess;
            public final int            attempt;

            public GuessMade(GuessingGameId gameId,
                             Guess guess,
                             int attempt) {
                this.gameId = gameId;
                this.guess = guess;
                this.attempt = attempt;
            }

            @Override
            public GuessingGameId gameId() {
                return gameId;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof GuessMade)) return false;
                GuessMade guessMade = (GuessMade) o;
                return attempt == guessMade.attempt && Objects.equals(gameId, guessMade.gameId) && Objects.equals(guess, guessMade.guess);
            }

            @Override
            public int hashCode() {
                return Objects.hash(gameId, guess, attempt);
            }

            @Override
            public String toString() {
                return "GuessMade{" +
                        "gameId=" + gameId +
                        ", guess=" + guess +
                        ", attempt=" + attempt +
                        '}';
            }
        }

        final class GameWon implements GuessingGameEvent {
            public final GuessingGameId gameId;
            public final int            numberOfAttempts;

            public GameWon(GuessingGameId gameId, int numberOfAttempts) {
                this.gameId = gameId;
                this.numberOfAttempts = numberOfAttempts;
            }

            @Override
            public GuessingGameId gameId() {
                return gameId;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof GameWon)) return false;
                GameWon gameWon = (GameWon) o;
                return numberOfAttempts == gameWon.numberOfAttempts && Objects.equals(gameId, gameWon.gameId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(gameId, numberOfAttempts);
            }

            @Override
            public String toString() {
                return "GameWon{" +
                        "gameId=" + gameId +
                        ", numberOfAttempts=" + numberOfAttempts +
                        '}';
            }
        }

        final class GameLost implements GuessingGameEvent {
            public final GuessingGameId gameId;
            public final int            maxAttempts;

            public GameLost(GuessingGameId gameId, int maxAttempts) {
                this.gameId = gameId;
                this.maxAttempts = maxAttempts;
            }

            @Override
            public GuessingGameId gameId() {
                return gameId;
            }
        }

    }

    // -------------------------- Errors --------------------------
    public interface GuessingGameError {
        GuessingGameId gameId();

        interface GameFinishedError extends GuessingGameError {
            final class GameAlreadyWon implements GameFinishedError {
                public final GuessingGameId gameId;

                public GameAlreadyWon(GuessingGameId gameId) {
                    this.gameId = gameId;
                }

                @Override
                public GuessingGameId gameId() {
                    return gameId;
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (!(o instanceof GameAlreadyWon)) return false;
                    GameAlreadyWon that = (GameAlreadyWon) o;
                    return Objects.equals(gameId, that.gameId);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(gameId);
                }

                @Override
                public String toString() {
                    return "GameAlreadyWon{" +
                            "gameId=" + gameId +
                            '}';
                }
            }

            final class GameAlreadyLost implements GameFinishedError {
                public final GuessingGameId gameId;

                public GameAlreadyLost(GuessingGameId gameId) {
                    this.gameId = gameId;
                }

                @Override
                public GuessingGameId gameId() {
                    return gameId;
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (!(o instanceof GameAlreadyLost)) return false;
                    GameAlreadyLost that = (GameAlreadyLost) o;
                    return Objects.equals(gameId, that.gameId);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(gameId);
                }

                @Override
                public String toString() {
                    return "GameAlreadyLost{" +
                            "gameId=" + gameId +
                            '}';
                }
            }
        }

        interface GuessError extends GuessingGameError {
            final class GameNotStarted implements GuessError {
                public final GuessingGameId gameId;

                public GameNotStarted(GuessingGameId gameId) {
                    this.gameId = gameId;
                }

                @Override
                public GuessingGameId gameId() {
                    return gameId;
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (!(o instanceof GameNotStarted)) return false;
                    GameNotStarted that = (GameNotStarted) o;
                    return Objects.equals(gameId, that.gameId);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(gameId);
                }

                @Override
                public String toString() {
                    return "GameNotStarted{" +
                            "gameId=" + gameId +
                            '}';
                }
            }

            final class GuessTooShort implements GuessError {
                public final GuessingGameId gameId;
                public final Guess          guess;
                public final int            requiredLength;

                public GuessTooShort(GuessingGameId gameId,
                                     Guess guess,
                                     int requiredLength) {
                    this.gameId = gameId;
                    this.guess = guess;
                    this.requiredLength = requiredLength;
                }

                @Override
                public GuessingGameId gameId() {
                    return gameId;
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (!(o instanceof GuessTooShort)) return false;
                    GuessTooShort that = (GuessTooShort) o;
                    return requiredLength == that.requiredLength && Objects.equals(gameId, that.gameId) && Objects.equals(guess, that.guess);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(gameId, guess, requiredLength);
                }

                @Override
                public String toString() {
                    return "GuessTooShort{" +
                            "gameId=" + gameId +
                            ", guess=" + guess +
                            ", requiredLength=" + requiredLength +
                            '}';
                }
            }

            final class GuessTooLong implements GuessError {
                public final GuessingGameId gameId;
                public final Guess          guess;
                public final int            requiredLength;

                public GuessTooLong(GuessingGameId gameId,
                                    Guess guess,
                                    int requiredLength) {
                    this.gameId = gameId;
                    this.guess = guess;
                    this.requiredLength = requiredLength;
                }

                @Override
                public GuessingGameId gameId() {
                    return gameId;
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (!(o instanceof GuessTooLong)) return false;
                    GuessTooLong that = (GuessTooLong) o;
                    return requiredLength == that.requiredLength && Objects.equals(gameId, that.gameId) && Objects.equals(guess, that.guess);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(gameId, guess, requiredLength);
                }

                @Override
                public String toString() {
                    return "GuessTooLong{" +
                            "gameId=" + gameId +
                            ", guess=" + guess +
                            ", requiredLength=" + requiredLength +
                            '}';
                }
            }

            final class InvalidDigitInGuess implements GuessError {
                public final GuessingGameId gameId;
                public final Guess          guess;
                public final Set<Digit>     allowedDigits;

                public InvalidDigitInGuess(GuessingGameId gameId,
                                           Guess guess,
                                           Set<Digit> allowedDigits) {
                    this.gameId = gameId;
                    this.guess = guess;
                    this.allowedDigits = allowedDigits;
                }

                @Override
                public GuessingGameId gameId() {
                    return gameId;
                }
            }
        }
    }

    // -------------------------- Base types --------------------------
    public static class GuessingGameId extends CharSequenceType<GuessingGameId> {

        protected GuessingGameId(CharSequence value) {
            super(value);
        }

        public static GuessingGameId random() {
            return new GuessingGameId(RandomIdGenerator.generate());
        }

        public static GuessingGameId of(CharSequence id) {
            return new GuessingGameId(id);
        }
    }

    public static class Guess {
        private final List<Digit> digits;

        public Guess(List<Digit> digits) {
            this.digits = digits;
            requireNonEmpty(digits, "No digits provided");
        }

        public Guess(int... digits) {
            this(Arrays.stream(digits).mapToObj(Digit::of).collect(Collectors.toList()));
        }

        public Guess(Digit... digits) {
            this(Arrays.asList(digits));
        }

        public int length() {
            return digits.size();
        }

        public List<Digit> digits() {
            return digits;
        }
    }

    public static class Secret {
        private final List<Digit> digits;

        public Secret(List<Digit> digits) {
            this.digits = digits;
            requireNonEmpty(digits, "No digits provided");
        }

        public Secret(int... digits) {
            this(Arrays.stream(digits).mapToObj(Digit::of).collect(Collectors.toList()));
        }

        public Secret(Digit... digits) {
            this(Arrays.asList(digits));
        }

        public int length() {
            return digits.size();
        }

        public Guess toGuess() {
            return new Guess(digits);
        }

        public List<Digit> digits() {
            return digits;
        }
    }

    public static class Digit extends IntegerType<Digit> {
        protected Digit(Integer value) {
            super(value);
        }


        public static Digit of(Integer value) {
            return new Digit(value);
        }
    }

    public static class DigitPair {
        private final Digit secret;
        private final Digit guess;

        public DigitPair(Digit secret, Digit guess) {

            this.secret = secret;
            this.guess = guess;
        }

        boolean matches() {
            return secret.equals(guess);
        }

        public Digit secret() {
            return secret;
        }

        public Digit guess() {
            return guess;
        }
    }
}