/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.reactive.command.CommandHandler;

import java.time.Duration;
import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * {@link DurableLocalCommandBus} strategy for selecting which {@link DurableQueues}
 * {@link QueueName} to use for a given combination of command and command handler
 */
public interface CommandQueueNameSelector {
    /**
     * Select the {@link QueueName}  for the given combination of command and command handler
     *
     * @param command              the command
     * @param commandHandler       the command handler that's capable of handling the command
     * @param delayMessageDelivery the message delivery delay
     * @return the selected {@link QueueName}
     */
    QueueName selectDurableQueueNameFor(Object command,
                                        CommandHandler commandHandler,
                                        Optional<Duration> delayMessageDelivery);

    static SameCommandQueueForAllCommands sameCommandQueueForAllCommands(QueueName queueName) {
        return new SameCommandQueueForAllCommands(queueName);
    }

    static SameCommandQueueForAllCommands defaultCommandQueueForAllCommands() {
        return new SameCommandQueueForAllCommands(QueueName.of(DurableLocalCommandBus.class.getSimpleName()));
    }

    /**
     * {@link CommandQueueNameSelector} that returns the same  {@link QueueName} for all queued Commands
     */
    class SameCommandQueueForAllCommands implements CommandQueueNameSelector {
        private final QueueName queueName;

        public SameCommandQueueForAllCommands(QueueName queueName) {
            this.queueName = requireNonNull(queueName, "No queueName provided");
        }

        @Override
        public QueueName selectDurableQueueNameFor(Object command,
                                                   CommandHandler commandHandler,
                                                   Optional<Duration> delayMessageDelivery) {
            return queueName;
        }

        @Override
        public String toString() {
            return "SameCommandQueueForAllCommands{" +
                    "queueName=" + queueName +
                    '}';
        }
    }

}
