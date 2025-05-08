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

package dk.trustworks.essentials.components.foundation.messaging.queue.micrometer;

import dk.trustworks.essentials.components.foundation.messaging.queue.*;
import dk.trustworks.essentials.components.foundation.messaging.queue.operations.*;
import dk.trustworks.essentials.shared.functional.tuple.Pair;
import dk.trustworks.essentials.shared.interceptor.InterceptorChain;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

public final class DurableQueuesMicrometerInterceptor implements DurableQueuesInterceptor {
    private static final String QUEUED_MESSAGES_GAUGE_NAME                     = "DurableQueues_QueuedMessages_Size";
    private static final String DEAD_LETTER_MESSAGES_GAUGE_NAME                = "DurableQueues_DeadLetterMessages_Size";
    public static final  String PROCESSED_QUEUED_MESSAGES_COUNTER_NAME         = "DurableQueues_QueuedMessages_Processed";
    public static final  String PROCESSED_QUEUED_MESSAGES_RETRIES_COUNTER_NAME = "DurableQueues_QueuedMessages_Retries";
    public static final  String PROCESSED_DEAD_LETTER_MESSAGES_COUNTER_NAME    = "DurableQueues_DeadLetterMessages_Processed";
    public static final  String QUEUE_NAME_TAG_NAME                            = "QueueName";
    public static final  String MODULE_TAG_NAME                                = "Module";

    private final MeterRegistry                              meterRegistry;
    private final ConcurrentHashMap<QueueName, GaugeWrapper> queuedMessagesGauges     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<QueueName, GaugeWrapper> deadLetterMessagesGauges = new ConcurrentHashMap<>();
    private       DurableQueues                              durableQueues;
    private final List<Tag>                                  commonTags               = new ArrayList<>();


    public DurableQueuesMicrometerInterceptor(MeterRegistry meterRegistry,
                                              String moduleTag) {
        this.meterRegistry = requireNonNull(meterRegistry, "No meterRegistry instance provided");
        Optional.ofNullable(moduleTag).map(t -> Tag.of(MODULE_TAG_NAME, t)).ifPresent(commonTags::add);
    }

    @Override
    public void setDurableQueues(DurableQueues durableQueues) {
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        durableQueues.getActiveQueueNames().forEach(this::updateQueueGaugeValues);
    }

    private void updateQueueGaugeValues(QueuedMessage message) {
        updateQueueGaugeValues(message.getQueueName());
    }

    private void updateQueueGaugeValues(QueueName queueName) {
        if (!durableQueues.getActiveQueueNames().contains(queueName)) {
            return;
        }
        var messageCounts = durableQueues.getQueuedMessageCountsFor(queueName);
        this.queuedMessagesGauges.computeIfAbsent(queueName, this::buildQueuedMessagesGauge)
                                 .setMessageCount(messageCounts.numberOfQueuedMessages());
        this.deadLetterMessagesGauges.computeIfAbsent(queueName, this::buildDeadLetterMessagesGauge)
                                     .setMessageCount(messageCounts.numberOfQueuedDeadLetterMessages());
    }

    private GaugeWrapper buildDeadLetterMessagesGauge(QueueName queueName) {
        var deadLetterMessagesQueuedCount = new AtomicLong();
        var gauge = Gauge
                .builder(DEAD_LETTER_MESSAGES_GAUGE_NAME, deadLetterMessagesQueuedCount::get)
                .tags(buildTagList(QUEUE_NAME_TAG_NAME, queueName.toString()))
                .register(meterRegistry);
        return new GaugeWrapper(gauge, deadLetterMessagesQueuedCount);
    }

    private GaugeWrapper buildQueuedMessagesGauge(QueueName queueName) {
        var queuedMessagesQueuedCount = new AtomicLong();
        var gauge = Gauge
                .builder(QUEUED_MESSAGES_GAUGE_NAME, queuedMessagesQueuedCount::get)
                .tags(buildTagList(QUEUE_NAME_TAG_NAME, queueName.toString()))
                .register(meterRegistry);
        return new GaugeWrapper(gauge, queuedMessagesQueuedCount);
    }

    @Override
    public QueueEntryId intercept(QueueMessage operation, InterceptorChain<QueueMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        var queueEntryId = interceptorChain.proceed();
        updateQueueGaugeValues(operation.queueName);
        incProcessedQueuedMessagesCount(operation.queueName);
        return queueEntryId;
    }


