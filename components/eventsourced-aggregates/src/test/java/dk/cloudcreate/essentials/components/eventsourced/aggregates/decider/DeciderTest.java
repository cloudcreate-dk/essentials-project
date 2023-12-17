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
    public sealed interface GuessingGameState extends Handler<GuessingGameCommand, GuessingGameEvent, GuessingGameError, GuessingGameState>, StateEvolver<GuessingGameState, GuessingGameEvent> {
        record NotStartedGame() implements GuessingGameState {
            @Override
            public HandlerResult<GuessingGameError, GuessingGameEvent> handle(GuessingGameCommand cmd, GuessingGameState game) {
                if (cmd instanceof GuessingGameCommand.JoinGame joinGame) {
                    return events(new GuessingGameEvent.GameStarted(joinGame.gameId,
                                                                    joinGame.secret,
                                                                    joinGame.maxAttempts,
                                                                    joinGame.allowedDigits));
                }
                return error(new GuessingGameError.GuessError.GameNotStarted(cmd.gameId()));
            }

            @Override
            public GuessingGameState applyEvent(GuessingGameState game, GuessingGameEvent gameEvent) {
                if (gameEvent instanceof GuessingGameEvent.GameStarted gameStarted) {
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
                              .toList();
            }

            @Override
            public HandlerResult<GuessingGameError, GuessingGameEvent> handle(GuessingGameCommand cmd, GuessingGameState game) {
                if (cmd instanceof GuessingGameCommand.MakeGuess makeGuess) {
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

        record GameWonState(Secret secret, int maxAttempts, int attempts) implements GuessingGameState {

            @Override
            public HandlerResult<GuessingGameError, GuessingGameEvent> handle(GuessingGameCommand cmd, GuessingGameState game) {
                return error(new GuessingGameError.GameFinishedError.GameAlreadyWon(cmd.gameId()));
            }

            @Override
            public GuessingGameState applyEvent(GuessingGameState game, GuessingGameEvent gameEvent) {
                return game;
            }
        }

        record GameLostState(Secret secret, int maxAttempts) implements GuessingGameState {
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
    public sealed interface GuessingGameCommand {
        GuessingGameId gameId();

        record JoinGame(GuessingGameId gameId,
                        Secret secret,
                        int maxAttempts,
                        Set<Digit> allowedDigits) implements GuessingGameCommand {

            public JoinGame {
                requireNonNull(gameId, "No gameId provided");
                requireNonNull(secret, "No secret provided");
                requireTrue(maxAttempts > 0, "You must specify > 0 maxAttempts");
                requireNonEmpty(allowedDigits, "No allowedDigits provided");
            }
        }

        record MakeGuess(GuessingGameId gameId,
                         Guess guess) implements GuessingGameCommand {

            public MakeGuess {
                requireNonNull(gameId, "No gameId provided");
                requireNonNull(guess, "No guess provided");
            }
        }
    }

    // -------------------------- Events --------------------------
    public sealed interface GuessingGameEvent {
        GuessingGameId gameId();

        record GameStarted(GuessingGameId gameId,
                           Secret secret,
                           int maxAttempts,
                           Set<Digit> allowedDigits) implements GuessingGameEvent {
        }

        record GuessMade(GuessingGameId gameId,
                         Guess guess,
                         int attempt) implements GuessingGameEvent {
        }

        record GameWon(GuessingGameId gameId, int numberOfAttempts) implements GuessingGameEvent {
        }

        record GameLost(GuessingGameId gameId, int maxAttempts) implements GuessingGameEvent {
        }

    }

    // -------------------------- Errors --------------------------
    public sealed interface GuessingGameError {
        GuessingGameId gameId();

        sealed interface GameFinishedError extends GuessingGameError {
            record GameAlreadyWon(GuessingGameId gameId) implements GameFinishedError {
            }

            record GameAlreadyLost(GuessingGameId gameId) implements GameFinishedError {
            }
        }

        sealed interface GuessError extends GuessingGameError {
            record GameNotStarted(GuessingGameId gameId) implements GuessError {
            }

            record GuessTooShort(GuessingGameId gameId,
                                 Guess guess,
                                 int requiredLength) implements GuessError {
            }

            record GuessTooLong(GuessingGameId gameId,
                                Guess guess,
                                int requiredLength) implements GuessError {
            }

            record InvalidDigitInGuess(GuessingGameId gameId,
                                       Guess guess,
                                       Set<Digit> allowedDigits) implements GuessError {
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

    public record Guess(List<Digit> digits) {
        public Guess {
            requireNonEmpty(digits, "No digits provided");
        }

        public Guess(int... digits) {
            this(Arrays.stream(digits).mapToObj(Digit::of).toList());
        }

        public Guess(Digit... digits) {
            this(Arrays.asList(digits));
        }

        public int length() {
            return digits.size();
        }
    }

    public record Secret(List<Digit> digits) {
        public Secret {
            requireNonEmpty(digits, "No digits provided");
        }

        public Secret(int... digits) {
            this(Arrays.stream(digits).mapToObj(Digit::of).toList());
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
    }

    public static class Digit extends IntegerType<Digit> {
        protected Digit(Integer value) {
            super(value);
        }


        public static Digit of(Integer value) {
            return new Digit(value);
        }
    }

    public record DigitPair(Digit secret, Digit guess) {
        boolean matches() {
            return secret.equals(guess);
        }
    }
}
