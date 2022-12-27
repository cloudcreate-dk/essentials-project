package dk.cloudcreate.essentials.components.foundation.messaging;

import java.time.Duration;

/**
 * A builder for defining a {@link RedeliveryPolicy} with a Fixed Backoff strategy
 * @see RedeliveryPolicy#builder()
 * @see RedeliveryPolicy#exponentialBackoff()
 * @see RedeliveryPolicy#linearBackoff()
 * @see RedeliveryPolicy#fixedBackoff()
 */
public class FixedBackoffBuilder {
    private Duration                    redeliveryDelay;
    private int                         maximumNumberOfRedeliveries;
    private MessageDeliveryErrorHandler deliveryErrorHandler = MessageDeliveryErrorHandler.alwaysRetry();


    public FixedBackoffBuilder setRedeliveryDelay(Duration redeliveryDelay) {
        this.redeliveryDelay = redeliveryDelay;
        return this;
    }

    public FixedBackoffBuilder setMaximumNumberOfRedeliveries(int maximumNumberOfRedeliveries) {
        this.maximumNumberOfRedeliveries = maximumNumberOfRedeliveries;
        return this;
    }

    public FixedBackoffBuilder setDeliveryErrorHandler(MessageDeliveryErrorHandler deliveryErrorHandler) {
        this.deliveryErrorHandler = deliveryErrorHandler;
        return this;
    }

    public RedeliveryPolicy build() {
        return RedeliveryPolicy.builder()
                               .setInitialRedeliveryDelay(redeliveryDelay)
                               .setFollowupRedeliveryDelay(redeliveryDelay)
                               .setFollowupRedeliveryDelayMultiplier(1.0d)
                               .setMaximumFollowupRedeliveryDelayThreshold(redeliveryDelay)
                               .setMaximumNumberOfRedeliveries(maximumNumberOfRedeliveries)
                               .setDeliveryErrorHandler(deliveryErrorHandler)
                               .build();
    }
}