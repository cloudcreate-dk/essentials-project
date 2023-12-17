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

/**
 * {@link Decider} related interface that is responsible for handling <code>COMMAND</code>(s),whose side effect is either an <code>ERROR</code> or a List of <code>EVENT</code>'s (can be an empty list)
 *
 * @param <COMMAND> The type of Commands that the {@link #handle(Object, Object)} can process
 * @param <EVENT>   The type of Events that can be returned by {@link #handle(Object, Object)} and applied in the {@link StateEvolver#applyEvent(Object, Object)}
 * @param <ERROR>   The type of Error that can be returned by the {@link #handle(Object, Object)} method
 * @param <STATE>   The type of Aggregate State that this {@link Decider} works with in {@link #handle(Object, Object)}, {@link InitialStateProvider#initialState()}, {@link StateEvolver#applyEvent(Object, Object)}
 *                  and {@link IsStateFinalResolver#isFinal(Object)}
 */
@FunctionalInterface
public interface Handler<COMMAND, EVENT, ERROR, STATE> {
    /**
     * The <code>execute</code> method is responsible for handling a <code>COMMAND</code>, which can either result
     * in an <code>ERROR</code> or a list of <code>EVENT</code>'s (can be an empty list).<br>
     * <b>Note: This method is called <code>decide</code> in the decider pattern</b><br>
     * Idempotent handling of a <code>COMMAND</code> will result in an <b>empty</b> list of <code>EVENT</code>'s
     *
     * @param cmd   the command to handle
     * @param state the state of the aggregate
     * @return either an <code>ERROR</code> or a list of <code>EVENT</code>'s.<br>
     * Idempotent handling of a <code>COMMAND</code> will result in an <b>empty</b> list of <code>EVENT</code>'s
     */
    HandlerResult<ERROR, EVENT> handle(COMMAND cmd, STATE state);
}
