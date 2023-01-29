/*
 * Copyright 2021-2023 the original author or authors.
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

import dk.cloudcreate.essentials.reactive.command.LocalCommandBus;

/**
 * An {@link CommandBusInterceptor} allows you to intercept Commands before and after they're being handled by the {@link LocalCommandBus}
 */
public interface CommandBusInterceptor {

    /**
     * Intercept the {@link LocalCommandBus#send(Object)} operation
     *
     * @param command                    the command
     * @param commandBusInterceptorChain the interceptor chain
     * @return the result of processing the command (default implementation just calls {@link CommandBusInterceptorChain#proceed()})
     */
    default Object interceptSend(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
        return commandBusInterceptorChain.proceed();
    }

    /**
     * Intercept the {@link LocalCommandBus#sendAsync(Object)} operation
     *
     * @param command                    the command
     * @param commandBusInterceptorChain the interceptor chain
     * @return the result of processing the command (default implementation just calls {@link CommandBusInterceptorChain#proceed()})
     */
    default Object interceptSendAsync(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
        return commandBusInterceptorChain.proceed();
    }

    /**
     * Intercept the {@link LocalCommandBus#sendAndDontWait(Object)} operation
     *
     * @param commandMessage             the command message (can be wrapped in an infrastructure wrapper, such as a Message/QueuedMessage, depending on which transport channel is used)
     * @param commandBusInterceptorChain the interceptor chain
     * @return the result of processing the command (default implementation just calls {@link CommandBusInterceptorChain#proceed()})
     */
    default void interceptSendAndDontWait(Object commandMessage, CommandBusInterceptorChain commandBusInterceptorChain) {
        commandBusInterceptorChain.proceed();
    }
}
