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

package dk.trustworks.essentials.components.foundation.messaging.eip.store_and_forward;

import dk.trustworks.essentials.components.foundation.fencedlock.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.components.foundation.transaction.*;
import dk.trustworks.essentials.reactive.EventHandler;
import org.slf4j.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static dk.trustworks.essentials.components.foundation.messaging.eip.store_and_forward.MessageConsumptionMode.SingleGlobalConsumer;
import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link Outbox} supports the transactional Store and Forward pattern from Enterprise Integration Patterns supporting At-Least-Once delivery guarantee.<br>
 * The {@link Outbox} pattern is used to handle outgoing messages, that are created as a side effect of adding/updating an entity in a database, but where the message infrastructure
 * (such as a Queue, Kafka, EventBus, etc.) that doesn't share the same underlying transactional resource as the database.<br>
 * Instead, you need to use an {@link Outbox} that can join in the same {@link UnitOfWork}/transactional-resource
 * that the database is using.<br>
 * IF the message is added to the {@link Outbox} in a transaction/{@link UnitOfWork} and the {@link UnitOfWork} is committed together (this is dependent on the underlying implementation - see {@link DurableQueueBasedOutboxes}).<br>
 * In case adding the message happens in a shared transaction/{@link UnitOfWork}, then if the transaction fails then both the entity and the message will be rolled back when then {@link UnitOfWork} rolls back.
 * <br>
 * After the {@link UnitOfWork} has been committed, the messages will be asynchronously delivered to the message consumer (typically in a new {@link UnitOfWork} dependent on the underlying implementation - see {@link DurableQueueBasedOutboxes}).<br>
 * The {@link Outbox} itself supports Message Redelivery in case the Message consumer experiences failures.<br>
 * This means that the Message consumer, registered with the {@link Outbox}, can and will receive Messages more than once and therefore its message handling has to be idempotent.
 * <p>
 * If you're working with {@link OrderedMessage}'s then the {@link Outbox} consumer must be configured
 * with {@link OutboxConfig#getMessageConsumptionMode()} having value {@link MessageConsumptionMode#SingleGlobalConsumer}
 * in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per {@link OrderedMessage#getKey()}
 * across as many {@link OutboxConfig#numberOfParallelMessageConsumers} as you wish to use.
 *
 * @see DurableQueueBasedOutboxes
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

    /**
     * {@link Outboxes} variant that uses {@link DurableQueues} as the underlying implementation.<br>
     * ONLY in cases where the underlying {@link DurableQueues} is associated with a {@link UnitOfWorkFactory} will
     * the {@link Outbox} message consumption be performed within {@link UnitOfWork}, otherwise
     * message consumption isn't performed with a {@link UnitOfWork}
     */
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
            private static final Logger            log = LoggerFactory.getLogger(DurableQueueBasedOutbox.class);
            private              Consumer<Message> messageConsumer;
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
                log.info("Starting Consuming from Outbox '{}'", config.outboxName);
                switch (config.messageConsumptionMode) {
                    case SingleGlobalConsumer:
                        var lockName = config.outboxName.asLockName();
                        log.info("Creating FencedLock '{}' for Consumer for Outbox '{}'", lockName, config.outboxName);

                        fencedLockManager.acquireLockAsync(config.outboxName.asLockName(),
                                                           LockCallback.builder()
                                                                       .onLockAcquired(lock -> {
                                                                           log.info("FencedLock '{}' for Outbox '{}' was ACQUIRED - will start Exclusive DurableQueueConsumer", lockName, config.outboxName);
                                                                           durableQueueConsumer = consumeFromDurableQueue(lock);
                                                                           log.info("Exclusive DurableQueueConsumer for Outbox '{}': {}", config.outboxName, durableQueueConsumer);
                                                                       })
                                                                       .onLockReleased(lock -> {
                                                                           if (durableQueueConsumer != null) {
                                                                               log.info("FencedLock '{}' for Outbox '{}' was RELEASED - will stop Exclusive DurableQueueConsumer: {}", lockName, config.outboxName, durableQueueConsumer);
                                                                               durableQueueConsumer.cancel();
                                                                               log.info("Stopped Exclusive DurableQueueConsumer for Outbox '{}': {}", config.outboxName, durableQueueConsumer);
                                                                           } else {
                                                                               log.warn("FencedLock '{}' for Outbox '{}' was RELEASED - didn't find an Exclusive DurableQueueConsumer!", lockName, config.outboxName);
                                                                           }
                                                                       })
                                                                       .build());
                        break;
                    case GlobalCompetingConsumers:
                        log.info("Starting Non-Exclusive DurableQueueConsumer for Outbox '{}'", config.outboxName);
                        durableQueueConsumer = consumeFromDurableQueue(null);
                        log.info("Non-Exclusive DurableQueueConsumer for Outbox '{}': {}", config.outboxName, durableQueueConsumer);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected messageConsumptionMode: " + config.messageConsumptionMode);
                }
                return this;
            }

            @Override
            public Outbox stopConsuming() {
                if (messageConsumer != null) {
                    log.info("Stop Consuming from Outbox '{}'", config.outboxName);
                    switch (config.messageConsumptionMode) {
                        case SingleGlobalConsumer:
                            var lockName = config.outboxName.asLockName();
                            log.info("CancelAsyncLockAcquiring FencedLock '{}' for Outbox '{}'", lockName, config.outboxName);
                            fencedLockManager.cancelAsyncLockAcquiring(lockName);
                            break;
                        case GlobalCompetingConsumers:
                            if (durableQueueConsumer != null) {
                                log.info("Stopping Non-Exclusive DurableQueueConsumer for Outbox '{}': {}", config.outboxName, durableQueueConsumer);
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
            public void deleteAllMessages() {
                durableQueues.purgeQueue(outboxQueueName);
            }

            @Override
            public Outbox sendMessages(List<Message> messages) {
                durableQueues.queueMessages(outboxQueueName, messages);
                return this;
            }

            @Override
            public Outbox sendMessages(List<Message> messages, Duration deliveryDelay) {
                durableQueues.queueMessages(outboxQueueName,
                                            messages,
                                            deliveryDelay);
                return this;
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
