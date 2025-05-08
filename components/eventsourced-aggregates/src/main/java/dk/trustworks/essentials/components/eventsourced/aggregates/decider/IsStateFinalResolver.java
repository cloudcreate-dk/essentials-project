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

/**
 * {@link Decider} related interface that resolves if the aggregate's state is final and no more changes can occur, i.e. no more events can be persisted (i.e. {@link Handler#handle(Object, Object)} will return an <code>ERROR</code>)
 *
 * @param <STATE> The type of Aggregate State that this {@link Decider} works with in {@link Handler#handle(Object, Object)}, {@link InitialStateProvider#initialState()},
 *                {@link StateEvolver#applyEvent(Object, Object)} and {@link #isFinal(Object)}
 */
public interface IsStateFinalResolver<STATE> {
    /**
     * Return true from this method IF the aggregate's state is final and no more changes can occur, i.e. no more events can be persisted (i.e. {@link Handler#handle(Object, Object)} will return an <code>ERROR</code>)<br>
     * <b>Note: This method is called <code>isTerminal</code> in the decider pattern</b><br>
     *
     * @param state the current <code>STATE</code> of the aggregate
     * @return true IF the aggregate's state is final, otherwise false
     */
    boolean isFinal(STATE state);
}
