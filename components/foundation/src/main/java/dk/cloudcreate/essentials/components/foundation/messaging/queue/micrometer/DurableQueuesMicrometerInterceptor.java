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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueuesInterceptor;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueEntryId;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueuedMessage;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.DeleteMessage;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.MarkAsDeadLetterMessage;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.QueueMessage;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.QueueMessageAsDeadLetterMessage;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.QueueMessages;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ResurrectDeadLetterMessage;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.RetryMessage;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public final class DurableQueuesMicrometerInterceptor implements DurableQueuesInterceptor {
    public static final String PROCESSED_QUEUED_MESSAGES_COUNTER_NAME         = "DurableQueues_QueuedMessages_Processed_";
    public static final String PROCESSED_QUEUED_MESSAGES_RETRIES_COUNTER_NAME = "DurableQueues_QueuedMessages_Retries_";
    public static final String PROCESSED_DEAD_LETTER_MESSAGES_COUNTER_NAME    = "DurableQueues_DeadLetterMessages_Processed_";
    public static final String QUEUE_NAME_TAG_NAME                            = "QueueName";

    private final MeterRegistry                             meterRegistry;
    private final ConcurrentHashMap<QueueName, QueueGauges> queueGauges = new ConcurrentHashMap<>();
    private       DurableQueues                             durableQueues;


    public DurableQueuesMicrometerInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry, "No meterRegistry instance provided");
    }

    @Override
    public void setDurableQueues(DurableQueues durableQueues) {
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        durableQueues.getQueueNames()
                     .forEach(this::addQueueGaugeIfMissing);
    }

    private void addQueueGaugeIfMissing(QueueName queueName) {
        queueGauges.computeIfAbsent(queueName, this::buildQueueGauges);
    }

    private QueueGauges buildQueueGauges(QueueName queueName) {
        var gauges = new QueueGauges();
        gauges.queuedMessagesGauge = Gauge
                .builder("DurableQueues_QueuedMessages_Size", () -> durableQueues.getTotalMessagesQueuedFor(queueName))
                .tag(QUEUE_NAME_TAG_NAME, queueName.toString())
                .register(meterRegistry);
        gauges.deadLetterMessagesGauge = Gauge
                .builder("DurableQueues_DeadLetterMessages_Size", () -> durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName))
                .tag(QUEUE_NAME_TAG_NAME, queueName.toString())
                .register(meterRegistry);
        return gauges;
    }

    @Override
    public QueueEntryId intercept(QueueMessage operation, InterceptorChain<QueueMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        var queueEntryId = interceptorChain.proceed();
        addQueueGaugeIfMissing(operation.queueName);
        incProcessedQueuedMessagesCount(operation.queueName);
        return queueEntryId;
    }


    @Override
    public List<QueueEntryId> intercept(QueueMessages operation, InterceptorChain<QueueMessages, List<QueueEntryId>, DurableQueuesInterceptor> interceptorChain) {
        var queueEntryIds = interceptorChain.proceed();
        addQueueGaugeIfMissing(operation.queueName);
        incProcessedQueuedMessagesCount(operation.queueName, queueEntryIds.size());
        return queueEntryIds;
    }


    @Override
    public QueueEntryId intercept(QueueMessageAsDeadLetterMessage operation, InterceptorChain<QueueMessageAsDeadLetterMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        var queueEntryId = interceptorChain.proceed();
        addQueueGaugeIfMissing(operation.queueName);
        incProcessedQueuedDeadLetterMessagesCount(operation.queueName);
        return queueEntryId;
    }


    @Override
    public Optional<QueuedMessage> intercept(MarkAsDeadLetterMessage operation, InterceptorChain<MarkAsDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        var optionalQueuedMessage = interceptorChain.proceed();
        optionalQueuedMessage.ifPresent(queuedMessage -> {
            addQueueGaugeIfMissing(queuedMessage.getQueueName());
            incProcessedQueuedDeadLetterMessagesCount(queuedMessage.getQueueName());
        });
        return optionalQueuedMessage;
    }

    @Override
    public boolean intercept(DeleteMessage operation, InterceptorChain<DeleteMessage, Boolean, DurableQueuesInterceptor> interceptorChain) {
        var queueName = durableQueues.getQueueNameFor(operation.queueEntryId).orElse(null);
        var succeeded = interceptorChain.proceed();
        if (succeeded && queueName != null) {
            addQueueGaugeIfMissing(queueName);
        }
        return succeeded;
    }

    @Override
    public Optional<QueuedMessage> intercept(ResurrectDeadLetterMessage operation, InterceptorChain<ResurrectDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        var optionalQueuedMessage = interceptorChain.proceed();
        optionalQueuedMessage.ifPresent(queuedMessage -> {
            addQueueGaugeIfMissing(queuedMessage.getQueueName());
        });
        return optionalQueuedMessage;
    }

    @Override
    public Optional<QueuedMessage> intercept(RetryMessage operation, InterceptorChain<RetryMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        var optionalQueuedMessage = interceptorChain.proceed();
        optionalQueuedMessage.ifPresent(queuedMessage -> {
            addQueueGaugeIfMissing(queuedMessage.getQueueName());
        });
        return optionalQueuedMessage;
    }

    protected void incProcessedQueuedMessagesCount(QueueName queueName) {
        requireNonNull(queueName, "No queueName provided");
        meterRegistry.counter(PROCESSED_QUEUED_MESSAGES_COUNTER_NAME + queueName.toString(), QUEUE_NAME_TAG_NAME, queueName.toString())
                     .increment();
    }


    protected void incProcessedQueuedMessagesCount(QueueName queueName, int countIncrease) {
        requireNonNull(queueName, "No queueName provided");
        meterRegistry.counter(PROCESSED_QUEUED_MESSAGES_COUNTER_NAME + queueName.toString(), QUEUE_NAME_TAG_NAME, queueName.toString())
                     .increment(countIncrease);
    }


    protected void incProcessedQueuedDeadLetterMessagesCount(QueueName queueName) {
        requireNonNull(queueName, "No queueName provided");
        meterRegistry.counter(PROCESSED_DEAD_LETTER_MESSAGES_COUNTER_NAME + queueName.toString(), QUEUE_NAME_TAG_NAME, queueName.toString())
                     .increment();
    }

    protected void incQueuedMessagesRetriesCount(QueueName queueName) {
        requireNonNull(queueName, "No queueName provided");
        meterRegistry.counter(PROCESSED_QUEUED_MESSAGES_RETRIES_COUNTER_NAME + queueName.toString(), QUEUE_NAME_TAG_NAME, queueName.toString())
                     .increment();
    }


    private static class QueueGauges {
        private Gauge queuedMessagesGauge;
        private Gauge deadLetterMessagesGauge;
    }
}
