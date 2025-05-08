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

package dk.trustworks.essentials.reactive.command.interceptor;

import dk.trustworks.essentials.reactive.command.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCommandBusInterceptorChainTest {

    private RecordingCommandBusInterceptor recordingInterceptor1;
    private RecordingCommandBusInterceptor recordingInterceptor2;
    private LocalCommandBus                cmdBus;

    @BeforeEach
    void setup() {
        recordingInterceptor1 = new RecordingCommandBusInterceptor();
        recordingInterceptor2 = new RecordingCommandBusInterceptor();
        cmdBus = new LocalCommandBus(recordingInterceptor1,
                                     recordingInterceptor2);
        cmdBus.addCommandHandler(new TestCmdHandler());
    }

    @Test
    void test_intercepting_send_commands() {
        var cmd1   = "Test";
        var result = cmdBus.send(cmd1);

        assertThat(result).isEqualTo(cmd1);
        assertThat(recordingInterceptor1.interceptedSendCommands).isEqualTo(List.of(cmd1));
        assertThat(recordingInterceptor1.interceptedSendAsyncCommands).isEmpty();
        assertThat(recordingInterceptor1.interceptedSendAndDontWaitCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendCommands).isEqualTo(List.of(cmd1));
        assertThat(recordingInterceptor2.interceptedSendAsyncCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendAndDontWaitCommands).isEmpty();

        var cmd2 = "Test2";
        result = cmdBus.send(cmd2);
        assertThat(result).isEqualTo(cmd2);
        assertThat(recordingInterceptor1.interceptedSendCommands).isEqualTo(List.of(cmd1, cmd2));
        assertThat(recordingInterceptor1.interceptedSendAsyncCommands).isEmpty();
        assertThat(recordingInterceptor1.interceptedSendAndDontWaitCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendCommands).isEqualTo(List.of(cmd1, cmd2));
        assertThat(recordingInterceptor2.interceptedSendAsyncCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendAndDontWaitCommands).isEmpty();
    }

    @Test
    void test_intercepting_sendAsync_commands() {
        var cmd1   = "Test";
        var result = cmdBus.sendAsync(cmd1).block();

        assertThat(result).isEqualTo(cmd1);
        assertThat(recordingInterceptor1.interceptedSendCommands).isEmpty();
        assertThat(recordingInterceptor1.interceptedSendAsyncCommands).isEqualTo(List.of(cmd1));
        assertThat(recordingInterceptor1.interceptedSendAndDontWaitCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendAsyncCommands).isEqualTo(List.of(cmd1));
        assertThat(recordingInterceptor2.interceptedSendAndDontWaitCommands).isEmpty();

        var cmd2 = "Test2";
        result = cmdBus.sendAsync(cmd2).block();
        assertThat(result).isEqualTo(cmd2);
        assertThat(recordingInterceptor1.interceptedSendCommands).isEmpty();
        assertThat(recordingInterceptor1.interceptedSendAsyncCommands).isEqualTo(List.of(cmd1, cmd2));
        assertThat(recordingInterceptor1.interceptedSendAndDontWaitCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendAsyncCommands).isEqualTo(List.of(cmd1, cmd2));
        assertThat(recordingInterceptor2.interceptedSendAndDontWaitCommands).isEmpty();
    }

    @Test
    void test_intercepting_sendAndDontWait_commands() {
        var cmd1 = "Test";
        cmdBus.sendAndDontWait(cmd1);

        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertThat(recordingInterceptor1.interceptedSendAndDontWaitCommands).isEqualTo(List.of(cmd1)));
        assertThat(recordingInterceptor1.interceptedSendCommands).isEmpty();
        assertThat(recordingInterceptor1.interceptedSendAsyncCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendAndDontWaitCommands).isEqualTo(List.of(cmd1));
        assertThat(recordingInterceptor2.interceptedSendCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendAsyncCommands).isEmpty();


        var cmd2 = "Test2";
        cmdBus.sendAndDontWait(cmd2);
        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertThat(recordingInterceptor1.interceptedSendAndDontWaitCommands).isEqualTo(List.of(cmd1, cmd2)));
        assertThat(recordingInterceptor1.interceptedSendCommands).isEmpty();
        assertThat(recordingInterceptor1.interceptedSendAsyncCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendAndDontWaitCommands).isEqualTo(List.of(cmd1, cmd2));
        assertThat(recordingInterceptor2.interceptedSendCommands).isEmpty();
        assertThat(recordingInterceptor2.interceptedSendAsyncCommands).isEmpty();
    }

    private static class TestCmdHandler implements CommandHandler {

        @Override
        public boolean canHandle(Class<?> commandType) {
            return true;
        }

        @Override
        public Object handle(Object command) {
            return command;
        }
    }

    private static class RecordingCommandBusInterceptor implements CommandBusInterceptor {
        private List<Object> interceptedSendCommands            = new ArrayList<>();
        private List<Object> interceptedSendAsyncCommands       = new ArrayList<>();
        private List<Object> interceptedSendAndDontWaitCommands = new ArrayList<>();

        @Override
        public Object interceptSend(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
            assertThat(command).isNotNull();
            assertThat(commandBusInterceptorChain).isNotNull();
            assertThat(commandBusInterceptorChain.matchedCommandHandler()).isNotNull();
            assertThat(commandBusInterceptorChain.command()).isSameAs(command);
            interceptedSendCommands.add(command);
            return CommandBusInterceptor.super.interceptSend(command, commandBusInterceptorChain);
        }

        @Override
        public Object interceptSendAsync(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
            assertThat(command).isNotNull();
            assertThat(commandBusInterceptorChain).isNotNull();
            assertThat(commandBusInterceptorChain.matchedCommandHandler()).isNotNull();
            assertThat(commandBusInterceptorChain.command()).isSameAs(command);
            interceptedSendAsyncCommands.add(command);
            return CommandBusInterceptor.super.interceptSendAsync(command, commandBusInterceptorChain);
        }

        @Override
        public void interceptSendAndDontWait(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
            assertThat(command).isNotNull();
            assertThat(commandBusInterceptorChain).isNotNull();
            assertThat(commandBusInterceptorChain.matchedCommandHandler()).isNotNull();
            assertThat(commandBusInterceptorChain.command()).isSameAs(command);
            interceptedSendAndDontWaitCommands.add(command);
            CommandBusInterceptor.super.interceptSendAndDontWait(command, commandBusInterceptorChain);
        }
    }
}