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

package dk.trustworks.essentials.components.foundation.messaging.queue.api;

import dk.trustworks.essentials.components.foundation.json.JSONSerializer;
import dk.trustworks.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.trustworks.essentials.components.foundation.messaging.queue.DurableQueues.QueueingSortOrder;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueEntryId;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueueName;
import dk.trustworks.essentials.components.foundation.messaging.queue.QueuedMessage;
import dk.trustworks.essentials.components.foundation.messaging.queue.stats.DurableQueuesStatistics;
import dk.trustworks.essentials.shared.security.EssentialsSecurityProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dk.trustworks.essentials.shared.security.EssentialsSecurityRoles.*;
import static dk.trustworks.essentials.shared.security.EssentialsSecurityValidator.*;

/**
 * <pre>
 * DefaultDurableQueuesApi is the default implementation of the DurableQueuesApi interface.
 * It provides methods for managing durable queues, handling messages,
 * retrieving queue statistics, and performing operations on dead-letter queues.
 *
 * This implementation includes role-based validation to ensure proper access control
 * for queue readers and writers. Messages and queues can be queried, marked as dead letters,
 * deleted, or purged based on the principal's permissions.
 *
 * Constructor Details:
 * The class requires the following dependencies:
 * - EssentialsSecurityProvider for access control and role validation.
 * - DurableQueues to interact with the underlying queue mechanism.
 * - JSONSerializer for serializing message payloads.
 * - DurableQueuesStatistics (optional) for retrieving queue statistics.
 *
 * Method Overview:
 * - getQueueNames: Retrieves the set of all queue names, ensuring the principal has reader access.
 * - getQueueNameFor: Finds the queue name for a specific queue entry ID, validating reader access.
 * - getQueuedMessage: Retrieves a queued message for the given queue entry ID.
 * - resurrectDeadLetterMessage: Resurrects a dead-letter message with an optional delay.
 * - markAsDeadLetterMessage: Marks a message as a dead letter manually, with user identification.
 * - deleteMessage: Deletes a specific message by its queue entry ID.
 * - getTotalMessagesQueuedFor: Gets the total number of messages queued for a specific queue.
 * - getTotalDeadLetterMessagesQueuedFor: Retrieves the total count of dead-letter messages for a queue.
 * - getQueuedMessages: Retrieves a paginated list of queued messages for a queue.
 * - getDeadLetterMessages: Retrieves a paginated list of dead-letter messages for a queue.
 * - purgeQueue: Purges all messages from the specified queue.
 * - getQueuedStatistics: Retrieves statistics for a specific queue, if statistics are available.
 *
 * </pre>
 */
public class DefaultDurableQueuesApi implements DurableQueuesApi {

    private final EssentialsSecurityProvider securityProvider;
    private final DurableQueues durableQueues;
    private final JSONSerializer jsonSerializer;
    private final DurableQueuesStatistics durableQueuesStatistics;

    public DefaultDurableQueuesApi(EssentialsSecurityProvider securityProvider,
                                   DurableQueues durableQueues,
                                   JSONSerializer jsonSerializer,
                                   @Autowired(required = false) @Nullable DurableQueuesStatistics durableQueuesStatistics) {
        this.securityProvider = securityProvider;
        this.durableQueues = durableQueues;
        this.durableQueuesStatistics = durableQueuesStatistics;
        this.jsonSerializer = jsonSerializer;
    }

    private void validateQueueReaderRole(Object principal) {
        validateHasAnyEssentialsSecurityRoles(securityProvider, principal, QUEUE_READER, ESSENTIALS_ADMIN);
    }

    private void validateQueueWriterRole(Object principal) {
        validateHasAnyEssentialsSecurityRoles(securityProvider, principal, QUEUE_WRITER, ESSENTIALS_ADMIN);
    }

    @Override
    public Set<QueueName> getQueueNames(Object principal) {
        validateQueueReaderRole(principal);
        return durableQueues.getQueueNames();
    }

