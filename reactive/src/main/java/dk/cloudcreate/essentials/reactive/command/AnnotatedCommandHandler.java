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

import dk.cloudcreate.essentials.reactive.Handler;
import dk.cloudcreate.essentials.shared.Exceptions;
import dk.cloudcreate.essentials.shared.reflection.Methods;
import org.slf4j.*;

import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Extending this class will allow you to colocate multiple related Command handling methods inside the same class and use it together with the {@link LocalCommandBus}<br>
 * Each method must accept a single Command argument, may return a value or return void and be annotated with either the {@link Handler} or {@link CmdHandler} annotation.<br>
 * The method argument type is matched against the concrete command type using {@link Class#isAssignableFrom(Class)}.<br>
 * The method accessibility can be any combination of private, protected, public, etc.<br>
 * Example:
 * <pre>{@code
 * public class OrdersCommandHandler extends AnnotatedCommandHandler {
 *
 *      @Handler
 *      private OrderId handle(CreateOrder cmd) {
 *         ...
 *      }
 *
 *      @CmdHandler
 *      private void someMethod(ReimburseOrder cmd) {
 *         ...
 *      }
 * }}</pre>
 */
public class AnnotatedCommandHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(AnnotatedCommandHandler.class);

    private final List<Method>                    invokableMethods;
    /**
     * Key: Concrete Command type<br>
     * Value: {@link Method} that can handle the command type
     */
    private final ConcurrentMap<Class<?>, Method> commandTypeToHandlerMethodCache = new ConcurrentHashMap<>();

    private final Object invokeCommandHandlerMethodsOn;

    /**
     * Create an {@link AnnotatedCommandHandler} that can resolve and invoke command handler methods, i.e. methods
     * annotated with {@literal @Handler}, on another object
     *
     * @param invokeCommandHandlerMethodsOn the object that contains the {@literal @Handler} annotated methods
     */
    public AnnotatedCommandHandler(Object invokeCommandHandlerMethodsOn) {
        this.invokeCommandHandlerMethodsOn = requireNonNull(invokeCommandHandlerMethodsOn, "No invokeCommandHandlerMethodsOn provided");
        invokableMethods = resolveCommandHandlerMethods();
    }

    /**
     * Create an {@link AnnotatedCommandHandler} that can resolve and invoke command handler methods, i.e. methods
     * annotated with {@literal @Handler}, on this concrete subclass of {@link AnnotatedCommandHandler}
     */
    protected AnnotatedCommandHandler() {
        this.invokeCommandHandlerMethodsOn = this;
        invokableMethods = resolveCommandHandlerMethods();
    }

    private List<Method> resolveCommandHandlerMethods() {
        var onClass = invokeCommandHandlerMethodsOn.getClass();
        var invokableMethods = Methods.methods(onClass)
                                      .stream()
                                      .filter(method -> method.getDeclaringClass() != Object.class)
                                      .filter(method -> method.getParameterCount() == 1)
                                      .filter(candidateMethod -> candidateMethod.isAnnotationPresent(Handler.class) ||
                                              candidateMethod.isAnnotationPresent(CmdHandler.class))
                                      .collect(Collectors.toList());
        if (log.isTraceEnabled()) {
            log.trace("InvokableMethod in {}: {}",
                      onClass.getName(),
                      invokableMethods);
        }
        return invokableMethods;
    }

    @Override
    public boolean canHandle(Class<?> commandType) {
        return getHandlerMethod(commandType) != null;
    }

    @Override
    public final Object handle(Object command) {
        Class<?> commandType   = command.getClass();
        var      handlerMethod = getHandlerMethod(commandType);
        if (handlerMethod == null) {
            throw new NoCommandHandlerFoundException(commandType,
                                                     msg("Couldn't find a @{} annotated method in '{}' that can handle a command of type: '{}'",
                                                         Handler.class.getSimpleName(),
                                                         this.toString(),
                                                         commandType.getName()));
        }
        try {
            return handlerMethod.invoke(invokeCommandHandlerMethodsOn, command);
        } catch (InvocationTargetException e) {
            log.error(msg("Failed to handle command of type {} using @{} annotated method {} in '{}'",
                          commandType.getName(),
                          Handler.class.getSimpleName(),
                          handlerMethod.toString(),
                          invokeCommandHandlerMethodsOn.toString()),
                      e);
            return Exceptions.sneakyThrow(e.getTargetException());
        } catch (Exception e) {
            log.error(msg("Failed to handle command of type {} using @{} annotated method {} in '{}'",
                          commandType.getName(),
                          Handler.class.getSimpleName(),
                          handlerMethod.toString(),
                          invokeCommandHandlerMethodsOn.toString()),
                      e);
            return Exceptions.sneakyThrow(e);
        }
    }

    private Method getHandlerMethod(Class<?> commandType) {
        return commandTypeToHandlerMethodCache.computeIfAbsent(commandType,
                                                               commandType_ -> {
                                                                   var matchingMethods = invokableMethods.stream()
                                                                                                         .filter(method -> {
                                                                                                             var firstMethodArgumentType = method.getParameterTypes()[0];
                                                                                                             return firstMethodArgumentType.isAssignableFrom(commandType);
                                                                                                         })
                                                                                                         .collect(Collectors.toList());
                                                                   if (matchingMethods.isEmpty()) {
                                                                       return null;
                                                                   } else if (matchingMethods.size() > 1) {
                                                                       throw new MultipleCommandHandlersFoundException(commandType,
                                                                                                                       msg("There should only be one @{} annotated method in '{}' that can handle a given command. " +
                                                                                                                                   "Found {} @{} annotated methods that all can handle a command of type '{}': {}",
                                                                                                                           Handler.class.getSimpleName(),
                                                                                                                           invokeCommandHandlerMethodsOn.toString(),
                                                                                                                           matchingMethods.size(),
                                                                                                                           Handler.class.getSimpleName(),
                                                                                                                           commandType.getName(),
                                                                                                                           matchingMethods.stream().map(Method::toString).collect(
                                                                                                                                   Collectors.toList())));

                                                                   } else {
                                                                       return matchingMethods.get(0);
                                                                   }
                                                               });
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "::AnnotatedCommandHandler {" +
                "handling-command(s)=" + commandTypeToHandlerMethodCache.keySet() +
                '}';
    }
}
