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

package dk.cloudcreate.essentials.reactive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedEventHandlerTest {
    @Test
    void test_using_an_unsupported_event_does_not_cause_an_exception() {
        var eventHandler = new TestAnnotatedEventHandler();

        eventHandler.handle(Byte.parseByte("1"));
    }

    @Test
    void test_using_a_string_event() {
        var eventHandler = new TestAnnotatedEventHandler();

        eventHandler.handle("Test");

        assertThat(eventHandler.stringEventReceived).isEqualTo("Test");
    }

    @Test
    void test_using_a_long_event() {
        var eventHandler = new TestAnnotatedEventHandler();

        eventHandler.handle(10L);

        assertThat(eventHandler.longEventReceived).isEqualTo(10L);
    }

    private static class TestAnnotatedEventHandler extends AnnotatedEventHandler {
        private String stringEventReceived;
        private Long   longEventReceived;

        @Handler
        void handle(String stringEvent) {
            stringEventReceived = stringEvent;
        }

        @Handler
        void handle(Long longEvent) {
            longEventReceived = longEvent;
        }
    }
}