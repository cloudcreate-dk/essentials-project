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

package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.reactive.command.interceptor.CommandBusInterceptor;

import java.time.Duration;
import java.util.*;

import static dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus.*;

/**
 * Builder for {@link DurableLocalCommandBus}
 */
public final class DurableLocalCommandBusBuilder {
    private DurableQueues               durableQueues;
    private int                         parallelSendAndDontWaitConsumers = Runtime.getRuntime().availableProcessors();
    private QueueName                   commandQueueName                 = DEFAULT_COMMAND_QUEUE_NAME;
    private RedeliveryPolicy            commandQueueRedeliveryPolicy     = DEFAULT_REDELIVERY_POLICY;
    private SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler      = new SendAndDontWaitErrorHandler.RethrowingSendAndDontWaitErrorHandler();
    private List<CommandBusInterceptor> interceptors                     = new ArrayList<>();

    /**
     * Set the underlying Durable Queues provider
     *
     * @param durableQueues the underlying Durable Queues provider
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setDurableQueues(DurableQueues durableQueues) {
        this.durableQueues = durableQueues;
        return this;
    }

    /**
     * Set how many parallel {@link DurableQueues} consumers should listen for messages added using {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}<br>
     * Defaults to the number of available processors
     *
     * @param parallelSendAndDontWaitConsumers How many parallel {@link DurableQueues} consumers should listen for messages added using {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setParallelSendAndDontWaitConsumers(int parallelSendAndDontWaitConsumers) {
        this.parallelSendAndDontWaitConsumers = parallelSendAndDontWaitConsumers;
        return this;
    }

    /**
     * Set the name of the {@link DurableQueues} that will be used queuing commands sent using {@link DurableLocalCommandBus#sendAndDontWait(Object)}<br>
     * Defaults to {@link DurableLocalCommandBus#DEFAULT_COMMAND_QUEUE_NAME}
     *
     * @param commandQueueName Set the name of the {@link DurableQueues} that will be used to store asynchronous commands sent using {@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setCommandQueueName(QueueName commandQueueName) {
        this.commandQueueName = commandQueueName;
        return this;
    }

    /**
     * Set the {@link RedeliveryPolicy} used when handling queued commands sent using {@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * Defaults to {@link DurableLocalCommandBus#DEFAULT_REDELIVERY_POLICY}.<br>
     * Example:
     * <pre>{@code
     * RedeliveryPolicy.linearBackoff(Duration.ofMillis(150),
     *                                Duration.ofMillis(1000),
     *                                20)
     * }</pre>
     *
     * @param commandQueueRedeliveryPolicy The strategy that allows the {@link DurableLocalCommandBus} to vary the {@link RedeliveryPolicy} per {@link QueueName}
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setCommandQueueRedeliveryPolicy(RedeliveryPolicy commandQueueRedeliveryPolicy) {
        this.commandQueueRedeliveryPolicy = commandQueueRedeliveryPolicy;
        return this;
    }

    /**
     * Set the Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     * then the message will not be retried by the underlying {@link DurableQueues}
     *
     * @param sendAndDontWaitErrorHandler The Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     *                                    then the message will not be retried by the underlying {@link DurableQueues}
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setSendAndDontWaitErrorHandler(SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler) {
        this.sendAndDontWaitErrorHandler = sendAndDontWaitErrorHandler;
        return this;
    }

    /**
     * Set all the {@link CommandBusInterceptor}'s to use
     *
     * @param interceptors all the {@link CommandBusInterceptor}'s to use
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setInterceptors(List<CommandBusInterceptor> interceptors) {
        this.interceptors = interceptors;
        return this;
    }

    /**
     * Set all the {@link CommandBusInterceptor}'s to use
     *
     * @param interceptors all the {@link CommandBusInterceptor}'s to use
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setInterceptors(CommandBusInterceptor... interceptors) {
        this.interceptors = List.of(interceptors);
        return this;
    }

    /**
     * Add additional {@link CommandBusInterceptor}'s to use
     *
     * @param interceptors the additional {@link CommandBusInterceptor}'s to use
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder addInterceptors(List<CommandBusInterceptor> interceptors) {
        this.interceptors.addAll(interceptors);
        return this;
    }

    /**
     * Add additional {@link CommandBusInterceptor}'s to use
     *
     * @param interceptors the additional {@link CommandBusInterceptor}'s to use
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder addInterceptors(CommandBusInterceptor... interceptors) {
        this.interceptors.addAll(List.of(interceptors));
        return this;
    }

    public DurableLocalCommandBus build() {
        return new DurableLocalCommandBus(durableQueues,
                                          parallelSendAndDontWaitConsumers,
                                          commandQueueName,
                                          commandQueueRedeliveryPolicy,
                                          sendAndDontWaitErrorHandler,
                                          interceptors);
    }
}