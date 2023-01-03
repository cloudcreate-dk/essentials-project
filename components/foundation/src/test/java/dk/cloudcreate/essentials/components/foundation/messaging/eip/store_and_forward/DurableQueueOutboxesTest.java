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

package dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward;

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DurableQueueOutboxesTest {

    @Test
    void test_getOrCreatedOutbox() {
        // Given
        var durableQueues     = mock(DurableQueues.class);
        var fencedLockManager = mock(FencedLockManager.class);
        var outboxes = Outboxes.durableQueueBasedOutboxes(durableQueues,
                                                          fencedLockManager);
        var outboxName       = OutboxName.of("test");
        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 10);

        // When
        var outbox = outboxes.getOrCreateOutbox(
                OutboxConfig.builder().setOutboxName(outboxName).setRedeliveryPolicy(redeliveryPolicy).setMessageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer).build(), o -> {
                });
        // Then
        assertThat(outbox).isNotNull();
        assertThat(outbox).isInstanceOf(Outboxes.DurableQueueBasedOutboxes.DurableQueueBasedOutbox.class);
        assertThat((CharSequence) ((Outboxes.DurableQueueBasedOutboxes.DurableQueueBasedOutbox<?>) outbox).outboxQueueName).isEqualTo(outboxName.asQueueName());
        assertThat(((Outboxes.DurableQueueBasedOutboxes.DurableQueueBasedOutbox<?>) outbox).config.messageConsumptionMode).isEqualTo(MessageConsumptionMode.SingleGlobalConsumer);
        assertThat(((Outboxes.DurableQueueBasedOutboxes.DurableQueueBasedOutbox<?>) outbox).config.redeliveryPolicy).isEqualTo(redeliveryPolicy);
        assertThat(((Outboxes.DurableQueueBasedOutboxes.DurableQueueBasedOutbox<?>) outbox).config.numberOfParallelMessageConsumers).isEqualTo(1);
        assertThat(outboxes.getOutboxes().size()).isEqualTo(1);
        assertThat(outboxes.getOutboxes()).contains(outbox);

        // And
        var outbox2 = outboxes.getOrCreateOutbox(
                OutboxConfig.builder()
                            .setOutboxName(outboxName)
                            .setRedeliveryPolicy(RedeliveryPolicy
                                                         .fixedBackoff(Duration.ofMillis(10),
                                                                       1))
                            .setMessageConsumptionMode(MessageConsumptionMode.GlobalCompetingConsumers)
                            .setNumberOfParallelMessageConsumers(2).build(),
                o -> {
                });
        assertThat(outbox2).isSameAs(outbox);
    }

    @Test
    void test_CompetingConsumers_with_single_consumer() {
        // Given
        var durableQueues     = mock(DurableQueues.class);
        var fencedLockManager = mock(FencedLockManager.class);
        var outboxes = Outboxes.durableQueueBasedOutboxes(durableQueues,
                                                          fencedLockManager);

        var outboxName = OutboxName.of("test");
        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1),
                                                             3);

        // When
        var outbox = outboxes.getOrCreateOutbox(
                OutboxConfig.builder()
                            .setOutboxName(outboxName)
                            .setRedeliveryPolicy(redeliveryPolicy)
                            .setMessageConsumptionMode(MessageConsumptionMode.GlobalCompetingConsumers)
                            .build(),
                o -> {
                });

        // Then
        verify(durableQueues).consumeFromQueue(eq(outboxName.asQueueName()),
                                               eq(redeliveryPolicy),
                                               eq(1),
                                               any());
        verifyNoInteractions(fencedLockManager);
    }

    @Test
    void test_SingleGlobalConsumer_without_lock_acquired() {
        var durableQueues     = mock(DurableQueues.class);
        var fencedLockManager = mock(FencedLockManager.class);
        var outboxes = Outboxes.durableQueueBasedOutboxes(durableQueues,
                                                          fencedLockManager);


        var outboxName = OutboxName.of("test");
        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1),
                                                             3);

        // When
        var outbox = outboxes.getOrCreateOutbox(
                OutboxConfig.builder()
                            .setOutboxName(outboxName)
                            .setRedeliveryPolicy(redeliveryPolicy)
                            .setMessageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer)
                            .build(),
                o -> {
                });

        // Then
        verify(fencedLockManager).acquireLockAsync(eq(outboxName.asLockName()),
                                                   any());
        verifyNoInteractions(durableQueues);
    }

    @Test
    void test_SingleGlobalConsumer_with_lock_acquired() {
        var outboxName         = OutboxName.of("test");
        var durableQueues      = mock(DurableQueues.class);
        var lockCallBackCaptor = ArgumentCaptor.forClass(LockCallback.class);
        var fencedLockManager  = mock(FencedLockManager.class);
        doNothing().when(fencedLockManager).acquireLockAsync(eq(outboxName.asLockName()),
                                                             lockCallBackCaptor.capture());
        var outboxes = Outboxes.durableQueueBasedOutboxes(durableQueues,
                                                          fencedLockManager);


        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1),
                                                             3);

        // When
        var outbox = outboxes.getOrCreateOutbox(
                OutboxConfig.builder()
                            .setOutboxName(outboxName)
                            .setRedeliveryPolicy(redeliveryPolicy)
                            .setMessageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer)
                            .build(),
                o -> {
                });
        // Then
        verify(fencedLockManager).acquireLockAsync(eq(outboxName.asLockName()),
                                                   eq(lockCallBackCaptor.getValue()));

        // And When
        lockCallBackCaptor.getValue().lockAcquired(mock(FencedLock.class));

        // Then
        verify(durableQueues).consumeFromQueue(eq(outboxName.asQueueName()),
                                               eq(redeliveryPolicy),
                                               eq(1),
                                               any());
    }

    @Test
    void test_addMessagesReceived() {
        // Given
        var durableQueues     = mock(DurableQueues.class);
        var fencedLockManager = mock(FencedLockManager.class);
        var outboxes = Outboxes.durableQueueBasedOutboxes(durableQueues,
                                                          fencedLockManager);

        var outboxName = OutboxName.of("test");
        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1),
                                                             3);
        var outbox = outboxes.getOrCreateOutbox(
                OutboxConfig.builder()
                            .setOutboxName(outboxName)
                            .setRedeliveryPolicy(redeliveryPolicy)
                            .setMessageConsumptionMode(MessageConsumptionMode.GlobalCompetingConsumers)
                            .build(),
                o -> {
                });

        // When
        outbox.sendMessage("Test message");

        // Then
        verify(durableQueues).queueMessage(eq(outboxName.asQueueName()),
                                           eq("Test message"));
    }
}