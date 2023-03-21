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

package dk.cloudcreate.essentials.shared.interceptor;

import java.util.List;
import java.util.function.*;

/**
 * Generic interceptor chain concept that supports intercepting concrete {@link Interceptor} operations to modify the behaviour or
 * add to the default behaviour
 *
 * @param <OPERATION>        the type of operation to intercept, aka. the argument to the interceptor
 * @param <RESULT>           the result of the operation
 * @param <INTERCEPTOR_TYPE> The type of interceptor
 */
public interface InterceptorChain<OPERATION, RESULT, INTERCEPTOR_TYPE extends Interceptor> {
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
    static <OPERATION, RESULT, INTERCEPTOR_TYPE extends Interceptor> InterceptorChain<OPERATION, RESULT, INTERCEPTOR_TYPE> newInterceptorChainForOperation(OPERATION operation,
                                                                                                                                                           List<INTERCEPTOR_TYPE> interceptors,
                                                                                                                                                           BiFunction<INTERCEPTOR_TYPE, InterceptorChain<OPERATION, RESULT, INTERCEPTOR_TYPE>, RESULT> interceptorMethodInvoker,
                                                                                                                                                           Supplier<RESULT> defaultBehaviour) {
        return new DefaultInterceptorChain<>(operation, interceptors, interceptorMethodInvoker, defaultBehaviour);
    }

}
