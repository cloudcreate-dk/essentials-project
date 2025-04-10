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

package dk.cloudcreate.essentials.components.foundation.events;

import dk.cloudcreate.essentials.reactive.LocalEventBus;

/**
 * Provides a shared lazily initialized, thread-safe instance of the infrastructure {@link LocalEventBus} for infrastructure events, using the <b>Initialization-on-Demand holder idiom</b>.
 *
 * <p>The <b>Initialization-on-Demand holder idiom</b> leverages the JVM's class loading mechanism to delay the creation of the
 * expensive {@link LocalEventBus} instance until it is first needed.<br>
 * The {@link LocalEventBus} returned from {@link #getInstance()} is only created when {@link InfrastructureLocalEventBus#getInstance()} is called
 */
public final class InfrastructureLocalEventBus {
    private InfrastructureLocalEventBus() {
    }

    private static class Holder {
        /**
         * Initialization-on-Demand holder idiom.<br>
         * <p>The <b>Initialization-on-Demand holder idiom</b> leverages the JVM's class loading mechanism to delay the creation of the
         * expensive {@link LocalEventBus} instance until it is first needed. The inner static
         * {@link Holder} class is not loaded until the {@link InfrastructureLocalEventBus#getInstance()} method is invoked,
         * ensuring thread safety and eliminating the need for explicit synchronization.
         */
        private static final LocalEventBus INSTANCE = new LocalEventBus("Infrastructure",
                                                                        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                                                                        128);
    }

    /**
     * Provides a lazily initialized, thread-safe instance of the infrastructure {@link LocalEventBus} using <b>Initialization-on-Demand holder idiom</b>.
     *
     * <p>The <b>Initialization-on-Demand holder idiom</b> leverages the JVM's class loading mechanism to delay the creation of the
     * expensive {@link LocalEventBus} instance until it is first needed.<br>
     * The {@link LocalEventBus} returned from this method is only created when {@link InfrastructureLocalEventBus#getInstance()} is called
     *
     * @return The shared {@link LocalEventBus} for infrastructure events
     */
    public static LocalEventBus getInstance() {
        return Holder.INSTANCE;
    }
}
