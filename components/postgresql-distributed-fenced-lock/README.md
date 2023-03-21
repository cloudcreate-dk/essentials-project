# Essentials Components - Postgresql Distributed Fenced Lock

This library provides a `FencedLockManager` implementation using Postgresql to coordinate intra-service distributed locks

Configuration example:

```
var lockManager = PostgresqlFencedLockManager.builder()
                                      .setJdbi(Jdbi.create(jdbcUrl,
                                                           username,
                                                           password))
                                      .setUnitOfWorkFactory(unitOfWorkFactory)
                                      .setLockTimeOut(Duration.ofSeconds(3))
                                      .setLockConfirmationInterval(Duration.ofSeconds(1))
                                      .buildAndStart(); 
```                                                

To use `PostgreSQL Distributed Fenced Lock` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>postgresql-distributed-fenced-lock</artifactId>
    <version>0.9.0</version>
</dependency>
```

See [foundation](../foundation/README.md) for more information about how to use the `FencedLockManager`  
