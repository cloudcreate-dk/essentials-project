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

package dk.cloudcreate.essentials.components.distributed.fencedlock.springdata.mongo;

import dk.cloudcreate.essentials.components.foundation.test.fencedlock.DBFencedLockManagerIT;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.test.context.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.Optional;

@Testcontainers
@DataMongoTest(excludeAutoConfiguration = EmbeddedMongoAutoConfiguration.class)
class MongoFencedLockManagerIT extends DBFencedLockManagerIT<MongoFencedLockManager> {
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoConverter mongoConverter;

    @Autowired
    private MongoTransactionManager transactionManager;

    @Autowired
    private MongoDatabaseFactory databaseFactory;

    @Override
    protected MongoFencedLockManager createLockManagerNode2() {
        return new MongoFencedLockManager(mongoTemplate,
                                          mongoConverter,
                                          new SpringMongoTransactionAwareUnitOfWorkFactory(transactionManager,
                                                                                           databaseFactory),
                                          Optional.of("node2"),
                                          Optional.empty(),
                                          Duration.ofSeconds(3),
                                          Duration.ofSeconds(1));
    }

    @Override
    protected MongoFencedLockManager createLockManagerNode1() {
        return new MongoFencedLockManager(mongoTemplate,
                                          mongoConverter,
                                          new SpringMongoTransactionAwareUnitOfWorkFactory(transactionManager,
                                                                                           databaseFactory),
                                          Optional.of("node1"),
                                          Optional.empty(),
                                          Duration.ofSeconds(3),
                                          Duration.ofSeconds(1));
    }
}