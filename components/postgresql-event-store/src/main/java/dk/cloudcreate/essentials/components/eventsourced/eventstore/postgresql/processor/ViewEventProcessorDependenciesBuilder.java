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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLockManager;
import dk.cloudcreate.essentials.components.foundation.messaging.MessageHandler;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.MessageHandlerInterceptor;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.cloudcreate.essentials.reactive.Handler;
import dk.cloudcreate.essentials.reactive.command.*;

import java.util.List;

/**
 * Builder class for constructing instances of {@link ViewEventProcessorDependencies}.
 * This class helps in managing the dependencies required by the {@link ViewEventProcessorDependencies}
 * by providing a fluent API to set each dependency.
 * <p>
 * The builder ensures that the dependencies are set up before creating the
 * {@link ViewEventProcessorDependencies} instance using the {@code build()} method.
 */
public class ViewEventProcessorDependenciesBuilder {
    private EventStoreSubscriptionManager   eventStoreSubscriptionManager;
    private FencedLockManager               fencedLockManager;
    private DurableQueues                   durableQueues;
    private DurableLocalCommandBus          commandBus;
    private List<MessageHandlerInterceptor> messageHandlerInterceptors = List.of();

    /**
     * @param eventStoreSubscriptionManager The {@link EventStoreSubscriptionManager} used for managing {@link EventStore} subscriptions<br>
     *                                      The  {@link EventStore} instance associated with the {@link EventStoreSubscriptionManager} is used to only query references to
     *                                      the {@link PersistedEvent}. Before an event reference message is forwarded to the corresponding {@link MessageHandler} we load the {@link PersistedEvent}'s
     * @return this builder instance
     */
    public ViewEventProcessorDependenciesBuilder setEventStoreSubscriptionManager(EventStoreSubscriptionManager eventStoreSubscriptionManager) {
        this.eventStoreSubscriptionManager = eventStoreSubscriptionManager;
        return this;
    }

    /**
     * @param fencedLockManager the manager providing mechanisms for distributed lock handling
     * @return this builder instance
     */
    public ViewEventProcessorDependenciesBuilder setFencedLockManager(FencedLockManager fencedLockManager) {
        this.fencedLockManager = fencedLockManager;
        return this;
    }

    /**
     * @param durableQueues a collection of durable queues used for reliable message processing
     * @return this builder instance
     */
    public ViewEventProcessorDependenciesBuilder setDurableQueues(DurableQueues durableQueues) {
        this.durableQueues = durableQueues;
        return this;
    }

    /**
     * @param commandBus The {@link CommandBus} where any {@link Handler} or {@link CmdHandler} annotated methods in the subclass of the {@link ViewEventProcessor} will be registered
     * @return this builder instance
     */
    public ViewEventProcessorDependenciesBuilder setCommandBus(DurableLocalCommandBus commandBus) {
        this.commandBus = commandBus;
        return this;
    }

    /**
     * @param messageHandlerInterceptors a list of interceptors that can modify or monitor message handling processes
     * @return this builder instance
     */
    public ViewEventProcessorDependenciesBuilder setMessageHandlerInterceptors(List<MessageHandlerInterceptor> messageHandlerInterceptors) {
        this.messageHandlerInterceptors = messageHandlerInterceptors;
        return this;
    }

    /**
     * Builds and returns a new {@link ViewEventProcessorDependencies} instance
     * using the configured dependencies.
     *
     * @return an instance of {@link ViewEventProcessorDependencies},
     *         containing the configured components required for processing view events
     */
    public ViewEventProcessorDependencies build() {
        return new ViewEventProcessorDependencies(eventStoreSubscriptionManager, fencedLockManager, durableQueues, commandBus, messageHandlerInterceptors);
    }
}
