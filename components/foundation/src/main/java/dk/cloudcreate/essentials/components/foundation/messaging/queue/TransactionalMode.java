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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;

/**
 * The transactional behaviour mode of a {@link DurableQueues}
 * <p>
 * The normal message processing flow looks like this:
 * <pre>{@code
 * durableQueues.queueMessage(queueName, message);
 * var msgUnderDelivery = durableQueues.getNextMessageReadyForDelivery(queueName);
 * if (msgUnderDelivery.isPresent()) {
 *    try {
 *       handleMessage(msgUnderDelivery.get());
 *       durableQueues.acknowledgeMessageAsHandled(msgUnderDelivery.get().getId());
 *    } catch (Exception e) {
 *       durableQueues.retryMessage(msgUnderDelivery.get().getId(),
 *                                  e,
 *                                  Duration.ofMillis(500));
 *    }
 * }
 * }</pre>
 * <p>
 * When using {@link TransactionalMode#SingleOperationTransaction} then depending on
 * the type of errors that can occur this MAY leave a dequeued message in a state of being marked as "being delivered" forever<br>
 * This is why {@link DurableQueues} supporting these modes must ensure that they periodically
 * discover messages that have been under delivery for a long time (aka. stuck messages or timed-out messages) and reset them in order for them to be redelivered.<br>
 */
public enum TransactionalMode {
    /**
     * When using this mode all the queueing, de-queueing methods requires an existing {@link UnitOfWork}
     * started prior to being called. The reason for this is that Queues are typically used together with the {@link Inbox}
     * {@link Outbox} pattern, which benefits from including queueing/de-queueing together with other database entity modifying operations.<br>
     * When changing an entity and queueing/de-queueing happens in ONE shared transaction (NOTE this requires that the entity storage and the queue storage
     * to use the same database - e.g. Postgresql or MongoDB) then the shared database transaction guarantees that all the data storage operations
     * are committed or rollback as one
     */
    FullyTransactional,
    /**
     * Useful for long-running message handling, you can choose to configure the {@link DurableQueues} to only require single operation
     * (such as {@link DurableQueues#getNextMessageReadyForDelivery(GetNextMessageReadyForDelivery)} , {@link DurableQueues#deleteMessage(DeleteMessage)} , etc.)
     * as well as for certain NoSQL databases, such as MongoDB/DocumentDB, that have limitations when performing multiple data storage operations within a transaction.<br>
     * For these cases you can configure the {@link TransactionalMode} as {@link #SingleOperationTransaction} where queueing and de-queueing are performed using separate (single document)
     * transactions and where acknowledging/retry is performed as a separate transaction.
     */
    SingleOperationTransaction
}
