/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.queue.springdata.mongodb;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory.SpringMongoTransactionAwareUnitOfWork;

import java.util.function.Consumer;

public class MongoDurableQueueConsumer extends DurableQueueConsumer.DefaultDurableQueueConsumer<MongoDurableQueues, SpringMongoTransactionAwareUnitOfWork, SpringMongoTransactionAwareUnitOfWorkFactory> {
    public MongoDurableQueueConsumer(QueueName queueName,
                                     QueuedMessageHandler queuedMessageHandler,
                                     RedeliveryPolicy redeliveryPolicy,
                                     int numberOfParallelMessageConsumers,
                                     SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory,
                                     MongoDurableQueues durableQueues,
                                     Consumer<DurableQueueConsumer> removeDurableQueueConsumer) {
        super(queueName, queuedMessageHandler, redeliveryPolicy, numberOfParallelMessageConsumers, unitOfWorkFactory, durableQueues, removeDurableQueueConsumer);
    }
}