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

package dk.cloudcreate.essentials.components.foundation.interceptor.micrometer;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;
import dk.cloudcreate.essentials.shared.measurement.*;
import dk.cloudcreate.essentials.shared.reflection.FunctionalInterfaceLoggingNameResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Measure the time operations take using the {@link MeasurementTaker} API.
 * <p>
 * The metric name always begins with {@value #METRIC_PREFIX} and any dynamic parameters (e.g. queue_name) are added as tags.
 * The following metric suffixes are used:
 * <ul>
 *  <li>{@link DurableQueueConsumer}'s {@link HandleQueuedMessage} using suffix {@value #HANDLE_QUEUED_MSG}</li>
 *  <li>{@link DurableQueues}'s {@link GetNextMessageReadyForDelivery} using suffix {@value #GET_NEXT_MSG_RDY}</li>
 *  <li>{@link DurableQueues}'s {@link QueueMessage} using suffix {@value #QUEUE_MSG}</li>
 *  <li>{@link DurableQueues}'s {@link QueueMessages} using suffix {@value #QUEUE_MSGS}</li>
 *  <li>{@link DurableQueues}'s {@link AcknowledgeMessageAsHandled} using suffix {@value #ACK_MSG}</li>
 *  <li>{@link DurableQueues}'s {@link RetryMessage} using suffix {@value #RETRY_MSG}</li>
 *  <li>{@link DurableQueues}'s {@link MarkAsDeadLetterMessage} using suffix {@value #MARK_MSG}</li>
 * </ul>
 * Any dynamic parameters (e.g. {@value #QUEUE_NAME}, {@value #MESSAGE_PAYLOAD_TYPE}, etc.) are added as tags.
 */
public class RecordExecutionTimeDurableQueueInterceptor implements DurableQueuesInterceptor {
    public static final  String    MODULE_TAG_NAME    = "Module";
    public static final  String    METRIC_PREFIX      = "essentials.messaging.durable_queues";
    private static final QueueName UNKNOWN_QUEUE_NAME = QueueName.of("?");

    public static final String HANDLE_QUEUED_MSG    = "handle_queued_message";
    public static final String GET_NEXT_MSG_RDY     = "get_next_message_ready_for_delivery";
    public static final String QUEUE_MSG            = "queue_message";
    public static final String QUEUE_MSGS           = "queue_messages";
    public static final String ACK_MSG              = "acknowledge_message_as_handled";
    public static final String RETRY_MSG            = "retry_message";
    public static final String MARK_MSG             = "mark_as_dead_letter_message";
    // Tags
    public static final String QUEUE_NAME           = "queue_name";
    public static final String MESSAGE_PAYLOAD_TYPE = "message_payload_type";

    private       DurableQueues    durableQueues;
    private final String           moduleTag;
    private final MeasurementTaker measurementTaker;
    private final boolean          recordExecutionTimeEnabled;

    /**
     * Constructs a new interceptor.
     *
     * @param meterRegistryOptional      an Optional MeterRegistry to enable Micrometer metrics
     * @param recordExecutionTimeEnabled whether to record execution times or not
     * @param thresholds                 the logging thresholds configuration
     * @param moduleTag                  Optional {@value #MODULE_TAG_NAME} Tag value
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public RecordExecutionTimeDurableQueueInterceptor(Optional<MeterRegistry> meterRegistryOptional,
                                                      boolean recordExecutionTimeEnabled,
                                                      LogThresholds thresholds,
                                                      String moduleTag) {
        this.recordExecutionTimeEnabled = recordExecutionTimeEnabled;
        this.moduleTag = moduleTag;
        this.measurementTaker = MeasurementTaker.builder()
                                                .addRecorder(
                                                        new LoggingMeasurementRecorder(
                                                                LoggerFactory.getLogger(this.getClass()),
                                                                thresholds))
                                                .withOptionalMicrometerMeasurementRecorder(meterRegistryOptional)
                                                .build();
    }

    @Override
    public void setDurableQueues(DurableQueues durableQueues) {
        this.durableQueues = durableQueues;
    }

    @Override
    public Void intercept(HandleQueuedMessage operation, InterceptorChain<HandleQueuedMessage, Void, DurableQueuesInterceptor> interceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + "." + HANDLE_QUEUED_MSG)
                                   .description("Time taken to handle a queued message by the QueuedMessageHandler")
                                   .tag("message_handler", FunctionalInterfaceLoggingNameResolver.resolveLoggingName(operation.messageHandler))
                                   .tag(MESSAGE_PAYLOAD_TYPE, operation.getMessage().getPayload() != null ? operation.getMessage().getPayload().getClass().getName() : "?")
                                   .tag(QUEUE_NAME, operation.message.getQueueName())
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(interceptorChain::proceed);
        } else {
            return interceptorChain.proceed();
        }
    }

    @Override
    public Optional<QueuedMessage> intercept(GetNextMessageReadyForDelivery operation, InterceptorChain<GetNextMessageReadyForDelivery, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + "." + GET_NEXT_MSG_RDY)
                                   .description("Time taken to query for the next message ready for delivery")
                                   .tag(QUEUE_NAME, operation.getQueueName())
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(interceptorChain::proceed);
        } else {
            return interceptorChain.proceed();
        }
    }

    @Override
    public QueueEntryId intercept(QueueMessage operation, InterceptorChain<QueueMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + "." + QUEUE_MSG)
                                   .description("Time taken to queue a message")
                                   .tag(QUEUE_NAME, operation.getQueueName())
                                   .tag(MESSAGE_PAYLOAD_TYPE, operation.getMessage().getPayload() != null ? operation.getMessage().getPayload().getClass().getName() : "?")
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(interceptorChain::proceed);
        } else {
            return interceptorChain.proceed();
        }
    }

    @Override
    public List<QueueEntryId> intercept(QueueMessages operation, InterceptorChain<QueueMessages, List<QueueEntryId>, DurableQueuesInterceptor> interceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + "." + QUEUE_MSGS)
                                   .description("Time taken to queue a set of messages")
                                   .tag(QUEUE_NAME, operation.getQueueName())
                                   .tag("message_count", operation.getMessages().size())
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(interceptorChain::proceed);
        } else {
            return interceptorChain.proceed();
        }
    }

    @Override
    public boolean intercept(AcknowledgeMessageAsHandled operation, InterceptorChain<AcknowledgeMessageAsHandled, Boolean, DurableQueuesInterceptor> interceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + "." + ACK_MSG)
                                   .description("Time taken to acknowledge a message as handled and delete it")
                                   .tag(QUEUE_NAME, durableQueues.getQueueNameFor(operation.getQueueEntryId()).orElse(UNKNOWN_QUEUE_NAME))
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(interceptorChain::proceed);
        } else {
            return interceptorChain.proceed();
        }
    }

    @Override
    public Optional<QueuedMessage> intercept(RetryMessage operation, InterceptorChain<RetryMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + "." + RETRY_MSG)
                                   .description("Time taken to mark a message for retry")
                                   .tag(QUEUE_NAME, durableQueues.getQueueNameFor(operation.getQueueEntryId()).orElse(UNKNOWN_QUEUE_NAME))
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(interceptorChain::proceed);
        } else {
            return interceptorChain.proceed();
        }
    }

    @Override
    public Optional<QueuedMessage> intercept(MarkAsDeadLetterMessage operation, InterceptorChain<MarkAsDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + "." + MARK_MSG)
                                   .description("Time taken to mark a message as a Dead Letter Message")
                                   .tag(QUEUE_NAME, durableQueues.getQueueNameFor(operation.getQueueEntryId()).orElse(UNKNOWN_QUEUE_NAME))
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(interceptorChain::proceed);
        } else {
            return interceptorChain.proceed();
        }
    }
}
