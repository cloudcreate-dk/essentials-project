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

package dk.cloudcreate.essentials.reactive.spring;

import dk.cloudcreate.essentials.reactive.AnnotatedEventHandler;
import dk.cloudcreate.essentials.reactive.EventHandler;
import dk.cloudcreate.essentials.reactive.LocalEventBus;

import java.lang.annotation.*;

/**
 * Annotate an {@link EventHandler}/{@link AnnotatedEventHandler} with this annotation
 * to have the {@link EventHandler} be registered as an asynchronous event-handler with the default {@link LocalEventBus} (default is registering them as a synchronous event handler).
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AsyncEventHandler {
}

