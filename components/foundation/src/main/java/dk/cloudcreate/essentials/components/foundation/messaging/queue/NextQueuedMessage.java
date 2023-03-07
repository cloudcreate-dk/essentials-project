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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import java.time.Instant;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class NextQueuedMessage implements Comparable<NextQueuedMessage> {
    public final QueueEntryId id;
    public final QueueName    queueName;
    public final Instant      addedTimestamp;
    public final Instant      nextDeliveryTimestamp;



    public NextQueuedMessage(QueueEntryId id,
                             QueueName queueName,
                             Instant addedTimestamp,
                             Instant nextDeliveryTimestamp) {
        this.id = requireNonNull(id, "No id provided");
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.addedTimestamp = requireNonNull(addedTimestamp, "No addedTimestamp provided");
        this.nextDeliveryTimestamp = requireNonNull(nextDeliveryTimestamp, "No nextDeliveryTimestamp provided");
    }

    public NextQueuedMessage(QueuedMessage queuedMessage) {
        this(queuedMessage.getId(),
             queuedMessage.getQueueName(),
             queuedMessage.getAddedTimestamp().toInstant(),
             queuedMessage.getNextDeliveryTimestamp().toInstant());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NextQueuedMessage that = (NextQueuedMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "NextQueuedMessage{" +
                "id=" + id +
                ", queueName=" + queueName +
                ", addedTimestamp=" + addedTimestamp +
                ", nextDeliveryTimestamp=" + nextDeliveryTimestamp +
                '}';
    }

    @Override
    public int compareTo(NextQueuedMessage o) {
        return this.nextDeliveryTimestamp.compareTo(o.nextDeliveryTimestamp);
    }
}
