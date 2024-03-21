# Essentials Java Components

Essentials Components is a set of Java version 17 (and later) components that are based on
the [Essentials](../README.md) library while providing more complex features
or Components such as `EventStore`, `EventSourced Aggregates`, `FencedLocks`, `DurableQueues`, `DurableLocalCommandbus`, `Inbox` and `Outbox`.

> Note: [For Java version 11 to 16 support, please use version 0.9.*](https://github.com/cloudcreate-dk/essentials-project/tree/java11)

> **NOTE:**  
> **The libraries are WORK-IN-PROGRESS**

# Security

Several of the components, as well as their subcomponents and/or supporting classes, allows the user of the components to provide customized:
- table names
- column names
- collection names 
- etc.

By using naming conventions for Postgresql table/column/index names and MongoDB Collection names, Essentials attempts to provide an initial layer of defense intended to reduce the risk of malicious input.    
**However, Essentials does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL and Mongo Queries/Updates against injection threats.**
> The responsibility for implementing protective measures against malicious API input and configuration values lies exclusively with the users/developers using the Essentials components and its supporting classes.
> Users must ensure thorough sanitization and validation of API input parameters,  SQL table/column/index names as well as MongoDB collection names.

**Insufficient attention to these practices may leave the application vulnerable to attacks, endangering the security and integrity of the database.**

> Please see the Java documentation and Readme's for the individual components for more information.
> Such as:
> - [foundation-types](foundation-types/README.md)
> - [postgresql-distributed-fenced-lock](postgresql-distributed-fenced-lock/README.md)
> - [springdata-mongo-distributed-fenced-lock](springdata-mongo-distributed-fenced-lock/README.md)
> - [postgresql-queue](postgresql-queue/README.md)
> - [springdata-mongo-queue](springdata-mongo-queue/README.md)
> - [postgresql-event-store](postgresql-event-store/README.md)
> - [eventsourced-aggregates](eventsourced-aggregates/README.md)
> - [spring-boot-starter-postgresql](spring-boot-starter-postgresql/README.md)
> - [spring-boot-starter-postgresql-event-store](spring-boot-starter-postgresql-event-store/README.md)
> - [spring-boot-starter-mongodb](spring-boot-starter-mongodb/README.md)

# Components overview
![Essentials Components modules](../images/essentials-components-modules.png)

# Foundation-Types
This library focuses purely on providing common types:

- **Identifiers**
    - `CorrelationId`
    - `EventId`
    - `MessageId`
    - `SubscriberId`
    - `Tenant` and `TenantId`
- **EventStore** types
    - `AggregateType`
    - `EventName`
    - `EventOrder`
    - `EventRevision`
    - `EventType`
    - `EventTypeOrName`
    - `GlobalEventOrder`
    - `Revision`

See [foundation-types](foundation-types/README.md)

# Foundation
This library contains the smallest set of supporting building blocks needed for other Essentials Components libraries,
such as:

- **Common Interfaces**
    - `Lifecycle`
- **DurableLocalCommandBus** (`CommandBus` variant that uses `DurableQueues` to ensure Commands sent using `sendAndDontWait` aren't lost in case of failure)
- **FencedLock** (For Intra-Service Distributed Locks)
- **PostgreSQL**
    - `ListenNotify`
    - `MultiTableChangeListener`
- **Transactions**
    - `UnitOfWork`
    - `UnitOfWorkFactory`
        - `JdbiUnitOfWorkFactory`
        - Spring
            - `SpringTransactionAwareJdbiUnitOfWorkFactory`
            - `SpringMongoTransactionAwareUnitOfWorkFactory`
- **Queues**
    - `DurableQueues` (For Intra-Service Durable point-to-point messaging)
- **Enterprise Integration Patterns**
    - `Inbox` (Store and Forward supported by a Durable Queue)
    - `Outbox` (Store and Forward supported by a Durable Queue)

See [foundation](foundation/README.md)

# Essentials Postgresql: Spring Boot starter
This library Spring Boot auto-configuration for all Postgresql focused Essentials components.
All `@Beans` auto-configured by this library use `@ConditionalOnMissingBean` to allow for easy overriding.

See [spring-boot-starter-postgresql](spring-boot-starter-postgresql/README.md)

# Essentials Postgresql Event Store: Spring Boot starter

Auto configures everything from [spring-boot-starter-postgresql](spring-boot-starter-postgresql/README.md)
as well as the `EventStore` from [postgresql-event-store](postgresql-event-store/README.md) and its supporting components.

See [spring-boot-starter-postgresql-event-store](spring-boot-starter-postgresql-event-store/README.md)

# Essentials MongoDB: Spring Boot starter
This library Spring Boot auto-configuration for all MongoDB focused Essentials components.
All `@Beans` auto-configured by this library use `@ConditionalOnMissingBean` to allow for easy overriding.

See [spring-boot-starter-mongodb](spring-boot-starter-mongodb/README.md)

# Event Sourced Aggregates

This library focuses on providing different flavours of Event Source Aggregates that are built to work with
the `EventStore` concept.  
The `EventStore` is very flexible and doesn't specify any specific design requirements for an Aggregate or its Events,
except that that have to be associated with an `AggregateType` (see the
`AggregateType` sub section or the `EventStore` section for more information).

See [eventsourced-aggregates](eventsourced-aggregates/README.md)

# PostgreSQL Event Store

This library contains a fully featured Event Store that supports persisting Aggregate event streams
into Postgresql.

The primary concept of the EventStore are **Event Streams**   
Definition: An Event Stream is a collection of related Events *(e.g. Order events that are related to Order aggregate
instances)*

It supports the [eventsourced-aggregates](eventsourced-aggregates/README.md) module as well as
advanced concepts such an `EventStoreSubscriptionManager` which supports durable subscriptions, where
the `EventStoreSubscriptionManager` keeps track of the individual subscribers `ResumePoint`'s (similar to
how Kafka keeps track of Consumers Topic offsets).

This library also provides an `EventProcessor` concept, which is an Event Modeling style Event Sourced Event Processor Event and Command message Handler.

See [postgresql-event-store](postgresql-event-store/README.md)

# Spring PostgreSQL Event Store

This library provides the `SpringTransactionAwareEventStoreUnitOfWorkFactory` (as opposed to the
standard `EventStoreManagedUnitOfWorkFactory`)
which allows the `EventStore` to participate in Spring managed Transactions.

See [spring-postgresql-event-store](spring-postgresql-event-store/README.md)

# Distributed Fenced Lock

This library provides a Distributed Locking Manager based of the Fenced Locking concept
described [here](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)  
and comes in two different flavours: `MongoFencedLockManager` and `PostgresqlFencedLockManager`  

The `FencedLockManager` is responsible for obtaining distributed `FencedLock`'s, which are named exclusive locks.  
Only one `FencedLockManager` instance can acquire a `FencedLock` at a time.
The implementation has been on supporting **intra-service** (i.e. across different deployed instances of the **same** service) Lock support through database based implementations (`MongoFencedLockManager` and `PostgresqlFencedLockManager`).  
In a service oriented architecture it's common for all deployed instances of a given service (e.g. a Sales service) to share the same underlying
database(s). As long as the different deployed (Sales) services instances can share the same underlying database, then you use the `FencedLockManager` concept for handling distributed locks across all deployed (Sales service)
instances in the cluster.  
If you need cross-service lock support, e.g. across instances of different services (such as across Sales, Billing and Shipping services), then you need to use a dedicated distributed locking service such as Zookeeper.

See [foundation](foundation/README.md) for more information about how to use the `FencedLockManager`

## PostgresqlFencedLockManager

Provides a `FencedLockManager` implementation using Postgresql to coordinate intra-service distributed locks 

See [foundation](foundation/README.md) for more information about how to use the `FencedLockManager`  
See [postgresql-distributed-fenced-lock](postgresql-distributed-fenced-lock/README.md) for more information about how to use the `PostgresqlFencedLockManager`

## MongoFencedLockManager

Provides a `FencedLockManager` implementation using MongoDB and the SpringData MongoDB library to coordinate intra-service distributed locks

See [foundation](foundation/README.md) for more information about how to use the `FencedLockManager`  
See [springdata-mongo-distributed-fenced-lock](springdata-mongo-distributed-fenced-lock/README.md) for more information about how to use the `MongoFencedLockManager`


## Durable Queue

The `DurableQueues` concept supports intra-service point-to-point messaging using durable Queues that guarantee At-Least-Once delivery of messages. The only requirement is that message producers and message consumers can access
the same underlying durable Queue storage.

This library focuses on providing a Durable Queue supporting message redelivery and Dead Letter Message functionality
and comes in two flavours `PostgresqlDurableQueues` and `MongoDurableQueues` which both implement the `DurableQueues` interface.

In a service oriented architecture it's common for all deployed instances of a given service (e.g. a Sales service) to share the same underlying
database(s). As long as the different deployed (Sales) services instances can share the same underlying database, then you use the `DurableQueues` concept for point-to-point messaging across all deployed (Sales service)
instances in the cluster.  
If you need cross-service point-to-point messaging support, e.g. across instances of different services (such as across Sales, Billing and Shipping services), then you need to use a dedicated distributed Queueing service such as RabbitMQ.

Each Queue is uniquely identified by its `QueueName`.
Durable Queue concept that supports **queuing** a message on to a Queue. Each message is associated with a
unique `QueueEntryId`.

See [foundation](foundation/README.md) for more information about how to use the `DurableQueues`

## PostgresqlDurableQueues

See [foundation](foundation/README.md) for more information about how to use the `DurableQueues`
See [postgresql-queue](postgresql-queue/README.md) for more information about how to use `PostgresqlDurableQueues`

## MongoDurableQueues

See [foundation](foundation/README.md) for more information about how to use the `DurableQueues`
See [springdata-mongo-queue](springdata-mongo-queue/README.md) for more information about how to use `MongoDurableQueues`

