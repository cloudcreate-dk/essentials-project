package dk.cloudcreate.essentials.components.foundation.messaging;

import java.time.Duration;

public class RedeliveryPolicyBuilder {
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