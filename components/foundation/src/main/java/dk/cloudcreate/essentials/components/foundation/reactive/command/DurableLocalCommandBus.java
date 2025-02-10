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

package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.reactive.command.SendAndDontWaitErrorHandler.RethrowingSendAndDontWaitErrorHandler;
import dk.cloudcreate.essentials.reactive.command.interceptor.*;
import org.slf4j.*;

import java.time.Duration;
import java.util.*;

import static dk.cloudcreate.essentials.shared.Exceptions.rethrowIfCriticalError;
import static dk.cloudcreate.essentials.shared.FailFast.*;

/**
 * Provides a JVM local and <b>durable</b>,
 * in regard to {@link #sendAndDontWait(Object)}/{@link #sendAndDontWait(Object, Duration)}),
 * variant of the {@link CommandBus} concept<br>
 * Durability for {@link #sendAndDontWait(Object)}/{@link #sendAndDontWait(Object, Duration)}) is delegated
 * to {@link DurableQueues}<br>
 * Which {@link QueueName} that is used will be determined by the specified {@link #getCommandQueueName()}<br>
 * The {@link RedeliveryPolicy} is determined by the specified {@link #getCommandQueueRedeliveryPolicy()}<br>
 * <br>
 * Note: If the {@link SendAndDontWaitErrorHandler} provided doesn't rethrow the exception, then the underlying {@link DurableQueues} will not be able to retry the command.<br>
 * Due to this the {@link DurableLocalCommandBus} defaults to using the {@link RethrowingSendAndDontWaitErrorHandler}
 *
 * @see AnnotatedCommandHandler
 */
public final class DurableLocalCommandBus extends AbstractCommandBus implements Lifecycle {
    private static final Logger           log                        = LoggerFactory.getLogger(DurableLocalCommandBus.class);
    public static final  QueueName        DEFAULT_COMMAND_QUEUE_NAME = QueueName.of("DefaultCommandQueue");
    public static final  RedeliveryPolicy DEFAULT_REDELIVERY_POLICY  = RedeliveryPolicy.linearBackoff(Duration.ofMillis(150),
                                                                                                      Duration.ofMillis(1000),
                                                                                                      20);
    
    private DurableQueues    durableQueues;
    private int              parallelSendAndDontWaitConsumers = Runtime.getRuntime().availableProcessors();
    private QueueName        commandQueueName                 = DEFAULT_COMMAND_QUEUE_NAME;
    private RedeliveryPolicy commandQueueRedeliveryPolicy     = DEFAULT_REDELIVERY_POLICY;

    private boolean              started;
    private DurableQueueConsumer durableQueueConsumer;

