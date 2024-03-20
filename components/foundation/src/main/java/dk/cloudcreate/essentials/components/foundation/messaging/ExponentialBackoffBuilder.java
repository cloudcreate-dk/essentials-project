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

package dk.cloudcreate.essentials.components.foundation.messaging;

import java.time.Duration;

/**
 * Builder for {@link RedeliveryPolicy} that allows for defining
 * an Exponential Backoff strategy - similar to {@link RedeliveryPolicyBuilder}
 * @see RedeliveryPolicy#builder()
 * @see RedeliveryPolicy#exponentialBackoff()
 * @see RedeliveryPolicy#linearBackoff()
 * @see RedeliveryPolicy#fixedBackoff()
 */
public final class ExponentialBackoffBuilder {
    private Duration                    initialRedeliveryDelay;
    private Duration                    followupRedeliveryDelay;
    private double                      followupRedeliveryDelayMultiplier;
    private Duration                    maximumFollowupRedeliveryDelayThreshold;
    private int                         maximumNumberOfRedeliveries;
    private MessageDeliveryErrorHandler deliveryErrorHandler = MessageDeliveryErrorHandler.alwaysRetry();

    public ExponentialBackoffBuilder setInitialRedeliveryDelay(Duration initialRedeliveryDelay) {
        this.initialRedeliveryDelay = initialRedeliveryDelay;
        return this;
    }

    public ExponentialBackoffBuilder setFollowupRedeliveryDelay(Duration followupRedeliveryDelay) {
        this.followupRedeliveryDelay = followupRedeliveryDelay;
        return this;
    }

    public ExponentialBackoffBuilder setFollowupRedeliveryDelayMultiplier(double followupRedeliveryDelayMultiplier) {
        this.followupRedeliveryDelayMultiplier = followupRedeliveryDelayMultiplier;
        return this;
    }

    public ExponentialBackoffBuilder setMaximumFollowupRedeliveryDelayThreshold(Duration maximumFollowupRedeliveryDelayThreshold) {
        this.maximumFollowupRedeliveryDelayThreshold = maximumFollowupRedeliveryDelayThreshold;
        return this;
    }

    public ExponentialBackoffBuilder setMaximumNumberOfRedeliveries(int maximumNumberOfRedeliveries) {
        this.maximumNumberOfRedeliveries = maximumNumberOfRedeliveries;
        return this;
    }

    public ExponentialBackoffBuilder setDeliveryErrorHandler(MessageDeliveryErrorHandler deliveryErrorHandler) {
        this.deliveryErrorHandler = deliveryErrorHandler;
        return this;
    }

    public RedeliveryPolicy build() {
        return RedeliveryPolicy.builder()
                               .setInitialRedeliveryDelay(initialRedeliveryDelay)
                               .setFollowupRedeliveryDelay(followupRedeliveryDelay)
                               .setFollowupRedeliveryDelayMultiplier(followupRedeliveryDelayMultiplier)
                               .setMaximumFollowupRedeliveryDelayThreshold(maximumFollowupRedeliveryDelayThreshold)
                               .setMaximumNumberOfRedeliveries(maximumNumberOfRedeliveries)
                               .setDeliveryErrorHandler(deliveryErrorHandler)
                               .build();
    }
}