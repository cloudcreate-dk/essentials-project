package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.reactive.command.interceptor.CommandBusInterceptor;

import java.time.Duration;
import java.util.*;

import static dk.cloudcreate.essentials.components.foundation.reactive.command.CommandQueueNameSelector.defaultCommandQueueForAllCommands;
import static dk.cloudcreate.essentials.components.foundation.reactive.command.CommandQueueRedeliveryPolicyResolver.sameReliveryPolicyForAllCommandQueues;

/**
 * Builder for {@link DurableLocalCommandBus}
 */
public class DurableLocalCommandBusBuilder {
    private DurableQueues                        durableQueues;
    private int                                  parallelSendAndDontWaitConsumers     = 10;
    private CommandQueueNameSelector             commandQueueNameSelector             = defaultCommandQueueForAllCommands();
    private CommandQueueRedeliveryPolicyResolver commandQueueRedeliveryPolicyResolver = sameReliveryPolicyForAllCommandQueues(RedeliveryPolicy.linearBackoff(Duration.ofMillis(150),
                                                                                                                                                             Duration.ofMillis(1000),
                                                                                                                                                             20));
    private SendAndDontWaitErrorHandler          sendAndDontWaitErrorHandler          = new SendAndDontWaitErrorHandler.RethrowingSendAndDontWaitErrorHandler();
    private List<CommandBusInterceptor>          interceptors                         = new ArrayList<>();

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
     * Defaults to 10
     *
     * @param parallelSendAndDontWaitConsumers How many parallel {@link DurableQueues} consumers should listen for messages added using {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setParallelSendAndDontWaitConsumers(int parallelSendAndDontWaitConsumers) {
        this.parallelSendAndDontWaitConsumers = parallelSendAndDontWaitConsumers;
        return this;
    }

    /**
     * Set the strategy for selecting which {@link DurableQueues} {@link QueueName} to use for a given combination of command and command handler<br>
     * Defaults to {@link CommandQueueRedeliveryPolicyResolver#sameReliveryPolicyForAllCommandQueues(RedeliveryPolicy)}
     *
     * @param commandQueueNameSelector The strategy for selecting which {@link DurableQueues} {@link QueueName} to use for a given combination of command and command handler
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setCommandQueueNameSelector(CommandQueueNameSelector commandQueueNameSelector) {
        this.commandQueueNameSelector = commandQueueNameSelector;
        return this;
    }

    /**
     * Set the strategy that allows the {@link DurableLocalCommandBus} to vary the {@link RedeliveryPolicy} per {@link QueueName}<br>
     * Defaults to {@link CommandQueueNameSelector#defaultCommandQueueForAllCommands()} with {@link RedeliveryPolicy}:
     * <pre>{@code
     * RedeliveryPolicy.linearBackoff(Duration.ofMillis(150),
     *                                Duration.ofMillis(1000),
     *                                20)
     * }</pre>
     *
     * @param commandQueueRedeliveryPolicyResolver The strategy that allows the {@link DurableLocalCommandBus} to vary the {@link RedeliveryPolicy} per {@link QueueName}
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setCommandQueueRedeliveryPolicyResolver(CommandQueueRedeliveryPolicyResolver commandQueueRedeliveryPolicyResolver) {
        this.commandQueueRedeliveryPolicyResolver = commandQueueRedeliveryPolicyResolver;
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
     * @param interceptors all the {@link CommandBusInterceptor}'s to use
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setInterceptors(List<CommandBusInterceptor> interceptors) {
        this.interceptors = interceptors;
        return this;
    }

    /**
     * Set all the {@link CommandBusInterceptor}'s to use
     * @param interceptors all the {@link CommandBusInterceptor}'s to use
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder setInterceptors(CommandBusInterceptor... interceptors) {
        this.interceptors = List.of(interceptors);
        return this;
    }

    /**
     * Add additional {@link CommandBusInterceptor}'s to use
     * @param interceptors the additional {@link CommandBusInterceptor}'s to use
     * @return this builder instance
     */
    public DurableLocalCommandBusBuilder addInterceptors(List<CommandBusInterceptor> interceptors) {
        this.interceptors.addAll(interceptors);
        return this;
    }

    /**
     * Add additional {@link CommandBusInterceptor}'s to use
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
                                          commandQueueNameSelector,
                                          commandQueueRedeliveryPolicyResolver,
                                          sendAndDontWaitErrorHandler,
                                          interceptors);
    }
}