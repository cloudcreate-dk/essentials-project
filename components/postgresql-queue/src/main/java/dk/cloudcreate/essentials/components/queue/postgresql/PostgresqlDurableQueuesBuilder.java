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

package dk.cloudcreate.essentials.components.queue.postgresql;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.foundation.postgresql.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;

import java.util.function.Function;

import static dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues.*;

public class PostgresqlDurableQueuesBuilder {
    private HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;
    private ObjectMapper                                                  messagePayloadObjectMapper;
    private String                                                        sharedQueueTableName         = DEFAULT_DURABLE_QUEUES_TABLE_NAME;
    private MultiTableChangeListener<TableChangeNotification>             multiTableChangeListener     = null;
    private Function<ConsumeFromQueue, QueuePollingOptimizer>             queuePollingOptimizerFactory = null;

    /**
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory} needed to access the database
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setUnitOfWorkFactory(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
        this.unitOfWorkFactory = unitOfWorkFactory;
        return this;
    }

    /**
     * @param messagePayloadObjectMapper the {@link ObjectMapper} that is used to serialize/deserialize message payloads<br>
     *                                   If not set, then {@link PostgresqlDurableQueues#createDefaultObjectMapper()} is used
     * @return this builder instance
     */
    public PostgresqlDurableQueuesBuilder setMessagePayloadObjectMapper(ObjectMapper messagePayloadObjectMapper) {
        this.messagePayloadObjectMapper = messagePayloadObjectMapper;
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

    public PostgresqlDurableQueues build() {
        return new PostgresqlDurableQueues(unitOfWorkFactory,
                                           messagePayloadObjectMapper != null ? messagePayloadObjectMapper : createDefaultObjectMapper(),
                                           sharedQueueTableName,
                                           multiTableChangeListener,
                                           queuePollingOptimizerFactory);
    }
}