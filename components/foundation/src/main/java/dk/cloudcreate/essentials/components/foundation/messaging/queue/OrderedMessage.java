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

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import static dk.cloudcreate.essentials.shared.FailFast.*;

/**
 * Represents a message that will be delivered in order.<br>
 * This of course requires that message are queued in order and that the consumer is single threaded.<br>
 * All messages sharing the same {@link #key}, will be delivered according to their {@link #order}<br>
 * An example of a message key is the id of the entity the message relates to
 */
public class OrderedMessage extends Message {
    /**
     * All messages sharing the same key, will be delivered according to their {@link #order}<br>
     * An example of a message key is the id of the entity the message relates to
     */
    public final String key;
    /**
     * Represent the order of the message relative to the {@link #key}.<br>
     * All messages sharing the same key, will be delivered according to their {@link #order}
     */
    public final long   order;

    /**
     * @param payload the message payload
     * @param key     the message key. All messages sharing the same key, will be delivered according to their {@link #getOrder()}
     * @param order   the order of the message relative to the {@link #getKey()}.
     */
    public OrderedMessage(Object payload, String key, long order) {
        super(payload);
        this.key = requireNonNull(key, "No message key provided");
        requireTrue(order >= 0, "order must be >= 0");
        this.order = order;
    }

    /**
     * @param payload  the message payload
     * @param key      the message key. All messages sharing the same key, will be delivered according to their {@link #getOrder()}
     * @param order    the order of the message relative to the {@link #getKey()}.
     * @param metaData the {@link MessageMetaData} associated with the message
     */
    public OrderedMessage(Object payload, String key, long order, MessageMetaData metaData) {
        super(payload, metaData);
        this.key = requireNonNull(key, "No message key provided");
        requireTrue(order >= 0, "order must be >= 0");
        this.order = order;
    }

    /**
     * Create a new {@link Message} and an empty {@link MessageMetaData}
     *
     * @param payload the message payload
     * @param key     the message key. All messages sharing the same key, will be delivered according to their {@link #getOrder()}
     * @param order   the order of the message relative to the {@link #getKey()}.
     * @return the new {@link Message}
     */
    public static OrderedMessage of(Object payload, String key, long order) {
        return new OrderedMessage(payload, key, order);
    }

    /**
     * Create a new {@link Message}
     *
     * @param payload  the message payload
     * @param key      the message key. All messages sharing the same key, will be delivered according to their {@link #getOrder()}
     * @param order    the order of the message relative to the {@link #getKey()}.
     * @param metaData the {@link MessageMetaData} associated with the message
     * @return the new {@link Message}
     */
    public static OrderedMessage of(Object payload, String key, long order, MessageMetaData metaData) {
        return new OrderedMessage(payload, key, order, metaData);
    }

    /**
     * All messages sharing the same key, will be delivered according to their {@link #getOrder()}<br>
     * An example of a message key is the id of the entity the message relates to
     *
     * @return The message key
     */
    public String getKey() {
        return key;
    }

    /**
     * Represent the order of a message relative to the {@link #getKey()}.<br>
     * All messages sharing the same key, will be delivered according to their {@link #getOrder()}
     *
     * @return the order of a message relative to the {@link #getKey()}
     */
    public long getOrder() {
        return order;
    }

    @Override
    public String toString() {
        return "OrderedMessage{" +
                "payload-type=" + _1.getClass().getName() +
                ", key='" + key + '\'' +
                ", order=" + order +
                ", metaData=" + _2 +
                '}';
    }
}
