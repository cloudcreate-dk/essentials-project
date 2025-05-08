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

package dk.trustworks.essentials.components.foundation.messaging.eip.store_and_forward;

import dk.trustworks.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.trustworks.essentials.components.foundation.messaging.queue.OrderedMessage;

import static dk.trustworks.essentials.shared.FailFast.*;

public final class OutboxConfig {
    /**
     * The name of the outbox
     */
    public final OutboxName             outboxName;
    /**
     * The message redelivery policy
     */
    public final RedeliveryPolicy       redeliveryPolicy;
    /**
     * The consumption mode for the outbox's <code>messageConsumer</code> across all the different instances in the entire cluster<br>
     * If you're working with {@link OrderedMessage}'s then the {@link Outbox} consumer must be configured
     * with {@link OutboxConfig#getMessageConsumptionMode()} having value {@link MessageConsumptionMode#SingleGlobalConsumer}
     * in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per {@link OrderedMessage#getKey()}
     * across as many {@link OutboxConfig#numberOfParallelMessageConsumers} as you wish to use.
     */
    public final MessageConsumptionMode messageConsumptionMode;
    /**
     * The number of local parallel message consumers
     */
    public final int                    numberOfParallelMessageConsumers;

    /**
     * @param outboxName                       the name of the outbox
     * @param redeliveryPolicy                 the message redelivery policy
     * @param messageConsumptionMode           the consumption mode for the outbox's <code>messageConsumer</code> across all the different instances in the entire cluster<br>
     *                                         If you're working with {@link OrderedMessage}'s then the {@link Outbox} consumer must be configured
     *                                         with {@link OutboxConfig#getMessageConsumptionMode()} having value {@link MessageConsumptionMode#SingleGlobalConsumer}
     *                                         in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per {@link OrderedMessage#getKey()}
     *                                         across as many {@link OutboxConfig#numberOfParallelMessageConsumers} as you wish to use.
     * @param numberOfParallelMessageConsumers the number of local parallel message consumers
     */
    public OutboxConfig(OutboxName outboxName, RedeliveryPolicy redeliveryPolicy, MessageConsumptionMode messageConsumptionMode, int numberOfParallelMessageConsumers) {
        this.outboxName = requireNonNull(outboxName, "No outboxName provided");
        this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
        this.messageConsumptionMode = requireNonNull(messageConsumptionMode, "No messageConsumptionMode specified");
        requireTrue(numberOfParallelMessageConsumers >= 1, "You must specify a number of parallelMessageConsumers >= 1");
        this.numberOfParallelMessageConsumers = numberOfParallelMessageConsumers;
    }

    public static OutboxConfigBuilder builder() {
        return new OutboxConfigBuilder();
    }

    /**
     * @return The name of the outbox
     */
    public OutboxName getOutboxName() {
        return outboxName;
    }

    /**
     * @return The message redelivery policy
     */
    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * @return the consumption mode for the outbox's <code>messageConsumer</code> across all the different instances in the entire cluster<br>
     * If you're working with {@link OrderedMessage}'s then the {@link Outbox} consumer must be configured
     * with {@link OutboxConfig#getMessageConsumptionMode()} having value {@link MessageConsumptionMode#SingleGlobalConsumer}
     * in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per {@link OrderedMessage#getKey()}
     * across as many {@link OutboxConfig#numberOfParallelMessageConsumers} as you wish to use.
     */
    public MessageConsumptionMode getMessageConsumptionMode() {
        return messageConsumptionMode;
    }

    /**
     * @return The number of local parallel message consumers
     */
    public int getNumberOfParallelMessageConsumers() {
        return numberOfParallelMessageConsumers;
    }
}
