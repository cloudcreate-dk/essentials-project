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

import java.util.Optional;

/**
 * Interface responsible for resolve the optional aggregate id associated with the type <code>T</code><br>
 * Example of usages:<br>
 * <ul>
 *     <li><b>Command</b> - return the aggregate id associated with the command (can return an {@link Optional#empty()})
 *     in case server generated id's are used for commands that create a new Aggregate instance)</li>
 *     <li><b>Event</b> - return the aggregate id associated with the event</li>
 *     <li><b>State</b> -  return the aggregate id associated with the aggregate state event projection</li>
 *     <li><b>View</b> -  return the aggregate id associated with the View event projection</li>
 * </ul>
 *
 * @param <T>  The type of object that this resolver support. Could e.g. be a <code>COMMAND</code>, <code>EVENT</code>, Aggregate <code>STATE</code>, <code>VIEW</code>, etc.
 * @param <ID> The type of Aggregate Id
 */
public interface AggregateIdResolver<T, ID> {
    /**
     * Resolve the optional aggregate id associated with the type <code>T</code><br>
     * Example of usages:<br>
     * <ul>
     *     <li><b>Command</b> - return the aggregate id associated with the command (can return an {@link Optional#empty()})
     *     in case server generated id's are used for commands that create a new Aggregate instance)</li>
     *     <li><b>Event</b> - return the aggregate id associated with the event</li>
     *     <li><b>State</b> -  return the aggregate id associated with the aggregate state event projection</li>
     *     <li><b>View</b> -  return the aggregate id associated with the View event projection</li>
     * </ul>
     *
     * @param t the instance of T
     * @return if the <code>t</code> contains an aggregate id then it MUST be returned
     * in an {@link Optional#of(Object)} otherwise an {@link Optional#empty()}
     */
    Optional<ID> resolveFrom(T t);
}
