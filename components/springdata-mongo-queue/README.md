# Essentials Components - SpringData Mongo Durable Queues

The `DurableQueues` concept supports intra-service point-to-point messaging using durable Queues that guarantee At-Least-Once delivery of messages.  
The only requirement is that message producers and message consumers can access
the same underlying durable Queue storage.

To use `MongoDB Durable Queue` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>springdata-mongo-queue</artifactId>
    <version>0.8.4</version>
</dependency>
```

Example setting up `MongoDurableQueues`:

```
    @Bean
    public SingleValueTypeRandomIdGenerator registerIdGenerator() {
        return new SingleValueTypeRandomIdGenerator();
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new SingleValueTypeConverter(QueueEntryId.class,
                                             QueueName.class)));
    }

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }


    @Bean
    MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoConverter converter) {
        MongoTemplate mongoTemplate = new MongoTemplate(mongoDbFactory, converter);
        mongoTemplate.setWriteConcern(WriteConcern.ACKNOWLEDGED);
        mongoTemplate.setWriteResultChecking(WriteResultChecking.EXCEPTION);
        return mongoTemplate;
    }
```

See [foundation](../foundation/README.md) for more information about how to use `MongoDurableQueues` and `DurableQueues`
