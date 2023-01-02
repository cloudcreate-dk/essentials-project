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

import dk.cloudcreate.essentials.components.foundation.fencedlock.LockName;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;
import dk.cloudcreate.essentials.types.*;

/**
 * The name of an {@link Inbox}
 */
public class InboxName extends CharSequenceType<InboxName> implements Identifier {
    public InboxName(CharSequence value) {
        super(value);
    }

    public static InboxName of(CharSequence value) {
        return new InboxName(value);
    }

    public QueueName asQueueName() {
        return QueueName.of("Inbox:" + value());
    }

    public LockName asLockName() {
        return LockName.of("Inbox:" + value());
    }
}
