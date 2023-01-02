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

package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Strategy that allows the {@link DurableLocalCommandBus} to vary the {@link RedeliveryPolicy}
 * per {@link QueueName}
 */
public interface CommandQueueRedeliveryPolicyResolver {
    /**
     * Resolve the {@link RedeliveryPolicy} to use for the given <code>queueName</code>
     *
     * @param queueName the name of the Queue that we're trying to resolve the {@link RedeliveryPolicy} for
     * @return the {@link RedeliveryPolicy} to use for the given Command Queue
     */
    RedeliveryPolicy resolveRedeliveryPolicyFor(QueueName queueName);

    /**
     * Apply the same {@link RedeliveryPolicy} independent on the {@link QueueName}
     *
     * @param redeliveryPolicy the {@link RedeliveryPolicy} used for all queues
     * @return resolve that uses the same {@link RedeliveryPolicy} independent on the {@link QueueName}
     */
    static CommandQueueRedeliveryPolicyResolver sameReliveryPolicyForAllCommandQueues(RedeliveryPolicy redeliveryPolicy) {
        requireNonNull(redeliveryPolicy, "No redeliveryPolicy provided");
        return queueName -> redeliveryPolicy;
    }
}
