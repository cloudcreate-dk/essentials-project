package dk.cloudcreate.essentials.components.queue.springdata.mongodb;

import dk.cloudcreate.essentials.components.foundation.test.reactive.command.AbstractDurableLocalCommandBusIT;
import dk.cloudcreate.essentials.components.foundation.transaction.spring.mongo.SpringMongoTransactionAwareUnitOfWorkFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@DataMongoTest(excludeAutoConfiguration = EmbeddedMongoAutoConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DurableLocalCommandBusIT extends AbstractDurableLocalCommandBusIT<MongoDurableQueues, SpringMongoTransactionAwareUnitOfWorkFactory.SpringMongoTransactionAwareUnitOfWork, SpringMongoTransactionAwareUnitOfWorkFactory> {
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoDatabaseFactory databaseFactory;

    @Autowired
    private MongoTransactionManager transactionManager;

    @Override
    protected MongoDurableQueues createDurableQueues(SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory) {
        return new MongoDurableQueues(mongoTemplate, unitOfWorkFactory);
    }

    @Override
    protected SpringMongoTransactionAwareUnitOfWorkFactory createUnitOfWorkFactory() {
        return new SpringMongoTransactionAwareUnitOfWorkFactory(transactionManager, databaseFactory);
    }
}
