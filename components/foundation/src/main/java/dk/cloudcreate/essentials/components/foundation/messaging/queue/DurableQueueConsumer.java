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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.shared.Exceptions;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import org.slf4j.*;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * {@link DurableQueues} consumer
 *
 * @see DefaultDurableQueueConsumer
 */
public interface DurableQueueConsumer extends Lifecycle {
    QueueName queueName();

    void cancel();

    class DefaultDurableQueueConsumer<DURABLE_QUEUES extends DurableQueues, UOW extends UnitOfWork, UOW_FACTORY extends UnitOfWorkFactory<UOW>> implements DurableQueueConsumer {
        private static final Logger log = LoggerFactory.getLogger(DefaultDurableQueueConsumer.class);

        public final     QueueName                      queueName;
        private final    ConsumeFromQueue               consumeFromQueue;
        private volatile boolean                        started;
        private final    ScheduledExecutorService       scheduler;
        private final    DURABLE_QUEUES                 durableQueues;
        private          Consumer<DurableQueueConsumer> removeDurableQueueConsumer;
        private          UOW_FACTORY                    unitOfWorkFactory;


        public DefaultDurableQueueConsumer(ConsumeFromQueue consumeFromQueue,
                                           UOW_FACTORY unitOfWorkFactory,
                                           DURABLE_QUEUES durableQueues,
                                           Consumer<DurableQueueConsumer> removeDurableQueueConsumer) {
            this.consumeFromQueue = requireNonNull(consumeFromQueue, "consumeFromQueue is missing");
            consumeFromQueue.validate();

            this.durableQueues = requireNonNull(durableQueues, "durableQueues is missing");
            if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
                this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "You must specify a unitOfWorkFactory");
            }
            this.removeDurableQueueConsumer = requireNonNull(removeDurableQueueConsumer, "removeDurableQueueConsumer is missing");
            this.queueName = consumeFromQueue.queueName;


