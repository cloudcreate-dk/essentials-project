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

package dk.trustworks.essentials.shared.reflection;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

import static org.assertj.core.api.Assertions.*;

public class FunctionalInterfaceLoggingNameResolverTest {

    /**
     * Sample functional interfaces for testing
     */
    @FunctionalInterface
    interface MyMessageHandler {
        void handle(String message);
    }

    @FunctionalInterface
    interface MessageHandler extends MyMessageHandler {
        // No additional methods
    }

    /**
     * Named implementations for testing
     */
    static class NamedHandler implements MyMessageHandler {
        @Override
        public void handle(String message) {
            // No-op
        }
    }

    /**
     * Test helper methods for method references
     */
    private String toUpperCase(String input) {
        return input.toUpperCase();
    }

    private static String toLowerCase(String input) {
        return input.toLowerCase();
    }

    @Test
    void testNamedClassLoggingName() {
        var handler     = new NamedHandler();
        var loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(handler);
        assertThat(loggingName).isEqualTo("NamedHandler");
    }

    @Test
    void testMyMessageHandlerLambda() {
        MyMessageHandler lambdaHandler = msg -> {
            // Lambda implementation for testing
        };
        var loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(lambdaHandler);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::MyMessageHandler");
    }

    @Test
    void testMessageHandlerLambda() {
        MessageHandler handler     = msg -> System.out.println(msg);
        var            loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(handler);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::MessageHandler");
    }


    @Test
    void testCachingMechanism() throws Exception {
        // Access the cache via reflection to directly verify caching
        var cacheField = FunctionalInterfaceLoggingNameResolver.class.getDeclaredField("loggingNameCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var cache = (ConcurrentHashMap<Class<?>, String>) cacheField.get(null);

        // Create a handler
        MessageHandler handler     = msg -> System.out.println(msg);
        Class<?> handlerClass = handler.getClass();

        // Clear any existing cache entry
        cache.remove(handlerClass);

        // First call - should compute and cache the name
        var name1 = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(handler);

        // Verify the cache now contains an entry for our handler class
        assertThat(cache).containsKey(handlerClass);
        assertThat(cache.get(handlerClass)).isEqualTo(name1);

        // Modify the cache entry to a unique value
        String modifiedName = name1 + "-MODIFIED";
        cache.put(handlerClass, modifiedName);

        // Second call - should use the modified cache value
        var name2 = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(handler);

        // Verify the second call returns the modified value, proving the cache was used
        assertThat(name2).isEqualTo(modifiedName);
        assertThat(name2).isNotEqualTo(name1);
    }

    @Test
    void testConsumerLambda() {
        Consumer<String> consumer    = s -> System.out.println(s);
        var              loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(consumer);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Consumer");
    }

    @Test
    void testInstanceMethodReference() {
        Function<String, String> func        = this::toUpperCase;
        var                      loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(func);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Function");
    }

    @Test
    void testStaticMethodReference() {
        Function<String, String> func        = FunctionalInterfaceLoggingNameResolverTest::toLowerCase;
        var                      loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(func);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Function");
    }

    @Test
    void testBuiltInMethodReference() {
        Function<String, Integer> func        = String::length;
        var                       loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(func);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Function");
    }

    @Test
    void testAnonymousInnerClass() {
        MyMessageHandler handler = new MyMessageHandler() {
            @Override
            public void handle(String message) {
                // No-op for testing
            }
        };
        var loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(handler);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest$Anonymous::handle");
    }

    @Test
    void testNestedLambda() {
        Supplier<Consumer<String>> nestedLambdaSupplier = () -> s -> System.out.println(s);
        Consumer<String>           consumer             = nestedLambdaSupplier.get();

        var loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(consumer);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Consumer");
    }

    @Test
    void testNullHandler() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FunctionalInterfaceLoggingNameResolver.resolveLoggingName(null))
                .withMessage("No handler provided");
    }

    @Test
    void testNonLambdaObject() {
        var regularObject = "Not a lambda";
        var    loggingName   = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(regularObject);
        assertThat(loggingName).isEqualTo("String");
    }

    @Test
    void testStandardFunctionalInterfaces() {
        // Test various standard functional interfaces from java.util.function
        Consumer<String>          consumer  = s -> System.out.println(s);
        Function<String, Integer> function  = String::length;
        Supplier<String>          supplier  = () -> "test";
        Predicate<String>         predicate = s -> s.length() > 5;

        assertThat(FunctionalInterfaceLoggingNameResolver.resolveLoggingName(consumer)).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Consumer");
        assertThat(FunctionalInterfaceLoggingNameResolver.resolveLoggingName(function)).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Function");
        assertThat(FunctionalInterfaceLoggingNameResolver.resolveLoggingName(supplier)).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Supplier");
        assertThat(FunctionalInterfaceLoggingNameResolver.resolveLoggingName(predicate)).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::Predicate");
    }

    @Test
    void testCqrsCommandHandler() {
        // Simulate a typical CQRS command handler
        interface Command {
        }
        class CreateOrderCommand implements Command {
            private String orderId;

            public CreateOrderCommand(String orderId) {
                this.orderId = orderId;
            }
        }

        @FunctionalInterface
        interface CommandHandler<T extends Command> {
            void handle(T command);
        }

        // Create a command handler lambda
        CommandHandler<CreateOrderCommand> handler = cmd ->
                System.out.println("Processing order: " + cmd);

        var loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(handler);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::CommandHandler");
    }

    @Test
    void testDomainEventListener() {
        // Simulate a typical event listener in event sourcing
        interface DomainEvent {
        }
        class OrderCreatedEvent implements DomainEvent {
            private String orderId;

            public OrderCreatedEvent(String orderId) {
                this.orderId = orderId;
            }
        }

        @FunctionalInterface
        interface EventListener<T extends DomainEvent> {
            void onEvent(T event);
        }

        EventListener<OrderCreatedEvent> listener = event ->
                System.out.println("Order created: " + event);

        var loggingName = FunctionalInterfaceLoggingNameResolver.resolveLoggingName(listener);
        assertThat(loggingName).isEqualTo("FunctionalInterfaceLoggingNameResolverTest::EventListener");
    }
}