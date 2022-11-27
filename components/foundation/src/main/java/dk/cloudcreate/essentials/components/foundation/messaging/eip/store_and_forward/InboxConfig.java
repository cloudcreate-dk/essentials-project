/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;

import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.*;

public class InboxConfig {
    public final InboxName              inboxName;
    public final RedeliveryPolicy       redeliveryPolicy;
    public final MessageConsumptionMode messageConsumptionMode;
    public final int                    numberOfParallelMessageConsumers;

    /**
     * @param inboxName                        the name of the inbox
     * @param redeliveryPolicy                 the message redelivery policy
     * @param messageConsumptionMode           the consumption mode for the inbox's <code>messageConsumer</code> across all the different instances in the entire cluster
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

    public InboxName getInboxName() {
        return inboxName;
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public MessageConsumptionMode getMessageConsumptionMode() {
        return messageConsumptionMode;
    }

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
