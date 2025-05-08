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

package dk.trustworks.essentials.components.foundation.messaging.queue;

import dk.trustworks.essentials.shared.functional.tuple.Pair;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Encapsulates a Message, which is a {@link Pair} of Payload and its {@link MessageMetaData}
 */
public class Message extends Pair<Object, MessageMetaData> {

    /**
     * Create a new {@link Message} and an empty {@link MessageMetaData}
     *
     * @param payload the message payload
     * @return the new {@link Message}
     */
    public static Message of(Object payload) {
        return new Message(payload);
    }

    /**
     * Create a new {@link Message}
     *
     * @param payload  the message payload
     * @param metaData the {@link MessageMetaData} associated with the message
     * @return the new {@link Message}
     */
    public static Message of(Object payload, MessageMetaData metaData) {
        return new Message(payload, metaData);
    }

    /**
     * Create a new {@link Message} and an empty {@link MessageMetaData}
     *
     * @param payload the message payload
     */
    public Message(Object payload) {
        this(payload, new MessageMetaData());
    }

    /**
     * Create a new {@link Message}
     *
     * @param payload  the message payload
     * @param metaData the {@link MessageMetaData} associated with the message
     */
    public Message(Object payload, MessageMetaData metaData) {
        super(requireNonNull(payload, "No payload provided"),
              requireNonNull(metaData, "No metaData provided"));
    }

    /**
     * Get the message payload
     *
     * @return the message payload
     */
    public Object getPayload() {
        return _1;
    }

    /**
     * Get the {@link MessageMetaData} associated with the message
     *
     * @return the {@link MessageMetaData} associated with the message
     */
    public MessageMetaData getMetaData() {
        return _2;
    }

    @Override
    public String toString() {
        return "Message{" +
                "payload-type=" + _1.getClass().getName() +
                ", metaData=" + _2 +
                '}';
    }
}
