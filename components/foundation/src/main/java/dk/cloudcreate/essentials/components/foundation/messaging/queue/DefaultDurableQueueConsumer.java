/*
 * Copyright 2021-2024 the original author or authors.
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

import dk.cloudcreate.essentials.components.foundation.IOExceptionUtil;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueuedMessage.DeliveryMode;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * The default {@link DurableQueueConsumer} which provides basic implementation (including retrying messages
 * in case of failure, polling interval optimization, etc.)<br>
 * Log levels of interest:
 * <pre>{@code
 * dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueueConsumer
 * dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueueConsumer.MessageHandlingFailures
 * }</pre>
 *
 * @param <DURABLE_QUEUES> the concrete type of {@link DurableQueues} implementation
 * @param <UOW>            the {@link UnitOfWork} type
 * @param <UOW_FACTORY>    the {@link UnitOfWorkFactory} type
 */
public abstract class DefaultDurableQueueConsumer<DURABLE_QUEUES extends DurableQueues, UOW extends UnitOfWork, UOW_FACTORY extends UnitOfWorkFactory<UOW>>
        implements DurableQueueConsumer, DurableQueueConsumerNotifications {
    public static final Logger   LOG                                          = LoggerFactory.getLogger(DurableQueueConsumer.class);
    public static final Logger   MESSAGE_HANDLING_FAILURE_LOG                 = LoggerFactory.getLogger(DurableQueueConsumer.class + ".MessageHandlingFailures");
    public static final Runnable NO_POSTPROCESSING_AFTER_PROCESS_NEXT_MESSAGE = () -> {
    };

    public final     QueueName                             queueName;
    private final    ConsumeFromQueue                      consumeFromQueue;
    private volatile boolean                               started;
    private final    ScheduledExecutorService              scheduler;
    private final    DURABLE_QUEUES                        durableQueues;
    private final    Consumer<DurableQueueConsumer>        removeDurableQueueConsumer;
    private final    UOW_FACTORY                           unitOfWorkFactory;
    private final    QueuePollingOptimizer                 queuePollingOptimizer;
    private final    long                                  pollingIntervalMs;
    /**
     * Only used for {@link DeliveryMode#IN_ORDER} - it is used to ensure that two (or more) threads aren't handling messages
     * belonging to the same {@link OrderedMessage#getKey()}
     * <p>
     * Key: The thread instance processing a given {@link OrderedMessage}<br>
     * Value: The {@link OrderedMessage} currently being handled by the Thread
     */
    private final    ConcurrentMap<Thread, OrderedMessage> orderedMessageDeliveryThreads = new ConcurrentHashMap<>();

    public DefaultDurableQueueConsumer(ConsumeFromQueue consumeFromQueue,
                                       UOW_FACTORY unitOfWorkFactory,
                                       DURABLE_QUEUES durableQueues,
                                       Consumer<DurableQueueConsumer> removeDurableQueueConsumer,
                                       long pollingIntervalMs,
                                       QueuePollingOptimizer queuePollingOptimizer) {
        this.consumeFromQueue = requireNonNull(consumeFromQueue, "consumeFromQueue is missing");
        consumeFromQueue.validate();

        this.durableQueues = requireNonNull(durableQueues, "durableQueues is missing");
        if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
            this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "You must specify a unitOfWorkFactory");
        } else {
            this.unitOfWorkFactory = null;
        }
        this.removeDurableQueueConsumer = requireNonNull(removeDurableQueueConsumer, "removeDurableQueueConsumer is missing");
        this.queueName = consumeFromQueue.queueName;


        this.pollingIntervalMs = pollingIntervalMs;
        if (queuePollingOptimizer != null) {
            this.queuePollingOptimizer = queuePollingOptimizer;
        } else {
            this.queuePollingOptimizer = QueuePollingOptimizer.None();
        }

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

            LOG.info("[{}] {} - Starting {} DurableQueueConsumer threads with polling interval {} ms",
                     queueName,
                     consumeFromQueue.consumerName,
                     consumeFromQueue.getParallelConsumers(),
                     pollingIntervalMs);
            for (var i = 0; i < consumeFromQueue.getParallelConsumers(); i++) {
                if (i > 0) {
                    try {
                        // As there are multiple parallel consumers, ensure they don't trigger at the exact same time
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // Ignore
                        Thread.currentThread().interrupt();
                    }
                }
                scheduler.scheduleAtFixedRate(this::pollQueue,
                                              pollingIntervalMs,
                                              pollingIntervalMs,
                                              TimeUnit.MILLISECONDS);
            }
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            LOG.info("[{}] {} - Stopping DurableQueueConsumer",
                     queueName,
                     consumeFromQueue.consumerName);
            started = false;
            try {
                scheduler.shutdownNow();
            } finally {
                removeDurableQueueConsumer.accept(this);
                LOG.info("[{}] {} - DurableQueueConsumer stopped",
                         queueName,
                         consumeFromQueue.consumerName);
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
            LOG.trace("[{}] {} - Skipping Polling Queue as the consumer is not started",
                      queueName,
                      consumeFromQueue.consumerName);
            return;
        }

        try {
            if (queuePollingOptimizer.shouldSkipPolling()) {
                LOG.trace("[{}] {} - Skipping polling",
                          queueName,
                          consumeFromQueue.consumerName);
                return;
            }

            LOG.trace("[{}] {} - Polling Queue for the next message ready for delivery. Transactional mode: {}",
                      queueName,
                      consumeFromQueue.consumerName,
                      durableQueues.getTransactionalMode());
            Runnable postTransactionalSideEffect = null;
            if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
                if (unitOfWorkFactory.getCurrentUnitOfWork().isPresent()) {
                    throw new DurableQueueException(msg("[{}] {} - Previous UnitOfWork isn't completed/removed: {}",
                                                        queueName,
                                                        consumeFromQueue.consumerName,
                                                        unitOfWorkFactory.getCurrentUnitOfWork().get()),
                                                    queueName);
                }

                try {
                    postTransactionalSideEffect = unitOfWorkFactory.withUnitOfWork(handleAwareUnitOfWork -> processNextMessageReadyForDelivery());
                } catch (Exception e) {
                    handleProcessNextMessageReadyForDeliveryException(e);
                }
            } else {
                try {
                    postTransactionalSideEffect = processNextMessageReadyForDelivery();
                } catch (Exception e) {
                    handleProcessNextMessageReadyForDeliveryException(e);
                }
            }

            if (postTransactionalSideEffect != null) {
                postTransactionalSideEffect.run();
            }
        } catch (Throwable e) {
            if (IOExceptionUtil.isIOException(e)) {
                LOG.debug(msg("[{}] {} - Experienced a Connection issue while polling queue",
                              queueName,
                              consumeFromQueue.consumerName), e);
            } else {
                LOG.error(msg("[{}] {} - Failed to poll queue",
                              queueName,
                              consumeFromQueue.consumerName), e);
            }
        }
    }

    private void handleProcessNextMessageReadyForDeliveryException(Exception e) {
        if (IOExceptionUtil.isIOException(e)) {
            LOG.trace(msg("[{}] {} - Experienced a Connection issue, will retry later",
                          queueName,
                          consumeFromQueue.consumerName), e);
        } else {
            LOG.error(msg("[{}] {} - Experienced an error, will retry later",
                          queueName,
                          consumeFromQueue.consumerName), e);
        }
    }

    /**
     * @return the post transactional side effect of the message handling
     */
    private Runnable processNextMessageReadyForDelivery() {
        try {
            if (started) {
                List<String> excludeOrderedMessagesWithTheseKeys = resolveMessageKeysToExclude();
                return durableQueues.getNextMessageReadyForDelivery(new GetNextMessageReadyForDelivery(queueName,
                                                                                                       excludeOrderedMessagesWithTheseKeys))
                                    .map(this::handleMessage)
                                    .orElseGet(() -> queuePollingOptimizer::queuePollingReturnedNoMessages);
            } else {
                return NO_POSTPROCESSING_AFTER_PROCESS_NEXT_MESSAGE;
            }
        } catch (Throwable e) {
            if (IOExceptionUtil.isIOException(e)) {
                LOG.debug(msg("[{}] {} Can't Poll Queue - Connection seems to be broken or closed, this can happen during JVM or application shutdown",
                             queueName,
                             consumeFromQueue.consumerName));
            } else {
                LOG.error(msg("[{}] {} Error Polling Queue",
                              queueName,
                              consumeFromQueue.consumerName), e);
            }
            return NO_POSTPROCESSING_AFTER_PROCESS_NEXT_MESSAGE;
        }
    }

    private List<String> resolveMessageKeysToExclude() {
        var orderedMessageLastHandled = orderedMessageDeliveryThreads.get(Thread.currentThread());
        var allOrderedMessages        = new HashSet<>(orderedMessageDeliveryThreads.values());
        allOrderedMessages.remove(orderedMessageLastHandled);
        var excludeOrderedMessagesWithTheseKeys = allOrderedMessages.stream()
                                                                    .map(OrderedMessage::getKey)
                                                                    .collect(Collectors.toList());
        return excludeOrderedMessagesWithTheseKeys;
    }

    private Runnable handleMessage(QueuedMessage queuedMessage) {
        var isOrderedMessage = queuedMessage.getMessage() instanceof OrderedMessage;
        LOG.debug("[{}:{}] {} - Delivering {}message{}. Total attempts: {}, Redelivery Attempts: {}",
                  queueName,
                  queuedMessage.getId(),
                  consumeFromQueue.consumerName,
                  isOrderedMessage ? "Ordered " : "",
                  isOrderedMessage ? msg(" {}:{}", ((OrderedMessage) queuedMessage.getMessage()).getKey(), ((OrderedMessage) queuedMessage.getMessage()).getOrder()) : "",
                  queuedMessage.getTotalDeliveryAttempts(),
                  queuedMessage.getRedeliveryAttempts());
        if (isOrderedMessage) {
            // Keep track of the ordered message being handled, to ensure other threads on this node doesn't start processing messages related to the OrderedMessage#getKey
            orderedMessageDeliveryThreads.put(Thread.currentThread(), (OrderedMessage) queuedMessage.getMessage());
        }
        try {
            consumeFromQueue.queueMessageHandler.handle(queuedMessage);
            LOG.debug("[{}:{}] {} - Message handled successfully. Deleting the message in the Queue Store message. Total attempts: {}, Redelivery Attempts: {}",
                      queueName,
                      queuedMessage.getId(),
                      consumeFromQueue.consumerName,
                      queuedMessage.getTotalDeliveryAttempts(),
                      queuedMessage.getRedeliveryAttempts());
            durableQueues.acknowledgeMessageAsHandled(queuedMessage.getId());
            orderedMessageDeliveryThreads.remove(Thread.currentThread());
            return () -> queuePollingOptimizer.queuePollingReturnedMessage(queuedMessage);
        } catch (Throwable e) {
            MESSAGE_HANDLING_FAILURE_LOG.debug(msg("[{}:{}] {} - QueueMessageHandler for failed to handle message: {}",
                                                   queueName,
                                                   queuedMessage.getId(),
                                                   consumeFromQueue.consumerName,
                                                   queuedMessage), e);
            var isPermanentError = consumeFromQueue.getRedeliveryPolicy().isPermanentError(queuedMessage, e);
            if (isPermanentError || queuedMessage.getTotalDeliveryAttempts() >= consumeFromQueue.getRedeliveryPolicy().maximumNumberOfRedeliveries + 1) {
                // Dead letter
                MESSAGE_HANDLING_FAILURE_LOG.debug("[{}:{}] {} - Marking Message as Dead Letter. Is Permanent Error: {}. Message: {}",
                                                   queueName,
                                                   queuedMessage.getId(),
                                                   consumeFromQueue.consumerName,
                                                   isPermanentError,
                                                   queuedMessage);
                try {
                    durableQueues.markAsDeadLetterMessage(queuedMessage.getId(), e);
                    orderedMessageDeliveryThreads.remove(Thread.currentThread());
                    return () -> queuePollingOptimizer.queuePollingReturnedMessage(queuedMessage);
                } catch (Throwable ex) {
                    MESSAGE_HANDLING_FAILURE_LOG.error(msg("[{}:{}] {} - Failed to mark the Message as a Dead Letter Message. Details: Is Permanent Error: {}. Message: {}",
                                                           queueName,
                                                           queuedMessage.getId(),
                                                           consumeFromQueue.consumerName,
                                                           isPermanentError,
                                                           queuedMessage), ex);
                    // Note: Don't clean up orderedMessageDeliveryThreads yet
                    return NO_POSTPROCESSING_AFTER_PROCESS_NEXT_MESSAGE;
                }
            } else {
                // Redeliver later
                var redeliveryDelay = consumeFromQueue.getRedeliveryPolicy().calculateNextRedeliveryDelay(queuedMessage.getRedeliveryAttempts());
                MESSAGE_HANDLING_FAILURE_LOG.debug(msg("[{}:{}] {} - Using redeliveryDelay '{}' for QueueEntryId '{}' due to: {}",
                                                       queueName,
                                                       queuedMessage.getId(),
                                                       consumeFromQueue.consumerName,
                                                       redeliveryDelay,
                                                       queuedMessage.getId(),
                                                       e.getMessage()));
                try {
                    // Don't update the polling optimizer as the message stays in the queue
                    durableQueues.retryMessage(queuedMessage.getId(),
                                               e,
                                               redeliveryDelay);
                    orderedMessageDeliveryThreads.remove(Thread.currentThread());
                    return NO_POSTPROCESSING_AFTER_PROCESS_NEXT_MESSAGE;
                } catch (Throwable ex) {
                    if (ex.getMessage().contains("Interrupted waiting for lock")) {
                        // Usually happening when SpringBoot is performing an unclean shutdown
                        MESSAGE_HANDLING_FAILURE_LOG.debug(msg("[{}:{}] {} - Failed to register the message for retry.",
                                                               queueName,
                                                               queuedMessage.getId(),
                                                               consumeFromQueue.consumerName), ex);

                    } else {
                        MESSAGE_HANDLING_FAILURE_LOG.error(msg("[{}:{}] {} - Failed to register the message for retry.",
                                                               queueName,
                                                               queuedMessage.getId(),
                                                               consumeFromQueue.consumerName), ex);
                    }
                    // Note: Don't clean up orderedMessageDeliveryThreads yet
                    return NO_POSTPROCESSING_AFTER_PROCESS_NEXT_MESSAGE;
                }
            }
        }
    }

    @Override
    public void messageAdded(QueuedMessage queuedMessage) {
        queuePollingOptimizer.messageAdded(queuedMessage);
    }

    @Override
    public String toString() {
        return "DurableQueueConsumer{" +
                ", started=" + started +
                consumeFromQueue.toString() +
                '}';
    }


}
