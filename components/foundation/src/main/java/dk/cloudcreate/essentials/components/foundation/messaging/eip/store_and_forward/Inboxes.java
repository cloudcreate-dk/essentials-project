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
import dk.cloudcreate.essentials.reactive.command.CommandBus;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link Inbox} supports the transactional Store and Forward pattern from Enterprise Integration Patterns supporting At-Least-Once delivery guarantee.<br>
 * The {@link Inbox} pattern is used to handle incoming messages from a message infrastructure (such as a Queue, Kafka, EventBus, etc). <br>
 * The message is added to the {@link Inbox} in a transaction/{@link UnitOfWork} and afterwards the message is Acknowledged (ACK) with the message infrastructure the {@link UnitOfWork} is committed.<br>
 * If the ACK fails then the message infrastructure will attempt to redeliver the message even if the {@link UnitOfWork} has been committed, since the message infrastructure and the {@link Inbox}
 * don't share the same transactional resource. This means that messages received from the message infrastructure
 * can be added more than once to the {@link Inbox}.<br>
 * After the {@link UnitOfWork} has been committed, the messages will be asynchronously delivered to the message consumer in a new {@link UnitOfWork}.<br>
 * The {@link Inbox} itself supports Message Redelivery in case the Message consumer experiences failures.<br>
 * This means that the Message consumer, registered with the {@link Inbox}, can and will receive Messages more than once and therefore its message handling has to be idempotent.
 */
public interface Inboxes {
    /**
     * Get an existing {@link Inbox} instance or create a new instance. If an existing {@link Inbox} with a matching {@link InboxName} is already
     * created then that instance is returned (irrespective of whether the redeliveryPolicy, etc. have the same values)<br>
     * Remember to call {@link Outbox#consume(Consumer)} to start consuming messages
     *
     * @param <MESSAGE_TYPE> the type of message
     * @param inboxConfig    the inbox configuration
     * @return the {@link Inbox}
     */
    <MESSAGE_TYPE> Inbox<MESSAGE_TYPE> getOrCreateInbox(InboxConfig inboxConfig);

    /**
     * Get an existing {@link Inbox} instance or create a new instance. If an existing {@link Inbox} with a matching {@link InboxName} is already
     * created then that instance is returned (irrespective of whether the redeliveryPolicy, etc. have the same values)
     *
     * @param <MESSAGE_TYPE>  the type of message
     * @param inboxConfig     the inbox configuration
     * @param messageConsumer the asynchronous message consumer
     * @return the {@link Inbox}
     */
    <MESSAGE_TYPE> Inbox<MESSAGE_TYPE> getOrCreateInbox(InboxConfig inboxConfig,
                                                        Consumer<MESSAGE_TYPE> messageConsumer);

    /**
     * Get an existing {@link Inbox} instance or create a new instance. If an existing {@link Inbox} with a matching {@link InboxName} is already
     * created then that instance is returned (irrespective of whether the redeliveryPolicy, etc. have the same values)
     *
     * @param <MESSAGE_TYPE> the type of message
     * @param inboxConfig    the inbox configuration
     * @param forwardTo      forward messages to this command bus using {@link CommandBus#send(Object)}
     * @return the {@link Inbox}
     */
    default <MESSAGE_TYPE> Inbox<MESSAGE_TYPE> getOrCreateInbox(InboxConfig inboxConfig,
                                                                CommandBus forwardTo) {
        requireNonNull(forwardTo, "No forwardTo command bus provided");
        return getOrCreateInbox(inboxConfig,
                                forwardTo::send);
    }

    /**
     * Get all the {@link Inbox} instances managed by this {@link Inboxes} instance
     *
     * @return all the {@link Inbox} instances managed by this {@link Inboxes} instance
     */
    Collection<Inbox<?>> getInboxes();

    /**
     * Create an {@link Inboxes} instance that uses a {@link DurableQueues} as its storage and message delivery mechanism.
     *
     * @param durableQueues     The {@link DurableQueues} implementation used by the {@link Inboxes} instance returned
     * @param fencedLockManager the {@link FencedLockManager} used for {@link Inbox}'s that use {@link MessageConsumptionMode#SingleGlobalConsumer}
     * @return the {@link Inboxes} instance
     */
    static Inboxes durableQueueBasedInboxes(DurableQueues durableQueues,
                                            FencedLockManager fencedLockManager) {
        return new DurableQueueBasedInboxes(durableQueues,
                                            fencedLockManager);
    }

    class DurableQueueBasedInboxes implements Inboxes {
        private final DurableQueues                      durableQueues;
        private final FencedLockManager                  fencedLockManager;
        private       ConcurrentMap<InboxName, Inbox<?>> inboxes = new ConcurrentHashMap<>();

        public DurableQueueBasedInboxes(DurableQueues durableQueues, FencedLockManager fencedLockManager) {
            this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
            this.fencedLockManager = requireNonNull(fencedLockManager, "No fencedLockManager instance provided");
        }

