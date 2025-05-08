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

package dk.trustworks.essentials.reactive.spring;

import dk.trustworks.essentials.reactive.*;
import dk.trustworks.essentials.reactive.command.*;
import org.slf4j.*;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

import java.util.*;

import static dk.trustworks.essentials.shared.MessageFormatter.msg;

@Configuration
public class ReactiveHandlersConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ReactiveHandlersConfiguration.class);

    @Bean
    public LocalEventBus localEventBus() {
        return new LocalEventBus("Test", 3, (failingSubscriber, event, exception) -> log.error(msg("Error for {} handling {}", failingSubscriber, event), exception));
    }

    @Bean
    public LocalCommandBus localCommandBus() {
        return new LocalCommandBus();
    }

    public static @Bean ReactiveHandlersBeanPostProcessor reactiveHandlersBeanPostProcessor() {
        return new ReactiveHandlersBeanPostProcessor();
    }

    @Component
    public static class MyStringCommandHandler implements CommandHandler {

        @Override
        public boolean canHandle(Class<?> commandType) {
            return String.class.isAssignableFrom(commandType);
        }

        @Override
        public Object handle(Object command) {
            return command;
        }
    }

    @Component
    public static class MyLongCommandHandler implements CommandHandler {

        @Override
        public boolean canHandle(Class<?> commandType) {
            return Long.class.isAssignableFrom(commandType);
        }

        @Override
        public Object handle(Object command) {
            return command;
        }
    }

    @Component
    public static class MyEventHandler1 implements EventHandler {
        List<Object> eventsReceived = new ArrayList<>();

        @Override
        public void handle(Object e) {
            eventsReceived.add(e);
        }
    }

    @Component
    @AsyncEventHandler
    public static class MyEventHandler2 implements EventHandler {
        List<Object> eventsReceived = new ArrayList<>();

        @Override
        public void handle(Object e) {
            eventsReceived.add(e);
        }
    }
}
