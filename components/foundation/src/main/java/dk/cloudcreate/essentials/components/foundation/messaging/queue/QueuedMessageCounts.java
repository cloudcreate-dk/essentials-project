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

/**
 * The total number of (non-dead-letter) messages queued and number of queued dead-letter Messages for the given queue
 *
 * @param queueName                        the name of the queue
 * @param numberOfQueuedMessages           the total number (non-dead-letter) messages queued
 * @param numberOfQueuedDeadLetterMessages the total number of dead-letter messages queued
 */
public record QueuedMessageCounts(QueueName queueName,
                                  long numberOfQueuedMessages,
                                  long numberOfQueuedDeadLetterMessages) {
}