    @Override
    public List<QueueEntryId> intercept(QueueMessages operation, InterceptorChain<QueueMessages, List<QueueEntryId>, DurableQueuesInterceptor> interceptorChain) {
        var queueEntryIds = interceptorChain.proceed();
        updateQueueGaugeValues(operation.queueName);
        incProcessedQueuedMessagesCount(operation.queueName, queueEntryIds.size());
        return queueEntryIds;
    }


    @Override
    public QueueEntryId intercept(QueueMessageAsDeadLetterMessage operation, InterceptorChain<QueueMessageAsDeadLetterMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        var queueEntryId = interceptorChain.proceed();
        updateQueueGaugeValues(operation.queueName);
        incProcessedQueuedDeadLetterMessagesCount(operation.queueName);
        return queueEntryId;
    }

    @Override
    public Optional<QueuedMessage> intercept(MarkAsDeadLetterMessage operation, InterceptorChain<MarkAsDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        var optionalQueuedMessage = interceptorChain.proceed();
        optionalQueuedMessage.ifPresent(queuedMessage -> {
            updateQueueGaugeValues(queuedMessage.getQueueName());
            incProcessedQueuedDeadLetterMessagesCount(queuedMessage.getQueueName());
        });
        return optionalQueuedMessage;
    }

    @Override
    public boolean intercept(DeleteMessage operation, InterceptorChain<DeleteMessage, Boolean, DurableQueuesInterceptor> interceptorChain) {
        var queueName = durableQueues.getQueueNameFor(operation.queueEntryId).orElse(null);
        var succeeded = interceptorChain.proceed();
        if (succeeded && queueName != null) {
            updateQueueGaugeValues(queueName);
        }
        return succeeded;
    }

    @Override
    public Optional<QueuedMessage> intercept(ResurrectDeadLetterMessage operation, InterceptorChain<ResurrectDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        var optionalQueuedMessage = interceptorChain.proceed();
        optionalQueuedMessage.ifPresent(this::updateQueueGaugeValues);
        return optionalQueuedMessage;
    }

    @Override
    public Optional<QueuedMessage> intercept(RetryMessage operation, InterceptorChain<RetryMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        var optionalQueuedMessage = interceptorChain.proceed();
        optionalQueuedMessage.ifPresent(this::updateQueueGaugeValues);
        return optionalQueuedMessage;
    }

    @Override
    public boolean intercept(AcknowledgeMessageAsHandled operation,
                             InterceptorChain<AcknowledgeMessageAsHandled, Boolean, DurableQueuesInterceptor> interceptorChain) {
        var queueName = durableQueues.getQueueNameFor(operation.queueEntryId).orElse(null);
        var succeeded = interceptorChain.proceed();
        if (Boolean.TRUE.equals(succeeded) && queueName != null) {
            updateQueueGaugeValues(queueName);
        }
        return succeeded;
    }

    private void incProcessedQueuedMessagesCount(QueueName queueName) {
        requireNonNull(queueName, "No queueName provided");
        meterRegistry.counter(PROCESSED_QUEUED_MESSAGES_COUNTER_NAME, buildTagList(QUEUE_NAME_TAG_NAME, queueName.toString()))
                     .increment();
    }


    private void incProcessedQueuedMessagesCount(QueueName queueName, int countIncrease) {
        requireNonNull(queueName, "No queueName provided");
        meterRegistry.counter(PROCESSED_QUEUED_MESSAGES_COUNTER_NAME, buildTagList(QUEUE_NAME_TAG_NAME, queueName.toString()))
                     .increment(countIncrease);
    }


    private void incProcessedQueuedDeadLetterMessagesCount(QueueName queueName) {
        requireNonNull(queueName, "No queueName provided");
        meterRegistry.counter(PROCESSED_DEAD_LETTER_MESSAGES_COUNTER_NAME, buildTagList(QUEUE_NAME_TAG_NAME, queueName.toString()))
                     .increment();
    }

    private void incQueuedMessagesRetriesCount(QueueName queueName) {
        requireNonNull(queueName, "No queueName provided");
        meterRegistry.counter(PROCESSED_QUEUED_MESSAGES_RETRIES_COUNTER_NAME, buildTagList(QUEUE_NAME_TAG_NAME, queueName.toString()))
                     .increment();
    }

    private List<Tag> buildTagList(String key, String value) {
        ArrayList<Tag> tagList = new ArrayList<>(this.commonTags);
        tagList.add(Tag.of(key, value));
        return tagList;
    }

    private static class GaugeWrapper extends Pair<Gauge, AtomicLong> {

        private GaugeWrapper(Gauge gauge, AtomicLong messageCount) {
            super(gauge, messageCount);
        }

        private void setMessageCount(long messageCount) {
            this._2.set(messageCount);
        }
    }

}
