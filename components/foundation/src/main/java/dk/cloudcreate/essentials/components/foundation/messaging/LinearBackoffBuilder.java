package dk.cloudcreate.essentials.components.foundation.messaging;

import java.time.Duration;

/**
 * A builder for defining a {@link RedeliveryPolicy} with a Linear Backoff strategy
 * @see RedeliveryPolicy#builder()
 * @see RedeliveryPolicy#exponentialBackoff()
 * @see RedeliveryPolicy#linearBackoff()
 * @see RedeliveryPolicy#fixedBackoff()
 */
public class LinearBackoffBuilder {
    private Duration                    redeliveryDelay;
    private Duration                    maximumFollowupRedeliveryDelayThreshold;
    private int                         maximumNumberOfRedeliveries;
    private MessageDeliveryErrorHandler deliveryErrorHandler = MessageDeliveryErrorHandler.alwaysRetry();


    public LinearBackoffBuilder setRedeliveryDelay(Duration redeliveryDelay) {
        this.redeliveryDelay = redeliveryDelay;
        return this;
    }

    public LinearBackoffBuilder setMaximumFollowupRedeliveryDelayThreshold(Duration maximumFollowupRedeliveryDelayThreshold) {
        this.maximumFollowupRedeliveryDelayThreshold = maximumFollowupRedeliveryDelayThreshold;
        return this;
    }

    public LinearBackoffBuilder setMaximumNumberOfRedeliveries(int maximumNumberOfRedeliveries) {
        this.maximumNumberOfRedeliveries = maximumNumberOfRedeliveries;
        return this;
    }

    public LinearBackoffBuilder setDeliveryErrorHandler(MessageDeliveryErrorHandler deliveryErrorHandler) {
        this.deliveryErrorHandler = deliveryErrorHandler;
        return this;
    }

    public RedeliveryPolicy build() {
        return RedeliveryPolicy.builder().setInitialRedeliveryDelay(redeliveryDelay)
                               .setFollowupRedeliveryDelay(redeliveryDelay)
                               .setFollowupRedeliveryDelayMultiplier(1.0d)
                               .setMaximumFollowupRedeliveryDelayThreshold(maximumFollowupRedeliveryDelayThreshold)
                               .setMaximumNumberOfRedeliveries(maximumNumberOfRedeliveries)
                               .setDeliveryErrorHandler(deliveryErrorHandler)
                               .build();

    }
}