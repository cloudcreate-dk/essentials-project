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
package dk.trustworks.essentials.components.foundation.messaging.queue;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import dk.trustworks.essentials.components.foundation.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.operations.*;
import dk.trustworks.essentials.shared.Exceptions;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

import static dk.trustworks.essentials.shared.Exceptions.rethrowIfCriticalError;
import static dk.trustworks.essentials.shared.FailFast.*;
import static dk.trustworks.essentials.shared.interceptor.InterceptorChain.newInterceptorChainForOperation;

/**
 * Centralized message fetcher that fetches messages from queues using a single polling thread
 * and distributes them to {@link DurableQueueConsumer} worker threads.
 * <p>
 * This design:
 * - Reduces database load by having a single polling thread per {@link DurableQueues} instance
 * - Maintains ordering guarantees for ordered messages
 * - Respects each consumer's thread allocation
 * - Supports dynamic thread allocation
 * - Optimizes polling frequency based on queue activity
 */
public class CentralizedMessageFetcher implements Lifecycle {
    private static final Logger log = LoggerFactory.getLogger(CentralizedMessageFetcher.class);

    private final DurableQueues                                              durableQueues;
    private final ScheduledExecutorService                                   scheduler;
    private final ConcurrentMap<QueueName, DurableQueueConsumerRegistration> consumerRegistrations;
    private final AtomicBoolean                                              started;
    private final long                                                       pollingIntervalMs;
    private final List<DurableQueuesInterceptor>                             interceptors;

    /**
     * Messages currently being processed per queue with their key
     * Map: QueueName -> (Set of {@link OrderedMessage#getKey()}'s currently being processed)
     */
    private final ConcurrentMap<QueueName, Set<String>> inProcessOrderedKeys;

