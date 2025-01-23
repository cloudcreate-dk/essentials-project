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

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;

import java.time.Duration;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.*;

/**
 * In case the message delivery, handled by the {@link DurableQueueConsumer}, experiences an error/exception,
 * then the {@link RedeliveryPolicy} determines, with the aid of the {@link MessageDeliveryErrorHandler} and the provided
 * delivery settings, IF a Message should be retried ({@link DurableQueues#retryMessage(RetryMessage)}
 * or if it's going to be marked as a Poison-Message/Dead-Letter-Message ({@link DurableQueues#markAsDeadLetterMessage(MarkAsDeadLetterMessage)})
 *
 * @see RedeliveryPolicy#builder()
 * @see RedeliveryPolicy#exponentialBackoff()
 * @see RedeliveryPolicy#linearBackoff()
 * @see RedeliveryPolicy#fixedBackoff()
 */
public final class RedeliveryPolicy {
    public final Duration                    initialRedeliveryDelay;
    public final Duration                    followupRedeliveryDelay;
    public final double                      followupRedeliveryDelayMultiplier;
    public final Duration                    maximumFollowupRedeliveryThreshold;
    public final int                         maximumNumberOfRedeliveries;
    public final MessageDeliveryErrorHandler deliveryErrorHandler;

    /**
     * Create a generic builder for defining a {@link RedeliveryPolicy}
     *
     * @return a generic builder for defining a {@link RedeliveryPolicy}
     */
    public static RedeliveryPolicyBuilder builder() {
        return new RedeliveryPolicyBuilder();
    }

    /**
     * Create a builder for defining a {@link RedeliveryPolicy} that allows for defining
     * an Exponential Backoff strategy
     *
     * @return a builder for defining a {@link RedeliveryPolicy} that allows for defining
     * an Exponential Backoff strategy
     */
    public static ExponentialBackoffBuilder exponentialBackoff() {
        return new ExponentialBackoffBuilder();
    }

    /**
     * Create a builder for defining a {@link RedeliveryPolicy} with a Linear Backoff strategy
     *
     * @return a builder for defining a {@link RedeliveryPolicy} with a Linear Backoff strategy
     */
    public static LinearBackoffBuilder linearBackoff() {
        return new LinearBackoffBuilder();
    }

    /**
     * Create a builder for defining a {@link RedeliveryPolicy} with a Fixed Backoff strategy
     *
     * @return a builder for defining a {@link RedeliveryPolicy} with a Fixed Backoff strategy
     */
    public static FixedBackoffBuilder fixedBackoff() {
        return new FixedBackoffBuilder();
    }

