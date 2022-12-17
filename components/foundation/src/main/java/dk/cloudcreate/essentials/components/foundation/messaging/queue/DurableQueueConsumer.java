/*
 * Copyright 2021-2022 the original author or authors.
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
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import org.slf4j.*;

import java.util.concurrent.*;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.*;
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

        public final QueueName                      queueName;
        private volatile boolean started;
        public final RedeliveryPolicy               redeliveryPolicy;
        public final int                            numberOfParallelMessageConsumers;
        public final QueuedMessageHandler           queuedMessageHandler;
        private final ScheduledExecutorService       scheduler;
        private final DURABLE_QUEUES                 durableQueues;
        private       Consumer<DurableQueueConsumer> removeDurableQueueConsumer;
        private       UOW_FACTORY                    unitOfWorkFactory;


        public DefaultDurableQueueConsumer(QueueName queueName,
                                           QueuedMessageHandler queuedMessageHandler,
                                           RedeliveryPolicy redeliveryPolicy,
                                           int numberOfParallelMessageConsumers,
                                           UOW_FACTORY unitOfWorkFactory,
                                           DURABLE_QUEUES durableQueues,
                                           Consumer<DurableQueueConsumer> removeDurableQueueConsumer) {
            this.queueName = requireNonNull(queueName, "queueName is missing");
            this.queuedMessageHandler = requireNonNull(queuedMessageHandler, "You must specify a queuedMessageHandler");
            this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "You must specify a redelivery policy");
            this.durableQueues = requireNonNull(durableQueues, "durableQueues is missing");
            if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
                this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "You must specify a unitOfWorkFactory");
            }
            this.removeDurableQueueConsumer = requireNonNull(removeDurableQueueConsumer, "removeDurableQueueConsumer is missing");

            requireTrue(numberOfParallelMessageConsumers >= 1, "You must specify a number of parallelMessageConsumers >= 1");
            this.numberOfParallelMessageConsumers = numberOfParallelMessageConsumers;
            this.scheduler = Executors.newScheduledThreadPool(this.numberOfParallelMessageConsumers,
                                                              new ThreadFactoryBuilder()
                                                                      .nameFormat("Queue-" + queueName + "-Polling-%d")
                                                                      .daemon(true)
                                                                      .build());
        }

        @Override
        public void start() {
            if (!started) {
                log.info("[{}] Starting {} DurableQueueConsumer threads with polling interval {} (based on initialRedeliveryDelay)",
                         queueName,
                         numberOfParallelMessageConsumers,
                         redeliveryPolicy.initialRedeliveryDelay);
                for (var i = 0; i < numberOfParallelMessageConsumers; i++) {
                    if (i > 0) {
                        try {
                            // As there are multiple parallel consumers, ensure they don't trigger at the exact same time
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            // Do nothing
                        }
                    }
                    scheduler.scheduleAtFixedRate(this::pollQueue,
                                                  redeliveryPolicy.initialRedeliveryDelay.toMillis(),
                                                  redeliveryPolicy.initialRedeliveryDelay.toMillis(),
                                                  TimeUnit.MILLISECONDS);
                }
                started = true;
            }
        }

        @Override
        public void stop() {
            if (started) {
                log.info("[{}] Stopping DurableQueueConsumer", queueName);
                scheduler.shutdownNow();
                started = false;
                removeDurableQueueConsumer.accept(this);
                log.info("[{}] DurableQueueConsumer stopped", queueName);
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
            log.trace("[{}] Polling Queue for the next message ready for delivery", queueName);
            if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
                if (unitOfWorkFactory.getCurrentUnitOfWork().isPresent()) {
                    throw new DurableQueueException(msg("Previous UnitOfWork isn't completed/removed: {}", unitOfWorkFactory.getCurrentUnitOfWork().get()), queueName);
                }

                unitOfWorkFactory.usingUnitOfWork(handleAwareUnitOfWork -> processNextMessageReadyForDelivery());
            } else {
                processNextMessageReadyForDelivery();
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
                                     queuedMessageHandler.handle(queuedMessage);
                                     log.debug("[{}:{}] Message handled successfully. Deleting the message in the Queue Store message. Total attempts: {}, Redelivery Attempts: {}",
                                               queueName,
                                               queuedMessage.getId(),
                                               queuedMessage.getTotalDeliveryAttempts(),
                                               queuedMessage.getRedeliveryAttempts());
                                     return durableQueues.acknowledgeMessageAsHandled(queuedMessage.getId());
                                 } catch (Exception e) {
                                     log.debug(msg("[{}:{}] QueueMessageHandler for failed to handle: {}",
                                                   queueName,
                                                   queuedMessage.getId(),
                                                   queuedMessage), e);
                                     if (queuedMessage.getTotalDeliveryAttempts() >= redeliveryPolicy.maximumNumberOfRedeliveries + 1) {
                                         // Dead letter
                                         log.debug("[{}:{}] Marking Message as Dead Letter: {}",
                                                   queueName,
                                                   queuedMessage.getId(),
                                                   queuedMessage);
                                         return durableQueues.markAsDeadLetterMessage(queuedMessage.getId(), e);
                                     } else {
                                         // Redeliver later
                                         var redeliveryDelay = redeliveryPolicy.calculateNextRedeliveryDelay(queuedMessage.getRedeliveryAttempts());
                                         log.debug(msg("[{}:{}] Using redeliveryDelay '{}' for QueueEntryId '{}' due to: {}",
                                                       queueName,
                                                       queuedMessage.getId(),
                                                       redeliveryDelay,
                                                       queuedMessage.getId(),
                                                       e.getMessage()));
                                         return durableQueues.retryMessage(queuedMessage.getId(),
                                                                           e,
                                                                           redeliveryDelay);
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
                    "queueName=" + queueName +
                    ", started=" + started +
                    ", redeliveryPolicy=" + redeliveryPolicy +
                    ", numberOfParallelMessageConsumers=" + numberOfParallelMessageConsumers +
                    ", queuedMessageHandler=" + queuedMessageHandler +
                    '}';
        }
    }
}
