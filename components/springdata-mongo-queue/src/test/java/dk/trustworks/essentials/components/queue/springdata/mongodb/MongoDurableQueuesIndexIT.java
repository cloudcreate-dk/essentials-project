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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.List;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MongoDurableQueuesIndexIT {
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void verify_indexes_can_be_replaced_with_new_definitions() {
        // Given an old outdated index
        var indexOperations = mongoTemplate.indexOps(MongoDurableQueues.DEFAULT_DURABLE_QUEUES_COLLECTION_NAME);
        var oldIndexName    = "next_msg";
        var oldIndex = new Index()
                .named(oldIndexName)
                .on("queueName", Sort.Direction.ASC)
                .on("nextDeliveryTimestamp", Sort.Direction.ASC);
        indexOperations.ensureIndex(oldIndex);

        var indexes    = mongoTemplate.getCollection(MongoDurableQueues.DEFAULT_DURABLE_QUEUES_COLLECTION_NAME).listIndexes();
        var indexNames = StreamSupport.stream(indexes.spliterator(), false).map(document -> (String) document.get("name")).collect(Collectors.toList());
        assertThat(indexNames).containsExactly("_id_", oldIndexName);


        // Create DurableQueues to ensure indexes
        var durableQueues = new MongoDurableQueues(mongoTemplate,
                                                   Duration.ofSeconds(10));

        indexes = mongoTemplate.getCollection(MongoDurableQueues.DEFAULT_DURABLE_QUEUES_COLLECTION_NAME).listIndexes();
        indexNames = StreamSupport.stream(indexes.spliterator(), false)
                                  .map(document -> (String) document.get("name"))
                                  .collect(Collectors.toList());

        var allIndexes = List.of("_id_", oldIndexName, "ordered_msg", "stuck_msgs", "find_msg", "resurrect_msg");
        assertThat(indexNames).containsAll(allIndexes);
        assertThat(allIndexes).containsAll(indexNames);

        var updatedIndex = indexOperations.getIndexInfo()
                                          .stream()
                                          .filter(index -> index.getName().equals(oldIndexName))
                                          .findFirst()
                                          .get();
        var newIndexKeys = updatedIndex.getIndexFields()
                                       .stream()
                                       .map(IndexField::getKey)
                                       .collect(Collectors.toList());
        var oldIndexKeys       = oldIndex.getIndexKeys().keySet();
        assertThat(oldIndexKeys).hasSize(2);
        assertThat(newIndexKeys).hasSize(6);
    }
}
