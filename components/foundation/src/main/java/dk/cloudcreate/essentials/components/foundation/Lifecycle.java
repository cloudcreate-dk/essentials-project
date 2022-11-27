/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.foundation;

/**
 * Common process life cycle interface
 */
public interface Lifecycle {
    /**
     * Start the processing. This operation must be idempotent, such that duplicate calls
     * to {@link #start()} for an already started process (where {@link #isStarted()} returns true)
     * is ignored
     */
    void start();

    /**
     * Stop the processing. This operation must be idempotent, such that duplicate calls
     * to {@link #stop()} for an already stopped process (where {@link #isStarted()} returns false)
     * is ignored
     */
    void stop();

    /**
     * Returns true if the process is started
     *
     * @return true if the process is started otherwise false
     */
    boolean isStarted();
}
