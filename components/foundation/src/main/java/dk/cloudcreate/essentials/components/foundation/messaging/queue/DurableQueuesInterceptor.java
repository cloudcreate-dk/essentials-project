package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.shared.interceptor.*;

import java.util.*;

public interface DurableQueuesInterceptor extends Interceptor {
    /**
     * Intercept {@link GetDeadLetterMessage} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the message wrapped in an {@link Optional} if the message exists and {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    default Optional<QueuedMessage> intercept(GetDeadLetterMessage operation, InterceptorChain<GetDeadLetterMessage, Optional<QueuedMessage>> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetQueuedMessage} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the message wrapped in an {@link Optional} if the message exists and NOT a {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    default Optional<QueuedMessage> intercept(GetQueuedMessage operation, InterceptorChain<GetQueuedMessage, Optional<QueuedMessage>> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link ConsumeFromQueue} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the queue consumer
     */
    default DurableQueueConsumer intercept(ConsumeFromQueue operation, InterceptorChain<ConsumeFromQueue, DurableQueueConsumer> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link QueueMessage} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the unique entry id for the message queued
     */
    default QueueEntryId intercept(QueueMessage operation, InterceptorChain<QueueMessage, QueueEntryId> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link QueueMessageAsDeadLetterMessage} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the unique entry id for the message queued
     */
    default QueueEntryId intercept(QueueMessageAsDeadLetterMessage operation, InterceptorChain<QueueMessage, QueueEntryId> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link QueueMessages} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the unique entry id's for the messages queued, ordered in the same order as the payloads that were queued
     */
    default List<QueueEntryId> intercept(QueueMessages operation, InterceptorChain<QueueMessages, List<QueueEntryId>> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link RetryMessage} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return true if the operation went well, otherwise false
     */
    default boolean intercept(RetryMessage operation, InterceptorChain<RetryMessage, Boolean> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link MarkAsDeadLetterMessage} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return true if the operation went well, otherwise false
     */
    default boolean intercept(MarkAsDeadLetterMessage operation, InterceptorChain<MarkAsDeadLetterMessage, Boolean> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link ResurrectDeadLetterMessage} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return true if the operation went well, otherwise false
     */
    default boolean intercept(ResurrectDeadLetterMessage operation, InterceptorChain<ResurrectDeadLetterMessage, Boolean> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link AcknowledgeMessageAsHandled} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return true if the operation went well, otherwise false
     */
    default boolean intercept(AcknowledgeMessageAsHandled operation, InterceptorChain<AcknowledgeMessageAsHandled, Boolean> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link DeleteMessage} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return true if the operation went well, otherwise false
     */
    default boolean intercept(DeleteMessage operation, InterceptorChain<DeleteMessage, Boolean> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetNextMessageReadyForDelivery} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the next message ready to be delivered (wrapped in an {@link Optional}) or {@link Optional#empty()} if no message is ready for delivery
     */
    default Optional<QueuedMessage> intercept(GetNextMessageReadyForDelivery operation, InterceptorChain<GetNextMessageReadyForDelivery, Optional<QueuedMessage>> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetTotalMessagesQueuedFor} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the number of queued messages for the given queue
     */
    default long intercept(GetTotalMessagesQueuedFor operation, InterceptorChain<GetTotalMessagesQueuedFor, Long> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetQueuedMessages} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return  the messages matching the criteria
     */
    default List<QueuedMessage> intercept(GetQueuedMessages operation, InterceptorChain<GetQueuedMessages, List<QueuedMessage>> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link GetDeadLetterMessages} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the dead letter messages matching the criteria
     */
    default List<QueuedMessage> intercept(GetDeadLetterMessages operation, InterceptorChain<GetDeadLetterMessages, List<QueuedMessage>> interceptorChain) {
        return interceptorChain.proceed();
    }

    /**
     * Intercept {@link PurgeQueue} calls
     * @param operation the operation
     * @param interceptorChain the interceptor chain (call {@link InterceptorChain#proceed()} to continue the processing chain)
     * @return the number of deleted messages
     */
    default int intercept(PurgeQueue operation, InterceptorChain<PurgeQueue, Integer> interceptorChain) {
        return interceptorChain.proceed();
    }
}
