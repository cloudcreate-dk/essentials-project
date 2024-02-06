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

package dk.cloudcreate.essentials.reactive.command;

import dk.cloudcreate.essentials.reactive.command.interceptor.CommandBusInterceptor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link CommandBus} provides an indirection between a command and the {@link CommandHandler} that's capable of handling the command.<br>
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
 * @see AnnotatedCommandHandler
 */
public interface CommandBus {
    /**
     * Returns an immutable list of interceptors
     *
     * @return Returns an immutable list of interceptors
     */
    List<CommandBusInterceptor> getInterceptors();

    /**
     * Add a new interceptor
     *
     * @param interceptor the interceptor to add
     * @return this bus instance
     */
    CommandBus addInterceptor(CommandBusInterceptor interceptor);

    /**
     * Add new interceptors
     *
     * @param interceptors the interceptors to add
     * @return this bus instance
     */
    default CommandBus addInterceptors(List<CommandBusInterceptor> interceptors) {
        requireNonNull(interceptors, "No interceptors list provided");
        interceptors.forEach(this::addInterceptor);
        return this;
    }

    /**
     * Guard method to test if the {@link CommandBus} already contains the interceptor
     *
     * @param interceptor the interceptor to check
     * @return true if the {@link CommandBus} already contains the interceptor, otherwise false
     */
    boolean hasInterceptor(CommandBusInterceptor interceptor);

    /**
     * Remove an interceptor
     *
     * @param interceptor the interceptor to remove
     * @return this bus instance
     */
    CommandBus removeInterceptor(CommandBusInterceptor interceptor);

    /**
     * Add a new command handler.
     *
     * @param commandHandler the command handler to add
     * @return this bus instance
     */
    CommandBus addCommandHandler(CommandHandler commandHandler);

    /**
     * Remove a command handler.
     *
     * @param commandHandler the command handler to remove
     * @return this bus instance
     */
    CommandBus removeCommandHandler(CommandHandler commandHandler);

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
    <R, C> R send(C command);

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
    <R, C> Mono<R> sendAsync(C command);

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
    <C> void sendAndDontWait(C command);

    /**
     * The send command asynchronously without waiting for the result of processing the Command.<br>
     * The command handler cannot return any value when invoked using this method.
     *
     * @param command              the command to send
     * @param delayMessageDelivery how long should we delay the command message sending
     * @param <C>                  the command type
     * @throws MultipleCommandHandlersFoundException If multiple {@link CommandHandler} claim that they can handle a given command
     * @throws NoCommandHandlerFoundException        If no {@link CommandHandler}'s can handle the given command
     */
    @SuppressWarnings("unchecked")
    <C> void sendAndDontWait(C command, Duration delayMessageDelivery);

    /**
     * Find a {@link CommandHandler} capable of handling the given command
     *
     * @param command the command to handle
     * @return the single {@link CommandHandler} that's capable of handling the given command
     * @throws MultipleCommandHandlersFoundException If multiple {@link CommandHandler} claim that they can handle a given command
     * @throws NoCommandHandlerFoundException        If no {@link CommandHandler}'s can handle the given command
     */
    CommandHandler findCommandHandlerCapableOfHandling(Object command);

    /**
     * Guard method to test if the {@link CommandBus} already contains the commandHandler
     *
     * @param commandHandler the commandHandler to check
     * @return true if the {@link CommandBus} already contains the commandHandler, otherwise false
     */
    boolean hasCommandHandler(CommandHandler commandHandler);
}
