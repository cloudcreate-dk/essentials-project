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

package dk.cloudcreate.essentials.reactive.command;

import dk.cloudcreate.essentials.reactive.command.interceptor.CommandBusInterceptor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * <h1>CommandBus / LocalCommandBus / DurableLocalCommandBus</h1>
 *
 * <p>The {@link CommandBus} pattern decouples the sending of commands from their handling
 * by introducing an indirection between the command and its corresponding {@link CommandHandler}.
 * This design provides <b>location transparency</b>, meaning that the sender does not need to
 * know which handler will process the command.
 * The {@link LocalCommandBus} is the default implementation of the {@link CommandBus} pattern.</p>
 *
 * <h2>Single CommandHandler Requirement</h2>
 * <ul>
 *   <li><b>Exactly One Handler:</b> Every command must have one, and only one, {@link CommandHandler}.</li>
 *   <li>If <b>no handler is found</b>, a {@link NoCommandHandlerFoundException} is thrown.</li>
 *   <li>If <b>more than one handler is found</b>, a {@link MultipleCommandHandlersFoundException} is thrown.</li>
 * </ul>
 *
 * <h2>Command Processing and Return Values</h2>
 * <ul>
 *   <li><b>No Expected Return Value:</b> According to CQRS principles, command handling typically does not return a value.</li>
 *   <li><b>Optional Return Value:</b> The API allows a {@link CommandHandler} to return a value if necessary
 *       (for example, a server-generated ID).</li>
 * </ul>
 *
 * <h2>Sending Commands</h2>
 * <p>Commands can be dispatched in different ways depending on your needs:</p>
 *
 * <h3>1. Synchronous processing</h3>
 * <ul>
 *   <li><b>Method:</b> {@link CommandBus#send(Object)}</li>
 *   <li><b>Usage:</b> Use this when you need to process a command immediately and receive instant feedback on success or failure.</li>
 * </ul>
 *
 * <h3>2. Asynchronous processing with Feedback</h3>
 * <ul>
 *   <li><b>Method:</b> {@link CommandBus#sendAsync(Object)}</li>
 *   <li><b>Usage:</b> Returns a {@link reactor.core.publisher.Mono} that will eventually contain the result of the command processing.
 *       This is useful when you want asynchronous processing but still need to know the outcome.</li>
 * </ul>
 *
 * <h3>3. Fire-and-Forget Asynchronous processing</h3>
 * <ul>
 *   <li><b>Method:</b> {@link CommandBus#sendAndDontWait(Object)} or {@link CommandBus#sendAndDontWait(Object, java.time.Duration)}</li>
 *   <li><b>Usage:</b> Sends a command without waiting for a result. This is true fire-and-forget asynchronous processing.</li>
 *   <li><b>Note:</b> If durability is required (ensuring the command is reliably processed),
 *       consider using <code>DurableLocalCommandBus</code> from the <code>essentials-components</code>'s <code>foundation</code> module instead.</li>
 * </ul>
 *
 * <h2>Handling Command Processing Failures</h2>
 *
 * <h3>Using {@link CommandBus#send(Object)} / {@link CommandBus#sendAsync(Object)}</h3>
 * <ul>
 *   <li><b>Immediate Error Feedback:</b> These methods provide quick feedback if command processing fails
 *       (e.g., due to business validation errors), making error handling straightforward.</li>
 * </ul>
 *
 * <h3>Using {@link CommandBus#sendAndDontWait(Object)}</h3>
 * <ul>
 *   <li><b>Delayed Asynchronous Processing:</b> Commands sent via this method are processed in a true asynchronous
 *       fire-and-forget fashion, with an optional delay.</li>
 *   <li><b>Spring Boot Starters Default:</b> When using the Essentials Spring Boot starters, the default {@link CommandBus}
 *       implementation is <code>DurableLocalCommandBus</code>, which places {@link CommandBus#sendAndDontWait(Object)}
 *       commands on a <code>DurableQueue</code> for reliable processing.</li>
 *   <li><b>Exception Handling:</b> Because processing is asynchronous fire-and-forget
 *       (and because commands may be processed on a different node or after a JVM restart),
 *       exceptions can not be captured by the caller.</li>
 *   <li><b>Error Management:</b> The {@link SendAndDontWaitErrorHandler} takes care of handling exceptions that occur
 *       during asynchronous command processing.</li>
 *   <li><b>Retries:</b> The <code>DurableLocalCommandBus</code> is configured with a <code>RedeliveryPolicy</code>
 *       to automatically retry commands that fail.</li>
 * </ul>
 *
 * <h2>Summary</h2>
 * <ul>
 *   <li><b>For Immediate Feedback:</b>
 *       Use {@link CommandBus#send(Object)} or {@link CommandBus#sendAsync(Object)} to simplify error handling
 *       with instant feedback on command processing.</li>
 *   <li><b>For True Asynchronous, Fire-and-Forget Processing:</b>
 *       Use {@link CommandBus#sendAndDontWait(Object)} when you require asynchronous processing with retry capabilities.</li>
 *   <li>Be aware that error handling is deferred to the {@link SendAndDontWaitErrorHandler} and managed by any configured <code>RedeliveryPolicy</code>.</li>
 * </ul>
 * <p>
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
 * <p>
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
