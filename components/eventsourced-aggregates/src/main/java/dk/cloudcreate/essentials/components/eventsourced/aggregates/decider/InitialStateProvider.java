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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.decider;

/**
 * {@link Decider} or {@link View} related interface, which provides the <b>Initial</b> <code>STATE</code> for a given  <i>aggregate/projection/view</i>.<br>
 * The {@link InitialStateProvider} works in collaboration with the {@link StateEvolver} in the context of
 * the {@link Decider} or {@link View} pattern
 *
 * @param <STATE> The type of <i>aggregate/projection/view</i> <code>STATE</code> provided
 */
public interface InitialStateProvider<STATE> {
    /**
     * The initial <i>aggregate/projection/view</i> <code>STATE</code> before <b>any</b> <code>EVENT</code>'s have been applied / projected onto the <i>aggregate/projection/view</i> <code>STATE</code><br>
     * <i>Note: The default implementation returns a <code>null</code> <i>aggregate/projection/view</i> <code>STATE</code></i>
     *
     * @return the initial projection/view <code>STATE</code>. May be null
     */
    default STATE initialState() {
        return null;
    }
}
