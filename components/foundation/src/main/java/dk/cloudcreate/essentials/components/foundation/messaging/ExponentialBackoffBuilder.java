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
public class ExponentialBackoffBuilder {
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