    /**
     * Create a new CentralizedMessageFetcher
     *
     * @param durableQueues     the DurableQueues instance to work with
     * @param pollingIntervalMs the polling interval in milliseconds
     */
    public CentralizedMessageFetcher(DurableQueues durableQueues,
                                     long pollingIntervalMs,
                                     List<DurableQueuesInterceptor> interceptors) {
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues provided");
        this.pollingIntervalMs = pollingIntervalMs;
        this.interceptors = new CopyOnWriteArrayList<>(requireNonNull(interceptors, "interceptors is missing"));
        this.consumerRegistrations = new ConcurrentHashMap<>();
        this.inProcessOrderedKeys = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r);
            thread.setName("DurableQueues-CentralizedMessageFetcher");
            thread.setDaemon(true);
            return thread;
        });
        this.started = new AtomicBoolean(false);
    }

    /**
     * Register a consumer with the centralized fetcher
     *
     * @param queueName            the queue name to consume from
     * @param consumer             the consumer instance
     * @param messageHandler       the message handler
     * @param workerPool           the worker pool to use
     * @param maxParallelConsumers the maximum number of parallel consumers
     */
    public void registerConsumer(QueueName queueName,
                                 DurableQueueConsumer consumer,
                                 QueuedMessageHandler messageHandler,
                                 ExecutorService workerPool,
                                 int maxParallelConsumers) {
        requireNonNull(queueName, "No queueName provided");
        requireNonNull(consumer, "No consumer provided");
        requireNonNull(messageHandler, "No messageHandler provided");
        requireNonNull(workerPool, "No workerPool provided");
        requireTrue(maxParallelConsumers > 0, "maxParallelConsumers must be > 0");

        log.debug("[{}] Registering consumer with max {} parallel consumers",
                  queueName,
                  maxParallelConsumers);

        consumerRegistrations.putIfAbsent(queueName,
                                          new DurableQueueConsumerRegistration(
                                                  queueName,
                                                  consumer,
                                                  messageHandler,
                                                  workerPool,
                                                  maxParallelConsumers));

        inProcessOrderedKeys.putIfAbsent(queueName, ConcurrentHashMap.newKeySet());
    }

    /**
     * Unregister a consumer
     *
     * @param queueName the queue name to unregister
     */
    public void unregisterConsumer(QueueName queueName) {
        requireNonNull(queueName, "No queueName provided");

        log.debug("[{}] Unregistering consumer", queueName);
        consumerRegistrations.remove(queueName);
        inProcessOrderedKeys.remove(queueName);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            log.info("Starting CentralizedMessageFetcher with polling interval {} ms", pollingIntervalMs);

            scheduler.scheduleAtFixedRate(() -> {
                if (!started.get()) {
                    return;
                }

                try {
                    fetchAndDistributeMessages();
                } catch (Throwable e) {
                    rethrowIfCriticalError(e);
                    if (IOExceptionUtil.isIOException(e)) {
                        log.debug("I/O issue while polling queues", e);
                    } else {
                        log.error("Error during centralized message fetching", e);
                    }
                }
            }, 0, pollingIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        if (started.compareAndSet(true, false)) {
            log.info("Stopping CentralizedMessageFetcher");
            scheduler.shutdownNow();
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    /**
     * Main method that fetches and distributes messages to workers
     */
    private void fetchAndDistributeMessages() {
        if (consumerRegistrations.isEmpty()) {
            log.trace("No consumers registered, skipping fetch and distribute");
            return;
        }

        // Collect active queue names and determine available worker slots
        var availableWorkerSlotsPerQueue = calculateAvailableWorkerSlotsPerQueue();

        // Skip if no capacity available
        if (availableWorkerSlotsPerQueue.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            log.trace("No worker capacity available, skipping fetch and distribute");
            return;
        }

        // Prepare excluded keys map for batch fetching
        var excludeKeysPerQueue = new HashMap<QueueName, Set<String>>();
        availableWorkerSlotsPerQueue.forEach((queueName, slots) -> {
            if (slots > 0) {
                excludeKeysPerQueue.put(queueName, new HashSet<>(inProcessOrderedKeys.getOrDefault(queueName, ConcurrentHashMap.newKeySet())));
            }
        });

        try {
            // Ask the DurableQueues implementation if it supports batch fetching
            if (durableQueues instanceof BatchMessageFetchingCapableDurableQueues batchCapableDurableQueues) {
                // Use batch fetching for better efficiency
                var messages = batchCapableDurableQueues.fetchNextBatchOfMessages(
                        availableWorkerSlotsPerQueue.keySet(),
                        excludeKeysPerQueue,
                        availableWorkerSlotsPerQueue);

                // Debug logging when using batch fetching
                if (log.isDebugEnabled()) {
                    var messageCountsByQueue = messages.stream()
                                                       .collect(Collectors.groupingBy(QueuedMessage::getQueueName, Collectors.counting()));
                    log.debug("Batch fetched {} messages across {} queues: {}",
                              messages.size(),
                              availableWorkerSlotsPerQueue.size(),
                              messageCountsByQueue);
                }

                // Process all messages without duplicates and with error handling for each
                for (var message : messages) {
                    var queueName = message.getQueueName();
                    try {
                        processMessage(queueName, message);
                    } catch (Exception e) {
                        // Log errors but continue processing other messages
                        log.error("[{}:{}] Error processing message in batch: {}",
                                  queueName, message.getId(), e.getMessage(), e);
                    }
                }
            } else {
                // Fall back to per-queue fetching for non-batch-capable implementations
                availableWorkerSlotsPerQueue.forEach((queueName, availableSlots) -> {
                    if (availableSlots <= 0) {
                        return;
                    }

                    var keysBeingProcessed = inProcessOrderedKeys.getOrDefault(queueName, Collections.emptySet());

                    try {
                        for (int i = 0; i < availableSlots; i++) {
                            var messageOpt = durableQueues.getNextMessageReadyForDelivery(
                                    new GetNextMessageReadyForDelivery(queueName, new ArrayList<>(keysBeingProcessed)));

                            if (messageOpt.isEmpty()) {
                                log.trace("[{}] No more messages to process for this queue. Slot: {} (1-based)", queueName, i + 1);
                                break;
                            }

                            processMessage(queueName, messageOpt.get());
                        }
                    } catch (Exception e) {
                        log.error("[{}] Error fetching messages: {}", queueName, e.getMessage(), e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error during batch message fetching: {}", e.getMessage(), e);
        }
    }

    /**
     * Process a message by submitting it to the appropriate worker pool
     */
    private void processMessage(QueueName queueName, QueuedMessage message) {
        // Track ordered message keys
        var sharedKeysBeingProcessed = inProcessOrderedKeys.computeIfAbsent(queueName, _queueName -> ConcurrentHashMap.newKeySet());

        // Add the key if this is an ordered message
        if (message.getMessage() instanceof OrderedMessage orderedMessage) {
            sharedKeysBeingProcessed.add(orderedMessage.getKey());
        }

        var registration = consumerRegistrations.get(queueName);
        if (registration == null) {
            log.warn("[{}] Received message for unregistered consumer - will retry the message", queueName);
            durableQueues.retryMessage(message.getId(),
                                       null,
                                       registration.consumer.getRedeliveryPolicy().initialRedeliveryDelay);
            return;
        }

        registration.activeWorkers.incrementAndGet();
        log.debug("[{}] Submitting message {} to worker pool",
                  queueName,
                  message.getId());

        // Submit message for processing
        registration.workerPool.submit(() -> {
            try {
                var operation = new HandleQueuedMessage(message, registration.messageHandler);
                newInterceptorChainForOperation(operation,
                                                interceptors,
                                                (interceptor, interceptorChain) -> interceptor.intercept(operation, interceptorChain),
                                                () -> {
                                                    registration.messageHandler.handle(message);
                                                    return (Void) null;
                                                })
                        .proceed();

                if (message.isManuallyMarkedForRedelivery()) {
                    log.debug("[{}:{}] Message handler manually requested redelivery",
                              queueName,
                              message.getId());

                    try {
                        durableQueues.retryMessage(message.getId(),
                                                   null,
                                                   message.getRedeliveryDelay());
                    } catch (Exception ex) {
                        // If retry fails due to connectivity issues, then it will be picked up by resetMessagesStuckBeingDelivered
                        log.warn("[{}:{}] Could not manually mark message for redelivery: {}",
                                 queueName,
                                 message.getId(),
                                 ex.getMessage());
                    }
                } else {
                    log.debug("[{}:{}] Message handled successfully, acknowledging",
                              queueName,
                              message.getId());

                    try {
                        boolean acknowledged = durableQueues.acknowledgeMessageAsHandled(message.getId());
                        if (!acknowledged) {
                            // Message was already acknowledged or deleted
                            log.debug("[{}:{}] Message acknowledgment reported message already handled or deleted",
                                      queueName,
                                      message.getId());
                        }
                    } catch (Exception ex) {
                        // If acknowledgment fails due to connectivity issues, the message will be
                        // picked up by resetMessagesStuckBeingDelivered after the handling timeout
                        log.warn("[{}:{}] Failed to acknowledge message: {}",
                                 queueName,
                                 message.getId(),
                                 ex.getMessage());
                    }
                }
            } catch (Throwable e) {
                rethrowIfCriticalError(e);

                try {
                    boolean isPermanentError = isPermanentError(message, e);
                    if (isPermanentError || message.getTotalDeliveryAttempts() >= registration.consumer.getRedeliveryPolicy().getMaximumNumberOfRedeliveries() + 1) {
                        log.error("[{}:{}] Marking message as dead letter due to error: {}",
                                  queueName,
                                  message.getId(),
                                  e.getMessage(),
                                  e);

                        durableQueues.markAsDeadLetterMessage(message.getId(), e);
                    } else {
                        // Redelivery
                        var redeliveryDelay = registration.consumer.getRedeliveryPolicy()
                                                                   .calculateNextRedeliveryDelay(message.getRedeliveryAttempts());

                        log.debug("[{}:{}] Will retry message with delay {}: {}",
                                  queueName,
                                  message.getId(),
                                  redeliveryDelay,
                                  e.getMessage());

                        durableQueues.retryMessage(message.getId(), e, redeliveryDelay);
                    }
                } catch (Exception retryEx) {
                    log.error("[{}:{}] Error handling message failure: {}",
                              queueName,
                              message.getId(),
                              retryEx.getMessage(),
                              retryEx);
                }
            } finally {
                // Cleanup from both the shared concurrent set
                if (message.getMessage() instanceof OrderedMessage orderedMessage) {
                    var key = orderedMessage.getKey();

                    // Remove from shared state
                    var currentKeysBeingProcessed = inProcessOrderedKeys.get(queueName);
                    if (currentKeysBeingProcessed != null) {
                        currentKeysBeingProcessed.remove(key);
                    }
                }

                registration.activeWorkers.decrementAndGet();
            }
        });
    }

    /**
     * Calculate available worker slots for each queue
     *
     * @return Map of queue name to available worker slots
     */
    private Map<QueueName, Integer> calculateAvailableWorkerSlotsPerQueue() {
        var result = new HashMap<QueueName, Integer>();

        // Process registrations in a thread-safe way
        for (Map.Entry<QueueName, DurableQueueConsumerRegistration> entry : consumerRegistrations.entrySet()) {
            var queueName = entry.getKey();
            var reg       = entry.getValue();

            if (reg == null) {
                // Skip if registration is somehow null
                log.warn("Registration for queue '{}' is null, skipping", queueName);
                continue;
            }

            // Ensure we have at least one slot available (even if max workers is reached)
            // to prevent complete starvation when using a small number of workers
            int activeWorkers  = reg.activeWorkers.get();
            int availableSlots = Math.max(1, reg.maxParallelConsumers - activeWorkers);

            // Adjust the slots to prevent overloading when nearing capacity
            if (activeWorkers > 0 && activeWorkers >= reg.maxParallelConsumers * 0.8) {
                // When we're at 80% capacity or higher, limit new slots 
                availableSlots = Math.min(availableSlots, 2);
            }

            // Add result to map
            result.put(queueName, availableSlots);
        }

        return result;
    }

    /**
     * Determine if an error is permanent and should mark the message as a dead letter
     */
    private boolean isPermanentError(QueuedMessage queuedMessage, Throwable e) {
        DurableQueueConsumerRegistration registration = consumerRegistrations.get(queuedMessage.getQueueName());
        if (registration == null) {
            // If registration is gone, treat as permanent to avoid message being stuck
            return true;
        }

        var rootCause = Exceptions.getRootCause(e);
        return registration.consumer.getRedeliveryPolicy().isPermanentError(queuedMessage, e) ||
                e instanceof DurableQueueDeserializationException ||
                e instanceof ClassCastException || rootCause instanceof ClassCastException ||
                e instanceof NoClassDefFoundError || rootCause instanceof NoClassDefFoundError ||
                rootCause instanceof MismatchedInputException ||
                e instanceof IllegalArgumentException || rootCause instanceof IllegalArgumentException;
    }

    public boolean containsConsumerFor(QueueName queueName) {
        requireNonNull(queueName, "No queueName provided");
        return consumerRegistrations.containsKey(queueName);
    }

    /**
     * Internal class to track consumer registrations
     */
    private static class DurableQueueConsumerRegistration {
        private final QueueName            queueName;
        private final DurableQueueConsumer consumer;
        private final QueuedMessageHandler messageHandler;
        private final ExecutorService      workerPool;
        private final int                  maxParallelConsumers;
        private final AtomicInteger        activeWorkers     = new AtomicInteger(0);

        private DurableQueueConsumerRegistration(QueueName queueName,
                                                 DurableQueueConsumer consumer,
                                                 QueuedMessageHandler messageHandler,
                                                 ExecutorService workerPool,
                                                 int maxParallelConsumers) {
            this.queueName = requireNonNull(queueName, "No queueName provided");
            this.consumer = requireNonNull(consumer, "No consumer provided");
            this.messageHandler = requireNonNull(messageHandler, "No messageHandler provided");
            this.workerPool = requireNonNull(workerPool, "No workerPool provided");
            this.maxParallelConsumers = maxParallelConsumers;
        }
    }
}