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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;
import io.micrometer.observation.*;
import io.micrometer.observation.transport.*;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class DurableQueuesMicrometerTracingInterceptor implements DurableQueuesInterceptor {
    public static final String QUEUE_ENTRY_ID = "queueEntryId";
    public static final String QUEUE_NAME = "queueName";
    private final Tracer tracer;
    private final Propagator                     propagator;
    private final ObservationRegistry            observationRegistry;
    private final boolean                        verboseTracing;
    private final ThreadLocal<Observation.Scope> activeObservationScope = new ThreadLocal<>();
    private       DurableQueues                  durableQueues;

    /**
     * @param tracer              The {@link Tracer}
     * @param propagator          the {@link Propagator}
     * @param observationRegistry the {@link ObservationRegistry}
     * @param verboseTracing      Should the Tracing produces only include all operations or only top level operations
     */
    public DurableQueuesMicrometerTracingInterceptor(Tracer tracer,
                                                     Propagator propagator,
                                                     ObservationRegistry observationRegistry,
                                                     boolean verboseTracing) {
        this.tracer = requireNonNull(tracer, "No tracer instance provided");
        this.propagator = requireNonNull(propagator, "No propagator instance provided");
        this.observationRegistry = requireNonNull(observationRegistry, "No observationRegistry instance provided");
        this.verboseTracing = verboseTracing;
    }

    @Override
    public void setDurableQueues(DurableQueues durableQueues) {
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    @Override
    public QueueEntryId intercept(QueueMessage operation, InterceptorChain<QueueMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        storeTraceContext(operation.getMetaData());
        var observation = Observation.createNotStarted("QueueMessage:" + operation.queueName.toString(), observationRegistry)
                                     .lowCardinalityKeyValue(QUEUE_NAME, operation.queueName.toString());
        return observation.observe(() -> {
            var queueEntryId = interceptorChain.proceed();
            observation.highCardinalityKeyValue(QUEUE_ENTRY_ID, queueEntryId.toString());
            return queueEntryId;
        });
    }

    @Override
    public List<QueueEntryId> intercept(QueueMessages operation, InterceptorChain<QueueMessages, List<QueueEntryId>, DurableQueuesInterceptor> interceptorChain) {
        operation.messages.forEach(message -> storeTraceContext(message.getMetaData()));
        return Observation.createNotStarted("QueueMessages:" + operation.queueName.toString(), observationRegistry)
                          .highCardinalityKeyValue("numberOfMessages", Integer.toString(operation.messages.size()))
                          .lowCardinalityKeyValue(QUEUE_NAME, operation.queueName.toString())
                          .observe(interceptorChain::proceed);
    }

    @Override
    public Optional<QueuedMessage> intercept(GetDeadLetterMessage operation, InterceptorChain<GetDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        if (verboseTracing) {
            var potentialMessage = Observation.createNotStarted("GetDeadLetterMessage", observationRegistry)
                                              .highCardinalityKeyValue(QUEUE_ENTRY_ID, operation.queueEntryId.toString())
                                              .observe(interceptorChain::proceed);
            return potentialMessage.map(queuedMessage -> restoreTraceContext(queuedMessage, "DeadLetterMessage"));
        }
        return interceptorChain.proceed();
    }

    @Override
    public Optional<QueuedMessage> intercept(GetQueuedMessage operation, InterceptorChain<GetQueuedMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        if (verboseTracing) {
            var potentialMessage = Observation.createNotStarted("GetQueuedMessage", observationRegistry)
                                              .highCardinalityKeyValue(QUEUE_ENTRY_ID, operation.queueEntryId.toString())
                                              .observe(interceptorChain::proceed);

            return potentialMessage.map(queuedMessage -> restoreTraceContext(queuedMessage, "GetQueuedMessage"));
        }
        return interceptorChain.proceed();
    }

    @Override
    public Optional<QueuedMessage> intercept(GetNextMessageReadyForDelivery operation, InterceptorChain<GetNextMessageReadyForDelivery, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        var potentialMessage = interceptorChain.proceed();
        return potentialMessage.map(queuedMessage -> restoreTraceContext(queuedMessage, "DeliverMessage"));
    }

    @Override
    public QueueEntryId intercept(QueueMessageAsDeadLetterMessage operation, InterceptorChain<QueueMessageAsDeadLetterMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        storeTraceContext(operation.getMetaData());
        return Observation.createNotStarted("QueueMessageAsDeadLetterMessage:" + operation.queueName.toString(), observationRegistry)
                          .lowCardinalityKeyValue(QUEUE_NAME, operation.queueName.toString())
                          .observe(interceptorChain::proceed);
    }

    @Override
    public Optional<QueuedMessage> intercept(RetryMessage operation, InterceptorChain<RetryMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return Observation.createNotStarted("RetryMessage", observationRegistry)
                          .highCardinalityKeyValue(QUEUE_ENTRY_ID, operation.queueEntryId.toString())
                          .observe(() -> {
                              var result = interceptorChain.proceed();
                              closeAnyActiveObservationScope();
                              return result;
                          });
    }

    @Override
    public Optional<QueuedMessage> intercept(MarkAsDeadLetterMessage operation, InterceptorChain<MarkAsDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return Observation.createNotStarted("MarkAsDeadLetterMessage", observationRegistry)
                          .highCardinalityKeyValue(QUEUE_ENTRY_ID, operation.queueEntryId.toString())
                          .observe(() -> {
                              var result = interceptorChain.proceed();
                              closeAnyActiveObservationScope();
                              return result;
                          });
    }

    @Override
    public boolean intercept(AcknowledgeMessageAsHandled operation, InterceptorChain<AcknowledgeMessageAsHandled, Boolean, DurableQueuesInterceptor> interceptorChain) {
        if (verboseTracing) {
            return Boolean.TRUE.equals(Observation.createNotStarted("AcknowledgeMessageAsHandled", observationRegistry)
                                                  .highCardinalityKeyValue(QUEUE_ENTRY_ID, operation.queueEntryId.toString())
                                                  .observe(() -> {
                                                      var result = interceptorChain.proceed();
                                                      closeAnyActiveObservationScope();
                                                      return result;
                                                  }));
        } else {
            var result = interceptorChain.proceed();
            closeAnyActiveObservationScope();
            return result;
        }
    }

    @Override
    public boolean intercept(DeleteMessage operation, InterceptorChain<DeleteMessage, Boolean, DurableQueuesInterceptor> interceptorChain) {
        if (verboseTracing) {
            return Boolean.TRUE.equals(Observation.createNotStarted("DeleteMessage", observationRegistry)
                                                  .highCardinalityKeyValue(QUEUE_ENTRY_ID, operation.queueEntryId.toString())
                                                  .observe(() -> {
                                                      var result = interceptorChain.proceed();
                                                      closeAnyActiveObservationScope();
                                                      return result;
                                                  }));
        } else {
            var result = interceptorChain.proceed();
            closeAnyActiveObservationScope();
            return result;
        }
    }

    protected void storeTraceContext(MessageMetaData messageMetaData) {
        if (messageMetaData != null) {
            var currentTraceContext = tracer.currentTraceContext();
            if (currentTraceContext != null && currentTraceContext.context() != null) {
                propagator.inject(currentTraceContext.context(),
                                  messageMetaData,
                                  MessageMetaData::put);
            }
        }
    }

    protected QueuedMessage restoreTraceContext(QueuedMessage queuedMessage, String contextDescription) {
        requireNonNull(queuedMessage, "No queuedMessage provided");
        requireNonNull(contextDescription, "No contextDescription provided");
        closeAnyActiveObservationScope();
        var observation = Observation.start(contextDescription + ":" + queuedMessage.getQueueName().toString(),
                                            () -> createTraceContextForMessage(queuedMessage),
                                            observationRegistry);
        observation.lowCardinalityKeyValue(QUEUE_NAME, queuedMessage.getQueueName().toString());
        observation.highCardinalityKeyValue(QUEUE_ENTRY_ID, queuedMessage.getId().toString());
        observation.highCardinalityKeyValue("addedTimestamp", queuedMessage.getAddedTimestamp().toString());
        observation.highCardinalityKeyValue("deliveryTimestamp", queuedMessage.getDeliveryTimestamp() != null ? queuedMessage.getDeliveryTimestamp().toString() : "");
        observation.highCardinalityKeyValue("totalDeliveryAttempts", Integer.toString(queuedMessage.getTotalDeliveryAttempts()));
        observation.highCardinalityKeyValue("redeliveryAttempts", Integer.toString(queuedMessage.getRedeliveryAttempts()));
        activeObservationScope.set(observation.openScope());
        return queuedMessage;
    }

    private ReceiverContext<MessageMetaData> createTraceContextForMessage(QueuedMessage queuedMessage) {
        requireNonNull(queuedMessage, "No queuedMessage provided");
        var context = new ReceiverContext<>(MessageMetaData::get,
                                            Kind.CONSUMER);
        context.setCarrier(queuedMessage.getMetaData());
        return context;
    }

    private void closeAnyActiveObservationScope() {
        var activeScope = activeObservationScope.get();
        if (activeScope != null) {
            activeScope.close();
            activeScope.getCurrentObservation().stop();
            activeObservationScope.remove();
        }
    }

}
