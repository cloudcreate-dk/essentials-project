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
package dk.cloudcreate.essentials.components.queue.postgresql;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.shared.functional.QuadFunction;
import org.apache.commons.lang3.function.TriFunction;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.*;
import java.time.OffsetDateTime;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * JDBI Row mapper for {@link QueuedMessage} objects.
 * Extracted to be reusable between single message and batch operations.
 */
public class QueuedMessageRowMapper implements RowMapper<QueuedMessage> {
    private final TriFunction<QueueName, QueueEntryId, String, MessageMetaData> metadataDeserializer;
    private final QuadFunction<QueueName, QueueEntryId, String, String, Object> payloadDeserializer;

    /**
     * Create a new mapper with the provided deserializers
     *
     * @param payloadDeserializer  function to deserialize message payloads
     * @param metadataDeserializer function to deserialize message metadata
     */
    public QueuedMessageRowMapper(QuadFunction<QueueName, QueueEntryId, String, String, Object> payloadDeserializer,
                                  TriFunction<QueueName, QueueEntryId, String, MessageMetaData> metadataDeserializer) {
        this.payloadDeserializer = payloadDeserializer;
        this.metadataDeserializer = metadataDeserializer;
    }

    @Override
    public QueuedMessage map(ResultSet rs, StatementContext ctx) throws SQLException {
        var queueName      = QueueName.of(rs.getString("queue_name"));
        var queueEntryId   = QueueEntryId.of(rs.getString("id"));
        var messagePayload = payloadDeserializer.apply(queueName, queueEntryId, rs.getString("message_payload"), rs.getString("message_payload_type"));

        MessageMetaData messageMetaData     = null;
        var             metaDataColumnValue = rs.getString("meta_data");
        if (metaDataColumnValue != null) {
            messageMetaData = metadataDeserializer.apply(queueName, queueEntryId, metaDataColumnValue);
        } else {
            messageMetaData = new MessageMetaData();
        }

        var     deliveryMode = QueuedMessage.DeliveryMode.valueOf(rs.getString("delivery_mode"));
        Message message      = null;
        switch (deliveryMode) {
            case NORMAL:
                message = new Message(messagePayload, messageMetaData);
                break;
            case IN_ORDER:
                message = new OrderedMessage(messagePayload,
                                             rs.getString("key"),
                                             rs.getLong("key_order"),
                                             messageMetaData);
                break;
            default:
                throw new IllegalStateException(msg("Unsupported deliveryMode '{}'", deliveryMode));
        }

        return new DefaultQueuedMessage(queueEntryId,
                                        queueName,
                                        message,
                                        rs.getObject("added_ts", OffsetDateTime.class),
                                        rs.getObject("next_delivery_ts", OffsetDateTime.class),
                                        rs.getObject("delivery_ts", OffsetDateTime.class),
                                        rs.getString("last_delivery_error"),
                                        rs.getInt("total_attempts"),
                                        rs.getInt("redelivery_attempts"),
                                        rs.getBoolean("is_dead_letter_message"),
                                        rs.getBoolean("is_being_delivered"));
    }
}