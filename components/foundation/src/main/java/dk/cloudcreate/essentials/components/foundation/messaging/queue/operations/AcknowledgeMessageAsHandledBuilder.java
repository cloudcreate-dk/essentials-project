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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueEntryId;

/**
 * Builder for {@link AcknowledgeMessageAsHandled}
 */
public final class AcknowledgeMessageAsHandledBuilder {
    private QueueEntryId queueEntryId;

    /**
     * @param queueEntryId the unique id of the Message to acknowledge
     * @return this builder instance
     */
    public AcknowledgeMessageAsHandledBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    /**
     * Builder an {@link AcknowledgeMessageAsHandled} instance from the builder properties
     * @return the {@link AcknowledgeMessageAsHandled} instance
     */
    public AcknowledgeMessageAsHandled build() {
        return new AcknowledgeMessageAsHandled(queueEntryId);
    }
}