            this.scheduler = consumeFromQueue.getConsumerExecutorService()
                                             .orElseGet(() -> Executors.newScheduledThreadPool(consumeFromQueue.getParallelConsumers(),
                                                                                               new ThreadFactoryBuilder()
                                                                                                       .nameFormat("Queue-" + queueName + "-Polling-%d")
                                                                                                       .daemon(true)
                                                                                                       .build()));

        }

        @Override
        public void start() {
            if (!started) {
                log.info("[{}] Starting {} DurableQueueConsumer threads with polling interval {} (based on initialRedeliveryDelay)",
                         queueName,
                         consumeFromQueue.getParallelConsumers(),
                         consumeFromQueue.getRedeliveryPolicy().initialRedeliveryDelay);
                for (var i = 0; i < consumeFromQueue.getParallelConsumers(); i++) {
                    if (i > 0) {
                        try {
                            // As there are multiple parallel consumers, ensure they don't trigger at the exact same time
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            // Do nothing
                        }
                    }
                    scheduler.scheduleAtFixedRate(this::pollQueue,
                                                  consumeFromQueue.getRedeliveryPolicy().initialRedeliveryDelay.toMillis(),
                                                  consumeFromQueue.getRedeliveryPolicy().initialRedeliveryDelay.toMillis(),
                                                  TimeUnit.MILLISECONDS);
                }
                started = true;
            }
        }

        @Override
        public void stop() {
            if (started) {
                log.info("[{}] Stopping DurableQueueConsumer", queueName);
                started = false;
                try {
                    scheduler.shutdownNow();
                } finally {
                    removeDurableQueueConsumer.accept(this);
                    log.info("[{}] DurableQueueConsumer stopped", queueName);
                }
            }
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public QueueName queueName() {
            return queueName;
        }

        @Override
        public void cancel() {
            stop();
        }


        private void pollQueue() {
            if (!started) {
                log.trace("[{}] Skipping Polling Queue as the consumer is not started", queueName);
                return;
            }

            try {
                log.trace("[{}] Polling Queue for the next message ready for delivery. Transactional mode: {}", queueName, durableQueues.getTransactionalMode());
                if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
                    if (unitOfWorkFactory.getCurrentUnitOfWork().isPresent()) {
                        throw new DurableQueueException(msg("Previous UnitOfWork isn't completed/removed: {}", unitOfWorkFactory.getCurrentUnitOfWork().get()), queueName);
                    }

                    try {
                        unitOfWorkFactory.usingUnitOfWork(handleAwareUnitOfWork -> processNextMessageReadyForDelivery());
                    } catch (Exception e) {
                        var rootCause = Exceptions.getRootCause(e);
                        if (e.getMessage().contains("has been closed") || rootCause.getClass().getSimpleName().equals("EOFException") || rootCause.getClass().getSimpleName().equals("ConnectionException")) {
                            log.trace(msg("[{}] Experienced a Connection issue, will retry later", queueName), e);
                        } else {
                            log.error(msg("[{}] Experienced an error", queueName), e);
                        }
                    }
                } else {
                    processNextMessageReadyForDelivery();
                }
            } catch (DurableQueueException e) {
                log.error(msg("[{}] Failed to poll queue", queueName), e);
            }
        }

        private void processNextMessageReadyForDelivery() {
            try {
                durableQueues.getNextMessageReadyForDelivery(queueName)
                             .map(queuedMessage -> {
                                 log.debug("[{}:{}] Delivering message. Total attempts: {}, Redelivery Attempts: {}",
                                           queueName,
                                           queuedMessage.getId(),
                                           queuedMessage.getTotalDeliveryAttempts(),
                                           queuedMessage.getRedeliveryAttempts());
                                 try {
                                     consumeFromQueue.queueMessageHandler.handle(queuedMessage);
                                     log.debug("[{}:{}] Message handled successfully. Deleting the message in the Queue Store message. Total attempts: {}, Redelivery Attempts: {}",
                                               queueName,
                                               queuedMessage.getId(),
                                               queuedMessage.getTotalDeliveryAttempts(),
                                               queuedMessage.getRedeliveryAttempts());
                                     return durableQueues.acknowledgeMessageAsHandled(queuedMessage.getId());
                                 } catch (Exception e) {
                                     log.debug(msg("[{}:{}] QueueMessageHandler for failed to handle message: {}",
                                                   queueName,
                                                   queuedMessage.getId(),
                                                   queuedMessage), e);
                                     var isPermanentError = consumeFromQueue.getRedeliveryPolicy().isPermanentError(queuedMessage, e);
                                     if (isPermanentError || queuedMessage.getTotalDeliveryAttempts() >= consumeFromQueue.getRedeliveryPolicy().maximumNumberOfRedeliveries + 1) {
                                         // Dead letter
                                         log.debug("[{}:{}] Marking Message as Dead Letter. Is Permanent Error: {}. Message: {}",
                                                   queueName,
                                                   queuedMessage.getId(),
                                                   isPermanentError,
                                                   queuedMessage);
                                         try {
                                             return durableQueues.markAsDeadLetterMessage(queuedMessage.getId(), e);
                                         } catch (Exception ex) {
                                             log.error(msg("[{}:{}] Failed to mark the Message as a Dead Letter Message. Details: Is Permanent Error: {}. Message: {}",
                                                           queueName,
                                                           queuedMessage.getId(),
                                                           isPermanentError,
                                                           queuedMessage), ex);
                                             return Optional.empty();
                                         }
                                     } else {
                                         // Redeliver later
                                         var redeliveryDelay = consumeFromQueue.getRedeliveryPolicy().calculateNextRedeliveryDelay(queuedMessage.getRedeliveryAttempts());
                                         log.debug(msg("[{}:{}] Using redeliveryDelay '{}' for QueueEntryId '{}' due to: {}",
                                                       queueName,
                                                       queuedMessage.getId(),
                                                       redeliveryDelay,
                                                       queuedMessage.getId(),
                                                       e.getMessage()));
                                         try {
                                             return durableQueues.retryMessage(queuedMessage.getId(),
                                                                               e,
                                                                               redeliveryDelay);
                                         } catch (Exception ex) {
                                             log.error(msg("[{}:{}] Failed to register the message for retry.",
                                                           queueName,
                                                           queuedMessage.getId()), ex);
                                             return Optional.empty();
                                         }
                                     }

                                 }
                             });
            } catch (Exception e) {
                log.error(msg("[{}] Error Polling Queue", queueName), e);
            }
        }

        @Override
        public String toString() {
            return "DurableQueueConsumer{" +
                    ", started=" + started +
                    consumeFromQueue.toString() +
                    '}';
        }
    }
}
