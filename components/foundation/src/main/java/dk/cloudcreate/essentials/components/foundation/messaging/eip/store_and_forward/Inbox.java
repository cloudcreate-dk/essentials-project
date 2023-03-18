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

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;

import java.util.function.Consumer;

public interface Inbox {
    /**
     * Start consuming messages from the Outbox using the provided message consumer.<br>
     * Only needs to be called if the instance was created without a message consumer
     * <p>
     * If the message is delivered via an {@link Inbox} using a {@link FencedLock}
     * (such as {@link Inboxes#durableQueueBasedInboxes(DurableQueues, FencedLockManager)})
     * to coordinate message consumption, then you can find the {@link FencedLock#getCurrentToken()}
     * of the consumer in the {@link Message#getMetaData()} under key {@link MessageMetaData#FENCED_LOCK_TOKEN}
     *
     * @param messageConsumer the message consumer
     * @return this
     */
    Inbox consume(Consumer<Message> messageConsumer);

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
     * The name of the inbox
     *
     * @return the name of the inbox
     */
    InboxName name();


    /**
     * Register or add a message (with meta-data) that has been received<br>
     * This message will be stored durably (without any duplication check) in connection with the currently active {@link UnitOfWork} (or a new {@link UnitOfWork} will be created in case no there isn't an active {@link UnitOfWork}).<br>
     * The message will be delivered asynchronously to the message consumer
     *
     * @param payload  the message payload
     * @param metaData the message meta-data
     */
    default void addMessageReceived(Object payload, MessageMetaData metaData) {
        addMessageReceived(new Message(payload, metaData));
    }

    /**
     * Register or add a message (without meta-data) that has been received<br>
     * This message will be stored durably (without any duplication check) in connection with the currently active {@link UnitOfWork} (or a new {@link UnitOfWork} will be created in case no there isn't an active {@link UnitOfWork}).<br>
     * The message will be delivered asynchronously to the message consumer
     *
     * @param payload the message payload
     */
    default void addMessageReceived(Object payload) {
        addMessageReceived(new Message(payload));
    }

    /**
     * Register or add a message that has been received<br>
     * This message will be stored durably (without any duplication check) in connection with the currently active {@link UnitOfWork} (or a new {@link UnitOfWork} will be created in case no there isn't an active {@link UnitOfWork}).<br>
     * The message will be delivered asynchronously to the message consumer
     *
     * @param message the message
     */
    void addMessageReceived(Message message);

    /**
     * Get the number of message received that haven't been processed yet (or successfully processed) by the message consumer
     *
     * @return the number of message received that haven't been processed yet (or successfully processed) by the message consumer
     */
    long getNumberOfUndeliveredMessages();
}
