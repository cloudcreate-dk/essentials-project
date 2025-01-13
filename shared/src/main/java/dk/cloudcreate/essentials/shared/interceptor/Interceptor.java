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

package dk.cloudcreate.essentials.shared.interceptor;

/**
 * Each interceptor, configured on the method allows you to perform before, after or around interceptor logic according to your needs.<br>
 * The {@link Interceptor} is built around supporting Intercepting specific Operations, where an Operation is determined
 * by the user of the Interceptor. In the case of the EventStore examples of Operations are: AppendToStream, LoadLastPersistedEventRelatedTo,
 * LoadEvent, FetchStream, etc.
 * <p>
 * <b>Recommendation:</b><br>
 * Your concrete {@link Interceptor} should provide a method for each Concrete Operation following this pattern:
 * <pre>{@code
 * default <RESULT> intercept(ConcreteOperation operation, InterceptorChain<ConcreteOperation, RESULT> interceptorChain) {
 *     return interceptorChain.proceed();
 * }
 * }</pre>
 * which allows concrete Interceptors to only override the Operations that it's concerned with
 * <p>
 * Example of using the {@link InterceptorChain}:
 * <pre>{@code
 * var result = InterceptorChain.newInterceptorChainForOperation(operation,
 *                                                               allInterceptorsConfigured,
 *                                                               (interceptor, interceptorChain) -> interceptorChain.intercept(operation, interceptorChain),
 *                                                               () -> performDefaultBehaviorForOperation(operation))
 *                              .proceed();
 * }</pre>

 * @see InterceptorChain
 */
public interface Interceptor {

}
