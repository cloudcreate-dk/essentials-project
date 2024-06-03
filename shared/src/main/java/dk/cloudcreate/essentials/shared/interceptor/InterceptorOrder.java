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

import java.lang.annotation.*;

/**
 * Annotation that can be applied to interceptors to control their order of execution
 * in the {@link InterceptorChain}<br>
 * The lower the number the higher the priority.<br>
 * An interceptor with order 1 will be invoked before an interceptor with order 10.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InterceptorOrder {
    /**
     * The interceptor order. The lower the number the higher the priority.<br>
     * An interceptor with order 1 will be invoked before an interceptor with order 10.
     *
     * @return
     */
    int value() default 10;
}