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

package dk.cloudcreate.essentials.components.foundation.messaging.queue.operations;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueEntryId;

/**
 * Builder for {@link MarkAsDeadLetterMessage}
 */
public class MarkAsDeadLetterMessageBuilder {
    private QueueEntryId queueEntryId;
    private Exception causeForBeingMarkedAsDeadLetter;

    /**
     *
     * @param queueEntryId the unique id of the message that must be marked as a Dead Letter Message
     * @return this builder instance
     */
    public MarkAsDeadLetterMessageBuilder setQueueEntryId(QueueEntryId queueEntryId) {
        this.queueEntryId = queueEntryId;
        return this;
    }

    /**
     *
     * @param causeForBeingMarkedAsDeadLetter the reason for the message being marked as a Dead Letter Message
     * @return this builder instance
     */
    public MarkAsDeadLetterMessageBuilder setCauseForBeingMarkedAsDeadLetter(Exception causeForBeingMarkedAsDeadLetter) {
        this.causeForBeingMarkedAsDeadLetter = causeForBeingMarkedAsDeadLetter;
        return this;
    }

    /**
     * Builder an {@link MarkAsDeadLetterMessage} instance from the builder properties
     * @return the {@link MarkAsDeadLetterMessage} instance
     */
    public MarkAsDeadLetterMessage build() {
        return new MarkAsDeadLetterMessage(queueEntryId, causeForBeingMarkedAsDeadLetter);
    }
}