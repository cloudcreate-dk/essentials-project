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

package dk.cloudcreate.essentials.reactive.command.interceptor;

import dk.cloudcreate.essentials.reactive.command.*;
import org.slf4j.*;

import java.util.*;
import java.util.function.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Generic interceptor chain concept that supports intercepting {@link LocalCommandBus} operations
 */
public interface CommandBusInterceptorChain {
    /**
     * To continue the processing a {@link CommandBusInterceptor} will call this method, which in turn will call other {@link CommandBusInterceptor}'s (if more interceptors are configured)
     * and finally the {@link LocalCommandBus}'s default implementation will be called.<br>
     * If the {@link CommandBusInterceptor} can provide a result without calling the default {@link LocalCommandBus} behaviour then it can just return its (e.g. cached) result and not call {@link #proceed()}
     *
     * @return the result of the operation
     */
    Object proceed();

    /**
     * The command associated with the interceptor chain
     *
     * @return The command associated with the interceptor chain
     */
    Object command();

    /**
     * The single {@link CommandHandler} the responded true to {@link CommandHandler#canHandle(Class)}
     *
     * @return the matched command handler
     */
    CommandHandler matchedCommandHandler();


    /**
     * Create a new {@link CommandBusInterceptorChain} instance for the handling of a Command
     *
     * @param command                     the command instance
     * @param matchedCommandHandler       the single {@link CommandHandler} the responded true to {@link CommandHandler#canHandle(Class)}
     * @param interceptors                the {@link CommandBusInterceptor}'s (can be an empty List if no interceptors have been configured)
     * @param interceptorOperationInvoker the function responsible for calling the correct {@link CommandBusInterceptor} <code>send</code> method
     * @param defaultCommandBusBehaviour  the function invoking the default {@link LocalCommandBus} <code>send</code> method logic
     * @return a new {@link CommandBusInterceptorChain} instance
     */
    static CommandBusInterceptorChain newInterceptorChain(Object command,
                                                          CommandHandler matchedCommandHandler,
                                                          List<CommandBusInterceptor> interceptors,
                                                          BiFunction<CommandBusInterceptor, CommandBusInterceptorChain, Object> interceptorOperationInvoker,
                                                          Function<Object, Object> defaultCommandBusBehaviour) {
        return new DefaultCommandBusInterceptorChain(command, matchedCommandHandler, interceptors, interceptorOperationInvoker, defaultCommandBusBehaviour);
    }

    /**
     * Default implementation for the {@link CommandBusInterceptorChain}. It's recommended to use {@link CommandBusInterceptorChain#newInterceptorChain(Object, CommandHandler, List, BiFunction, Function)}
     * to create a new chain
     */
    class DefaultCommandBusInterceptorChain implements CommandBusInterceptorChain {
        private static final Logger                                                                log = LoggerFactory.getLogger(DefaultCommandBusInterceptorChain.class);
        private final        Object                                                                command;
        private              CommandHandler                                                        matchedCommandHandler;
        private final        Iterator<CommandBusInterceptor>                                       interceptorsIterator;
        private final        BiFunction<CommandBusInterceptor, CommandBusInterceptorChain, Object> interceptorOperationInvoker;
        private final        Function<Object, Object>                                              defaultCommandBusBehaviour;

        /**
         * Create a new {@link CommandBusInterceptorChain} instance for the handling of a Command
         *
         * @param command                     the command instance
         * @param matchedCommandHandler       the single {@link CommandHandler} the responded true to {@link CommandHandler#canHandle(Class)}
         * @param interceptors                the ordered {@link CommandBusInterceptor}'s (can be an empty List if no interceptors have been configured)
         * @param interceptorOperationInvoker the function responsible for calling the correct {@link CommandBusInterceptor} <code>send</code> method
         * @param defaultCommandBusBehaviour  the function invoking the default {@link LocalCommandBus} <code>send</code> method logic
         */
        public DefaultCommandBusInterceptorChain(Object command,
                                                 CommandHandler matchedCommandHandler,
                                                 List<CommandBusInterceptor> interceptors,
                                                 BiFunction<CommandBusInterceptor, CommandBusInterceptorChain, Object> interceptorOperationInvoker,
                                                 Function<Object, Object> defaultCommandBusBehaviour) {
            this.command = requireNonNull(command, "No command provided");
            this.matchedCommandHandler = requireNonNull(matchedCommandHandler, "No matchedCommandHandler provided");
            this.interceptorsIterator = requireNonNull(interceptors, "No CommandBusInterceptor list provided").iterator();
            this.interceptorOperationInvoker = requireNonNull(interceptorOperationInvoker, "No interceptorOperationInvoker provided");
            this.defaultCommandBusBehaviour = requireNonNull(defaultCommandBusBehaviour, "No defaultCommandBusBehaviour function provided");
        }


        @Override
        public Object proceed() {
            if (interceptorsIterator.hasNext()) {
                var interceptor = interceptorsIterator.next();
                log.trace("Invoking interceptor '{}' for Command '{}", interceptor.getClass().getName(),
                          command.getClass().getName());
                return interceptorOperationInvoker.apply(interceptor, this);
            } else {
                log.trace("Invoking default CommandBus behaviour for Command '{}",
                          command.getClass().getName());
                return defaultCommandBusBehaviour.apply(command);
            }
        }

        @Override
        public Object command() {
            return command;
        }

        @Override
        public CommandHandler matchedCommandHandler() {
            return matchedCommandHandler;
        }
    }
}
