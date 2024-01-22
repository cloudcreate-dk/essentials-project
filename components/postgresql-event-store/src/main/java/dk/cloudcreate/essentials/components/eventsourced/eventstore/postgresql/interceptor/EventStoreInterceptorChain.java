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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import org.slf4j.*;

import java.util.*;
import java.util.function.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Generic interceptor chain concept that supports intercepting {@link EventStore} operations (such as loading events, fetching event streams and persisting events) to modify the behaviour or
 * add to the default behaviour of the {@link EventStore}
 *
 * @param <OPERATION> the type of {@link EventStore} operation to intercept, aka. the argument to the interceptor
 * @param <RESULT>    the result of the operation
 */
public interface EventStoreInterceptorChain<OPERATION, RESULT> {
    /**
     * To continue the processing a {@link EventStoreInterceptor} will call this method, which in turn will call other {@link EventStoreInterceptor}'s (if more interceptors are configured)
     * and finally the {@link EventStore}'s default implementation will be called.<br>
     * If the {@link EventStoreInterceptor} can provide a result without calling the default {@link EventStore} behaviour then it can just return its (e.g. cached) result and not call {@link #proceed()}
     *
     * @return the result of the operation
     */
    RESULT proceed();

    /**
     * The operation details
     *
     * @return the operation details
     */
    OPERATION operation();

    /**
     * Create a new {@link EventStoreInterceptorChain} instance for the provided <code>operation</code>
     *
     * @param operation                  the {@link EventStore} operation to intercept, aka. the argument to the interceptor
     * @param interceptors               the {@link EventStoreInterceptor}'s (can be an empty List if no interceptors have been configured)
     * @param interceptorMethodInvoker   the function that's responsible for invoking the matching {@link EventStoreInterceptor} method
     * @param defaultEventStoreBehaviour the default {@link EventStore} behaviour for the given <code>operation</code> in case none of the interceptors provided a different result and stopped the interceptor chain
     * @param <OPERATION>                the type of {@link EventStore} operation to intercept, aka. the argument to the interceptor
     * @param <RESULT>                   the result of the operation
     * @return a new {@link EventStoreInterceptorChain} instance for the provided <code>operation</code>
     */
    static <OPERATION, RESULT> EventStoreInterceptorChain<OPERATION, RESULT> newInterceptorChainForOperation(OPERATION operation,
                                                                                                             List<EventStoreInterceptor> interceptors,
                                                                                                             BiFunction<EventStoreInterceptor, EventStoreInterceptorChain<OPERATION, RESULT>, RESULT> interceptorMethodInvoker,
                                                                                                             Supplier<RESULT> defaultEventStoreBehaviour) {
        return new DefaultEventStoreInterceptorChain<>(operation, interceptors, interceptorMethodInvoker, defaultEventStoreBehaviour);
    }

    /**
     * Default implementation for the {@link EventStoreInterceptorChain}. It's recommended to use {@link EventStoreInterceptorChain#newInterceptorChainForOperation(Object, List, BiFunction, Supplier)} to create a new chain
     * instance for a given operation
     *
     * @param <OPERATION> the type of {@link EventStore} operation to intercept, aka. the argument to the interceptor
     * @param <RESULT>    the result of the operation
     */
    class DefaultEventStoreInterceptorChain<OPERATION, RESULT> implements EventStoreInterceptorChain<OPERATION, RESULT> {
        private static final Logger                                                                                   log = LoggerFactory.getLogger(DefaultEventStoreInterceptorChain.class);
        private final        OPERATION                                                                                operation;
        private final        Iterator<EventStoreInterceptor>                                                          interceptorIterator;
        private final        BiFunction<EventStoreInterceptor, EventStoreInterceptorChain<OPERATION, RESULT>, RESULT> interceptorMethodInvoker;
        private final        Supplier<RESULT>                                                                         defaultEventStoreBehaviour;

        /**
         * Create a new {@link EventStoreInterceptorChain} instance for the provided <code>operation</code>
         *
         * @param operation                  the {@link EventStore} operation to intercept, aka. the argument to the interceptor
         * @param interceptors               the ordered {@link EventStoreInterceptor}'s (can be an empty List if no interceptors have been configured)
         * @param interceptorMethodInvoker   the function that's responsible for invoking the matching {@link EventStoreInterceptor} method
         * @param defaultEventStoreBehaviour the default {@link EventStore} behaviour for the given <code>operation</code> in case none of the interceptors provided a different result and stopped the interceptor chain
         */
        public DefaultEventStoreInterceptorChain(OPERATION operation,
                                                 List<EventStoreInterceptor> interceptors,
                                                 BiFunction<EventStoreInterceptor, EventStoreInterceptorChain<OPERATION, RESULT>, RESULT> interceptorMethodInvoker,
                                                 Supplier<RESULT> defaultEventStoreBehaviour) {
            this.operation = requireNonNull(operation, "No operation provided");
            this.interceptorIterator = requireNonNull(interceptors, "No interceptors provided").iterator();
            this.interceptorMethodInvoker = requireNonNull(interceptorMethodInvoker, "No interceptorMethodInvoker provided");
            this.defaultEventStoreBehaviour = requireNonNull(defaultEventStoreBehaviour, "No defaultEventStoreBehaviour supplier provided");
        }

        @Override
        public RESULT proceed() {
            if (interceptorIterator.hasNext()) {
                var interceptor = interceptorIterator.next();
                log.trace("Invoking interceptor '{}'", interceptor.getClass().getName());
                return interceptorMethodInvoker.apply(interceptor, this);
            } else {
                log.trace("Invoking default EventStore behaviour for operation '{}'", operation.getClass().getSimpleName());
                return defaultEventStoreBehaviour.get();
            }
        }

        @Override
        public OPERATION operation() {
            return operation;
        }
    }
}
