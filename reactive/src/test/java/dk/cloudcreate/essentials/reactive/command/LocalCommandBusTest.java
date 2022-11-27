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

package dk.cloudcreate.essentials.reactive.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCommandBusTest {
    public static final String          ON_PURPOSE = "On purpose";
    private             LocalCommandBus commandBus;

    @BeforeEach
    void setup() {
        commandBus = new LocalCommandBus();
    }

    @Test
    void test_sync_send() {
        // Given
        var cmdHandler = new TestCommandHandler(String.class);
        commandBus.addCommandHandler(cmdHandler);

        // When
        var result = commandBus.send("Hello World");

        // Then
        assertThat(result).isEqualTo(TestCommandHandler.TEST);
        assertThat(cmdHandler.receivedCommand).isEqualTo("Hello World");
    }

    @Test
    void test_sync_send_with_command_processing_exception() {
        // Given
        var cmdHandler = new ExceptionThrowingCommandHandler();
        commandBus.addCommandHandler(cmdHandler);

        // When
        assertThatThrownBy(() -> commandBus.send("Hello World"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(ON_PURPOSE);
    }

    @Test
    void test_async_send() {
        // Given
        var cmdHandler = new TestCommandHandler(String.class);
        commandBus.addCommandHandler(cmdHandler);

        // When
        var result = commandBus.sendAsync("Hello World")
                               .block(Duration.ofMillis(1000));

        // Then
        assertThat(result).isEqualTo(TestCommandHandler.TEST);
        assertThat(cmdHandler.receivedCommand).isEqualTo("Hello World");
    }

    @Test
    void test_async_send_with_command_processing_exception() {
        // Given
        var cmdHandler = new ExceptionThrowingCommandHandler();
        commandBus.addCommandHandler(cmdHandler);

        // When
        assertThatThrownBy(() -> commandBus.sendAsync("Hello World")
                                           .block(Duration.ofMillis(1000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(ON_PURPOSE);
    }

    @Test
    void test_no_matching_command_handler() {
        var longCmdHandler = new TestCommandHandler(Long.class);
        commandBus.addCommandHandler(longCmdHandler);

        assertThatThrownBy(() -> commandBus.send("Hello World"))
                .isInstanceOf(NoCommandHandlerFoundException.class);
    }

    @Test
    void test_multiple_matching_command_handlers() {
        var longCmdHandler = new TestCommandHandler(Long.class);
        commandBus.addCommandHandler(longCmdHandler);
        var longCmd2Handler = new TestCommandHandler(Long.class);
        commandBus.addCommandHandler(longCmd2Handler);

        assertThatThrownBy(() -> commandBus.send(10L))
                .isInstanceOf(MultipleCommandHandlersFoundException.class);
    }

    private static class TestCommandHandler implements CommandHandler {
        private static      Logger   log  = LoggerFactory.getLogger(TestCommandHandler.class);
        public static final String   TEST = "test";
        private final       Class<?> canHandleCommandsOfType;
        private             Object   receivedCommand;

        private TestCommandHandler(Class<?> canHandleCommandsOfType) {
            this.canHandleCommandsOfType = canHandleCommandsOfType;
        }


        @Override
        public boolean canHandle(Class<?> commandType) {
            return canHandleCommandsOfType.isAssignableFrom(commandType);
        }

        @Override
        public Object handle(Object command) {
            log.info("Received command: {}", command);
            receivedCommand = command;
            return TEST;
        }
    }

    private static class ExceptionThrowingCommandHandler implements CommandHandler {
        private static Logger log = LoggerFactory.getLogger(ExceptionThrowingCommandHandler.class);

        @Override
        public boolean canHandle(Class<?> commandType) {
            return true;
        }

        @Override
        public Object handle(Object command) {
            log.info("Received command '{}', will now throw a RuntimeException", command);
            throw new RuntimeException(ON_PURPOSE);
        }
    }

}