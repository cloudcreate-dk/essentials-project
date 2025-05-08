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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.processor;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.trustworks.essentials.components.foundation.fencedlock.FencedLockManager;
import dk.trustworks.essentials.components.foundation.messaging.eip.store_and_forward.MessageHandlerInterceptor;
import dk.trustworks.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.trustworks.essentials.components.foundation.reactive.command.DurableLocalCommandBus;

import java.util.List;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * A container class that encapsulates dependencies for the {@link ViewEventProcessor}.
 */
public record ViewEventProcessorDependencies(
        EventStoreSubscriptionManager eventStoreSubscriptionManager,
        FencedLockManager fencedLockManager,
        DurableQueues durableQueues,
        DurableLocalCommandBus commandBus,
        List<MessageHandlerInterceptor> messageHandlerInterceptors
) {

    /**
     * Creates and returns a new instance of {@link ViewEventProcessorDependenciesBuilder}.
     * This builder is used to construct instances of {@link ViewEventProcessorDependencies}
     * by allowing the configuration of its dependencies through a fluent API.
     *
     * @return a new instance of {@link ViewEventProcessorDependenciesBuilder}
     */
    public static ViewEventProcessorDependenciesBuilder builder() {
        return new ViewEventProcessorDependenciesBuilder();
    }

    /**
     * Constructs an instance of {@code ViewEventProcessorDependencies} with the provided dependencies.
     *
     * @param eventStoreSubscriptionManager the subscription manager for the event store, used to handle event store subscriptions
     * @param fencedLockManager             the manager for coordinating distributed locks
     * @param durableQueues                 the durable queues used for managing reliable message processing
     * @param commandBus                    the command bus for sending and delivering commands
     * @param messageHandlerInterceptors    the list of interceptors applied to message handlers
     */
    public ViewEventProcessorDependencies(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                          FencedLockManager fencedLockManager,
                                          DurableQueues durableQueues,
                                          DurableLocalCommandBus commandBus,
                                          List<MessageHandlerInterceptor> messageHandlerInterceptors) {
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "No eventStoreSubscriptionManager provided");
        this.fencedLockManager = requireNonNull(fencedLockManager, "No fencedLockManager provided");
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues provided");
        this.commandBus = requireNonNull(commandBus, "No commandBus provided");
        this.messageHandlerInterceptors = requireNonNull(messageHandlerInterceptors, "No messageHandlerInterceptors provided");
    }
}
