# Essentials Components - SpringData Mongo Distributed Fenced Lock

Provides a `FencedLockManager` implementation using MongoDB and the SpringData MongoDB library to coordinate intra-service distributed locks

```
    public MongoFencedLockManager(MongoTemplate mongoTemplate,
                                  MongoConverter mongoConverter,
                                  UnitOfWorkFactory<? extends ClientSessionAwareUnitOfWork> unitOfWorkFactory,
                                  Optional<String> lockManagerInstanceId,
                                  Optional<String> fencedLocksCollectionName,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval) {
      ...
    }
```

Usage example:

```
@Bean
public FencedLockManager fencedLockManager(MongoTemplate mongoTemplate,
                                           MongoConverter mongoConverter,
                                           MongoTransactionManager transactionManager,
                                           MongoDatabaseFactory databaseFactory) {
  return MongoFencedLockManager.builder()
                               .setMongoTemplate(mongoTemplate)
                               .setMongoConverter(mongoConverter)
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

See [foundation](../foundation/README.md) for more information about how to use the `FencedLockManager`  