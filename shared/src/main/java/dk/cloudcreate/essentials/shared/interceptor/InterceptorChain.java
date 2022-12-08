/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.shared.interceptor;

import org.slf4j.*;

import java.util.*;
import java.util.function.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Generic interceptor chain concept that supports intercepting concrete {@link Interceptor} operations to modify the behaviour or
 * add to the default behaviour
 *
 * @param <OPERATION> the type of operation to intercept, aka. the argument to the interceptor
 * @param <RESULT>    the result of the operation
 */
public interface InterceptorChain<OPERATION, RESULT> {
    /**
     * To continue the processing a {@link Interceptor} will call this method, which in turn will call other {@link Interceptor}'s (if more interceptors are configured)
     * and finally the default implementation will be called.<br>
     * If the {@link Interceptor} can provide a result without calling the default behaviour then it can just return its (e.g. cached) result and not call {@link #proceed()}
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
     * Create a new {@link InterceptorChain} instance for the provided <code>operation</code> instance<br>
     * {@link InterceptorChain} instances are not reusable across different operation instances.<br>
     * Example:
     * <pre>{@code
     * var result = newInterceptorChainForOperation(operation,
     *                                              allInterceptorsConfigured,
     *                                              (interceptor, interceptorChain) -> interceptorChain.intercept(operation, interceptorChain),
     *                                              () -> performDefaultBehaviorForOperation(operation)
     *                                              ).proceed();
     * }</pre>
     *
     * @param operation                the operation to intercept, aka. the argument to the interceptor
     * @param interceptors             the {@link Interceptor}'s (can be an empty List if no interceptors have been configured)
     * @param interceptorMethodInvoker the function that's responsible for invoking the matching {@link Interceptor} method
     * @param defaultBehaviour         the default behaviour for the given <code>operation</code> in case none of the interceptors provided a different result and stopped the interceptor chain
     * @param <OPERATION>              the type of operation to intercept, aka. the argument to the interceptor
     * @param <RESULT>                 the result of the operation
     * @return a new {@link InterceptorChain} instance for the provided <code>operation</code>
     */
    static <OPERATION, RESULT> InterceptorChain<OPERATION, RESULT> newInterceptorChainForOperation(OPERATION operation,
                                                                                                   List<Interceptor> interceptors,
                                                                                                   BiFunction<Interceptor, InterceptorChain<OPERATION, RESULT>, RESULT> interceptorMethodInvoker,
                                                                                                   Supplier<RESULT> defaultBehaviour) {
        return new DefaultInterceptorChain<>(operation, interceptors, interceptorMethodInvoker, defaultBehaviour);
    }

    /**
     * Default implementation for the {@link InterceptorChain}. It's recommended to use
     * {@link InterceptorChain#newInterceptorChainForOperation(Object, List, BiFunction, Supplier)} to create a new chain
     * instance for a given operation
     *
     * @param <OPERATION> the type of operation to intercept, aka. the argument to the interceptor
     * @param <RESULT>    the result of the operation
     */
    class DefaultInterceptorChain<OPERATION, RESULT> implements InterceptorChain<OPERATION, RESULT> {
        private static final Logger                                                               log = LoggerFactory.getLogger(DefaultInterceptorChain.class);
        private final        OPERATION                                                            operation;
        private final        Iterator<Interceptor>                                                interceptorIterator;
        private final        BiFunction<Interceptor, InterceptorChain<OPERATION, RESULT>, RESULT> interceptorMethodInvoker;
        private final        Supplier<RESULT>                                                     defaultEventStoreBehaviour;

        /**
         * Create a new {@link InterceptorChain} instance for the provided <code>operation</code>
         *
         * @param operation                the operation to intercept, aka. the argument to the interceptor
         * @param interceptors             the {@link Interceptor}'s (can be an empty List if no interceptors have been configured)
         * @param interceptorMethodInvoker the function that's responsible for invoking the matching {@link Interceptor} method
         * @param defaultBehaviour         the default behaviour for the given <code>operation</code> in case none of the interceptors provided a different result and stopped the interceptor chain
         */
        public DefaultInterceptorChain(OPERATION operation,
                                       List<Interceptor> interceptors,
                                       BiFunction<Interceptor, InterceptorChain<OPERATION, RESULT>, RESULT> interceptorMethodInvoker,
                                       Supplier<RESULT> defaultBehaviour) {
            this.operation = requireNonNull(operation, "No operation provided");
            this.interceptorIterator = requireNonNull(interceptors, "No interceptors provided").iterator();
            this.interceptorMethodInvoker = requireNonNull(interceptorMethodInvoker, "No interceptorMethodInvoker provided");
            this.defaultEventStoreBehaviour = requireNonNull(defaultBehaviour, "No defaultBehaviour supplier provided");
        }

        @Override
        public RESULT proceed() {
            if (interceptorIterator.hasNext()) {
                var interceptor = interceptorIterator.next();
                log.trace("Invoking interceptor '{}'", interceptor.getClass().getName());
                return interceptorMethodInvoker.apply(interceptor, this);
            } else {
                log.trace("Invoking default behaviour for operation '{}'", operation.getClass().getSimpleName());
                return defaultEventStoreBehaviour.get();
            }
        }

        @Override
        public OPERATION operation() {
            return operation;
        }
    }
}
