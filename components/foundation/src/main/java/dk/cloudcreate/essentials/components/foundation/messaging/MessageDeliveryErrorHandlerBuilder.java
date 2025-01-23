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

package dk.cloudcreate.essentials.components.foundation.messaging;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueuedMessage;
import dk.cloudcreate.essentials.shared.Exceptions;

import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Builder for {@link MessageDeliveryErrorHandler}
 */
public final class MessageDeliveryErrorHandlerBuilder {
    private List<Class<? extends Exception>> alwaysRetryOnExceptions    = List.of();
    private List<Class<? extends Exception>> stopRedeliveryOnExceptions = List.of();

    /**
     * The resulting {@link MessageDeliveryErrorHandler} will keep retrying message redelivery no matter how many times
     * message handling experiences an exception for in the list of <code>exceptions</code>.<br>
     * It will first attempt to match directly on {@link Exception} class, next it will attempt to match on hierarchy (i.e.
     * a concrete error which is a subtype of an {@link Exception} found in the <code>exceptions</code> will also match)
     *
     * @param exceptions the exceptions where message redelivery will be continued no matter how many times this exception occurs
     * @return this builder instance
     */
    @SafeVarargs
    public final MessageDeliveryErrorHandlerBuilder alwaysRetryOn(Class<? extends Exception>... exceptions) {
        return alwaysRetryOn(List.of(exceptions));
    }

    /**
     * The resulting {@link MessageDeliveryErrorHandler} will keep retrying message redelivery no matter how many times
     * message handling experiences an exception for in the list of <code>exceptions</code>.<br>
     * It will first attempt to match directly on {@link Exception} class, next it will attempt to match on hierarchy (i.e.
     * a concrete error which is a subtype of an {@link Exception} found in the <code>exceptions</code> will also match)
     *
     * @param exceptions the exceptions where message redelivery will be continued no matter how many times this exception occurs
     * @return this builder instance
     */
    public MessageDeliveryErrorHandlerBuilder alwaysRetryOn(List<Class<? extends Exception>> exceptions) {
        alwaysRetryOnExceptions = requireNonNull(exceptions, "No exceptions list provided");
        return this;
    }

    /**
     * The resulting {@link MessageDeliveryErrorHandler} will stop message redelivery in case
     * message handling experiences an exception for in the list of <code>exceptions</code>.<br>
     * It will first attempt to match directly on {@link Exception} class, next it will attempt to match on hierarchy (i.e.
     * a concrete error which is a subtype of an {@link Exception} found in the <code>exceptions</code> will also match)
     *
     * @param exceptions the exceptions where message redelivery will be stopped and the Message will be
     *                   instantly marked as a Poison-Message/Dead-Letter-Message
     * @return this builder instance
     */
    @SafeVarargs
    public final MessageDeliveryErrorHandlerBuilder stopRedeliveryOn(Class<? extends Exception>... exceptions) {
        return stopRedeliveryOn(List.of(exceptions));
    }

    /**
     * The resulting {@link MessageDeliveryErrorHandler} will stop message redelivery in case
     * message handling experiences an exception for in the list of <code>exceptions</code>.<br>
     * It will first attempt to match directly on {@link Exception} class, next it will attempt to match on hierarchy (i.e.
     * a concrete error which is a subtype of an {@link Exception} found in the <code>exceptions</code> will also match)
     *
     * @param exceptions the exceptions where message redelivery will be stopped and the Message will be
     *                   instantly marked as a Poison-Message/Dead-Letter-Message
     * @return this builder instance
     */
    public MessageDeliveryErrorHandlerBuilder stopRedeliveryOn(List<Class<? extends Exception>> exceptions) {
        stopRedeliveryOnExceptions = requireNonNull(exceptions, "No exceptions list provided");
        return this;
    }

    @Override
    public String toString() {
        return "MessageDeliveryErrorHandlerBuilder{" +
                "alwaysRetryOnExceptions=" + alwaysRetryOnExceptions +
                ", stopRedeliveryOnExceptions=" + stopRedeliveryOnExceptions +
                '}';
    }

    public MessageDeliveryErrorHandler build() {

        var stopRedeliveryOnHandler = MessageDeliveryErrorHandler.stopRedeliveryOn(stopRedeliveryOnExceptions);
        return new MessageDeliveryErrorHandler() {
            @Override
            public boolean isPermanentError(QueuedMessage queuedMessage, Throwable error) {
                if (shouldAlwaysRetryOn(error)) {
                    return false;
                }
                return stopRedeliveryOnHandler.isPermanentError(queuedMessage, error);
            }

            private boolean shouldAlwaysRetryOn(Throwable error) {
                return alwaysRetryOnExceptions.contains(error.getClass()) ||
                        alwaysRetryOnExceptions.stream()
                                               .anyMatch(alwaysRetryOnException -> Exceptions.doesStackTraceContainExceptionOfType(error, alwaysRetryOnException));
            }

            @Override
            public String toString() {
                return "MessageDeliveryErrorHandler{" +
                        "alwaysRetryOnExceptions=" + alwaysRetryOnExceptions +
                        ", stopRedeliveryOnExceptions=" + stopRedeliveryOnExceptions +
                        '}';
            }
        };
    }
}