    public RedeliveryPolicy(Duration initialRedeliveryDelay,
                            Duration followupRedeliveryDelay,
                            double followupRedeliveryDelayMultiplier,
                            Duration maximumFollowupRedeliveryDelayThreshold,
                            int maximumNumberOfRedeliveries,
                            MessageDeliveryErrorHandler deliveryErrorHandler) {
        this.initialRedeliveryDelay = requireNonNull(initialRedeliveryDelay, "You must specify an initialRedeliveryDelay");
        this.followupRedeliveryDelay = requireNonNull(followupRedeliveryDelay, "You must specify an followupRedeliveryDelay");
        this.followupRedeliveryDelayMultiplier = followupRedeliveryDelayMultiplier;
        this.maximumFollowupRedeliveryThreshold = requireNonNull(maximumFollowupRedeliveryDelayThreshold, "You must specify an maximumFollowupRedeliveryDelayThreshold");
        this.maximumNumberOfRedeliveries = maximumNumberOfRedeliveries;
        this.deliveryErrorHandler = requireNonNull(deliveryErrorHandler, "You must specify a " + MessageDeliveryErrorHandler.class.getSimpleName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedeliveryPolicy that = (RedeliveryPolicy) o;
        return Double.compare(that.followupRedeliveryDelayMultiplier, followupRedeliveryDelayMultiplier) == 0 &&
                maximumNumberOfRedeliveries == that.maximumNumberOfRedeliveries &&
                Objects.equals(initialRedeliveryDelay, that.initialRedeliveryDelay) &&
                Objects.equals(followupRedeliveryDelay, that.followupRedeliveryDelay) &&
                Objects.equals(maximumFollowupRedeliveryThreshold, that.maximumFollowupRedeliveryThreshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialRedeliveryDelay, followupRedeliveryDelay, followupRedeliveryDelayMultiplier,
                            maximumFollowupRedeliveryThreshold, maximumNumberOfRedeliveries);
    }

    @Override
    public String toString() {
        return "RedeliveryPolicy{" +
                "initialRedeliveryDelay=" + initialRedeliveryDelay +
                ", followupRedeliveryDelay=" + followupRedeliveryDelay +
                ", followupRedeliveryDelayMultiplier=" + followupRedeliveryDelayMultiplier +
                ", maximumFollowupRedeliveryThreshold=" + maximumFollowupRedeliveryThreshold +
                ", maximumNumberOfRedeliveries=" + maximumNumberOfRedeliveries +
                ", deliveryErrorHandler=" + deliveryErrorHandler +
                '}';
    }

    public Duration calculateNextRedeliveryDelay(int currentNumberOfRedeliveryAttempts) {
        requireTrue(currentNumberOfRedeliveryAttempts >= 0, "currentNumberOfRedeliveryAttempts must be 0 or larger");
        if (currentNumberOfRedeliveryAttempts == 0) {
            return initialRedeliveryDelay;
        }
        var calculatedRedeliveryDelay = initialRedeliveryDelay.plus(
                Duration.ofMillis((long) (followupRedeliveryDelay.toMillis() * followupRedeliveryDelayMultiplier)));
        if (calculatedRedeliveryDelay.compareTo(maximumFollowupRedeliveryThreshold) >= 0) {
            return maximumFollowupRedeliveryThreshold;
        } else {
            return calculatedRedeliveryDelay;
        }
    }

    public static RedeliveryPolicy fixedBackoff(Duration redeliveryDelay,
                                                int maximumNumberOfRedeliveries) {
        return builder().setInitialRedeliveryDelay(redeliveryDelay)
                        .setFollowupRedeliveryDelay(redeliveryDelay)
                        .setFollowupRedeliveryDelayMultiplier(1.0d)
                        .setMaximumFollowupRedeliveryDelayThreshold(redeliveryDelay)
                        .setMaximumNumberOfRedeliveries(maximumNumberOfRedeliveries)
                        .setDeliveryErrorHandler(MessageDeliveryErrorHandler.alwaysRetry())
                        .build();
    }

    public static RedeliveryPolicy linearBackoff(Duration redeliveryDelay,
                                                 Duration maximumFollowupRedeliveryDelayThreshold,
                                                 int maximumNumberOfRedeliveries) {
        return builder().setInitialRedeliveryDelay(redeliveryDelay)
                        .setFollowupRedeliveryDelay(redeliveryDelay)
                        .setFollowupRedeliveryDelayMultiplier(1.0d)
                        .setMaximumFollowupRedeliveryDelayThreshold(maximumFollowupRedeliveryDelayThreshold)
                        .setMaximumNumberOfRedeliveries(maximumNumberOfRedeliveries)
                        .setDeliveryErrorHandler(MessageDeliveryErrorHandler.alwaysRetry())
                        .build();
    }

    public static RedeliveryPolicy exponentialBackoff(Duration initialRedeliveryDelay,
                                                      Duration followupRedeliveryDelay,
                                                      double followupRedeliveryDelayMultiplier,
                                                      Duration maximumFollowupRedeliveryDelayThreshold,
                                                      int maximumNumberOfRedeliveries) {
        return builder().setInitialRedeliveryDelay(initialRedeliveryDelay)
                        .setFollowupRedeliveryDelay(followupRedeliveryDelay)
                        .setFollowupRedeliveryDelayMultiplier(followupRedeliveryDelayMultiplier)
                        .setMaximumFollowupRedeliveryDelayThreshold(maximumFollowupRedeliveryDelayThreshold)
                        .setMaximumNumberOfRedeliveries(maximumNumberOfRedeliveries)
                        .setDeliveryErrorHandler(MessageDeliveryErrorHandler.alwaysRetry())
                        .build();
    }

    /**
     * If an exception occurs during message handling, the {@link #isPermanentError(QueuedMessage, Throwable)} will be called with
     * only the top-level exception - the associated {@link MessageDeliveryErrorHandler#isPermanentError(QueuedMessage, Throwable)} must itself check the error exceptions causal chain
     * to determine if it represents a permanent error.
     * @param queuedMessage The message being processed by the message handler
     * @param error the exception that occurred
     * @return true if the error represents a permanent error, otherwise false
     */
    public boolean isPermanentError(QueuedMessage queuedMessage, Throwable error) {
        return deliveryErrorHandler.isPermanentError(queuedMessage, error);
    }
}
