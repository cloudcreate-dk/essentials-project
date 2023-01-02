/*
 * Copyright 2021-2023 the original author or authors.
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

import static dk.cloudcreate.essentials.shared.FailFast.*;

public class OutboxConfig {
    public final OutboxName             outboxName;
    public final RedeliveryPolicy       redeliveryPolicy;
    public final MessageConsumptionMode messageConsumptionMode;
    public final int                    numberOfParallelMessageConsumers;

    /**
     * @param outboxName                       the name of the outbox
     * @param redeliveryPolicy                 the message redelivery policy
     * @param messageConsumptionMode           the consumption mode for the outbox's <code>messageConsumer</code> across all the different instances in the entire cluster
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

    public OutboxName getOutboxName() {
        return outboxName;
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
}
