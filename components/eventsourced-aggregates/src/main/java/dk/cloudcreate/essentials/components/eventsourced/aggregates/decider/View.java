/*
 * Copyright 2021-2024 the original author or authors.
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
 * Variant of the Event Sourced Projection/View concept
 *
 * @param <EVENT> The type of Events that can be applied in the {@link #applyEvent(Object, Object)}
 * @param <STATE> The type of <i>projection/view</i> <code>STATE</code> that {@link #applyEvent(Object, Object)} supports
 */
public interface View<EVENT, STATE> extends StateEvolver<EVENT, STATE>, InitialStateProvider<STATE> {

}
