# Essentials Components - SpringData Mongo Distributed Fenced Lock

Provides a `FencedLockManager` implementation using MongoDB and the SpringData MongoDB library to coordinate intra-service distributed locks  
See [foundation](../foundation/README.md) for more information about how to use the `FencedLockManager`

> **NOTE:**  
> **The library is WORK-IN-PROGRESS**

> Please see the **Security** notices below to familiarize yourself with the security risks related to Collection name configurations

# Security
To support customization of storage collection name, the `fencedLocksCollectionName` will be directly used as Collection name, which exposes the component to the risk of malicious input.  
It is the responsibility of the user of this component to sanitize the `fencedLocksCollectionName` to ensure the security of the resulting MongoDB configuration and associated Queries/Updates/etc. 

The `MongoFencedLockStorage` component, used by `MongoFencedLockManager`, will call the `MongoUtil.checkIsValidCollectionName(String)` method to validate the collection name as a first line of defense.   
The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.   
**However, Essentials components as well as `MongoUtil.checkIsValidCollectionName(String)` does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc.**
> The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes. 
Users must ensure thorough sanitization and validation of API input parameters,  collection names. Insufficient attention to these practices may leave the application vulnerable to attacks, potentially 
endangering the security and integrity of the database.  It is highly recommended that the `fencedLocksCollectionName` value is only derived from a controlled and trusted source. 

To mitigate the risk of malicious input attacks, external or untrusted inputs should never directly provide the `fencedLocksCollectionName` value. 
**Failure to adequately sanitize and validate this value could expose the application to malicious input attacks, compromising the security and integrity of the database.**

# Configuration

To use `Spring Data MongoDB Distributed Fenced Lock` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>springdata-mongo-distributed-fenced-lock</artifactId>
    <version>0.20.13</version>
</dependency>
```

Configuration example:

```
@Bean
public FencedLockManager fencedLockManager(MongoTemplate mongoTemplate,
                                           MongoTransactionManager transactionManager,
                                           MongoDatabaseFactory databaseFactory) {
  return MongoFencedLockManager.builder()
                               .setMongoTemplate(mongoTemplate)
                               .setUnitOfWorkFactory(new SpringMongoTransactionAwareUnitOfWorkFactory(transactionManager,
                                                                                                      databaseFactory))
                               .setLockTimeOut(Duration.ofSeconds(3))
                               .setLockConfirmationInterval(Duration.ofSeconds(1)))
                               .buildAndStart();
}

@Bean
public SingleValueTypeRandomIdGenerator registerIdGenerator() {
    return new SingleValueTypeRandomIdGenerator();
}

@Bean
public MongoCustomConversions mongoCustomConversions() {
    return new MongoCustomConversions(List.of(
            new SingleValueTypeConverter(LockName.class)));
}

@Bean
public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
    return new MongoTransactionManager(databaseFactory);
}
```
  