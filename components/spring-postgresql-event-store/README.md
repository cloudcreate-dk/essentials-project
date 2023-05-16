# Essentials Components - Spring PostgreSQL Event Store

This library provides the `SpringTransactionAwareEventStoreUnitOfWorkFactory` (as opposed to the
standard `EventStoreManagedUnitOfWorkFactory`)
which allows the `EventStore` to participate in Spring managed Transactions.

```
@SpringBootApplication
class Application {
    @Bean
    public com.fasterxml.jackson.databind.Module essentialJacksonModule() {
        return new EssentialTypesJacksonModule();
    }

    @Bean
    public Jdbi jdbi(DataSource dataSource) {
        Jdbi jdbi = Jdbi.create(new TransactionAwareDataSourceProxy(dataSource));
        return jdbi;
    }
    
    @Bean
    public EventStoreUnitOfWorkFactory unitOfWorkFactory(Jdbi jdbi, PlatformTransactionManager transactionManager) {
        return new SpringTransactionAwareEventStoreUnitOfWorkFactory(jdbi, transactionManager);
    }
}
```

With the `SpringTransactionAwareEventStoreUnitOfWorkFactory` you can either use the `UnitOfWorkFactory` to start and commit Spring transactions, or you can use
the `TransactionTemplate` class or `@Transactional` annotation to start and commit transactions.

No matter how a transaction is started, you can always acquire the active `UnitOfWork` using

```
unitOfWorkFactory.getCurrentUnitOfWork()
```

To use `Spring Postgresql Event Store` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>spring-postgresql-event-store</artifactId>
    <version>0.20.1</version>
</dependency>
```

See [postgresql-event-store](../postgresql-event-store/README.md) for more information about using the `PostgresqlEventStore`