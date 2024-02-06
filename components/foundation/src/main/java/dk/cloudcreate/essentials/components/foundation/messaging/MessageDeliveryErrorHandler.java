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

package dk.cloudcreate.essentials.components.foundation.messaging;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Strategy for resolving which errors, experienced during Message delivery by the {@link DurableQueueConsumer},
 * should be instantly marked as a Poison-Message/Dead-Letter-Message or
 * if we can attempt message redelivery according to the specified {@link RedeliveryPolicy}
 */
@FunctionalInterface
public interface MessageDeliveryErrorHandler {
    /**
     * This method is called when the {@link DurableQueueConsumer} experiences an exception during Message delivery.<br>
     * The result of this method determines if the Message is instantly marked as a Poison-Message/Dead-Letter-Message or
     * if we can attempt message redelivery according to the specified {@link RedeliveryPolicy}
     *
     * @param queuedMessage the message that failed to be delivered
     * @param error         the error experienced
     * @return true if the error is a permanent error - in which case the message is instantly marked as an Poison-Message/Dead-Letter-Message,
     * otherwise false - in which case the message will be redelivered according to the specified {@link RedeliveryPolicy}
     */
    boolean isPermanentError(QueuedMessage queuedMessage, Throwable error);

    /**
     * Create a {@link MessageDeliveryErrorHandler} that always retries no matter which exception occurs
     *
     * @return a {@link MessageDeliveryErrorHandler} that always retries no matter which exception occurs
     */
    static MessageDeliveryErrorHandler alwaysRetry() {
        return new AlwaysRetry();
    }

    /**
     * Create a {@link MessageDeliveryErrorHandler} that stops message redelivery in case
     * message handling experiences an exception for in the array of <code>exceptions</code>.<br>
     * It will first attempt to match directly on {@link Exception} class, next it will attempt to match on hierarchy (i.e.
     * a concrete error which is a subtype of an {@link Exception} found in the <code>exceptions</code> will also match)
     *
     * @param exceptions the array of exceptions where message redelivery will be stopped and the Message will be
     *                   instantly marked as an Poison-Message/Dead-Letter-Message or
     * @return a {@link MessageDeliveryErrorHandler} that stops message redelivery in case
     * message handling experiences an exception for in the list of <code>exceptions</code>
     */
    static MessageDeliveryErrorHandler stopRedeliveryOn(Class<? extends Exception>... exceptions) {
        requireNonNull(exceptions, "Null provided instead of an array of exception classes");
        return stopRedeliveryOn(List.of(exceptions));
    }

    /**
     * Create a {@link MessageDeliveryErrorHandler} that stops message redelivery in case
     * message handling experiences an exception for in the list of <code>exceptions</code>.<br>
     * It will first attempt to match directly on {@link Exception} class, next it will attempt to match on hierarchy (i.e.
     * a concrete error which is a subtype of an {@link Exception} found in the <code>exceptions</code> will also match)
     *
     * @param exceptions the list of exceptions where message redelivery will be stopped and the Message will be
     *                   instantly marked as a Poison-Message/Dead-Letter-Message
     * @return a {@link MessageDeliveryErrorHandler} that stops message redelivery in case
     * message handling experiences an exception for in the list of <code>exceptions</code>
     */
    static MessageDeliveryErrorHandler stopRedeliveryOn(List<Class<? extends Exception>> exceptions) {
        requireNonNull(exceptions, "No exceptions list provided");
        return new StopRedeliveryOn(exceptions);
    }

    /**
     * Create a new builder for a flexible {@link MessageDeliveryErrorHandler} which supports
     * both alwaysRetryOnExceptions and stopRedeliveryOnExceptions
     * @return the builder instance
     */
    static MessageDeliveryErrorHandlerBuilder builder() {
        return new MessageDeliveryErrorHandlerBuilder();
    }

    class StopRedeliveryOn implements MessageDeliveryErrorHandler {
        private final List<Class<? extends Exception>> exceptions;

        public StopRedeliveryOn(List<Class<? extends Exception>> exceptions) {
            this.exceptions = exceptions;
        }

        @Override
        public boolean isPermanentError(QueuedMessage queuedMessage, Throwable error) {
            return exceptions.contains(error.getClass()) ||
                    exceptions.stream()
                              .anyMatch(exception -> exception.isAssignableFrom(error.getClass()));
        }

        @Override
        public String toString() {
            return "StopRedeliveryOn{" +
                    exceptions +
                    '}';
        }
    }

    class AlwaysRetry implements MessageDeliveryErrorHandler {
        @Override
        public boolean isPermanentError(QueuedMessage queuedMessage, Throwable error) {
            return false;
        }

        @Override
        public String toString() {
            return "AlwaysRetry";
        }
    }

    class NeverRetry implements MessageDeliveryErrorHandler {
        @Override
        public boolean isPermanentError(QueuedMessage queuedMessage, Throwable error) {
            return true;
        }

        @Override
        public String toString() {
            return "NeverRetry";
        }
    }
}
