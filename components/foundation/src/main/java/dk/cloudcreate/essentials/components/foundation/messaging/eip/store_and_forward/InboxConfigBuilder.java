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

public class InboxConfigBuilder {
    private InboxName inboxName;
    private RedeliveryPolicy       redeliveryPolicy;
    private MessageConsumptionMode messageConsumptionMode;
    private int                    numberOfParallelMessageConsumers = 1;

    public InboxConfigBuilder inboxName(InboxName inboxName) {
        this.inboxName = inboxName;
        return this;
    }

    public InboxConfigBuilder redeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
        return this;
    }

    public InboxConfigBuilder messageConsumptionMode(MessageConsumptionMode messageConsumptionMode) {
        this.messageConsumptionMode = messageConsumptionMode;
        return this;
    }

    public InboxConfigBuilder numberOfParallelMessageConsumers(int numberOfParallelMessageConsumers) {
        this.numberOfParallelMessageConsumers = numberOfParallelMessageConsumers;
        return this;
    }

    public InboxConfig build() {
        return new InboxConfig(inboxName, redeliveryPolicy, messageConsumptionMode, numberOfParallelMessageConsumers);
    }
}