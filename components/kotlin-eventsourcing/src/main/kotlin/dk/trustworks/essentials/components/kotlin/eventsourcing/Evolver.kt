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

package dk.trustworks.essentials.components.kotlin.eventsourcing

/**
 * A [Decider] or View related interface, which can apply `EVENT`<(s) to a *aggregate/projection/view* `STATE` instance
 *
 * @param EVENT The type of Events that can be applied in the [.applyEvent]
 * @param STATE The type of *aggregate/projection/view* `STATE` that [.applyEvent] supports
 */
fun interface Evolver<EVENT, STATE> {
    /**
     * Apply the `EVENT` to the *aggregate/projection/view* `STATE` instance<br></br>
     * **Note: This method is called `evolve` in the decider pattern**<br></br>
     *
     * @param event the `EVENT` to be applied / projected onto the current *aggregate/projection/view* `STATE`
     * @param state the current `STATE` of the *aggregate/projection/view*
     * @return the new *aggregate/projection/view* `STATE` (after the `EVENT` has been applied / projected onto the current *aggregate/projection/view* `STATE`)
     */
    fun applyEvent(event: EVENT, state: STATE?): STATE?

    companion object {
        /**
         * Perform a left-fold over the `eventStream` using the `initialState` as the initial state<br></br>
         *
         * @param stateEvolver the state evolver (that applies events to the state)
         * @param initialState the initial state provided to the state evolver
         * @param eventStream  the stream of Events supplied one by one (in-order) to the state evolver
         * @param EVENT      The type of Events that can be applied in the [.applyEvent]
         * @param STATE      The type of *aggregate/projection/view* `STATE` that [.applyEvent] supports
         * @return the initial state with all events applied to it
         */
        fun <STATE, EVENT> applyEvents(
            stateEvolver: Evolver<EVENT, STATE>,
            initialState: STATE?,
            eventStream: List<EVENT>
        ): STATE {
            return eventStream
                .fold(initialState)
                    { currentState, event ->
                        stateEvolver.applyEvent(
                            event,
                            currentState
                        )
                    }!!
        }
    }
}