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

import dk.cloudcreate.essentials.reactive.LocalEventBus;
import dk.cloudcreate.essentials.reactive.command.LocalCommandBus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration(classes = ReactiveHandlersConfiguration.class)
@ComponentScan
@ExtendWith(SpringExtension.class)
class ReactiveHandlersBeanPostProcessorTest {
    @Autowired
    private LocalEventBus                                        localEventBus;
    @Autowired
    private LocalCommandBus                                      localCommandBus;
    @Autowired
    private ReactiveHandlersConfiguration.MyStringCommandHandler myStringCommandHandler;
    @Autowired
    private ReactiveHandlersConfiguration.MyLongCommandHandler   myLongCommandHandler;
    @Autowired
    private ReactiveHandlersConfiguration.MyEventHandler1        myEventHandler1;
    @Autowired
    private ReactiveHandlersConfiguration.MyEventHandler2        myEventHandler2;

    @Test
    void verify_that_all_command_handlers_are_registered() {
        assertThat(localCommandBus.hasCommandHandler(myLongCommandHandler)).isTrue();
        assertThat(localCommandBus.hasCommandHandler(myLongCommandHandler)).isTrue();
    }

    @Test
    void verify_that_all_event_handlers_are_registered() {
        assertThat(localEventBus.hasAsyncSubscriber(myEventHandler2)).isTrue();
        assertThat(localEventBus.hasAsyncSubscriber(myEventHandler1)).isFalse();
        assertThat(localEventBus.hasSyncSubscriber(myEventHandler1)).isTrue();
        assertThat(localEventBus.hasSyncSubscriber(myEventHandler2)).isFalse();
    }

    @Test
    void verify_string_commands_are_sent_to_the_right_command_handler() {
        String result = localCommandBus.send("TestCommand");
        assertThat(result).isEqualTo("TestCommand");
    }

    @Test
    void verify_long_commands_are_sent_to_the_right_command_handler() {
        long result = localCommandBus.send(10L);
        assertThat(result).isEqualTo(10L);
    }

    @Test
    void verify_events_are_sent_to_both_event_listeners() {
        var event = "TestEvent";
        localEventBus.publish(event);
        assertThat(myEventHandler1.eventsReceived).isEqualTo(List.of(event));
        Awaitility.waitAtMost(Duration.ofMillis(500))
                  .untilAsserted(() -> assertThat(myEventHandler2.eventsReceived).isEqualTo(List.of(event)));
    }

}