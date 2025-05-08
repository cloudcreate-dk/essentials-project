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

package dk.trustworks.essentials.components.eventsourced.aggregates.decider;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Variant of the event sourced Decider pattern, which supports building an Aggregate <code>STATE</code> based on previous <code>EVENT</code>'s that relate to the
 * aggregate instance, and which can handle <code>COMMAND</code>'s, whose side effect is either an <code>ERROR</code> or a List of <code>EVENT</code>'s (can be an empty list)
 *
 * @param <COMMAND> The type of Commands that the {@link #handle(Object, Object)} can process
 * @param <EVENT>   The type of Events that can be returned by {@link #handle(Object, Object)} and applied in the {@link #applyEvent(Object, Object)}
 * @param <ERROR>   The type of Error that can be returned by the {@link #handle(Object, Object)} method
 * @param <STATE>   The type of Aggregate State that this {@link Decider} works with in {@link #handle(Object, Object)}, {@link #initialState()}, {@link #applyEvent(Object, Object)} and {@link #isFinal(Object)}
 */
public interface Decider<COMMAND, EVENT, ERROR, STATE> extends Handler<COMMAND, EVENT, ERROR, STATE>, StateEvolver<EVENT, STATE>, InitialStateProvider<STATE>, IsStateFinalResolver<STATE> {
    /**
     * Factory method for creating a {@link Decider} instance from instances of the various {@link Decider} related interfaces
     *
     * @param handler                   A {@link Decider} related interface that is responsible for handling <code>COMMAND</code>(s)
     * @param initialStateProvider      A {@link Decider} related interface, which provides the <b>Initial</b> <code>STATE</code> for a given  <i>aggregate</i>.
     * @param stateEvolver              A {@link Decider} related interface, which can apply <code>EVENT</code><(s) to an <i>aggregate/i>'s <code>STATE</code> instance
     * @param stateIsStateFinalResolver A {@link Decider} related interface that resolves if the aggregate's state is final and no more changes can occur, i.e. no more events can be persisted
     * @param <COMMAND>                 The type of Commands that the {@link #handle(Object, Object)} can process
     * @param <EVENT>                   The type of Events that can be returned by {@link #handle(Object, Object)} and applied in the {@link #applyEvent(Object, Object)}
     * @param <ERROR>                   The type of Error that can be returned by the {@link #handle(Object, Object)} method
     * @param <STATE>                   The type of Aggregate State that this {@link Decider} works with in {@link #handle(Object, Object)}, {@link #initialState()}, {@link #applyEvent(Object, Object)} and {@link #isFinal(Object)}
     * @return a {@link Decider} instance that delegates to the {@link Decider} related instances provided as parameters
     */
    static <COMMAND, EVENT, ERROR, STATE> Decider<COMMAND, EVENT, ERROR, STATE> decider(Handler<COMMAND, EVENT, ERROR, STATE> handler,
                                                                                        InitialStateProvider<STATE> initialStateProvider,
                                                                                        StateEvolver<EVENT, STATE> stateEvolver,
                                                                                        IsStateFinalResolver<STATE> stateIsStateFinalResolver) {
        requireNonNull(handler, "No handler provided");
        requireNonNull(initialStateProvider, "No initialStateProvider provided");
        requireNonNull(stateEvolver, "No stateEvolver provided");
        requireNonNull(stateIsStateFinalResolver, "No stateIsStateFinalResolver provided");
        return new Decider<>() {
            @Override
            public STATE applyEvent(EVENT event, STATE state) {
                return stateEvolver.applyEvent(event, state);
            }

            @Override
            public STATE initialState() {
                return initialStateProvider.initialState();
            }

            @Override
            public HandlerResult<ERROR, EVENT> handle(COMMAND cmd, STATE state) {
                return handler.handle(cmd, state);
            }

            @Override
            public boolean isFinal(STATE state) {
                return stateIsStateFinalResolver.isFinal(state);
            }
        };
    }

    /**
     * Return true from this method IF the aggregate's state is final and no more changes can occur, i.e. no more events can be persisted (i.e. {@link Handler#handle(Object, Object)} will return an <code>ERROR</code>)<br>
     * <b>Note: This method is called <code>isTerminal</code> in the decider pattern</b><br>
     * See {@link IsStateFinalResolver#isFinal(Object)} for more details.<br>
     * <b>The default implementation in the {@link Decider} always return <code>false</code></b>
     *
     * @param state the current <code>STATE</code> of the aggregate
     * @return true IF the aggregate's state is final, otherwise false
     */
    @Override
    default boolean isFinal(STATE state) {
        return false;
    }
}
