package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
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
    private SendAndDontWaitErrorHandler          sendAndDontWaitErrorHandler          = new AbstractCommandBus.RethrowingSendAndDontWaitErrorHandler();
    private List<CommandBusInterceptor>          interceptors                         = new ArrayList<>();

    public DurableLocalCommandBusBuilder setDurableQueues(DurableQueues durableQueues) {
        this.durableQueues = durableQueues;
        return this;
    }

    public DurableLocalCommandBusBuilder setParallelSendAndDontWaitConsumers(int parallelSendAndDontWaitConsumers) {
        this.parallelSendAndDontWaitConsumers = parallelSendAndDontWaitConsumers;
        return this;
    }

    public DurableLocalCommandBusBuilder setCommandQueueNameSelector(CommandQueueNameSelector commandQueueNameSelector) {
        this.commandQueueNameSelector = commandQueueNameSelector;
        return this;
    }

    public DurableLocalCommandBusBuilder setCommandQueueRedeliveryPolicyResolver(CommandQueueRedeliveryPolicyResolver commandQueueRedeliveryPolicyResolver) {
        this.commandQueueRedeliveryPolicyResolver = commandQueueRedeliveryPolicyResolver;
        return this;
    }

    public DurableLocalCommandBusBuilder setSendAndDontWaitErrorHandler(SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler) {
        this.sendAndDontWaitErrorHandler = sendAndDontWaitErrorHandler;
        return this;
    }

    public DurableLocalCommandBusBuilder setInterceptors(List<CommandBusInterceptor> interceptors) {
        this.interceptors = interceptors;
        return this;
    }

    public DurableLocalCommandBusBuilder setInterceptors(CommandBusInterceptor... interceptors) {
        this.interceptors = List.of(interceptors);
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