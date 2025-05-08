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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSON;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.types.EventType;
import dk.trustworks.essentials.components.foundation.json.JSONDeserializationException;
import dk.trustworks.essentials.components.foundation.transaction.UnitOfWork;
import dk.trustworks.essentials.components.foundation.types.SubscriberId;
import dk.trustworks.essentials.shared.functional.tuple.Pair;
import dk.trustworks.essentials.shared.reflection.invocation.*;

import java.lang.reflect.Method;
import java.util.Optional;

import static dk.trustworks.essentials.shared.FailFast.*;
import static dk.trustworks.essentials.shared.MessageFormatter.msg;

/**
 * Pattern matching {@link TransactionalPersistedEventHandler} for use with the {@link EventStoreSubscriptionManager}'s:
 * <ul>
 *     <li>{@link EventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)}
 * </ul>
 * <br>
 * The {@link PatternMatchingTransactionalPersistedEventHandler} will automatically call methods annotated with the {@literal @SubscriptionEventHandler} annotation and
 * where the 1st argument matches the actual Event type (contained in the {@link PersistedEvent#event()}) provided to the {@link PersistedEventHandler#handle(PersistedEvent)} method
 * and where the 2nd argument is a {@link UnitOfWork}:
 * <ul>
 * <li>If the {@link PersistedEvent#event()} contains a <b>typed/class based Event</b> (i.e. {@link EventJSON#getEventType()} is present), then it matches on the 1st argument/parameter of the
 * {@link SubscriptionEventHandler} annotated method.</li>
 * <li>If the {@link PersistedEvent#event()} contains a <b>named Event</b> (i.e. {@link EventJSON#getEventName()} is present, then it matches on a {@link SubscriptionEventHandler} annotated method that
 * accepts a {@link String} as 1st argument.</li>
 * </ul>
 * Each method may also include a 3rd argument that of type {@link PersistedEvent} in which case the event that's being matched is included as the 3rd argument in the call to the method.<br>
 * The methods can have any accessibility (private, public, etc.), they just have to be instance methods.
 * <p>
 * Example:
 * <pre>{@code
 * public class MyEventHandler extends PatternMatchingTransactionalPersistedEventHandler {
 *
 *         @SubscriptionEventHandler
 *         public void handle(OrderEvent.OrderAdded orderAdded, UnitOfWork unitOfWork) {
 *             ...
 *         }
 *
 *         @SubscriptionEventHandler
 *         private void handle(OrderEvent.ProductAddedToOrder productAddedToOrder, UnitOfWork unitOfWork) {
 *           ...
 *         }
 *
 *         @SubscriptionEventHandler
 *         private void handle(OrderEvent.ProductRemovedFromOrder productRemovedFromOrder, UnitOfWork unitOfWork, PersistedEvent productRemovedFromOrderPersistedEvent) {
 *           ...
 *         }
 *
 *         @SubscriptionEventHandler
 *         private void handle(String json, UnitOfWork unitOfWork, PersistedEvent jsonPersistedEvent) {
 *           ...
 *         }
 * }
 * }</pre>
 */
public class PatternMatchingTransactionalPersistedEventHandler implements TransactionalPersistedEventHandler {
    private final PatternMatchingMethodInvoker<Object> invoker;
    private final Object                               invokePersistedEventHandlerMethodsOn;
    private       boolean                              allowUnmatchedEvents = false;

    /**
     * Create an {@link PatternMatchingTransactionalPersistedEventHandler} that can resolve and invoke event handler methods, i.e. methods
     * annotated with {@literal @SubscriptionEventHandler}, on another object
     *
     * @param invokePersistedEventHandlerMethodsOn the object that contains the {@literal @Handler} annotated methods
     */
    public PatternMatchingTransactionalPersistedEventHandler(Object invokePersistedEventHandlerMethodsOn) {
        this.invokePersistedEventHandlerMethodsOn = requireNonNull(invokePersistedEventHandlerMethodsOn, "No invokePersistedEventHandlerMethodsOn provided");
        invoker = createMethodInvoker();

    }

    /**
     * Create an {@link PatternMatchingTransactionalPersistedEventHandler} that can resolve and invoke event handler methods, i.e. methods
     * annotated with {@literal @SubscriptionEventHandler}, on this concrete subclass of {@link PatternMatchingTransactionalPersistedEventHandler}
     */
    public PatternMatchingTransactionalPersistedEventHandler() {
        this.invokePersistedEventHandlerMethodsOn = this;
        invoker = createMethodInvoker();
    }

    private PatternMatchingMethodInvoker<Object> createMethodInvoker() {
        return new PatternMatchingMethodInvoker<>(invokePersistedEventHandlerMethodsOn,
                                                  new PersistedEventHandlerMethodPatternMatcher(),
                                                  InvocationStrategy.InvokeMostSpecificTypeMatched);
    }

    /**
     * Should the event handler allow unmatched events?
     * If true then an unmatched event is ignored, if false (the default value)
     * then an unmatched event
     * will cause  {@link #handleUnmatchedEvent(PersistedEvent, UnitOfWork)}  will throw
     * an {@link IllegalArgumentException}
     *
     * @return should the event handler allow unmatched events
     */
    public boolean isAllowUnmatchedEvents() {
        return allowUnmatchedEvents;
    }

