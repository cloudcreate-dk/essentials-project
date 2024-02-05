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

package dk.cloudcreate.essentials.components.queue.postgresql;

import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;

import java.time.Duration;
import java.util.function.Function;

import static dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues.*;

public class PostgresqlDurableQueuesBuilder {
    private HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
    private JSONSerializer                                                jsonSerializer;
    private String                                                        sharedQueueTableName         = DEFAULT_DURABLE_QUEUES_TABLE_NAME;
    private MultiTableChangeListener<TableChangeNotification>             multiTableChangeListener     = null;
    private Function<ConsumeFromQueue, QueuePollingOptimizer>             queuePollingOptimizerFactory = null;
    private TransactionalMode                                             transactionalMode            = TransactionalMode.SingleOperationTransaction;
    /**
     * Only used if {@link #transactionalMode} has value {@link TransactionalMode#SingleOperationTransaction}
     */
    private Duration                                                      messageHandlingTimeout = Duration.ofSeconds(30);

    /**
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} needed to access the database
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setUnitOfWorkFactory(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
        this.unitOfWorkFactory = unitOfWorkFactory;
        return this;
    }

    /**
     * @param jsonSerializer Set the {@link JSONSerializer} that is used to serialize/deserialize message payloads.<br>
     *                       If not set, then {@link JacksonJSONSerializer} with the {@link PostgresqlDurableQueues#createDefaultObjectMapper()} will be used
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setJsonSerializer(JSONSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
        return this;
    }

    /**
     * @param sharedQueueTableName the name of the table that will contain all messages (across all {@link QueueName}'s)<br>
     *                             Default is {@link PostgresqlDurableQueues#DEFAULT_DURABLE_QUEUES_TABLE_NAME}
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setSharedQueueTableName(String sharedQueueTableName) {
        this.sharedQueueTableName = sharedQueueTableName;
        return this;
    }

    /**
     * @param multiTableChangeListener optional {@link MultiTableChangeListener} that allows {@link PostgresqlDurableQueues} to use {@link QueuePollingOptimizer}
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setMultiTableChangeListener(MultiTableChangeListener<TableChangeNotification> multiTableChangeListener) {
        this.multiTableChangeListener = multiTableChangeListener;
        return this;
    }

    /**
     * @param queuePollingOptimizerFactory optional {@link QueuePollingOptimizer} factory that creates a {@link QueuePollingOptimizer} per {@link ConsumeFromQueue} command -
     *                                     if set to null {@link PostgresqlDurableQueues#createQueuePollingOptimizerFor(ConsumeFromQueue)} is used instead
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setQueuePollingOptimizerFactory(Function<ConsumeFromQueue, QueuePollingOptimizer> queuePollingOptimizerFactory) {
        this.queuePollingOptimizerFactory = queuePollingOptimizerFactory;
        return this;
    }

    /**
     * @param messageHandlingTimeout Only required if <code>transactionalMode</code> is {@link TransactionalMode#SingleOperationTransaction}.<br>
     *                               The parameter defines the timeout for messages being delivered, but haven't yet been acknowledged.
     *                               After this timeout the message delivery will be reset and the message will again be a candidate for delivery<br>
     *                               Default is 30 seconds
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setMessageHandlingTimeout(Duration messageHandlingTimeout) {
        this.messageHandlingTimeout = messageHandlingTimeout;
        return this;
    }

    /**
     * @param transactionalMode The {@link TransactionalMode} for this {@link DurableQueues} instance. If set to {@link TransactionalMode#SingleOperationTransaction}
     *                          then the consumer MUST call the {@link DurableQueues#acknowledgeMessageAsHandled(AcknowledgeMessageAsHandled)} explicitly in a new {@link UnitOfWork}<br>
     *                          Note: The default consumer calls {@link DurableQueues#acknowledgeMessageAsHandled(AcknowledgeMessageAsHandled)} after successful message handling
     *                          Default value {@link TransactionalMode#SingleOperationTransaction}
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setTransactionalMode(TransactionalMode transactionalMode) {
        this.transactionalMode = transactionalMode;
        return this;
    }

    public PostgresqlDurableQueues build() {
        return new PostgresqlDurableQueues(unitOfWorkFactory,
                                           jsonSerializer != null ? jsonSerializer : new JacksonJSONSerializer(createDefaultObjectMapper()),
                                           sharedQueueTableName,
                                           multiTableChangeListener,
                                           queuePollingOptimizerFactory,
                                           transactionalMode,
                                           messageHandlingTimeout);
    }
}