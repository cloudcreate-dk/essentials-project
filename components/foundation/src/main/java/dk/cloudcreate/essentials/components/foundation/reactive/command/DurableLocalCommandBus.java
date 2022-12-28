package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.reactive.command.interceptor.*;
import org.slf4j.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static dk.cloudcreate.essentials.components.foundation.reactive.command.CommandQueueNameSelector.defaultCommandQueueForAllCommands;
import static dk.cloudcreate.essentials.components.foundation.reactive.command.CommandQueueRedeliveryPolicyResolver.sameReliveryPolicyForAllCommandQueues;
import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Provides a JVM local and <b>durable</b>,
 * in regard to {@link #sendAndDontWait(Object)}/{@link #sendAndDontWait(Object, Duration)}),
 * variant of the {@link CommandBus} concept<br>
 * Durability for {@link #sendAndDontWait(Object)}/{@link #sendAndDontWait(Object, Duration)}) is delegated
 * to {@link DurableQueues}<br>
 * Which {@link QueueName} that is used will be determined by the {@link CommandQueueNameSelector} and the {@link RedeliveryPolicy} is determined by the {@link CommandQueueRedeliveryPolicyResolver}<br>
 * <br>
 * Note: If the {@link SendAndDontWaitErrorHandler} provided doesn't rethrow the exception, then the underlying {@link DurableQueues} will not be able to retry the command.<br>
 * Due to this the {@link DurableLocalCommandBus} defaults to using the {@link RethrowingSendAndDontWaitErrorHandler}
 *
 * @see AnnotatedCommandHandler
 */
public class DurableLocalCommandBus extends AbstractCommandBus {
    private static final Logger log = LoggerFactory.getLogger(dk.cloudcreate.essentials.reactive.command.LocalCommandBus.class);

    private DurableQueues                        durableQueues;
    private int                                  parallelSendAndDontWaitConsumers     = 10;
    private CommandQueueNameSelector             commandQueueNameSelector             = defaultCommandQueueForAllCommands();
    private CommandQueueRedeliveryPolicyResolver commandQueueRedeliveryPolicyResolver = sameReliveryPolicyForAllCommandQueues(RedeliveryPolicy.linearBackoff(Duration.ofMillis(150),
                                                                                                                                                             Duration.ofMillis(1000),
                                                                                                                                                             20));


    private ConcurrentMap<QueueName, DurableQueueConsumer> queueConsumers = new ConcurrentHashMap<>();

    /**
     * Builder for a {@link DurableLocalCommandBusBuilder}
     *
     * @return builder for a {@link DurableLocalCommandBusBuilder}
     */
    public static DurableLocalCommandBusBuilder builder() {
        return new DurableLocalCommandBusBuilder();
    }

    public DurableLocalCommandBus(DurableQueues durableQueues) {
        super(new RethrowingSendAndDontWaitErrorHandler(), List.of());
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  CommandQueueNameSelector commandQueueNameSelector,
                                  CommandQueueRedeliveryPolicyResolver commandQueueRedeliveryPolicyResolver) {
        super(new RethrowingSendAndDontWaitErrorHandler(), List.of());
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.commandQueueNameSelector = requireNonNull(commandQueueNameSelector, "No durableQueueNameSelector provided");
        this.commandQueueRedeliveryPolicyResolver = requireNonNull(commandQueueRedeliveryPolicyResolver, "No commandQueueRedeliveryPolicyResolver provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler) {
        super(sendAndDontWaitErrorHandler,
              List.of());
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  CommandQueueNameSelector commandQueueNameSelector,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler) {
        super(sendAndDontWaitErrorHandler,
              List.of());
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.commandQueueNameSelector = requireNonNull(commandQueueNameSelector, "No durableQueueNameSelector provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  List<CommandBusInterceptor> interceptors) {
        super(new RethrowingSendAndDontWaitErrorHandler(), interceptors);
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  CommandQueueNameSelector commandQueueNameSelector,
                                  List<CommandBusInterceptor> interceptors) {
        super(new RethrowingSendAndDontWaitErrorHandler(), interceptors);
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.commandQueueNameSelector = requireNonNull(commandQueueNameSelector, "No durableQueueNameSelector provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  List<CommandBusInterceptor> interceptors) {
        super(sendAndDontWaitErrorHandler,
              interceptors);
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  int parallelSendAndDontWaitConsumers,
                                  CommandQueueNameSelector commandQueueNameSelector,
                                  CommandQueueRedeliveryPolicyResolver commandQueueRedeliveryPolicyResolver,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  List<CommandBusInterceptor> interceptors) {
        super(sendAndDontWaitErrorHandler,
              interceptors);
        requireTrue(parallelSendAndDontWaitConsumers >= 1, "parallelSendAndDontWaitConsumers is < 1");
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.parallelSendAndDontWaitConsumers = parallelSendAndDontWaitConsumers;
        this.commandQueueNameSelector = requireNonNull(commandQueueNameSelector, "No durableQueueNameSelector provided");
        this.commandQueueRedeliveryPolicyResolver = requireNonNull(commandQueueRedeliveryPolicyResolver, "No commandQueueRedeliveryPolicyResolver provided");

    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  CommandQueueNameSelector commandQueueNameSelector,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  List<CommandBusInterceptor> interceptors) {
        super(sendAndDontWaitErrorHandler,
              interceptors);
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.commandQueueNameSelector = requireNonNull(commandQueueNameSelector, "No durableQueueNameSelector provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  CommandBusInterceptor... interceptors) {
        this(durableQueues,
             List.of(interceptors));
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  CommandQueueNameSelector commandQueueNameSelector,
                                  CommandBusInterceptor... interceptors) {
        this(durableQueues,
             List.of(interceptors));
        this.commandQueueNameSelector = requireNonNull(commandQueueNameSelector, "No durableQueueNameSelector provided");
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  CommandBusInterceptor... interceptors) {
        this(durableQueues,
             sendAndDontWaitErrorHandler,
             List.of(interceptors));
    }

    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  int parallelSendAndDontWaitConsumers,
                                  CommandQueueNameSelector commandQueueNameSelector,
                                  CommandQueueRedeliveryPolicyResolver commandQueueRedeliveryPolicyResolver,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  CommandBusInterceptor... interceptors) {
        this(durableQueues,
             parallelSendAndDontWaitConsumers,
             commandQueueNameSelector,
             commandQueueRedeliveryPolicyResolver,
             sendAndDontWaitErrorHandler,
             List.of(interceptors)
            );
    }


    @Override
    @SuppressWarnings("unchecked")
    public <C> void sendAndDontWait(C command) {
        _sendAndDontWait(command, Optional.empty());
    }

    @Override
    public <C> void sendAndDontWait(C command, Duration delayMessageDelivery) {
        _sendAndDontWait(command, Optional.ofNullable(delayMessageDelivery));

    }

    private <C> void _sendAndDontWait(C command, Optional<Duration> messageDeliveryDelay) {
        var commandHandler = findCommandHandlerCapableOfHandling(command);
        requireNonNull(messageDeliveryDelay, "You must provide a messageDeliveryDelay value");
        var durableQueueName = commandQueueNameSelector.selectDurableQueueNameFor(command,
                                                                                  commandHandler,
                                                                                  messageDeliveryDelay);
        if (durableQueueName == null) {
            throw new IllegalStateException(msg("{} selected a <null> QueueName for the combination of CommandHandler: {},  messageDeliveryDelay: {}, Command: {}",
                                                CommandQueueNameSelector.class.getSimpleName(),
                                                commandHandler.getClass().getName(),
                                                messageDeliveryDelay,
                                                command));
        }

        queueConsumers.computeIfAbsent(durableQueueName, queueName -> durableQueues.consumeFromQueue(durableQueueName,
                                                                                                     commandQueueRedeliveryPolicyResolver.resolveRedeliveryPolicyFor(durableQueueName),
                                                                                                     parallelSendAndDontWaitConsumers,
                                                                                                     this::processSendAndDontWaitMessage));

        if (messageDeliveryDelay.isPresent()) {
            log.debug("[{}] Queuing Durable delayed {} sendAndDontWait for command of type '{}' to {} '{}'",
                      durableQueueName,
                      messageDeliveryDelay,
                      command.getClass().getName(),
                      CommandHandler.class.getSimpleName(),
                      commandHandler.toString());
        } else {
            log.debug("[{}] Queuing Durable sendAndDontWait command of type '{}' to {} '{}'",
                      durableQueueName,
                      command.getClass().getName(),
                      CommandHandler.class.getSimpleName(),
                      commandHandler.toString());
        }

        if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {
            // Allow sendAndDontWait to automatically start a new or join in an existing UnitOfWork
            durableQueues.getUnitOfWorkFactory().get().usingUnitOfWork(() -> {
                durableQueues.queueMessage(durableQueueName,
                                           command,
                                           messageDeliveryDelay);
            });
        } else {
            durableQueues.queueMessage(durableQueueName,
                                       command,
                                       messageDeliveryDelay);
        }
    }

    private void processSendAndDontWaitMessage(QueuedMessage queuedMessage) {
        var command        = queuedMessage.getPayload();
        var commandHandler = findCommandHandlerCapableOfHandling(command);
        log.debug("[{}] Handling Durable sendAndDontWait command of type '{}' to {} '{}'",
                  queuedMessage.getQueueName(),
                  command.getClass().getName(),
                  CommandHandler.class.getSimpleName(),
                  commandHandler.toString());

        CommandBusInterceptorChain.newInterceptorChain(command,
                                                       commandHandler,
                                                       interceptors,
                                                       (interceptor, commandBusInterceptorChain) -> {
                                                           interceptor.interceptSendAndDontWait(command, commandBusInterceptorChain);
                                                           return null;
                                                       },
                                                       _cmd -> {
                                                           try {
                                                               return commandHandler.handle(_cmd);
                                                           } catch (Exception e) {
                                                               sendAndDontWaitErrorHandler.handleError(e,
                                                                                                       _cmd,
                                                                                                       commandHandler);
                                                               return null;
                                                           }
                                                       })
                                  .proceed();
        if (durableQueues.getTransactionalMode() == TransactionalMode.ManualAcknowledgement) {
            durableQueues.acknowledgeMessageAsHandled(queuedMessage.getId());
        }
    }

}