    @Override
    public Optional<QueueName> getQueueNameFor(Object principal, QueueEntryId queueEntryId) {
        validateQueueReaderRole(principal);
        return durableQueues.getQueueNameFor(queueEntryId);
    }

    @Override
    public Optional<ApiQueuedMessage> getQueuedMessage(Object principal, QueueEntryId queueEntryId) {
        validateQueueReaderRole(principal);
        return durableQueues.getQueuedMessage(queueEntryId)
                .map(queueMessage -> ApiQueuedMessage.from(queueMessage, getMessagePayload(principal, queueMessage)));
    }

    private String getMessagePayload(Object principal, QueuedMessage queuedMessage) {
        if (hasAnyEssentialsSecurityRoles(securityProvider, principal, QUEUE_PAYLOAD_READER, ESSENTIALS_ADMIN)) {
            return jsonSerializer.serializePrettyPrint(queuedMessage.getPayload());
        }
        return null;
    }

    @Override
    public Optional<ApiQueuedMessage> resurrectDeadLetterMessage(Object principal, QueueEntryId queueEntryId, Duration deliveryDelay) {
        validateQueueWriterRole(principal);
        return durableQueues.resurrectDeadLetterMessage(queueEntryId, deliveryDelay).map(ApiQueuedMessage::from);
    }

    @Override
    public Optional<ApiQueuedMessage> markAsDeadLetterMessage(Object principal, QueueEntryId queueEntryId) {
        validateQueueWriterRole(principal);
        String userIdentification = securityProvider.getPrincipalName(principal).orElse("");
        return durableQueues.markAsDeadLetterMessage(queueEntryId, "Manual marked by " + userIdentification).map(ApiQueuedMessage::from);
    }

    @Override
    public boolean deleteMessage(Object principal, QueueEntryId queueEntryId) {
        validateQueueWriterRole(principal);
        return durableQueues.deleteMessage(queueEntryId);
    }

    @Override
    public long getTotalMessagesQueuedFor(Object principal, QueueName queueName) {
        validateQueueReaderRole(principal);
        return durableQueues.getTotalMessagesQueuedFor(queueName);
    }

    @Override
    public long getTotalDeadLetterMessagesQueuedFor(Object principal, QueueName queueName) {
        validateQueueReaderRole(principal);
        return durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName);
    }

    @Override
    public List<ApiQueuedMessage> getQueuedMessages(Object principal, QueueName queueName, QueueingSortOrder queueingSortOrder, long startIndex, long pageSize) {
        validateQueueReaderRole(principal);
        return durableQueues.getQueuedMessages(queueName, queueingSortOrder, startIndex, pageSize).stream()
                .map(queueMessage -> ApiQueuedMessage.from(queueMessage, getMessagePayload(principal, queueMessage)))
                .toList();
    }

    @Override
    public List<ApiQueuedMessage> getDeadLetterMessages(Object principal, QueueName queueName, QueueingSortOrder queueingSortOrder, long startIndex, long pageSize) {
        validateQueueReaderRole(principal);
        return durableQueues.getDeadLetterMessages(queueName, queueingSortOrder, startIndex, pageSize).stream()
                .map(queueMessage -> ApiQueuedMessage.from(queueMessage, getMessagePayload(principal, queueMessage)))
                .toList();
    }

    @Override
    public int purgeQueue(Object principal, QueueName queueName) {
        validateQueueWriterRole(principal);
        return durableQueues.purgeQueue(queueName);
    }

    @Override
    public Optional<ApiQueuedStatistics> getQueuedStatistics(Object principal, QueueName queueName) {
        validateQueueReaderRole(principal);
        if (durableQueuesStatistics != null) {
            return durableQueuesStatistics.getQueueStatistics(queueName).map(ApiQueuedStatistics::from);
        }

        return Optional.empty();
    }
}
