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

package dk.trustworks.essentials.components.queue.springdata.mongodb;

import dk.trustworks.essentials.components.foundation.json.JSONSerializer;
import dk.trustworks.essentials.components.foundation.mongo.InvalidCollectionNameException;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MongoDurableQueuesTest {
    @Test
    void initializeWithInvalidOverriddenCollectionName() {
        assertThatThrownBy(() ->
                                   new MongoDurableQueues(
                                           mock(MongoTemplate.class),
                                           mock(Duration.class),
                                           mock(JSONSerializer.class),
                                           "system.collection",
                                           null
                                   ))
                .isInstanceOf(InvalidCollectionNameException.class);

        assertThatThrownBy(() ->
                                   new MongoDurableQueues(
                                           mock(MongoTemplate.class),
                                           mock(Duration.class),
                                           mock(JSONSerializer.class),
                                           "my$_collection",
                                           null
                                   ))
                .isInstanceOf(InvalidCollectionNameException.class);
        assertThatThrownBy(() ->
                                   new MongoDurableQueues(
                                           mock(MongoTemplate.class),
                                           mock(Duration.class),
                                           mock(JSONSerializer.class),
                                           "collection\0name",
                                           null
                                   ))
                .isInstanceOf(InvalidCollectionNameException.class);
        assertThatThrownBy(() ->
                                   new MongoDurableQueues(
                                           mock(MongoTemplate.class),
                                           mock(Duration.class),
                                           mock(JSONSerializer.class),
                                           "Invalid Name With Spaces",
                                           null
                                   ))
                .isInstanceOf(InvalidCollectionNameException.class);
    }
}