/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;


import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public final class DurableQueueException extends RuntimeException {
    public final QueueName              queueName;
    public final Optional<QueueEntryId> queueEntryId;


    public DurableQueueException(String message, QueueName queueName) {
        this(message, null, queueName, null);
    }

    public DurableQueueException(String message, Throwable cause, QueueName queueName) {
        this(message, cause, queueName, null);
    }

    public DurableQueueException(Throwable cause, QueueName queueName) {
        this(null, cause, queueName, null);
    }

    public DurableQueueException(String message, QueueName queueName, QueueEntryId queueEntryId) {
        this(message, null, queueName, queueEntryId);
    }

    public DurableQueueException(Throwable cause, QueueName queueName, QueueEntryId queueEntryId) {
        this(null, cause, queueName, queueEntryId);
    }

    public DurableQueueException(String message, Throwable cause, QueueName queueName, QueueEntryId queueEntryId) {
        super(enrichMessage(message, queueName, queueEntryId), cause);
        this.queueName = queueName;
        this.queueEntryId = Optional.ofNullable(queueEntryId);
    }


    private static String enrichMessage(String message, QueueName queueName, QueueEntryId queueEntryId) {
        String messageToLog = message != null ? " " + message : "";

        if (queueEntryId != null) {
            return msg("[{}:{}]{}",
                       requireNonNull(queueName, "queueName missing"),
                       queueEntryId,
                       messageToLog);
        } else {
            return msg("[{}]{}",
                       requireNonNull(queueName, "queueName missing"),
                       messageToLog);
        }
    }
}
