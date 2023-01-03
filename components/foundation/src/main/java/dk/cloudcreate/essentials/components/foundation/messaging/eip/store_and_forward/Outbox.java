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

package dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward;

import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;

import java.util.function.Consumer;

public interface Outbox<MESSAGE_TYPE> {
    /**
     * Start consuming messages from the Outbox using the provided message consumer.<br>
     * Only needs to be called if the instance was created without a message consumer
     *
     * @param messageConsumer the message consumer
     * @return
     */
    Outbox<MESSAGE_TYPE> consume(Consumer<MESSAGE_TYPE> messageConsumer);

    /**
     * Stop consuming messages from the {@link Outbox}. Calling this method will remove the message consumer
     * and to resume message consumption you need to call {@link #consume(Consumer)}
     */
    void stopConsuming();

    /**
     * Has the instance been created with a Message consumer or has {@link #consume(Consumer)} been called
     *
     * @return Has the instance been created with a Message consumer or has {@link #consume(Consumer)} been called
     */
    boolean hasAMessageConsumer();

    /**
     * Is the provided Message consumer consuming messages from the {@link Outbox}
     *
     * @return Is the provided Message consumer consuming messages from the {@link Outbox}
     */
    boolean isConsumingMessages();

    /**
     * The name of the outbox
     *
     * @return the name of the outbox
     */
    OutboxName name();

    /**
     * Send a message asynchronously.<br>
     * This message will be stored durably (without any duplication check) in connection with the currently active {@link UnitOfWork} (or a new {@link UnitOfWork} will be created in case no there isn't an active {@link UnitOfWork}).<br>
     * The message will be delivered asynchronously to the message consumer
     *
     * @param message the message
     */
    void sendMessage(MESSAGE_TYPE message);

    /**
     * Get the number of message in the outbox that haven't been sent yet
     *
     * @return Get the number of message in the outbox that haven't been sent yet
     */
    long getNumberOfOutgoingMessages();
}