        @SuppressWarnings("unchecked")
        @Override
        public <MESSAGE_TYPE> Inbox<MESSAGE_TYPE> getOrCreateInbox(InboxConfig inboxConfig,
                                                                   Consumer<MESSAGE_TYPE> messageConsumer) {
            requireNonNull(inboxConfig, "No inboxConfig provided");
            return (Inbox<MESSAGE_TYPE>) inboxes.computeIfAbsent(inboxConfig.getInboxName(),
                                                                 inboxName_ -> new DurableQueueBasedInbox<>(inboxConfig,
                                                                                                            messageConsumer));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <MESSAGE_TYPE> Inbox<MESSAGE_TYPE> getOrCreateInbox(InboxConfig inboxConfig) {
            requireNonNull(inboxConfig, "No inboxConfig provided");
            return (Inbox<MESSAGE_TYPE>) inboxes.computeIfAbsent(inboxConfig.getInboxName(),
                                                                 inboxName_ -> new DurableQueueBasedInbox<>(inboxConfig));
        }

        @Override
        public Collection<Inbox<?>> getInboxes() {
            return inboxes.values();
        }

        public class DurableQueueBasedInbox<MESSAGE_TYPE> implements Inbox<MESSAGE_TYPE> {

            private      Consumer<MESSAGE_TYPE> messageConsumer;
            public final QueueName              inboxQueueName;
            public final InboxConfig            config;
            private      DurableQueueConsumer   durableQueueConsumer;

            public DurableQueueBasedInbox(InboxConfig config,
                                          Consumer<MESSAGE_TYPE> messageConsumer) {
                this(config);
                consume(messageConsumer);
            }

            public DurableQueueBasedInbox(InboxConfig config) {
                this.config = requireNonNull(config, "No inbox config provided");
                inboxQueueName = config.inboxName.asQueueName();
            }

            @Override
            public Inbox<MESSAGE_TYPE> consume(Consumer<MESSAGE_TYPE> messageConsumer) {
                if (this.messageConsumer != null) {
                    throw new IllegalStateException("Outbox already has a message consumer");
                }
                this.messageConsumer = requireNonNull(messageConsumer, "No messageConsumer provided");
                switch (config.messageConsumptionMode) {
                    case SingleGlobalConsumer:
                        fencedLockManager.acquireLockAsync(config.inboxName.asLockName(),
                                                           LockCallback.builder()
                                                                       .onLockAcquired(lock -> durableQueueConsumer = consumeFromDurableQueue())
                                                                       .onLockReleased(lock -> durableQueueConsumer.cancel())
                                                                       .build());
                        break;
                    case GlobalCompetingConsumers:
                        durableQueueConsumer = consumeFromDurableQueue();
                        break;
                    default:
                        throw new IllegalStateException("Unexpected messageConsumptionMode: " + config.messageConsumptionMode);
                }
                return this;
            }

            @Override
            public boolean hasAMessageConsumer() {
                return messageConsumer != null;
            }

            @Override
            public boolean isConsumingMessages() {
                return durableQueueConsumer != null;
            }

            @Override
            public void stopConsuming() {
                if (messageConsumer == null) return;

                switch (config.messageConsumptionMode) {
                    case SingleGlobalConsumer:
                        fencedLockManager.cancelAsyncLockAcquiring(config.inboxName.asLockName());
                        break;
                    case GlobalCompetingConsumers:
                        if (durableQueueConsumer != null) {
                            durableQueueConsumer.cancel();
                            durableQueueConsumer = null;
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unexpected messageConsumptionMode: " + config.messageConsumptionMode);
                }
                messageConsumer = null;
            }

            @Override
            public InboxName name() {
                return config.inboxName;
            }

            @Override
            public void addMessageReceived(MESSAGE_TYPE message) {
                // An Inbox is usually used to bridge receiving messages from a Messaging system
                // In these cases we rarely have other business logic that's already started a Transaction/UnitOfWork.
                // So to simplify using the Inbox we allow adding a message to start a UnitOfWork if none exists

                if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
                    // Allow addMessageReceived to automatically start a new or join in an existing UnitOfWork
                    durableQueues.getUnitOfWorkFactory().get().usingUnitOfWork(() -> {
                        durableQueues.queueMessage(inboxQueueName,
                                                   message);
                    });
                } else {
                    durableQueues.queueMessage(inboxQueueName,
                                               message);
                }
            }

            private DurableQueueConsumer consumeFromDurableQueue() {
                return durableQueues.consumeFromQueue(inboxQueueName,
                                                      config.redeliveryPolicy,
                                                      config.numberOfParallelMessageConsumers,
                                                      this::handleMessage);
            }

            @SuppressWarnings("unchecked")
            private void handleMessage(QueuedMessage queuedMessage) {
                messageConsumer.accept((MESSAGE_TYPE) queuedMessage.getPayload());
            }

            @Override
            public long getNumberOfUndeliveredMessages() {
                return durableQueues.getTotalMessagesQueuedFor(inboxQueueName);
            }

            @Override
            public String toString() {
                return "DurableQueueBasedInbox{" +
                        "config=" + config + ", " +
                        "inboxQueueName=" + inboxQueueName +
                        '}';
            }
        }
    }
}
