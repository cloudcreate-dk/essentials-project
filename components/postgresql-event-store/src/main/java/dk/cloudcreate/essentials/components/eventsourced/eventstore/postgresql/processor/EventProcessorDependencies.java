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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;

import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Encapsulates all the dependencies required by an instance of an {@link EventProcessor}
 */
public class EventProcessorDependencies {
    public final EventStoreSubscriptionManager   eventStoreSubscriptionManager;
    public final Inboxes                         inboxes;
    public final DurableLocalCommandBus          commandBus;
    public final List<MessageHandlerInterceptor> messageHandlerInterceptors;

    public static EventProcessorDependenciesBuilder builder() {
        return new EventProcessorDependenciesBuilder();
    }

    public EventProcessorDependencies(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                      Inboxes inboxes,
                                      DurableLocalCommandBus commandBus,
                                      List<MessageHandlerInterceptor> messageHandlerInterceptors) {
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "No eventStoreSubscriptionManager provided");
        this.inboxes = requireNonNull(inboxes, "No inboxes provided");
        this.commandBus = requireNonNull(commandBus, "No commandBus provided");
        this.messageHandlerInterceptors = requireNonNull(messageHandlerInterceptors, "No messageHandlerInterceptors provided");
    }
}
