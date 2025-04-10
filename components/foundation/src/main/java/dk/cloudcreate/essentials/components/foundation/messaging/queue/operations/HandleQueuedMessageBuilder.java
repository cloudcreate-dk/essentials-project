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

/**
 * Builder for {@link HandleQueuedMessage}
 */
public final class HandleQueuedMessageBuilder {
    /**
     * The {@link QueuedMessage} ready for being handled by the {@link #setMessageHandler(QueuedMessageHandler)}
     */
    private QueuedMessage        message;
    /**
     * The {@link QueuedMessageHandler} configured in the {@link DurableQueues#consumeFromQueue(ConsumeFromQueue)} operation
     */
    private QueuedMessageHandler messageHandler;

    /**
     * @param message The {@link QueuedMessage} ready for being handled by the {@link #setMessageHandler(QueuedMessageHandler)}
     * @return this builder instance
     */
    public HandleQueuedMessageBuilder setMessage(QueuedMessage message) {
        this.message = message;
        return this;
    }

    /**
     * @param messageHandler The {@link QueuedMessageHandler} configured in the {@link DurableQueues#consumeFromQueue(ConsumeFromQueue)} operation
     * @return this builder instance
     */
    public HandleQueuedMessageBuilder setMessageHandler(QueuedMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        return this;
    }

    /**
     * Build an {@link HandleQueuedMessage} instance from the builder properties
     *
     * @return the {@link HandleQueuedMessage} instance
     */
    public HandleQueuedMessage build() {
        return new HandleQueuedMessage(message, messageHandler);
    }
}