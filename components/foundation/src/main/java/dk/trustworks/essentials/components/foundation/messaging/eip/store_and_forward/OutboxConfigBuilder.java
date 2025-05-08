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

public final class OutboxConfigBuilder {
    private OutboxName outboxName;
    private RedeliveryPolicy       redeliveryPolicy;
    private MessageConsumptionMode messageConsumptionMode;
    private int                    numberOfParallelMessageConsumers = 1;

    public OutboxConfigBuilder setOutboxName(OutboxName outboxName) {
        this.outboxName = outboxName;
        return this;
    }

    public OutboxConfigBuilder setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
        return this;
    }

    public OutboxConfigBuilder setMessageConsumptionMode(MessageConsumptionMode messageConsumptionMode) {
        this.messageConsumptionMode = messageConsumptionMode;
        return this;
    }

    public OutboxConfigBuilder setNumberOfParallelMessageConsumers(int numberOfParallelMessageConsumers) {
        this.numberOfParallelMessageConsumers = numberOfParallelMessageConsumers;
        return this;
    }

    public OutboxConfig build() {
        return new OutboxConfig(outboxName, redeliveryPolicy, messageConsumptionMode, numberOfParallelMessageConsumers);
    }
}