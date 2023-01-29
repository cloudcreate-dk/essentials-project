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
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DurableQueueInboxesTest {

    @Test
    void test_getOrCreatedInbox() {
        // Given
        var durableQueues     = mock(DurableQueues.class);
        var fencedLockManager = mock(FencedLockManager.class);
        var inboxes = Inboxes.durableQueueBasedInboxes(durableQueues,
                                                       fencedLockManager);
        var inboxName        = InboxName.of("test");
        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1), 10);

        // When
        var inbox = inboxes.getOrCreateInbox(
                InboxConfig.builder()
                           .inboxName(inboxName)
                           .redeliveryPolicy(redeliveryPolicy)
                           .messageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer)
                           .build(), o -> {
                });
        // Then
        assertThat(inbox).isNotNull();
        assertThat(inbox).isInstanceOf(Inboxes.DurableQueueBasedInboxes.DurableQueueBasedInbox.class);
        assertThat((CharSequence) ((Inboxes.DurableQueueBasedInboxes.DurableQueueBasedInbox) inbox).inboxQueueName).isEqualTo(inboxName.asQueueName());
        assertThat(((Inboxes.DurableQueueBasedInboxes.DurableQueueBasedInbox) inbox).config.messageConsumptionMode).isEqualTo(MessageConsumptionMode.SingleGlobalConsumer);
        assertThat(((Inboxes.DurableQueueBasedInboxes.DurableQueueBasedInbox) inbox).config.redeliveryPolicy).isEqualTo(redeliveryPolicy);
        assertThat(((Inboxes.DurableQueueBasedInboxes.DurableQueueBasedInbox) inbox).config.numberOfParallelMessageConsumers).isEqualTo(1);
        assertThat(inboxes.getInboxes().size()).isEqualTo(1);
        assertThat(inboxes.getInboxes()).contains(inbox);

        // And
        var inbox2 = inboxes.getOrCreateInbox(
                InboxConfig.builder()
                           .inboxName(inboxName)
                           .redeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(10), 1))
                           .messageConsumptionMode(MessageConsumptionMode.GlobalCompetingConsumers).
                           numberOfParallelMessageConsumers(2).build(),
                o -> {
                });
        assertThat(inbox2).isSameAs(inbox);
    }

    @Test
    void test_CompetingConsumers_with_single_consumer() {
        // Given
        var durableQueues     = mock(DurableQueues.class);
        var fencedLockManager = mock(FencedLockManager.class);
        var inboxes = Inboxes.durableQueueBasedInboxes(durableQueues,
                                                       fencedLockManager);

        var inboxName = InboxName.of("test");
        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1),
                                                             3);

        // When
        var inbox = inboxes.getOrCreateInbox(
                InboxConfig.builder()
                           .inboxName(inboxName)
                           .redeliveryPolicy(redeliveryPolicy)
                           .messageConsumptionMode(MessageConsumptionMode.GlobalCompetingConsumers)
                           .build(),
                o -> {
                });

        // Then
        verify(durableQueues).consumeFromQueue(eq(inboxName.asQueueName()),
                                               eq(redeliveryPolicy),
                                               eq(1),
                                               any());
        verifyNoInteractions(fencedLockManager);
    }

    @Test
    void test_SingleGlobalConsumer_without_lock_acquired() {
        var durableQueues     = mock(DurableQueues.class);
        var fencedLockManager = mock(FencedLockManager.class);
        var inboxes = Inboxes.durableQueueBasedInboxes(durableQueues,
                                                       fencedLockManager);


        var inboxName = InboxName.of("test");
        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1),
                                                             3);

        // When
        var inbox = inboxes.getOrCreateInbox(
                InboxConfig.builder()
                           .inboxName(inboxName)
                           .redeliveryPolicy(redeliveryPolicy)
                           .messageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer)
                           .build(),
                o -> {
                });

        // Then
        verify(fencedLockManager).acquireLockAsync(eq(inboxName.asLockName()),
                                                   any());
        verifyNoInteractions(durableQueues);
    }

    @Test
    void test_SingleGlobalConsumer_with_lock_acquired() {
        var inboxName          = InboxName.of("test");
        var durableQueues      = mock(DurableQueues.class);
        var lockCallBackCaptor = ArgumentCaptor.forClass(LockCallback.class);
        var fencedLockManager  = mock(FencedLockManager.class);
        doNothing().when(fencedLockManager).acquireLockAsync(eq(inboxName.asLockName()),
                                                             lockCallBackCaptor.capture());
        var inboxes = Inboxes.durableQueueBasedInboxes(durableQueues,
                                                       fencedLockManager);


        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1),
                                                             3);

        // When
        var inbox = inboxes.getOrCreateInbox(
                InboxConfig.builder()
                           .inboxName(inboxName)
                           .redeliveryPolicy(redeliveryPolicy)
                           .messageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer)
                           .build(),
                o -> {
                });
        // Then
        verify(fencedLockManager).acquireLockAsync(eq(inboxName.asLockName()),
                                                   eq(lockCallBackCaptor.getValue()));

        // And When
        lockCallBackCaptor.getValue().lockAcquired(mock(FencedLock.class));

        // Then
        verify(durableQueues).consumeFromQueue(eq(inboxName.asQueueName()),
                                               eq(redeliveryPolicy),
                                               eq(1),
                                               any());
    }

    @Test
    void test_addMessagesReceived() {
        // Given
        var durableQueues     = mock(DurableQueues.class);
        var fencedLockManager = mock(FencedLockManager.class);
        var inboxes = Inboxes.durableQueueBasedInboxes(durableQueues,
                                                       fencedLockManager);

        var inboxName = InboxName.of("test");
        var redeliveryPolicy = RedeliveryPolicy.fixedBackoff(Duration.ofMillis(1),
                                                             3);
        var inbox = inboxes.getOrCreateInbox(
                InboxConfig.builder()
                           .inboxName(inboxName)
                           .redeliveryPolicy(redeliveryPolicy)
                           .messageConsumptionMode(MessageConsumptionMode.GlobalCompetingConsumers)
                           .build(),
                o -> {
                });

        // When
        var testMessage = Message.of("Test message");
        inbox.addMessageReceived(testMessage);

        // Then
        verify(durableQueues).queueMessage(eq(inboxName.asQueueName()),
                                           eq(testMessage));
    }
}