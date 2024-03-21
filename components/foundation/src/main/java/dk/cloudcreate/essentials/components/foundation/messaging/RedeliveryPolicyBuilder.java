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

public final class RedeliveryPolicyBuilder {
    private Duration                    initialRedeliveryDelay;
    private Duration                    followupRedeliveryDelay;
    private double                      followupRedeliveryDelayMultiplier;
    private Duration                    maximumFollowupRedeliveryDelayThreshold;
    private int                         maximumNumberOfRedeliveries;
    private MessageDeliveryErrorHandler deliveryErrorHandler = MessageDeliveryErrorHandler.alwaysRetry();

    public RedeliveryPolicyBuilder setInitialRedeliveryDelay(Duration initialRedeliveryDelay) {
        this.initialRedeliveryDelay = initialRedeliveryDelay;
        return this;
    }

    public RedeliveryPolicyBuilder setFollowupRedeliveryDelay(Duration followupRedeliveryDelay) {
        this.followupRedeliveryDelay = followupRedeliveryDelay;
        return this;
    }

    public RedeliveryPolicyBuilder setFollowupRedeliveryDelayMultiplier(double followupRedeliveryDelayMultiplier) {
        this.followupRedeliveryDelayMultiplier = followupRedeliveryDelayMultiplier;
        return this;
    }

    public RedeliveryPolicyBuilder setMaximumFollowupRedeliveryDelayThreshold(Duration maximumFollowupRedeliveryDelayThreshold) {
        this.maximumFollowupRedeliveryDelayThreshold = maximumFollowupRedeliveryDelayThreshold;
        return this;
    }

    public RedeliveryPolicyBuilder setMaximumNumberOfRedeliveries(int maximumNumberOfRedeliveries) {
        this.maximumNumberOfRedeliveries = maximumNumberOfRedeliveries;
        return this;
    }

    public RedeliveryPolicyBuilder setDeliveryErrorHandler(MessageDeliveryErrorHandler deliveryErrorHandler) {
        this.deliveryErrorHandler = deliveryErrorHandler;
        return this;
    }

    public RedeliveryPolicy build() {
        return new RedeliveryPolicy(initialRedeliveryDelay,
                                    followupRedeliveryDelay,
                                    followupRedeliveryDelayMultiplier,
                                    maximumFollowupRedeliveryDelayThreshold,
                                    maximumNumberOfRedeliveries,
                                    deliveryErrorHandler);
    }
}