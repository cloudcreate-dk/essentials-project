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

package dk.cloudcreate.essentials.reactive;

import dk.cloudcreate.essentials.shared.reflection.invocation.*;
import dk.cloudcreate.essentials.shared.types.GenericType;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Extending this class will allow you to colocate multiple related Event handling methods inside the same class and use it together with the {@link LocalEventBus}<br>
 * Each method must accept a single Event argument, return void and be annotated with the {@link Handler} annotation.<br>
 * The method argument type is matched against the concrete event type using {@link Class#isAssignableFrom(Class)}.<br>
 * The method accessibility can be any combination of private, protected, public, etc.<br>
 * Example:<br>
 * <pre>{@code
 * public class OrderEventsHandler extends AnnotatedEventHandler {
 *
 *     @EventHandler
 *     void handle(OrderCreated event) {
 *     }
 *
 *     @EventHandler
 *     void handle(OrderCancelled event) {
 *     }
 * }}</pre>
 * <br>
 * Example of registering the {@link AnnotatedEventHandler} with the {@link LocalEventBus}:
 * <pre>{@code
 * LocalEventBus localEventBus    = new LocalEventBus("TestBus", 3, (failingSubscriber, event, exception) -> log.error("...."));
 * localEventBus.addAsyncSubscriber(new OrderEventsHandler(...));
 * localEventBus.addSyncSubscriber(new OrderEventsHandler(...));
 * }</pre>
 */
public class AnnotatedEventHandler implements EventHandler {
    private final PatternMatchingMethodInvoker<Object> invoker;
    private final Object invokeEventHandlerMethodsOn;

    /**
     * Create an {@link AnnotatedEventHandler} that can resolve and invoke event handler methods, i.e. methods
     * annotated with {@literal @Handler}, on another object
     *
     * @param invokeEventHandlerMethodsOn the object that contains the {@literal @Handler} annotated methods
     */
    public AnnotatedEventHandler(Object invokeEventHandlerMethodsOn) {
        this.invokeEventHandlerMethodsOn = requireNonNull(invokeEventHandlerMethodsOn, "No invokeEventHandlerMethodsOn provided");
        invoker = createMethodInvoker();
    }

    /**
     * Create an {@link AnnotatedEventHandler} that can resolve and invoke event handler methods, i.e. methods
     * annotated with {@literal @Handler}, on this concrete subclass of {@link AnnotatedEventHandler}
     */
    public AnnotatedEventHandler() {
        this.invokeEventHandlerMethodsOn = this;
        invoker = createMethodInvoker();
    }

    private PatternMatchingMethodInvoker<Object> createMethodInvoker() {
        return new PatternMatchingMethodInvoker<>(invokeEventHandlerMethodsOn,
                                                  new SingleArgumentAnnotatedMethodPatternMatcher<>(Handler.class,
                                                                                                    new GenericType<>() {
                                                                                                    }),
                                                  InvocationStrategy.InvokeMostSpecificTypeMatched);
    }

    @Override
    public final void handle(Object event) {
        invoker.invoke(event, argument -> {
            // Ignore if a given handler doesn't support this event type
        });
    }
}
