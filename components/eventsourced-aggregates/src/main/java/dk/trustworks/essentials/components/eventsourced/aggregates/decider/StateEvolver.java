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

import java.util.stream.Stream;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * A {@link Decider} or {@link View} related interface, which can apply <code>EVENT</code><(s) to a <i>aggregate/projection/view</i> <code>STATE</code> instance
 *
 * @param <EVENT> The type of Events that can be applied in the {@link #applyEvent(Object, Object)}
 * @param <STATE> The type of <i>aggregate/projection/view</i> <code>STATE</code> that {@link #applyEvent(Object, Object)} supports
 */
@FunctionalInterface
public interface StateEvolver<EVENT, STATE> {
    /**
     * Apply the <code>EVENT</code> to the <i>aggregate/projection/view</i> <code>STATE</code> instance<br>
     * <b>Note: This method is called <code>evolve</code> in the decider pattern</b><br>
     *
     * @param event the <code>EVENT</code> to be applied / projected onto the current <i>aggregate/projection/view</i> <code>STATE</code>
     * @param state the current <code>STATE</code> of the <i>aggregate/projection/view</i>
     * @return the new <i>aggregate/projection/view</i> <code>STATE</code> (after the <code>EVENT</code> has been applied / projected onto the current <i>aggregate/projection/view</i> <code>STATE</code>)
     */
    STATE applyEvent(EVENT event, STATE state);

    /**
     * Perform a left-fold over the <code>eventStream</code> using the <code>initialState</code> as the initial state<br>
     *
     * @param stateEvolver the state evolver (that applies events to the state)
     * @param initialState the initial state provided to the state evolver
     * @param eventStream  the stream of Events supplied one by one (in-order) to the state evolver
     * @param <EVENT>      The type of Events that can be applied in the {@link #applyEvent(Object, Object)}
     * @param <STATE>      The type of <i>aggregate/projection/view</i> <code>STATE</code> that {@link #applyEvent(Object, Object)} supports
     * @return the initial state with all events applied to it
     */
    static <STATE, EVENT> STATE applyEvents(StateEvolver<EVENT, STATE> stateEvolver,
                                            STATE initialState,
                                            Stream<EVENT> eventStream) {
        requireNonNull(initialState, "No initialState provided");
        return requireNonNull(eventStream, "No eventStream provided")
                .reduce(initialState,
                        (state, event) -> stateEvolver.applyEvent(event, state),
                        (deltaState, deltaState2) -> deltaState2);
    }
}