    /**
     * Should the event handler allow unmatched events?
     * If true then an unmatched event is ignored, if false (the default value)
     * then an unmatched event
     * will cause  {@link #handleUnmatchedEvent(PersistedEvent, UnitOfWork)}  will throw
     * an {@link IllegalArgumentException}
     *
     * @param allowUnmatchedEvents should the event handler allow unmatched events
     */
    public void setAllowUnmatchedEvents(boolean allowUnmatchedEvents) {
        this.allowUnmatchedEvents = allowUnmatchedEvents;
    }

    /**
     * Allow unmatched events, in which case an unmatched event is ignored.<br>
     * If unmatched events are disallowed (the default)
     * then {@link #handleUnmatchedEvent(PersistedEvent, UnitOfWork)}  will throw
     * an {@link IllegalArgumentException} in case of an unmatched event
     *
     * @see #setAllowUnmatchedEvents(boolean)
     */
    public void allowUnmatchedEvents() {
        setAllowUnmatchedEvents(true);
    }

    @Override
    public void handle(PersistedEvent event, UnitOfWork unitOfWork) {
        invoker.invoke(Pair.of(event, unitOfWork), unmatchedEvent -> {
            handleUnmatchedEvent(event, unitOfWork);
        });
    }

    /**
     * Override this method to provide custom handling for events that aren't matched<br>
     * Default behaviour is to throw an {@link IllegalArgumentException} unless {@link #isAllowUnmatchedEvents()}
     * is set to true (default value is false)
     *
     * @param event      the unmatched event
     * @param unitOfWork the unit of work
     */
    protected void handleUnmatchedEvent(PersistedEvent event, UnitOfWork unitOfWork) {
        if (!allowUnmatchedEvents) {
            throw new IllegalArgumentException(msg("Unmatched PersistedEvent with eventId: {}, globalOrder: {}, eventType: {}, aggregateId: {}, eventOrder: {}",
                                                   event.eventId(),
                                                   event.globalEventOrder(),
                                                   event.event().getEventTypeOrName().getValue(),
                                                   event.aggregateId(),
                                                   event.eventOrder()));
        }
    }

    private static class PersistedEventHandlerMethodPatternMatcher implements MethodPatternMatcher<Object> {

        @Override
        public boolean isInvokableMethod(Method method) {
            requireNonNull(method, "No candidate method supplied");
            var isCandidate = method.isAnnotationPresent(SubscriptionEventHandler.class) &&
                    method.getParameterCount() >= 2 && method.getParameterCount() <= 3;
            if (!isCandidate) {
                return false;
            }

            // Check that the 2nd parameter is a UnitOfWork subtype, otherwise it's not supported
            if (!UnitOfWork.class.isAssignableFrom(method.getParameterTypes()[1])) {
                return false;
            }

            if (method.getParameterCount() == 3) {
                // Check that the 3rd parameter is a PersistedEvent, otherwise it's not supported
                return PersistedEvent.class.equals(method.getParameterTypes()[2]);
            } else {
                return true;
            }
        }

        @Override
        public Class<?> resolveInvocationArgumentTypeFromMethodDefinition(Method method) {
            requireNonNull(method, "No method supplied");
            return method.getParameterTypes()[0];
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<?> resolveInvocationArgumentTypeFromObject(Object argument) {
            requireNonNull(argument, "No argument supplied");
            requireMustBeInstanceOf(argument, Pair.class);
            var argumentPair   = (Pair<PersistedEvent, UnitOfWork>) argument;
            var persistedEvent = argumentPair._1;

            if (persistedEvent.event().getEventType().isPresent()) {
                return persistedEvent.event().getEventType()
                                     .map(EventType::toJavaClass)
                                     .get();
            } else {
                // In case it was a named Event, then let's return String as the lowest common denominator
                return String.class;
            }
        }

        @SuppressWarnings("unchecked")
        public void invokeMethod(Method methodToInvoke, Object argument, Object invokeMethodOn, Class<?> resolvedInvokeMethodWithArgumentOfType) throws Exception {
            requireNonNull(methodToInvoke, "No methodToInvoke supplied");
            requireNonNull(argument, "No argument supplied");
            requireMustBeInstanceOf(argument, Pair.class);
            requireNonNull(invokeMethodOn, "No invokeMethodOn supplied");
            requireNonNull(resolvedInvokeMethodWithArgumentOfType, "No resolvedInvokeMethodWithArgumentOfType supplied");

            var    argumentPair   = (Pair<PersistedEvent, UnitOfWork>) argument;
            var    persistedEvent = argumentPair._1;
            Object firstParameter;
            if (persistedEvent.event().getEventType().isPresent()) {
                firstParameter = persistedEvent.event()
                                               .getJsonDeserialized()
                                               .orElseThrow(() -> new JSONDeserializationException(msg("No JSON Deserialized payload available for PersistedEvent with eventId: {}, globalOrder: {} and eventType: {}",
                                                                                                       persistedEvent.eventId(),
                                                                                                       persistedEvent.globalEventOrder(),
                                                                                                       persistedEvent.event().getEventTypeOrName().getValue())));

            } else {
                firstParameter = persistedEvent.event().getJson();
            }

            if (methodToInvoke.getParameterCount() == 2) {
                methodToInvoke.invoke(invokeMethodOn, firstParameter, argumentPair._2);
            } else {
                methodToInvoke.invoke(invokeMethodOn, firstParameter, argumentPair._2, persistedEvent);
            }
        }
    }
}