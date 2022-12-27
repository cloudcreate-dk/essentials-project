package dk.cloudcreate.essentials.components.queue.postgresql;

import dk.cloudcreate.essentials.components.foundation.test.reactive.command.AbstractDurableLocalCommandBusIT;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
public class DurableLocalCommandBusIT extends AbstractDurableLocalCommandBusIT<PostgresqlDurableQueues, GenericHandleAwareUnitOfWorkFactory.GenericHandleAwareUnitOfWork, JdbiUnitOfWorkFactory> {
    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("queue-db")
            .withUsername("test-user")
            .withPassword("secret-password");

    @Override
    protected PostgresqlDurableQueues createDurableQueues(JdbiUnitOfWorkFactory unitOfWorkFactory) {
        return new PostgresqlDurableQueues(unitOfWorkFactory);
    }

    @Override
    protected JdbiUnitOfWorkFactory createUnitOfWorkFactory() {
        return new JdbiUnitOfWorkFactory(Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                                                     postgreSQLContainer.getUsername(),
                                                     postgreSQLContainer.getPassword()));
    }
}
