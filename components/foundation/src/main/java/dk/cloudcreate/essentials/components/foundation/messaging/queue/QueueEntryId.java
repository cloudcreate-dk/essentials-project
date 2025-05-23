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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.types.RandomIdGenerator;
import dk.cloudcreate.essentials.types.CharSequenceType;

/**
 * The unique entry for a message in a queue. This id is unique across all messages across all Queues (as identified by the {@link QueueName})
 */
public final class QueueEntryId extends CharSequenceType<QueueEntryId> {
    public QueueEntryId(CharSequence value) {
        super(value);
    }

    public QueueEntryId(String value) {
        super(value);
    }

    public static QueueEntryId of(CharSequence value) {
        return new QueueEntryId(value);
    }

    public static QueueEntryId random() {
        return new QueueEntryId(RandomIdGenerator.generate());
    }
}