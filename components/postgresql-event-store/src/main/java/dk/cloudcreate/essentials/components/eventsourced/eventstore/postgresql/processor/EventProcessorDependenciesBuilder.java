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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;

import java.util.List;

/**
 * Builder for {@link EventProcessorDependencies}, which encapsulates all the dependencies
 * required by an instance of an {@link EventProcessor}
 */
public class EventProcessorDependenciesBuilder {
    private EventStoreSubscriptionManager   eventStoreSubscriptionManager;
    private Inboxes                         inboxes;
    private DurableLocalCommandBus          commandBus;
    private List<MessageHandlerInterceptor> messageHandlerInterceptors = List.of();

    public EventProcessorDependenciesBuilder setEventStoreSubscriptionManager(EventStoreSubscriptionManager eventStoreSubscriptionManager) {
        this.eventStoreSubscriptionManager = eventStoreSubscriptionManager;
        return this;
    }

    public EventProcessorDependenciesBuilder setInboxes(Inboxes inboxes) {
        this.inboxes = inboxes;
        return this;
    }

    public EventProcessorDependenciesBuilder setCommandBus(DurableLocalCommandBus commandBus) {
        this.commandBus = commandBus;
        return this;
    }

    public EventProcessorDependenciesBuilder setMessageHandlerInterceptors(List<MessageHandlerInterceptor> messageHandlerInterceptors) {
        this.messageHandlerInterceptors = messageHandlerInterceptors;
        return this;
    }

    public EventProcessorDependencies build() {
        return new EventProcessorDependencies(eventStoreSubscriptionManager, inboxes, commandBus, messageHandlerInterceptors);
    }
}