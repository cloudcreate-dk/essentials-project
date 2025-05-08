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

package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData;
import dk.trustworks.essentials.components.foundation.json.*;

/**
 * JSON serializer and deserializer
 */
public interface JSONEventSerializer extends JSONSerializer {
    /**
     * Serialize a java object to {@link EventJSON}
     *
     * @param objectToSerialize the java object that will be serialized to JSON
     * @return the corresponding {@link EventJSON} object
     * @throws JSONSerializationException in case the <code>objectToSerialize</code> couldn't be serialized to JSON
     */
    EventJSON serializeEvent(Object objectToSerialize);

    /**
     * Serialize the {@link EventMetaData} object to {@link EventJSON}
     *
     * @param metaData the {@link EventMetaData}  object that will be serialized to JSON
     * @return the corresponding {@link EventMetaDataJSON} object
     * @throws JSONSerializationException in case the <code>objectToSerialize</code> couldn't be serialized to JSON
     */
    EventMetaDataJSON serializeMetaData(EventMetaData metaData);
}
