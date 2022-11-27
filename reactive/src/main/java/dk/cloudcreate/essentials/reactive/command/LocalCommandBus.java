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

package dk.cloudcreate.essentials.reactive.command;

import dk.cloudcreate.essentials.reactive.command.interceptor.*;
import org.slf4j.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * The {@link LocalCommandBus} provides an indirection between a command and the {@link CommandHandler} that's capable of handling the command.<br>
 * Commands can be sent synchronously using {@link #send(Object)} or asynchronously using {@link #sendAsync(Object)} that returns a {@link Mono}.<br>
 * The handling of a command usually doesn't return any value (according to the principles of CQRS), however the {@link LocalCommandBus} API allows
 * a {@link CommandHandler} to return a value if needed (e.g. such as a server generated id)<br>
 * One important rule is that there can only be one {@link CommandHandler} instance that can handle any given command type.<br>
 * If multiple {@link CommandHandler} claim that they all can handle a given command type then {@link #send(Object)}/{@link #sendAsync(Object)} will throw {@link MultipleCommandHandlersFoundException}<br>
 * If no {@link CommandHandler}'s can handle the given command type then {@link #send(Object)}/{@link #sendAsync(Object)} will throw {@link NoCommandHandlerFoundException}<br>
 * <p></p>
 * Example:
 * <pre>{@code
 *  var commandBus = new LocalCommandBus();
 *  commandBus.addCommandHandler(new CreateOrderCommandHandler(...));
 *  commandBus.addCommandHandler(new ImburseOrderCommandHandler(...));
 *
 *  var optionalResult = commandBus.send(new CreateOrder(...));
 *  // or
 *  var monoWithOptionalResult = commandBus.sendAsync(new ImbuseOrder(...))
 *                                         .block(Duration.ofMillis(1000));
 * }
 * </pre>
 * <p></p>
 * In case you need to colocate multiple related command handling methods inside a single class then you should have your command handling class extend {@link AnnotatedCommandHandler}.<br>
 * Example:
 * <pre>{@code
 * public class OrdersCommandHandler extends AnnotatedCommandHandler {
 *
 *      @Handler
 *      private OrderId handle(CreateOrder cmd) {
 *         ...
 *      }
 *
 *      @Handler
 *      private void someMethod(ReimburseOrder cmd) {
 *         ...
 *      }
 * }}</pre>
 *
 * @see AnnotatedCommandHandler
 */
public class LocalCommandBus {
    private static final Logger log = LoggerFactory.getLogger(LocalCommandBus.class);

    private final List<CommandBusInterceptor> interceptors    = new ArrayList<>();
    private final Set<CommandHandler>         commandHandlers = new HashSet<>();

    /**
     * Key: Concrete Command type<br>
     * Value: {@link CommandHandler} that can handle the command type
     */
    private final ConcurrentMap<Class<?>, CommandHandler> commandTypeToCommandHandlerCache = new ConcurrentHashMap<>();

    public LocalCommandBus() {
        this(List.of());
    }

    public LocalCommandBus(List<CommandBusInterceptor> interceptors) {
        requireNonNull(interceptors, "No interceptors list provided");
        interceptors.forEach(this::addInterceptor);
    }

    public LocalCommandBus(CommandBusInterceptor... interceptors) {
        this(List.of(interceptors));
    }

    /**
     * Returns an immutable list of interceptors
     *
     * @return Returns an immutable list of interceptors
     */
    public List<CommandBusInterceptor> getInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    /**
     * Add a new interceptor
     *
     * @param interceptor the interceptor to add
     * @return this bus instance
     */
    public LocalCommandBus addInterceptor(CommandBusInterceptor interceptor) {
        if (!interceptors.contains(interceptor)) {
            log.info("Adding CommandBusInterceptor: {}", interceptor);
            interceptors.add(requireNonNull(interceptor, "No interceptor provided"));
        }
        return this;
    }

    /**
     * Guard method to test if the {@link LocalCommandBus} already contains the interceptor
     *
     * @param interceptor the interceptor to check
     * @return true if the {@link LocalCommandBus} already contains the interceptor, otherwise false
     */
    public boolean hasInterceptor(CommandBusInterceptor interceptor) {
        return interceptors.contains(requireNonNull(interceptor, "No interceptor provided"));
    }

    /**
     * Remove an interceptor
     *
     * @param interceptor the interceptor to remove
     * @return this bus instance
     */
    public LocalCommandBus removeInterceptor(CommandBusInterceptor interceptor) {
        log.info("Removing CommandBusInterceptor: {}", interceptor);
        interceptors.remove(requireNonNull(interceptor, "No interceptor provided"));
        return this;
    }

    /**
     * Add a new command handler.
     *
     * @param commandHandler the command handler to add
     * @return this bus instance
     */
    public LocalCommandBus addCommandHandler(CommandHandler commandHandler) {
        if (!hasCommandHandler(commandHandler)) {
            log.info("Adding CommandHandler: {}", commandHandler);
            if (commandHandlers.add(requireNonNull(commandHandler, "No commandHandler provided"))) {
                commandTypeToCommandHandlerCache.clear();
            }
        }
        return this;
    }


    /**
     * Remove a command handler.
     *
     * @param commandHandler the command handler to remove
     * @return this bus instance
     */
    public LocalCommandBus removeCommandHandler(CommandHandler commandHandler) {
        log.info("Removing CommandHandler: {}", commandHandler);
        if (commandHandlers.remove(requireNonNull(commandHandler, "No commandHandler provided"))) {
            commandTypeToCommandHandlerCache.clear();
        }
        return this;
    }

    /**
     * The send command synchronously and process the command on the sending thread
     *
     * @param command the command to send
     * @param <R>     the return type
     * @param <C>     the command type
     * @return the result of the command processing (if any)
     * @throws MultipleCommandHandlersFoundException If multiple {@link CommandHandler} claim that they can handle a given command
     * @throws NoCommandHandlerFoundException        If no {@link CommandHandler}'s can handle the given command
     */
    @SuppressWarnings("unchecked")
    public <R, C> R send(C command) {
        var commandHandler = findCommandHandlerCapableOfHandling(command);
        log.debug("Synchronously sending command of type '{}' to {} '{}'", command.getClass().getName(), CommandHandler.class.getSimpleName(), commandHandler.toString());
        return (R) CommandBusInterceptorChain.newInterceptorChain(command,
                                                                  commandHandler,
                                                                  interceptors,
                                                                  (interceptor, commandBusInterceptorChain) -> interceptor.interceptSend(command, commandBusInterceptorChain),
                                                                  commandHandler::handle)
                                             .proceed();
    }

    /**
     * The send command asynchronously and process the command on a {@link Schedulers#boundedElastic()} thread
     *
     * @param command the command to send
     * @param <R>     the return type
     * @param <C>     the command type
     * @return a {@link Mono} that will contain the result of the command processing (if any)
     * @throws MultipleCommandHandlersFoundException If multiple {@link CommandHandler} claim that they can handle a given command
     * @throws NoCommandHandlerFoundException        If no {@link CommandHandler}'s can handle the given command
     */
    @SuppressWarnings("unchecked")
    public <R, C> Mono<R> sendAsync(C command) {
        var commandHandler = findCommandHandlerCapableOfHandling(command);
        log.debug("Asynchronously sending command of type '{}' to {} '{}'", command.getClass().getName(), CommandHandler.class.getSimpleName(), commandHandler.toString());
        return Mono.fromCallable(() -> (R) CommandBusInterceptorChain.newInterceptorChain(command,
                                                                                          commandHandler,
                                                                                          interceptors,
                                                                                          (interceptor, commandBusInterceptorChain) -> interceptor.interceptSendAsync(command, commandBusInterceptorChain),
                                                                                          commandHandler::handle)
                                                                     .proceed()).publishOn(Schedulers.boundedElastic());
    }

    /**
     * The send command asynchronously without waiting for the result of processing the Command.<br>
     * The command handler cannot return any value when invoked using this method.
     *
     * @param command the command to send
     * @param <C>     the command type
     * @throws MultipleCommandHandlersFoundException If multiple {@link CommandHandler} claim that they can handle a given command
     * @throws NoCommandHandlerFoundException        If no {@link CommandHandler}'s can handle the given command
     */
    @SuppressWarnings("unchecked")
    public <C> void sendAndDontWait(C command) {
        var commandHandler = findCommandHandlerCapableOfHandling(command);
        log.debug("Asynchronously sending command of type '{}' to {} '{}'", command.getClass().getName(), CommandHandler.class.getSimpleName(), commandHandler.toString());
        Mono.fromCallable(() -> CommandBusInterceptorChain.newInterceptorChain(command,
                                                                               commandHandler,
                                                                               interceptors,
                                                                               (interceptor, commandBusInterceptorChain) -> {
                                                                                   interceptor.interceptSendAndDontWait(command, commandBusInterceptorChain);
                                                                                   return null;
                                                                               },
                                                                               commandHandler::handle)
                                                          .proceed())
            .publishOn(Schedulers.boundedElastic())
            .subscribe();
    }

    /**
     * Find a {@link CommandHandler} capable of handling the given command
     *
     * @param command the command to handle
     * @return the single {@link CommandHandler} that's capable of handling the given command
     * @throws MultipleCommandHandlersFoundException If multiple {@link CommandHandler} claim that they can handle a given command
     * @throws NoCommandHandlerFoundException        If no {@link CommandHandler}'s can handle the given command
     */
    public CommandHandler findCommandHandlerCapableOfHandling(Object command) {
        requireNonNull(command, "No command provided");
        return commandTypeToCommandHandlerCache.computeIfAbsent(command.getClass(), commandType -> {
            var commandHandlersThatCanHandleCommand = commandHandlers.stream().filter(commandHandler -> commandHandler.canHandle(commandType)).collect(Collectors.toList());
            if (commandHandlersThatCanHandleCommand.isEmpty()) {
                throw new NoCommandHandlerFoundException(commandType, msg("Couldn't find a {} that can handle a command of type '{}'", CommandHandler.class.getSimpleName(), commandType.getName()));
            } else if (commandHandlersThatCanHandleCommand.size() > 1) {
                throw new MultipleCommandHandlersFoundException(commandType,
                                                                msg("There should only be one {} that can handle a given command. Found {} {}'s that all can handle a command of type '{}': {}",
                                                                    CommandHandler.class.getSimpleName(),
                                                                    commandHandlersThatCanHandleCommand.size(),
                                                                    CommandHandler.class.getSimpleName(),
                                                                    commandType.getName(),
                                                                    commandHandlersThatCanHandleCommand.stream().map(Object::toString).collect(Collectors.toList())));

            } else {
                return commandHandlersThatCanHandleCommand.get(0);
            }
        });
    }

    /**
     * Guard method to test if the {@link LocalCommandBus} already contains the commandHandler
     *
     * @param commandHandler the commandHandler to check
     * @return true if the {@link LocalCommandBus} already contains the commandHandler, otherwise false
     */
    public boolean hasCommandHandler(CommandHandler commandHandler) {
        return commandHandlers.contains(requireNonNull(commandHandler, "No commandHandler provided"));
    }

    @Override
    public String toString() {
        return "LocalCommandBus{" +
                "interceptors=" + interceptors +
                ", commandHandlers=" + commandHandlers +
                '}';
    }
}
