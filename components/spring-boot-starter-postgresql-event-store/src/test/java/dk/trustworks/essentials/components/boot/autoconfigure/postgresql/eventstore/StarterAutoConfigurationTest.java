package dk.trustworks.essentials.components.boot.autoconfigure.postgresql.eventstore;

import dk.trustworks.essentials.components.boot.autoconfigure.postgresql.EssentialsComponentsConfiguration;
import dk.trustworks.essentials.components.boot.autoconfigure.postgresql.EssentialsComponentsProperties;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.api.EventStoreApi;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.api.PostgresqlEventStoreStatisticsApi;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
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
                            EssentialsComponentsConfiguration.class,
                            EventStoreConfiguration.class
                    ))

                    .withInitializer(ctx -> TestPropertyValues.of(
                            "spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                            "spring.datasource.username=" + postgreSQLContainer.getUsername(),
                            "spring.datasource.password=" + postgreSQLContainer.getPassword()
                    ).applyTo(ctx.getEnvironment()));

    @Test
    void verify_api_beans() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(EventStoreApi.class);
            EventStoreApi eventStoreApi = ctx.getBean(EventStoreApi.class);
            assertThat(eventStoreApi.findAllSubscriptions("principal")).isNotNull();

            assertThat(ctx).hasSingleBean(PostgresqlEventStoreStatisticsApi.class);
            PostgresqlEventStoreStatisticsApi postgresqlEventStoreStatisticsApi = ctx.getBean(PostgresqlEventStoreStatisticsApi.class);
            assertThat(postgresqlEventStoreStatisticsApi.fetchTableActivityStatistics("principal")).isNotNull();
        });
    }

    @Test
    void verify_essentials_properties() {
        contextRunner
                .withPropertyValues("essentials.event-store.use-event-stream-gap-handler=true")
                .run(ctx -> {
                    EssentialsEventStoreProperties props = ctx.getBean(EssentialsEventStoreProperties.class);
                    assertThat(props.isUseEventStreamGapHandler()).isTrue();
                });
    }
}
