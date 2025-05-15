package dk.trustworks.essentials.components.boot.autoconfigure.postgresql;

import dk.trustworks.essentials.components.foundation.fencedlock.api.DBFencedLockApi;
import dk.trustworks.essentials.components.foundation.messaging.queue.api.DurableQueuesApi;
import dk.trustworks.essentials.components.foundation.postgresql.api.PostgresqlQueryStatisticsApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.assertj.core.api.Assertions.assertThat;

public class StarterAutoConfigurationTest {

    @Container
    private static final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("starter-test-db")
            .withUsername("test-user")
            .withPassword("secret-password");

    static {
        postgreSQLContainer.start();
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            DataSourceAutoConfiguration.class,
                            DataSourceTransactionManagerAutoConfiguration.class,
                            EssentialsComponentsConfiguration.class
                    ))
                    .withInitializer(ctx -> TestPropertyValues.of(
                            "spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                            "spring.datasource.username=" + postgreSQLContainer.getUsername(),
                            "spring.datasource.password=" + postgreSQLContainer.getPassword()
                    ).applyTo(ctx.getEnvironment()));

    @Test
    void verify_api_beans() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DBFencedLockApi.class);
            DBFencedLockApi dbFencedLockApi = ctx.getBean(DBFencedLockApi.class);
            assertThat(dbFencedLockApi.getAllLocks("principal")).isNotNull();

            assertThat(ctx).hasSingleBean(DurableQueuesApi.class);
            DurableQueuesApi durableQueuesApi = ctx.getBean(DurableQueuesApi.class);
            assertThat(durableQueuesApi.getQueueNames("principal")).isNotNull();

            assertThat(ctx).hasSingleBean(PostgresqlQueryStatisticsApi.class);
            PostgresqlQueryStatisticsApi postgresqlQueryStatisticsApi = ctx.getBean(PostgresqlQueryStatisticsApi.class);
            assertThat(postgresqlQueryStatisticsApi.getTopTenSlowestQueries("principal")).isNotNull();
        });
    }

    @Test
    void verify_essentials_properties() {
        contextRunner
                .withPropertyValues("essentials.immutable-jackson-module-enabled=true")
                .run(ctx -> {
                    EssentialsComponentsProperties props = ctx.getBean(EssentialsComponentsProperties.class);
                    assertThat(props.isImmutableJacksonModuleEnabled()).isTrue();
                });
    }
}
