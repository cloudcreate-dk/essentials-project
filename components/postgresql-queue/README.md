# Essentials Components - PostgreSQL Durable Queues

The `DurableQueues` concept supports intra-service point-to-point messaging using durable Queues that guarantee At-Least-Once delivery of messages.  
The only requirement is that message producers and message consumers can access
the same underlying durable Queue storage.

To use `PostgreSQL Durable Queue` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>postgresql-queue</artifactId>
    <version>0.9.8</version>
</dependency>
```

Example setting up `PostgresqlDurableQueues` (note: you can also use it together with either
the `EventStoreManagedUnitOfWorkFactory` or `SpringManagedUnitOfWorkFactory`):

```
var unitOfWorkFactory = new JdbiUnitOfWorkFactory(jdbi);
var durableQueues = new PostgresqlDurableQueues(unitOfWorkFactory);
durableQueues.start();
```

See [foundation](../foundation/README.md) for more information about how to use `PostgresqlDurableQueues` and `DurableQueues`
