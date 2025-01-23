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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.messaging.MessageHandler;
import dk.cloudcreate.essentials.shared.reflection.invocation.*;

import java.lang.reflect.Method;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Pattern matching {@link QueuedMessageHandler} for use with {@link DurableQueues}'s<br>
 * The {@link PatternMatchingQueuedMessageHandler} will automatically call methods annotated with the {@literal @MessageHandler} annotation and
 * where the 1st argument matches the actual Message payload type (contained in the {@link Message#getPayload()} provided to the {@link QueuedMessageHandler#handle(QueuedMessage)} method)
 * <p>
 * Each method may also include a 2nd argument that of type {@link QueuedMessage} in which case the event that's being matched is included as the 2nd argument in the call to the method.<br>
 * The methods can have any accessibility (private, public, etc.), they just have to be instance methods.
 * <p>
 * Example:
 * <pre>{@code
 * public class MyMessageHandler extends PatternMatchingQueuedMessageHandler {
 *
 *         @MessageHandler
 *         public void handle(OrderEvent.OrderAdded orderAdded) {
 *             ...
 *         }
 *
 *         @MessageHandler
 *         private void handle(OrderEvent.ProductRemovedFromOrder productRemovedFromOrder, QueuedMessage queuedMessage) {
 *           ...
 *         }
 * }
 * }</pre>
 */
public abstract class PatternMatchingQueuedMessageHandler implements QueuedMessageHandler {
    private final PatternMatchingMethodInvoker<Object> invoker;
    private final Object  invokeMessageHandlerMethodsOn;
    private       boolean allowUnmatchedMessages = false;

    /**
     * Create an {@link PatternMatchingQueuedMessageHandler} that can resolve and invoke message handler methods, i.e. methods
     * annotated with {@literal @MessageHandler}, on another object
     *
     * @param invokeMessageHandlerMethodsOn the object that contains the {@literal @MessageHandler} annotated methods
     */
    public PatternMatchingQueuedMessageHandler(Object invokeMessageHandlerMethodsOn) {
        this.invokeMessageHandlerMethodsOn = requireNonNull(invokeMessageHandlerMethodsOn, "No invokeMessageHandlerMethodsOn provided");
        invoker = createMethodInvoker();

    }

    /**
     * Create an {@link PatternMatchingQueuedMessageHandler} that can resolve and invoke message handler methods, i.e. methods
     * annotated with {@literal @MessageHandler}, on this concrete subclass of {@link PatternMatchingQueuedMessageHandler}
     */
    public PatternMatchingQueuedMessageHandler() {
        this.invokeMessageHandlerMethodsOn = this;
        invoker = createMethodInvoker();
    }

    private PatternMatchingMethodInvoker<Object> createMethodInvoker() {
        return new PatternMatchingMethodInvoker<>(invokeMessageHandlerMethodsOn,
                                                  new QueuedMessageHandlerMethodPatternMatcher(),
                                                  InvocationStrategy.InvokeMostSpecificTypeMatched);
    }

    /**
     * Should the event handler allow unmatched {@link Message#getPayload()}?
     * If true then an unmatched {@link Message#getPayload()} is ignored, if false (the default value)
     * then an unmatched event
     * will cause {@link #handleUnmatchedMessage(QueuedMessage)} will throw
     * an {@link IllegalArgumentException}
     *
     * @return should the event handler allow unmatched events
     */
    public boolean isAllowUnmatchedMessages() {
        return allowUnmatchedMessages;
    }

    /**
     * Should the event handler allow unmatched {@link Message#getPayload()}?
     * If true then an unmatched {@link Message#getPayload()} is ignored, if false (the default value)
     * then an unmatched event
     * will cause {@link #handleUnmatchedMessage(QueuedMessage)} will throw
     * an {@link IllegalArgumentException}
     *
     * @param allowUnmatchedMessages should the event handler allow unmatched {@link Message#getPayload()}
     */
    public void setAllowUnmatchedMessages(boolean allowUnmatchedMessages) {
        this.allowUnmatchedMessages = allowUnmatchedMessages;
    }

    /**
     * Should the event handler allow unmatched {@link Message#getPayload()}?
     * If true then an unmatched {@link Message#getPayload()} is ignored, if false (the default value)
     * then an unmatched event
     * will cause {@link #handleUnmatchedMessage(QueuedMessage)} will throw
     * an {@link IllegalArgumentException}
     *
     * @see #setAllowUnmatchedMessages(boolean)
     */
    public void allowUnmatchedMessages() {
        setAllowUnmatchedMessages(true);
    }

    @Override
    public void handle(QueuedMessage queuedMessage) {
        invoker.invoke(queuedMessage, unmatchedEvent -> {
            handleUnmatchedMessage(queuedMessage);
        });
    }

    /**
     * Override this method to provide custom handling for {@link QueuedMessage}'s who's {@link Message#getPayload()} aren't matched<br>
     * Default behaviour is to throw an {@link IllegalArgumentException} unless {@link #isAllowUnmatchedMessages()}
     * is set to true (default value is false)
     *
     * @param queuedMessage the unmatched queue message
     */
    protected void handleUnmatchedMessage(QueuedMessage queuedMessage) {
        if (!allowUnmatchedMessages) {
            throw new IllegalArgumentException(msg("Unmatched Message with payload-type: '{}'",
                                                   queuedMessage.getMessage().getPayload().getClass().getName()));
        }
    }

    private static class QueuedMessageHandlerMethodPatternMatcher implements MethodPatternMatcher<Object> {

        @Override
        public boolean isInvokableMethod(Method method) {
            requireNonNull(method, "No candidate method supplied");
            var isCandidate = method.isAnnotationPresent(MessageHandler.class) &&
                    method.getParameterCount() >= 1 && method.getParameterCount() <= 2;
            if (isCandidate && method.getParameterCount() == 2) {
                // Check that the 2nd parameter is a PersistedEvent, otherwise it's not supported
                return QueuedMessage.class.equals(method.getParameterTypes()[1]);
            }
            return isCandidate;

        }

        @Override
        public Class<?> resolveInvocationArgumentTypeFromMethodDefinition(Method method) {
            requireNonNull(method, "No method supplied");
            return method.getParameterTypes()[0];
        }

        @Override
        public Class<?> resolveInvocationArgumentTypeFromObject(Object argument) {
            requireNonNull(argument, "No argument supplied");
            requireMustBeInstanceOf(argument, QueuedMessage.class);
            var queuedMessage = (QueuedMessage) argument;

            return queuedMessage.getPayload().getClass();
        }

        public void invokeMethod(Method methodToInvoke,
                                 Object argument,
                                 Object invokeMethodOn,
                                 Class<?> resolvedInvokeMethodWithArgumentOfType) throws Exception {
            requireNonNull(methodToInvoke, "No methodToInvoke supplied");
            requireNonNull(argument, "No argument supplied");
            requireMustBeInstanceOf(argument, QueuedMessage.class);
            requireNonNull(invokeMethodOn, "No invokeMethodOn supplied");
            requireNonNull(resolvedInvokeMethodWithArgumentOfType, "No resolvedInvokeMethodWithArgumentOfType supplied");

            var queuedMessage = (QueuedMessage) argument;
            var payload       = queuedMessage.getPayload();
            if (methodToInvoke.getParameterCount() == 1) {
                methodToInvoke.invoke(invokeMethodOn, payload);
            } else {
                methodToInvoke.invoke(invokeMethodOn, payload, queuedMessage);
            }
        }
    }
}