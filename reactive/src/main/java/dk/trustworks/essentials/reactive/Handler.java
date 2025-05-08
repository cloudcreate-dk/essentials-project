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

package dk.trustworks.essentials.reactive;

import dk.trustworks.essentials.reactive.command.AnnotatedCommandHandler;

import java.lang.annotation.*;

/**
 * Generic Method annotation that can be applied to any single argument method inside a {@link AnnotatedCommandHandler} or {@link AnnotatedEventHandler}.<br>
 * <b>Depending on which class you extend the rules that apply to each method may be different.</b><br>
 * <blockquote><b>Common for all is that the method accessibility can be any combination of private, protected, public, etc.</b></blockquote>
 * <p>
 * <b><u>{@link AnnotatedCommandHandler}</u></b><br>
 * Rules when applied to single argument method inside {@link AnnotatedCommandHandler}<br>
 * Each annotated method will be a candidate for <b>command message handling</b>.<br>
 * The single method argument type is matched against the concrete command type using {@link Class#isAssignableFrom(Class)}.<br>
 * The method MAY return a value or void.
 * <pre>{@code
 * public class OrdersCommandHandler extends AnnotatedCommandHandler {
 *
 *      @Handler
 *      private OrderId handle(CreateOrder cmd) {
 *         ...
 *      }
 *
 *      @Handler
 *      private void someMethod(ReimburseOrder cmd) {
 *         ...
 *      }
 * }}</pre>
 * </p>
 * <p>
 * <b><u>{@link AnnotatedEventHandler}</u></b><br>
 * Rules when applied to single argument method inside {@link AnnotatedEventHandler}<br>
 * Each annotated method will be a candidate for <b>event message handling</b>.<br>
 * Each method must accept a single Event argument, return void and be annotated with the {@link EventHandler} annotation.<br>
 * The method argument type is matched against the concrete event type using {@link Class#isAssignableFrom(Class)}.<br>
 * The method MUST return void.
 * <pre>{@code
 * public class OrderEventsHandler extends AnnotatedEventHandler {
 *
 *     @Handler
 *     void handle(OrderCreated event) {
 *     }
 *
 *     @Handler
 *     void handle(OrderCancelled event) {
 *     }
 * }}</pre>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Handler {
}
