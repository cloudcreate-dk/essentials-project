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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.shared.reflection.invocation.*;

import java.lang.reflect.Method;
import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Pattern matching {@link PersistedEventHandler} for use with the {@link EventStoreSubscriptionManager}'s:
 * <ul>
 *     <li>{@link EventStoreSubscriptionManager#exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, FencedLockAwareSubscriber, PersistedEventHandler)} </li>
 *     <li>{@link EventStoreSubscriptionManager#subscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, PersistedEventHandler)}</li>
 * </ul>
 * <br>
 * The {@link PatternMatchingPersistedEventHandler} will automatically call methods annotated with the {@literal @SubscriptionEventHandler} annotation and
 * where the 1st argument matches the actual Event type (contained in the {@link PersistedEvent#event()}) provided to the {@link PersistedEventHandler#handle(PersistedEvent)} method.:
 * <ul>
 * <li>If the {@link PersistedEvent#event()} contains a <b>typed/class based Event</b> (i.e. {@link EventJSON#getEventType()} is present), then it matches on the first argument/parameter of the
 * {@link SubscriptionEventHandler} annotated method.</li>
 * <li>If the {@link PersistedEvent#event()} contains a <b>named Event</b> (i.e. {@link EventJSON#getEventName()} is present, then it matches on a {@link SubscriptionEventHandler} annotated method that
 * accepts a {@link String} as first argument.</li>
 * </ul>
 * Each method may also include a 2nd argument that of type {@link PersistedEvent} in which case the event that's being matched is included as the 2nd argument in the call to the method.<br>
 * The methods can have any accessibility (private, public, etc.), they just have to be instance methods.
 * <p>
 * Example:
 * <pre>{@code
 * public class MyEventHandler extends PatternMatchingPersistedEventHandler {
 *
 *         @Override
 *         public void onResetFrom(GlobalEventOrder globalEventOrder) {
 *
 *         }
 *
 *         @SubscriptionEventHandler
 *         public void handle(OrderEvent.OrderAdded orderAdded) {
 *             ...
 *         }
 *
 *         @SubscriptionEventHandler
 *         private void handle(OrderEvent.ProductAddedToOrder productAddedToOrder) {
 *           ...
 *         }
 *
 *         @SubscriptionEventHandler
 *         private void handle(OrderEvent.ProductRemovedFromOrder productRemovedFromOrder, PersistedEvent productRemovedFromOrderPersistedEvent) {
 *           ...
 *         }
 *
 *         @SubscriptionEventHandler
 *         private void handle(String json, PersistedEvent jsonPersistedEvent) {
 *           ...
 *         }
 * }
 * }</pre>
 */
public abstract class PatternMatchingPersistedEventHandler implements PersistedEventHandler {
    private final PatternMatchingMethodInvoker<Object> invoker;
    private       boolean                              allowUnmatchedEvents = false;

    public PatternMatchingPersistedEventHandler() {
        invoker = new PatternMatchingMethodInvoker<>(this,
                                                     new PersistedEventHandlerMethodPatternMatcher(),
                                                     InvocationStrategy.InvokeMostSpecificTypeMatched);
    }

    /**
     * Should the event handler allow unmatched events?
     * If true then an unmatched event is ignored, if false (the default value)
     * then an unmatched event
     * will cause {@link #handleUnmatchedEvent(PersistedEvent)} will throw
     * an {@link IllegalArgumentException}
     *
     * @return should the event handler allow unmatched events
     */
    protected boolean isAllowUnmatchedEvents() {
        return allowUnmatchedEvents;
    }

    /**
     * Should the event handler allow unmatched events?
     * If true then an unmatched event is ignored, if false (the default value)
     * then an unmatched event
     * will cause {@link #handleUnmatchedEvent(PersistedEvent)} will throw
     * an {@link IllegalArgumentException}
     *
     * @param allowUnmatchedEvents should the event handler allow unmatched events
     */
    protected void setAllowUnmatchedEvents(boolean allowUnmatchedEvents) {
        this.allowUnmatchedEvents = allowUnmatchedEvents;
    }

    /**
     * Allow unmatched events, in which case an unmatched event is ignored.<br>
     * If unmatched events are disallowed (the default)
     * then {@link #handleUnmatchedEvent(PersistedEvent)} will throw
     * an {@link IllegalArgumentException} in case of an unmatched event
     *
     * @see #setAllowUnmatchedEvents(boolean)
     */
    protected void allowUnmatchedEvents() {
        setAllowUnmatchedEvents(true);
    }

    @Override
    public void handle(PersistedEvent event) {
        invoker.invoke(event, unmatchedEvent -> {
            handleUnmatchedEvent(event);
        });
    }

    /**
     * Override this method to provide custom handling for events that aren't matched<br>
     * Default behaviour is to throw an {@link IllegalArgumentException} unless {@link #isAllowUnmatchedEvents()}
     * is set to true (default value is false)
     *
     * @param event the unmatched event
     */
    protected void handleUnmatchedEvent(PersistedEvent event) {
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
                    method.getParameterCount() >= 1 && method.getParameterCount() <= 2;
            if (isCandidate && method.getParameterCount() == 2) {
                // Check that the 2nd parameter is a PersistedEvent, otherwise it's not supported
                return PersistedEvent.class.equals(method.getParameterTypes()[1]);
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
            requireMustBeInstanceOf(argument, PersistedEvent.class);
            var persistedEvent = (PersistedEvent) argument;

            if (persistedEvent.event().getEventType().isPresent()) {
                return persistedEvent.event().getEventType()
                                     .map(EventType::toJavaClass)
                                     .get();
            } else {
                // In case it was a named Event, then let's return String as the lowest common denominator
                return String.class;
            }
        }

        public void invokeMethod(Method methodToInvoke, Object argument, Object invokeMethodOn, Class<?> resolvedInvokeMethodWithArgumentOfType) throws Exception {
            requireNonNull(methodToInvoke, "No methodToInvoke supplied");
            requireNonNull(argument, "No argument supplied");
            requireMustBeInstanceOf(argument, PersistedEvent.class);
            requireNonNull(invokeMethodOn, "No invokeMethodOn supplied");
            requireNonNull(resolvedInvokeMethodWithArgumentOfType, "No resolvedInvokeMethodWithArgumentOfType supplied");

            var    persistedEvent = (PersistedEvent) argument;
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

            if (methodToInvoke.getParameterCount() == 1) {
                methodToInvoke.invoke(invokeMethodOn, firstParameter);
            } else {
                methodToInvoke.invoke(invokeMethodOn, firstParameter, persistedEvent);
            }
        }
    }
}