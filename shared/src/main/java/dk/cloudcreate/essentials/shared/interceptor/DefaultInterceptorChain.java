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

package dk.cloudcreate.essentials.shared.interceptor;

import org.slf4j.*;

import java.util.*;
import java.util.function.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Default implementation for the {@link InterceptorChain}. It's recommended to use
 * {@link InterceptorChain#newInterceptorChainForOperation(Object, List, BiFunction, Supplier)} to create a new chain
 * instance for a given operation
 *
 * @param <OPERATION> the type of operation to intercept, aka. the argument to the interceptor
 * @param <RESULT>    the result of the operation
 */
public final class DefaultInterceptorChain<OPERATION, RESULT, INTERCEPTOR_TYPE extends Interceptor> implements InterceptorChain<OPERATION, RESULT, INTERCEPTOR_TYPE> {
    private static final Logger                                                                                      log = LoggerFactory.getLogger(dk.cloudcreate.essentials.shared.interceptor.DefaultInterceptorChain.class);
    private final        OPERATION                                                                                   operation;
    private final        Iterator<INTERCEPTOR_TYPE>                                                                  interceptorIterator;
    private final        BiFunction<INTERCEPTOR_TYPE, InterceptorChain<OPERATION, RESULT, INTERCEPTOR_TYPE>, RESULT> interceptorMethodInvoker;
    private final        Supplier<RESULT>                                                                            defaultBehaviour;

    /**
     * Create a new {@link InterceptorChain} instance for the provided <code>operation</code>
     *
     * @param operation                the operation to intercept, aka. the argument to the interceptor
     * @param interceptors             the ordered {@link Interceptor}'s (can be an empty List if no interceptors have been configured)
     * @param interceptorMethodInvoker the function that's responsible for invoking the matching {@link Interceptor} method
     * @param defaultBehaviour         the default behaviour for the given <code>operation</code> in case none of the interceptors provided a different result and stopped the interceptor chain
     */
    public DefaultInterceptorChain(OPERATION operation,
                                   List<INTERCEPTOR_TYPE> interceptors,
                                   BiFunction<INTERCEPTOR_TYPE, InterceptorChain<OPERATION, RESULT, INTERCEPTOR_TYPE>, RESULT> interceptorMethodInvoker,
                                   Supplier<RESULT> defaultBehaviour) {
        this.operation = requireNonNull(operation, "No operation provided");
        this.interceptorIterator = requireNonNull(interceptors, "No interceptors provided").iterator();
        this.interceptorMethodInvoker = requireNonNull(interceptorMethodInvoker, "No interceptorMethodInvoker provided");
        this.defaultBehaviour = requireNonNull(defaultBehaviour, "No defaultBehaviour supplier provided");
    }

    /**
     * Order the interceptors according to the specified {@link InterceptorOrder} annotation (if the annotation is left out
     * then the default order is 10)
     *
     * @param interceptors       the list of interceptors that should be ordered (expects a mutable list that can be reordered)
     * @param <INTERCEPTOR_TYPE> the type of interceptor contained in the list
     * @return the <code>interceptors</code> parameter after it has been reordered
     */
    public static <INTERCEPTOR_TYPE> List<INTERCEPTOR_TYPE> sortInterceptorsByOrder(List<INTERCEPTOR_TYPE> interceptors) {
        interceptors.sort(Comparator.comparingInt(interceptor ->
                                                          interceptor.getClass().getAnnotation(InterceptorOrder.class) != null ?
                                                          interceptor.getClass().getAnnotation(InterceptorOrder.class).value() : 10));
        return interceptors;
    }

    @Override
    public RESULT proceed() {
        if (interceptorIterator.hasNext()) {
            var interceptor = interceptorIterator.next();
            log.trace("Invoking interceptor '{}'", interceptor.getClass().getName());
            return interceptorMethodInvoker.apply(interceptor, this);
        } else {
            log.trace("Invoking default behaviour for operation '{}'", operation.getClass().getSimpleName());
            return defaultBehaviour.get();
        }
    }

    @Override
    public OPERATION operation() {
        return operation;
    }

    @Override
    public String toString() {
        return "InterceptorChain{" +
                "operation=" + operation +
                '}';
    }
}
