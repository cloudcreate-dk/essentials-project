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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Called by the {@link DurableQueueConsumer} when a {@link QueuedMessage} has been selected
 * for processing.
 * Operation also matches {@link DurableQueuesInterceptor#intercept(HandleQueuedMessage, InterceptorChain)}
 */
public final class HandleQueuedMessage {
    /**
     * The {@link QueuedMessage} ready for being handled by the {@link #messageHandler}
     */
    public final QueuedMessage        message;
    /**
     * The {@link QueuedMessageHandler} configured in the {@link DurableQueues#consumeFromQueue(ConsumeFromQueue)} operation
     */
    public final QueuedMessageHandler messageHandler;

    /**
     * Create a new builder that produces a new {@link HandleQueuedMessage} instance
     *
     * @return a new {@link HandleQueuedMessageBuilder} instance
     */
    public static HandleQueuedMessageBuilder builder() {
        return new HandleQueuedMessageBuilder();
    }

    /**
     * Called by the {@link DurableQueueConsumer} when a {@link QueuedMessage} has been selected
     * for processing.
     *
     * @param message        The {@link QueuedMessage} ready for being handled by the {@link #messageHandler}
     * @param messageHandler The {@link QueuedMessageHandler} configured in the {@link DurableQueues#consumeFromQueue(ConsumeFromQueue)} operation
     */
    public HandleQueuedMessage(QueuedMessage message, QueuedMessageHandler messageHandler) {
        this.message = requireNonNull(message, "No message was provided");
        this.messageHandler = requireNonNull(messageHandler, "No message handler was provided");
    }

    /**
     * The {@link QueuedMessage} ready for being handled by the {@link #messageHandler}
     *
     * @return The {@link QueuedMessage} ready for being handled by the {@link #messageHandler}
     */
    public QueuedMessage getMessage() {
        return message;
    }

    /**
     * The {@link QueuedMessageHandler} configured in the {@link DurableQueues#consumeFromQueue(ConsumeFromQueue)} operation
     *
     * @return The {@link QueuedMessageHandler} configured in the {@link DurableQueues#consumeFromQueue(ConsumeFromQueue)} operation
     */
    public QueuedMessageHandler getMessageHandler() {
        return messageHandler;
    }

    @Override
    public String toString() {
        return "HandleQueuedMessage{" +
                "message=" + message +
                ", messageHandler=" + messageHandler +
                '}';
    }
}
