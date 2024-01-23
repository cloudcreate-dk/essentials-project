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
import dk.cloudcreate.essentials.reactive.EventHandler;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.MessageConsumptionMode.SingleGlobalConsumer;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link Outbox} supports the transactional Store and Forward pattern from Enterprise Integration Patterns supporting At-Least-Once delivery guarantee.<br>
 * The {@link Outbox} pattern is used to handle outgoing messages, that are created as a side effect of adding/updating an entity in a database, but where the message infrastructure
 * (such as a Queue, Kafka, EventBus, etc.) that doesn't share the same underlying transactional resource as the database.<br>
 * Instead, you need to use an {@link Outbox} that can join in the same {@link UnitOfWork}/transactional-resource
 * that the database is using.<br>
 * The message is added to the {@link Outbox} in a transaction/{@link UnitOfWork} and afterwards the {@link UnitOfWork} is committed.<br>
 * If the transaction fails then both the entity and the message will be rolled back when then {@link UnitOfWork} rolls back.<br>
 * After the {@link UnitOfWork} has been committed, the messages will be asynchronously delivered to the message consumer in a new {@link UnitOfWork}.<br>
 * The {@link Outbox} itself supports Message Redelivery in case the Message consumer experiences failures.<br>
 * This means that the Message consumer, registered with the {@link Outbox}, can and will receive Messages more than once and therefore its message handling has to be idempotent.
 * <p>
 * If you're working with {@link OrderedMessage}'s then the {@link Outbox} consumer must be configured
 * with {@link OutboxConfig#getMessageConsumptionMode()} having value {@link MessageConsumptionMode#SingleGlobalConsumer}
 * in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per {@link OrderedMessage#getKey()}
 * across as many {@link OutboxConfig#numberOfParallelMessageConsumers} as you wish to use.
 */
public interface Outboxes {
    /**
     * Get an existing {@link Outbox} instance or create a new instance. If an existing {@link Outbox} with a matching {@link OutboxName} is already
     * created then that instance is returned (irrespective of whether the redeliveryPolicy, etc. have the same values)
     *
     * @param outboxConfig    the outbox configuration
     * @param messageConsumer the asynchronous message consumer. See {@link PatternMatchingMessageHandler}
     * @return the {@link Outbox}
     */
    Outbox getOrCreateOutbox(OutboxConfig outboxConfig,
                             Consumer<Message> messageConsumer);

    /**
     * Get an existing {@link Outbox} instance or create a new instance. If an existing {@link Outbox} with a matching {@link OutboxName} is already
     * created then that instance is returned (irrespective of whether the redeliveryPolicy, etc. have the same values)<br>
     * Remember to call {@link Outbox#consume(Consumer)} to start consuming messages
     *
     * @param outboxConfig the outbox configuration
     * @return the {@link Outbox}
     */
    Outbox getOrCreateOutbox(OutboxConfig outboxConfig);

    /**
     * Get an existing {@link Outbox} instance or create a new instance that forwards to an {@link EventHandler}.<br>
     * If an existing {@link Outbox} with a matching {@link OutboxName} is already
     * created then that instance is returned (irrespective of whether the redeliveryPolicy, etc. have the same values)
     *
     * @param outboxConfig the outbox configuration
     * @param eventHandler the asynchronous event handler
     * @return the {@link Outbox}
     */
    default Outbox getOrCreateForwardingOutbox(OutboxConfig outboxConfig,
                                               EventHandler eventHandler) {
        requireNonNull(eventHandler, "No eventHandler provided");
        return getOrCreateOutbox(outboxConfig,
                                 message -> eventHandler.handle(message.getPayload()));
    }

    /**
     * Get all the {@link Outbox} instances managed by this {@link Outboxes} instance
     *
     * @return all the {@link Outbox} instances managed by this {@link Outboxes} instance
     */
    Collection<Outbox> getOutboxes();

    /**
     * Create an {@link Outboxes} instance that uses a {@link DurableQueues} as its storage and message delivery mechanism.
     *
     * @param durableQueues     The {@link DurableQueues} implementation used by the {@link Outboxes} instance returned
     * @param fencedLockManager the {@link FencedLockManager} used for {@link Outbox}'s that use {@link MessageConsumptionMode#SingleGlobalConsumer}
     * @return the {@link Outboxes} instance
     */
    static Outboxes durableQueueBasedOutboxes(DurableQueues durableQueues,
                                              FencedLockManager fencedLockManager) {
        return new DurableQueueBasedOutboxes(durableQueues,
                                             fencedLockManager);
    }

    class DurableQueueBasedOutboxes implements Outboxes {
        private final DurableQueues                     durableQueues;
        private final FencedLockManager                 fencedLockManager;
        private       ConcurrentMap<OutboxName, Outbox> outboxes = new ConcurrentHashMap<>();

        public DurableQueueBasedOutboxes(DurableQueues durableQueues, FencedLockManager fencedLockManager) {
            this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
            this.fencedLockManager = requireNonNull(fencedLockManager, "No fencedLockManager instance provided");
        }

        @SuppressWarnings("unchecked")
        @Override
        public Outbox getOrCreateOutbox(OutboxConfig outboxConfig) {
            requireNonNull(outboxConfig.getOutboxName(), "No outboxName provided");
            return outboxes.computeIfAbsent(outboxConfig.getOutboxName(), outboxName_ -> new DurableQueueBasedOutbox(outboxConfig));
        }

        @SuppressWarnings("unchecked")
        @Override
        public Outbox getOrCreateOutbox(OutboxConfig outboxConfig, Consumer<Message> messageConsumer) {
            requireNonNull(outboxConfig.getOutboxName(), "No outboxName provided");
            return outboxes.computeIfAbsent(outboxConfig.getOutboxName(), outboxName_ -> new DurableQueueBasedOutbox(outboxConfig,
                                                                                                                     messageConsumer));
        }

        @Override
        public Collection<Outbox> getOutboxes() {
            return outboxes.values();
        }

        public class DurableQueueBasedOutbox implements Outbox {
            private      Consumer<Message>    messageConsumer;
            public final QueueName            outboxQueueName;
            public final OutboxConfig         config;
            private      DurableQueueConsumer durableQueueConsumer;

            public DurableQueueBasedOutbox(OutboxConfig config,
                                           Consumer<Message> messageConsumer) {
                this(config);
                consume(messageConsumer);
            }

            public DurableQueueBasedOutbox(OutboxConfig config) {
                this.config = requireNonNull(config, "No outboxConfig provided");
                outboxQueueName = config.outboxName.asQueueName();
            }

            @Override
            public Outbox setMessageConsumer(Consumer<Message> messageConsumer) {
                this.messageConsumer = requireNonNull(messageConsumer, "No messageConsumer provided");
                return this;
            }

            @Override
            public Outbox consume(Consumer<Message> messageConsumer) {
                if (this.messageConsumer != null) {
                    throw new IllegalStateException("Outbox already has a message consumer");
                }
                setMessageConsumer(messageConsumer);
                startConsuming();
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
            public Outbox startConsuming() {
                if (this.messageConsumer == null) {
                    throw new IllegalStateException("No message consumer specified. Please call #setMessageConsumer");
                }
                switch (config.messageConsumptionMode) {
                    case SingleGlobalConsumer:
                        fencedLockManager.acquireLockAsync(config.outboxName.asLockName(),
                                                           LockCallback.builder()
                                                                       .onLockAcquired(lock -> durableQueueConsumer = consumeFromDurableQueue(lock))
                                                                       .onLockReleased(lock -> durableQueueConsumer.cancel())
                                                                       .build());
                        break;
                    case GlobalCompetingConsumers:
                        durableQueueConsumer = consumeFromDurableQueue(null);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected messageConsumptionMode: " + config.messageConsumptionMode);
                }
                return this;
            }

            @Override
            public Outbox stopConsuming() {
                if (messageConsumer != null) {

                    switch (config.messageConsumptionMode) {
                        case SingleGlobalConsumer:
                            fencedLockManager.cancelAsyncLockAcquiring(config.outboxName.asLockName());
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
                return this;
            }

            @Override
            public OutboxName name() {
                return config.outboxName;
            }

            @Override
            public Outbox sendMessage(Message payload) {
                durableQueues.queueMessage(outboxQueueName,
                                           payload);
                return this;
            }

            @Override
            public Outbox sendMessage(Message payload, Duration deliveryDelay) {
                durableQueues.queueMessage(outboxQueueName,
                                           payload,
                                           deliveryDelay);
                return this;
            }

            private DurableQueueConsumer consumeFromDurableQueue(FencedLock lock) {
                return durableQueues.consumeFromQueue(outboxQueueName,
                                                      config.redeliveryPolicy,
                                                      config.numberOfParallelMessageConsumers,
                                                      queuedMessage -> {
                                                          if (config.messageConsumptionMode == SingleGlobalConsumer) {
                                                              queuedMessage.getMetaData().put(MessageMetaData.FENCED_LOCK_TOKEN,
                                                                                              lock.getCurrentToken().toString());
                                                          }
                                                          handleMessage(queuedMessage);
                                                      });
            }

            @SuppressWarnings("unchecked")
            private void handleMessage(QueuedMessage queuedMessage) {
                if (durableQueues.getUnitOfWorkFactory().isPresent()) {
                    durableQueues.getUnitOfWorkFactory().get()
                                 .usingUnitOfWork(() -> messageConsumer.accept(queuedMessage.getMessage()));
                } else {
                    messageConsumer.accept(queuedMessage.getMessage());
                }
            }

            @Override
            public long getNumberOfOutgoingMessages() {
                return durableQueues.getTotalMessagesQueuedFor(outboxQueueName);
            }

            @Override
            public String toString() {
                return "DurableQueueBasedOutbox{" +
                        "config=" + config + ", " +
                        "outboxQueueName=" + outboxQueueName +
                        '}';
            }

        }
    }
}