    /**
     * Builder for a {@link DurableLocalCommandBusBuilder}
     *
     * @return builder for a {@link DurableLocalCommandBusBuilder}
     */
    public static DurableLocalCommandBusBuilder builder() {
        return new DurableLocalCommandBusBuilder();
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueName()}: {@link #DEFAULT_COMMAND_QUEUE_NAME}</li>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     *     <li>{@link SendAndDontWaitErrorHandler}: {@link RethrowingSendAndDontWaitErrorHandler}</li>
     * </ul>
     *
     * @param durableQueues the underlying Durable Queues provider
     */
    public DurableLocalCommandBus(DurableQueues durableQueues) {
        super(new RethrowingSendAndDontWaitErrorHandler(), List.of());
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link SendAndDontWaitErrorHandler}: {@link RethrowingSendAndDontWaitErrorHandler}</li>
     * </ul>
     *
     * @param durableQueues                the underlying Durable Queues provider
     * @param commandQueueName             Set the name of the {@link DurableQueues} that will be used queuing commands sent using {@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * @param commandQueueRedeliveryPolicy Set the {@link RedeliveryPolicy} used when handling queued commnds sent using{@link DurableLocalCommandBus#sendAndDontWait(Object)}
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  QueueName commandQueueName,
                                  RedeliveryPolicy commandQueueRedeliveryPolicy) {
        super(new RethrowingSendAndDontWaitErrorHandler(), List.of());
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.commandQueueName = requireNonNull(commandQueueName, "No commandQueueName provided");
        this.commandQueueRedeliveryPolicy = requireNonNull(commandQueueRedeliveryPolicy, "No commandQueueRedeliveryPolicy provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueName()}: {@link #DEFAULT_COMMAND_QUEUE_NAME}</li>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     * </ul>
     *
     * @param durableQueues               the underlying Durable Queues provider
     * @param sendAndDontWaitErrorHandler Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     *                                    then the message will not be retried by the underlying {@link DurableQueues}
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler) {
        super(sendAndDontWaitErrorHandler,
              List.of());
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link SendAndDontWaitErrorHandler}: {@link RethrowingSendAndDontWaitErrorHandler}</li>
     * </ul>
     *
     * @param durableQueues               the underlying Durable Queues provider
     * @param commandQueueName            Set the name of the {@link DurableQueues} that will be used queuing commands sent using {@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * @param sendAndDontWaitErrorHandler Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     *                                    then the message will not be retried by the underlying {@link DurableQueues}
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  QueueName commandQueueName,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler) {
        super(sendAndDontWaitErrorHandler,
              List.of());
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.commandQueueName = requireNonNull(commandQueueName, "No commandQueueName provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueName()}: {@link #DEFAULT_COMMAND_QUEUE_NAME}</li>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     *     <li>{@link SendAndDontWaitErrorHandler}: {@link RethrowingSendAndDontWaitErrorHandler}</li>
     * </ul>
     *
     * @param durableQueues the underlying Durable Queues provider
     * @param interceptors  all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  List<CommandBusInterceptor> interceptors) {
        super(new RethrowingSendAndDontWaitErrorHandler(), interceptors);
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     *     <li>{@link SendAndDontWaitErrorHandler}: {@link RethrowingSendAndDontWaitErrorHandler}</li>
     * </ul>
     *
     * @param durableQueues    the underlying Durable Queues provider
     * @param commandQueueName Set the name of the {@link DurableQueues} that will be used queuing commands sent using {@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * @param interceptors     all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  QueueName commandQueueName,
                                  List<CommandBusInterceptor> interceptors) {
        super(new RethrowingSendAndDontWaitErrorHandler(), interceptors);
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.commandQueueName = requireNonNull(commandQueueName, "No commandQueueName provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueName()}: {@link #DEFAULT_COMMAND_QUEUE_NAME}</li>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     * </ul>
     *
     * @param durableQueues               the underlying Durable Queues provider
     * @param sendAndDontWaitErrorHandler Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     *                                    then the message will not be retried by the underlying {@link DurableQueues}
     * @param interceptors                all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  List<CommandBusInterceptor> interceptors) {
        super(sendAndDontWaitErrorHandler,
              interceptors);
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus}
     *
     * @param durableQueues                    the underlying Durable Queues provider
     * @param parallelSendAndDontWaitConsumers How many parallel {@link DurableQueues} consumers should listen for messages added using {@link #sendAndDontWait(Object)}/{@link #sendAndDontWait(Object, Duration)}
     * @param commandQueueName                 Set the name of the {@link DurableQueues} that will be used queuing commands sent using {@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * @param commandQueueRedeliveryPolicy     Set the {@link RedeliveryPolicy} used when handling queued commnds sent using{@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * @param sendAndDontWaitErrorHandler      Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     *                                         then the message will not be retried by the underlying {@link DurableQueues}
     * @param interceptors                     all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  int parallelSendAndDontWaitConsumers,
                                  QueueName commandQueueName,
                                  RedeliveryPolicy commandQueueRedeliveryPolicy,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  List<CommandBusInterceptor> interceptors) {
        super(sendAndDontWaitErrorHandler,
              interceptors);
        requireTrue(parallelSendAndDontWaitConsumers >= 1, "parallelSendAndDontWaitConsumers is < 1");
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.parallelSendAndDontWaitConsumers = parallelSendAndDontWaitConsumers;
        this.commandQueueName = requireNonNull(commandQueueName, "No commandQueueName provided");
        this.commandQueueRedeliveryPolicy = requireNonNull(commandQueueRedeliveryPolicy, "No commandQueueRedeliveryPolicy provided");

    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     * </ul>
     *
     * @param durableQueues               the underlying Durable Queues provider
     * @param commandQueueName            {The strategy for selecting which {@link DurableQueues}
     *                                    {@link QueueName} to use for a given combination of command and command handler
     * @param sendAndDontWaitErrorHandler Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     *                                    then the message will not be retried by the underlying {@link DurableQueues}
     * @param interceptors                all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  QueueName commandQueueName,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  List<CommandBusInterceptor> interceptors) {
        super(sendAndDontWaitErrorHandler,
              interceptors);
        this.durableQueues = requireNonNull(durableQueues, "No durableQueues instance provided");
        this.commandQueueName = requireNonNull(commandQueueName, "No commandQueueName provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueName()}: {@link #DEFAULT_COMMAND_QUEUE_NAME}</li>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     *     <li>{@link SendAndDontWaitErrorHandler}: {@link RethrowingSendAndDontWaitErrorHandler}</li>
     * </ul>
     *
     * @param durableQueues the underlying Durable Queues provider
     * @param interceptors  all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  CommandBusInterceptor... interceptors) {
        this(durableQueues,
             List.of(interceptors));
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     *     <li>{@link SendAndDontWaitErrorHandler}: {@link RethrowingSendAndDontWaitErrorHandler}</li>
     * </ul>
     *
     * @param durableQueues    the underlying Durable Queues provider
     * @param commandQueueName {The strategy for selecting which {@link DurableQueues}
     *                         {@link QueueName} to use for a given combination of command and command handler
     * @param interceptors     all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  QueueName commandQueueName,
                                  CommandBusInterceptor... interceptors) {
        this(durableQueues,
             List.of(interceptors));
        this.commandQueueName = requireNonNull(commandQueueName, "No commandQueueName provided");
    }

    /**
     * Create a new {@link DurableLocalCommandBus} using defaults: using defaults:
     * <ul>
     *     <li>{@link #getCommandQueueName()}: {@link #DEFAULT_COMMAND_QUEUE_NAME}</li>
     *     <li>{@link #getCommandQueueRedeliveryPolicy()}: {@link #DEFAULT_REDELIVERY_POLICY}</li>
     * </ul>
     *
     * @param durableQueues               the underlying Durable Queues provider
     * @param sendAndDontWaitErrorHandler Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     *                                    then the message will not be retried by the underlying {@link DurableQueues}
     * @param interceptors                all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  CommandBusInterceptor... interceptors) {
        this(durableQueues,
             sendAndDontWaitErrorHandler,
             List.of(interceptors));
    }

    /**
     * Create a new {@link DurableLocalCommandBus}
     *
     * @param durableQueues                    the underlying Durable Queues provider
     * @param parallelSendAndDontWaitConsumers How many parallel {@link DurableQueues} consumers should listen for messages added using {@link #sendAndDontWait(Object)}/{@link #sendAndDontWait(Object, Duration)}
     * @param commandQueueName                 Set the name of the {@link DurableQueues} that will be used queuing commands sent using {@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * @param commandQueueRedeliveryPolicy     Set the {@link RedeliveryPolicy} used when handling queued commnds sent using{@link DurableLocalCommandBus#sendAndDontWait(Object)}
     * @param sendAndDontWaitErrorHandler      Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}. If this handler doesn't rethrow the exeption,
     *                                         then the message will not be retried by the underlying {@link DurableQueues}
     * @param interceptors                     all the {@link CommandBusInterceptor}'s
     */
    public DurableLocalCommandBus(DurableQueues durableQueues,
                                  int parallelSendAndDontWaitConsumers,
                                  QueueName commandQueueName,
                                  RedeliveryPolicy commandQueueRedeliveryPolicy,
                                  SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler,
                                  CommandBusInterceptor... interceptors) {
        this(durableQueues,
             parallelSendAndDontWaitConsumers,
             commandQueueName,
             commandQueueRedeliveryPolicy,
             sendAndDontWaitErrorHandler,
             List.of(interceptors)
            );
    }

    @Override
    public void start() {
        if (!started) {
            log.info("Starting...");
            started = true;
            durableQueueConsumer = durableQueues.consumeFromQueue(commandQueueName,
                                                                  commandQueueRedeliveryPolicy,
                                                                  parallelSendAndDontWaitConsumers,
                                                                  this::processSendAndDontWaitMessage);
            log.info("Started");
        }
    }

    @Override
    public void stop() {
        if (started) {
            log.info("Stopping...");
            started = false;
            if (durableQueueConsumer != null) {
                durableQueueConsumer.stop();
                durableQueueConsumer = null;
            }
            log.info("Stopped");
        }
    }

    @Override
    public boolean isStarted() {
        return started;
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


        if (messageDeliveryDelay.isPresent()) {
            log.debug("[{}] Queuing Durable delayed {} sendAndDontWait for command of type '{}' to {} '{}'. TransactionalMode: {}",
                      commandQueueName,
                      messageDeliveryDelay,
                      command.getClass().getName(),
                      CommandHandler.class.getSimpleName(),
                      commandHandler.toString(),
                      durableQueues.getTransactionalMode());
        } else {
            log.debug("[{}] Queuing Durable sendAndDontWait command of type '{}' to {} '{}'. TransactionalMode: {}",
                      commandQueueName,
                      command.getClass().getName(),
                      CommandHandler.class.getSimpleName(),
                      commandHandler.toString(),
                      durableQueues.getTransactionalMode());
        }

        if (durableQueues.getTransactionalMode() == TransactionalMode.FullyTransactional) {

            // Allow sendAndDontWait to automatically start a new or join in an existing UnitOfWork
            durableQueues.getUnitOfWorkFactory().get().usingUnitOfWork(() -> {
                durableQueues.queueMessage(commandQueueName,
                                           Message.of(command),
                                           messageDeliveryDelay);
            });
        } else {
            durableQueues.queueMessage(commandQueueName,
                                       Message.of(command),
                                       messageDeliveryDelay);
        }
    }

    private void processSendAndDontWaitMessage(QueuedMessage queuedMessage) {
        var command        = queuedMessage.getMessage().getPayload();
        var commandHandler = findCommandHandlerCapableOfHandling(command);
        log.debug("[{}] Handling Durable sendAndDontWait command of type '{}' to {} '{}'",
                  queuedMessage.getQueueName(),
                  command.getClass().getName(),
                  CommandHandler.class.getSimpleName(),
                  commandHandler.toString());

        CommandBusInterceptorChain.newInterceptorChain(queuedMessage,
                                                       commandHandler,
                                                       interceptors,
                                                       (interceptor, commandBusInterceptorChain) -> {
                                                           interceptor.interceptSendAndDontWait(queuedMessage, commandBusInterceptorChain);
                                                           return null;
                                                       },
                                                       msg -> {
                                                           try {
                                                               return commandHandler.handle(command);
                                                           } catch (Throwable e) {
                                                               rethrowIfCriticalError(e);
                                                               sendAndDontWaitErrorHandler.handleError(e,
                                                                                                       queuedMessage,
                                                                                                       commandHandler);
                                                               return null;
                                                           }
                                                       })
                                  .proceed();
    }

    public int getParallelSendAndDontWaitConsumers() {
        return parallelSendAndDontWaitConsumers;
    }

    public QueueName getCommandQueueName() {
        return commandQueueName;
    }

    public RedeliveryPolicy getCommandQueueRedeliveryPolicy() {
        return commandQueueRedeliveryPolicy;
    }
}