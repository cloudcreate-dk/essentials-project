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

import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.shared.interceptor.*;

import java.util.*;

public interface DurableQueuesInterceptor extends Interceptor {
    /**
     * This method will be called by the {@link DurableQueues} instance that the {@link DurableQueuesInterceptor} is added to
     *
     * @param durableQueues the durable queue instance that this interceptor is added to
     */
    void setDurableQueues(DurableQueues durableQueues);

    /**
     * Intercept {@link GetDeadLetterMessage} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the message wrapped in an {@link Optional} if the message exists and {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    default Optional<QueuedMessage> intercept(GetDeadLetterMessage operation, InterceptorChain<GetDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetQueuedMessage} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the message wrapped in an {@link Optional} if the message exists and NOT a {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    default Optional<QueuedMessage> intercept(GetQueuedMessage operation, InterceptorChain<GetQueuedMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link ConsumeFromQueue} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the queue consumer
     */
    default DurableQueueConsumer intercept(ConsumeFromQueue operation, InterceptorChain<ConsumeFromQueue, DurableQueueConsumer, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link StopConsumingFromQueue} calls - is initiated when {@link DurableQueueConsumer#cancel()} is called
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the queue consumer that was stopped
     */
    default DurableQueueConsumer intercept(StopConsumingFromQueue operation, InterceptorChain<StopConsumingFromQueue, DurableQueueConsumer, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link QueueMessage} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the unique entry id for the message queued
     */
    default QueueEntryId intercept(QueueMessage operation, InterceptorChain<QueueMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link QueueMessageAsDeadLetterMessage} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the unique entry id for the message queued
     */
    default QueueEntryId intercept(QueueMessageAsDeadLetterMessage operation, InterceptorChain<QueueMessageAsDeadLetterMessage, QueueEntryId, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link QueueMessages} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the unique entry id's for the messages queued, ordered in the same order as the payloads that were queued
     */
    default List<QueueEntryId> intercept(QueueMessages operation, InterceptorChain<QueueMessages, List<QueueEntryId>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link RetryMessage} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    default Optional<QueuedMessage> intercept(RetryMessage operation, InterceptorChain<RetryMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link MarkAsDeadLetterMessage} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    default Optional<QueuedMessage> intercept(MarkAsDeadLetterMessage operation, InterceptorChain<MarkAsDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link ResurrectDeadLetterMessage} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the {@link QueuedMessage} message wrapped in an {@link Optional} if the operation was successful, otherwise it returns an {@link Optional#empty()}
     */
    default Optional<QueuedMessage> intercept(ResurrectDeadLetterMessage operation, InterceptorChain<ResurrectDeadLetterMessage, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link AcknowledgeMessageAsHandled} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return true if the operation went well, otherwise false
     */
    default boolean intercept(AcknowledgeMessageAsHandled operation, InterceptorChain<AcknowledgeMessageAsHandled, Boolean, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link DeleteMessage} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return true if the operation went well, otherwise false
     */
    default boolean intercept(DeleteMessage operation, InterceptorChain<DeleteMessage, Boolean, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetNextMessageReadyForDelivery} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the next message ready to be delivered (wrapped in an {@link Optional}) or {@link Optional#empty()} if no message is ready for delivery
     */
    default Optional<QueuedMessage> intercept(GetNextMessageReadyForDelivery operation, InterceptorChain<GetNextMessageReadyForDelivery, Optional<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetTotalMessagesQueuedFor} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the number of queued messages for the given queue
     */
    default long intercept(GetTotalMessagesQueuedFor operation, InterceptorChain<GetTotalMessagesQueuedFor, Long, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetQueuedMessageCountsFor} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the total number of (non-dead-letter) messages queued and number of queued dead-letter Messages for the given queue
     */
    default QueuedMessageCounts intercept(GetQueuedMessageCountsFor operation, InterceptorChain<GetQueuedMessageCountsFor, QueuedMessageCounts, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetTotalDeadLetterMessagesQueuedFor} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the number of dead-letter-messages/poison-messages queued for the given queue
     */
    default long intercept(GetTotalDeadLetterMessagesQueuedFor operation, InterceptorChain<GetTotalDeadLetterMessagesQueuedFor, Long, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetQueuedMessages} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the messages matching the criteria
     */
    default List<QueuedMessage> intercept(GetQueuedMessages operation, InterceptorChain<GetQueuedMessages, List<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetDeadLetterMessages} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the dead letter messages matching the criteria
     */
    default List<QueuedMessage> intercept(GetDeadLetterMessages operation, InterceptorChain<GetDeadLetterMessages, List<QueuedMessage>, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link PurgeQueue} calls
     *
     * @param operation        the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the number of deleted messages
     */
    default int intercept(PurgeQueue operation, InterceptorChain<PurgeQueue, Integer, DurableQueuesInterceptor> interceptorChain) {
        return interceptorChain.proceed();
    }
}
