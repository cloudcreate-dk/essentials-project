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

import dk.cloudcreate.essentials.shared.functional.tuple.Either;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Captures that result of calling a {@link Handler#handle(Object, Object)} for a specific <code>COMMAND</code> and aggregate <code>STATE</code><br>
 * Concrete instances can either be of type {@link Success} or type {@link Error}
 *
 * @param <EVENT> The type of Events that can be returned by {@link Handler#handle(Object, Object)} and applied in the {@link StateEvolver#applyEvent(Object, Object)}
 * @param <ERROR> The type of Error that can be returned by the {@link Handler#handle(Object, Object)} method
 */
public sealed interface HandlerResult<ERROR, EVENT> {
    /**
     * Did the command handling result in an error?
     *
     * @return Did the command handling result in an error?
     */
    boolean isError();

    /**
     * Was the result of the command handling a success?
     *
     * @return Was the result of the command handling a success?
     */
    default boolean isSuccess() {
        return !isError();
    }

    /**
     * Convert the {@link HandlerResult} to a {@link Success}
     *
     * @return the {@link HandlerResult} converted to a {@link Success}
     * @throws IllegalStateException if the {@link HandlerResult} is an {@link Error}
     */
    default Success<ERROR, EVENT> asSuccess() {
        if (isSuccess()) {
            return (Success<ERROR, EVENT>) this;
        } else {
            throw new IllegalStateException(msg("Can't convert an error to a success. Error: {}", asError()));
        }
    }

    /**
     * Convert the {@link HandlerResult} to an {@link Error}
     *
     * @return the {@link HandlerResult} converted to an {@link Error}
     * @throws IllegalStateException if the {@link HandlerResult} is an {@link Success}
     */
    default Error<ERROR, EVENT> asError() {
        if (isError()) {
            return (Error<ERROR, EVENT>) this;
        } else {
            throw new IllegalStateException(msg("Can't convert an success to an error. Success: {}", asSuccess()));
        }
    }


    // ---- Test related methods ----

    /**
     * Test oriented method that verify that the {@link HandlerResult}
     * {@link #isSuccess()} and the {@link Success#events()} contains exactly same events as the <code>events</code> parameter
     *
     * @param events the events that we want to verify the {@link Success#events()} contains
     * @throws IllegalStateException in case the expectations aren't met
     */
    default void shouldSucceedWith(EVENT... events) {
        shouldSucceedWith(List.of(events));
    }

    /**
     * Test oriented method that verify that the {@link HandlerResult}
     * {@link #isSuccess()} and the {@link Success#events()} contains exactly same events as the <code>events</code> parameter
     *
     * @param events the events that we want to verify the {@link Success#events()} contains
     * @throws IllegalStateException in case the expectations aren't met
     */
    default void shouldSucceedWith(List<EVENT> events) {
        if (isError()) {
            throw new IllegalStateException(msg("Expected a success, but was a failure: {}", asError().error));
        }
        if (!Objects.equals(asSuccess().events, events)) {
            throw new IllegalStateException(msg("Expected events {}, but received events: {}", asSuccess().events, events));
        }
    }

    /**
     * Test oriented method that verify that the {@link HandlerResult}
     * {@link #isError()}  and the {@link Error#error()} is exactly same as the <code>error</code> parameter
     *
     * @param error the error that we want to verify is the same as the {@link Error#error()}
     * @throws IllegalStateException in case the expectations aren't met
     */
    default void shouldFailWith(ERROR error) {
        if (isSuccess()) {
            throw new IllegalStateException(msg("Expected a failure, but was a success: {}", asSuccess().events));
        }
        if (!Objects.equals(asError().error, error)) {
            throw new IllegalStateException(msg("Expected error {}, but received error: {}", asError().error, error));
        }
    }

    /**
     * Error variant of the {@link HandlerResult}
     *
     * @param error   the error (non-null) that the {@link Handler#handle(Object, Object)} resulted in
     * @param <ERROR> the type of Error supported by the {@link Handler}
     * @param <EVENT> the type of Events supported by the {@link Handler}
     */
    record Error<ERROR, EVENT>(ERROR error) implements HandlerResult<ERROR, EVENT> {
        public Error {
            requireNonNull(error, "No error provided");
        }

        @Override
        public boolean isError() {
            return true;
        }
    }

    /**
     * Success variant of the {@link HandlerResult}
     *
     * @param events  the (non-null) events that the {@link Handler#handle(Object, Object)} resulted in. Is allowed to be empty
     * @param <ERROR> the type of Error supported by the {@link Handler}
     * @param <EVENT> the type of Events supported by the {@link Handler}
     */
    record Success<ERROR, EVENT>(List<EVENT> events) implements HandlerResult<ERROR, EVENT> {
        public Success {
            requireNonNull(events, "events list was null");
        }

        public Success(EVENT... events) {
            this(List.of(events));
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    /**
     * Factory method for returning an <code>ERROR</code> from {@link Handler#handle(Object, Object)}
     *
     * @param error   the error to return
     * @param <EVENT> The type of Events that can be returned by {@link Handler#handle(Object, Object)} and applied in the {@link StateEvolver#applyEvent(Object, Object)}
     * @param <ERROR> The type of Error that can be returned by the {@link Handler#handle(Object, Object)} method
     * @return An {@link Either} with {@link Either#_1} containing the <code>error</code>
     */
    static <EVENT, ERROR> HandlerResult.Error<ERROR, EVENT> error(ERROR error) {
        requireNonNull(error, "No error provided");
        return new HandlerResult.Error<>(error);
    }

    /**
     * Factory method for returning a list of <code>EVENT</code>'s from {@link Handler#handle(Object, Object)}
     *
     * @param events  the events to return (is allowed to be empty)
     * @param <EVENT> The type of Events that can be returned by {@link Handler#handle(Object, Object)} and applied in the {@link StateEvolver#applyEvent(Object, Object)}
     * @param <ERROR> The type of Error that can be returned by the {@link Handler#handle(Object, Object)} method
     * @return An {@link Either} with {@link Either#_2} containing a list of <code>events</code>
     */
    static <EVENT, ERROR> HandlerResult.Success<ERROR, EVENT> events(EVENT... events) {
        return new HandlerResult.Success<>(events);
    }

    /**
     * Factory method for returning a list of <code>EVENT</code>'s from {@link Handler#handle(Object, Object)}
     *
     * @param events  the events to return (is allowed to be empty)
     * @param <EVENT> The type of Events that can be returned by {@link Handler#handle(Object, Object)} and applied in the {@link StateEvolver#applyEvent(Object, Object)}
     * @param <ERROR> The type of Error that can be returned by the {@link Handler#handle(Object, Object)} method
     * @return An {@link Either} with {@link Either#_2} containing a list of <code>events</code>
     */
    static <EVENT, ERROR> HandlerResult.Success<ERROR, EVENT> events(List<EVENT> events) {
        return new HandlerResult.Success<>(events);
    }
}
