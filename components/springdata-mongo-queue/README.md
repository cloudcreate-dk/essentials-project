# Essentials Components - SpringData Mongo Durable Queues

The `DurableQueues` concept supports intra-service point-to-point messaging using durable Queues that guarantee At-Least-Once delivery of messages.  
The only requirement is that message producers and message consumers can access the same underlying durable Queue storage.  
See [foundation](../foundation/README.md) for more information about how to use the `DurableQueues`

> **NOTE:**  
> **The library is WORK-IN-PROGRESS**

> Please see the **Security** notices below to familiarize yourself with the security risks related to Collection name configurations

# Security
To support customization of storage collection name, the `sharedQueueCollectionName` will be directly used as Collection name, which exposes the component to the risk of malicious input.  
It is the responsibility of the user of this component to sanitize the `sharedQueueCollectionName` to avoid the risk of malicious input and that can compromise the security and integrity of the database

The `MongoDurableQueues` component,  will call the `MongoUtil.checkIsValidCollectionName(String)` method to validate the collection name as a first line of defense.   
The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.   
**However, Essentials components as well as `MongoUtil.checkIsValidCollectionName(String)` does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc.**
> The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.
Users must ensure thorough sanitization and validation of API input parameters,  collection names. Insufficient attention to these practices may leave the application vulnerable to attacks, potentially
endangering the security and integrity of the database. It is highly recommended that the `sharedQueueCollectionName` value is only derived from a controlled and trusted source.

To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the `sharedQueueCollectionName` value.
**Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.**

See [foundation](../foundation/README.md) for more information about how to use `MongoDurableQueues` and `DurableQueues`

# Configuration

To use `MongoDB Durable Queue` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>springdata-mongo-queue</artifactId>
    <version>0.40.16</version>
</dependency>
```
You need to decide on which `TransactionalMode` to run the `MongoDurableQueues` in.

## SingleOperationTransaction

The recommended `TransactionalMode` is `SingleOperationTransaction`   
Running this mode is also useful for Long-running message handling and to ensure a Transaction failure doesn't affect re-queueing the failed message.
With `TransactionalMode` as `SingleOperationTransaction` where queueing and de-queueing are  performed using separate single document
transactions and where acknowledging/retry are also performed as separate transactions.

Depending on the type of errors that can occur this MAY leave a dequeued message
in a state of being marked as "being delivered" forever. Hence `MongoDurableQueues` supports periodically
discovering messages that have been under delivery for a long time (aka. stuck messages or timed-out messages) and will
reset them in order for them to be redelivered.

Example `TransactionalMode#SingleOperationTransaction` Spring configuration:

```
@Bean
public DurableQueues durableQueues(MongoTemplate mongoTemplate) {
    return new MongoDurableQueues(mongoTemplate,
                                  Duration.ofSeconds(10));
}
```

Using TransactionalMode#SingleOperationTransaction (if consuming messages manually without using `DurableQueues.consumeFromQueue(ConsumeFromQueue)`):

```
durableQueues.queueMessage(queueName, message);
var msgUnderDelivery = durableQueues.getNextMessageReadyForDelivery(queueName);
if (msgUnderDelivery.isPresent()) {
   try {
      handleMessage(msgUnderDelivery.get());
      durableQueues.acknowledgeMessageAsHandled(msgUnderDelivery.get().getId());
   } catch (Exception e) {
      durableQueues.retryMessage(msgUnderDelivery.get().getId(), 
                                 e,
                                 Duration.ofMillis(500));
   }
}
```

## FullyTransactional

Not recommended, since in this mode all the queueing, de-queueing methods requires an existing `UnitOfWork`
started prior to being called.
When changing an entity and queueing/de-queueing happens in ONE shared transaction *(NOTE this requires that the entity
storage and the queue storage  to use the same MongoDB database) then the shared database transaction guarantees that
all the data storage operations are committed or rollback as one, with the caveat that exceptions also affect the re-queueing the failed message

Example `TransactionalMode#FullyTransactional` Spring configuration:

```
@Bean
public DurableQueues durableQueues(MongoTemplate mongoTemplate, 
                                   SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory) {
    return new MongoDurableQueues(mongoTemplate,
                                      unitOfWorkFactory);
}
        
@Bean
public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
    TransactionOptions transactionOptions = TransactionOptions.builder()
                                                              .readConcern(ReadConcern.SNAPSHOT)
                                                              .writeConcern(WriteConcern.ACKNOWLEDGED)
                                                              .build();
    return new MongoTransactionManager(databaseFactory, transactionOptions);
}

@Bean
public SpringMongoTransactionAwareUnitOfWorkFactory unitOfWorkFactory(MongoTransactionManager transactionManager,
                                                                      MongoDatabaseFactory databaseFactory) {
    return new SpringMongoTransactionAwareUnitOfWorkFactory(transactionManager, databaseFactory);
}
```


## Typical Spring Beans required for setting up `MongoDurableQueues`:
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
    MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoConverter converter) {
        MongoTemplate mongoTemplate = new MongoTemplate(mongoDbFactory, converter);
        mongoTemplate.setWriteConcern(WriteConcern.ACKNOWLEDGED);
        mongoTemplate.setWriteResultChecking(WriteResultChecking.EXCEPTION);
        return mongoTemplate;
    }
    
    @Bean
    public com.fasterxml.jackson.databind.Module essentialJacksonModule() {
        return new EssentialTypesJacksonModule();
    }
```

