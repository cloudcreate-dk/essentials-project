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

package dk.cloudcreate.essentials.reactive.command;

import dk.cloudcreate.essentials.reactive.Handler;

import java.lang.annotation.*;

/**
 * {@link AnnotatedCommandHandler} specific method annotation, that can be used instead of {@link Handler},
 * that can be applied to any single argument method inside a {@link AnnotatedCommandHandler}<br>
 * The annotated methods can have the following accessibility: private, protected, public, etc.</b></blockquote>
 * <p>
 * Each annotated method will be a candidate for <b>command message handling</b>.<br>
 * The single method argument type is matched against the concrete command type using {@link Class#isAssignableFrom(Class)}.<br>
 * The method MAY return a value or void.
 * <pre>{@code
 * public class OrdersCommandHandler extends AnnotatedCommandHandler {
 *
 *      @CmdHandler
 *      private OrderId handle(CreateOrder cmd) {
 *         ...
 *      }
 *
 *      @CmdHandler
 *      private void someMethod(ReimburseOrder cmd) {
 *         ...
 *      }
 * }}</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CmdHandler {
}
