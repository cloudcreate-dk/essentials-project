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

import dk.cloudcreate.essentials.reactive.Handler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AnnotatedCommandHandlerTest {

    @Test
    void test_canHandle() {
        var handler = new TestAnnotatedCommandHandler();

        assertThat(handler.canHandle(String.class)).isTrue();
        assertThat(handler.canHandle(Long.class)).isTrue();
        assertThat(handler.canHandle(Byte.class)).isTrue();
        assertThat(handler.canHandle(Double.class)).isFalse();
    }

    @Test
    void test_canHandle_with_duplicate_Handler_methods() {
        var handler = new TestDuplicateHandlerTypesAnnotatedCommandHandler();

        assertThatThrownBy(() -> handler.canHandle(String.class))
                .isInstanceOf(MultipleCommandHandlersFoundException.class);
    }

    @Test
    void test_handle_String_command() {
        var handler = new TestAnnotatedCommandHandler();

        var result = handler.handle("Test");
        assertThat(handler.receivedStringCommand).isEqualTo("Test");
        assertThat(result).isEqualTo("handleString-Test");
    }

    @Test
    void test_handle_Long_command() {
        var handler = new TestAnnotatedCommandHandler();

        var result = handler.handle(10L);
        assertThat(handler.receivedLongCommand).isEqualTo(10L);
        assertThat(result).isEqualTo("handleLong-10");
    }

    @Test
    void test_handle_Byte_command_that_does_not_return_a_value() {
        var handler = new TestAnnotatedCommandHandler();

        var byteValue = Byte.valueOf("1");
        handler.handle(byteValue);
        assertThat(handler.receivedByteCommand).isEqualTo(byteValue);
    }

    @Test
    void test_handle_unsupported_int_command() {
        var handler = new TestAnnotatedCommandHandler();

        assertThatThrownBy(() -> handler.handle(10))
                .isInstanceOf(NoCommandHandlerFoundException.class);
    }

    @Test
    void test_handle_failing_command_handler() {
        var handler = new FailingAnnotatedCommandHandler();

        assertThatThrownBy(() -> handler.handle("Test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("On purpose");
    }

    private static class TestAnnotatedCommandHandler extends AnnotatedCommandHandler {
        private String receivedStringCommand;
        private Long   receivedLongCommand;
        private Byte   receivedByteCommand;

        @Handler
        private String handleString(String stringCommand) {
            receivedStringCommand = stringCommand;
            return "handleString-" + stringCommand;
        }

        @Handler
        private String handleLong(Long longCommand) {
            receivedLongCommand = longCommand;
            return "handleLong-" + longCommand;
        }

        @Handler
        private void handleLong(Byte byteCommand) {
            receivedByteCommand = byteCommand;
        }
    }

    private static class FailingAnnotatedCommandHandler extends AnnotatedCommandHandler {

        @Handler
        private String handleString(String stringCommand) {
            throw new RuntimeException("On purpose");
        }
    }

    private static class TestDuplicateHandlerTypesAnnotatedCommandHandler extends AnnotatedCommandHandler {
        @Handler
        private void handleString(String stringCommand) {
        }

        @Handler
        private void alsoHandleString(String stringCommand) {
        }
    }
}