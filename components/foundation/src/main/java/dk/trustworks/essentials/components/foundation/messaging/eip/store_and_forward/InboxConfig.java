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

import java.util.Objects;

import static dk.trustworks.essentials.shared.FailFast.*;

public final class InboxConfig {
    /**
     * The name of the inbox
     */
    public final InboxName              inboxName;
    /**
     * The message redelivery policy
     */
    public final RedeliveryPolicy       redeliveryPolicy;
    /**
     * The consumption mode for the inbox's <code>messageConsumer</code> across all the different instances in the entire cluster<br>
     * If you're working with {@link OrderedMessage}'s then the {@link Inbox} consumer must be configured
     * with {@link InboxConfig#getMessageConsumptionMode()} having value {@link MessageConsumptionMode#SingleGlobalConsumer}
     * in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per {@link OrderedMessage#getKey()}
     * across as many {@link InboxConfig#numberOfParallelMessageConsumers} as you wish to use.
     */
    public final MessageConsumptionMode messageConsumptionMode;
    /**
     * The number of local parallel message consumers
     */
    public final int                    numberOfParallelMessageConsumers;

    /**
     * @param inboxName                        the name of the inbox
     * @param redeliveryPolicy                 the message redelivery policy
     * @param messageConsumptionMode           the consumption mode for the inbox's <code>messageConsumer</code> across all the different instances in the entire cluster<br>
     *                                         If you're working with {@link OrderedMessage}'s then the {@link Inbox} consumer must be configured
     *                                         with {@link InboxConfig#getMessageConsumptionMode()} having value {@link MessageConsumptionMode#SingleGlobalConsumer}
     *                                         in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per {@link OrderedMessage#getKey()}
     *                                         across as many {@link InboxConfig#numberOfParallelMessageConsumers} as you wish to use.
     * @param numberOfParallelMessageConsumers the number of local parallel message consumers
     */
    public InboxConfig(InboxName inboxName, RedeliveryPolicy redeliveryPolicy, MessageConsumptionMode messageConsumptionMode, int numberOfParallelMessageConsumers) {
        this.inboxName = requireNonNull(inboxName, "No inboxName provided");
        this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
        this.messageConsumptionMode = requireNonNull(messageConsumptionMode, "No messageConsumptionMode specified");
        requireTrue(numberOfParallelMessageConsumers >= 1, "You must specify a number of parallelMessageConsumers >= 1");
        this.numberOfParallelMessageConsumers = numberOfParallelMessageConsumers;
    }

    public static InboxConfigBuilder builder() {
        return new InboxConfigBuilder();
    }

    /**
     * @return the name of the inbox
     */
    public InboxName getInboxName() {
        return inboxName;
    }

    /**
     * @return the message redelivery policy
     */
    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * @return the consumption mode for the inbox's <code>messageConsumer</code> across all the different instances in the entire cluster<br>
     * If you're working with {@link OrderedMessage}'s then the {@link Inbox} consumer must be configured
     * with {@link InboxConfig#getMessageConsumptionMode()} having value {@link MessageConsumptionMode#SingleGlobalConsumer}
     * in order to be able to guarantee that {@link OrderedMessage}'s are delivered in {@link OrderedMessage#getOrder()} per {@link OrderedMessage#getKey()}
     * across as many {@link InboxConfig#numberOfParallelMessageConsumers} as you wish to use.
     */
    public MessageConsumptionMode getMessageConsumptionMode() {
        return messageConsumptionMode;
    }

    /**
     * @return the number of local parallel message consumers
     */
    public int getNumberOfParallelMessageConsumers() {
        return numberOfParallelMessageConsumers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InboxConfig that = (InboxConfig) o;
        return inboxName.equals(that.inboxName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inboxName);
    }

    @Override
    public String toString() {
        return "InboxConfig{" +
                "inboxName=" + inboxName +
                ", redeliveryPolicy=" + redeliveryPolicy +
                ", messageConsumptionMode=" + messageConsumptionMode +
                ", numberOfParallelMessageConsumers=" + numberOfParallelMessageConsumers +
                '}';
    }


}
