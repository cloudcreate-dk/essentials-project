/*
 * Copyright 2021-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.bitemporal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateRepository;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreManagedUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventTypeOrName;
import dk.cloudcreate.essentials.components.foundation.postgresql.SqlExecutionTimeLogger;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.shared.functional.CheckedConsumer;
import dk.cloudcreate.essentials.types.*;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.slf4j.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.bitemporal.ProductPrice.PRESENT;
import static dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory;
import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfigurationUsingJackson;
import static dk.cloudcreate.essentials.shared.collections.Lists.toIndexedStream;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class BiTemporalProductPriceIT {
    private static final Logger log = LoggerFactory.getLogger(BiTemporalProductPriceIT.class);

    public static final AggregateType PRODUCT_PRICE = AggregateType.of("ProductPrice");

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("event-store")
            .withUsername("test-user")
            .withPassword("secret-password");

    private Jdbi                                                                    jdbi;
    private EventStoreManagedUnitOfWorkFactory                                      unitOfWorkFactory;
    private PostgresqlEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;
    private StatefulAggregateRepository<ProductId, ProductPriceEvent, ProductPrice> productPriceRepository;

    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new SqlExecutionTimeLogger());

        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);

        var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                       unitOfWorkFactory,
                                                                                       persistableEventMapper(),
                                                                                       standardSingleTenantConfigurationUsingJackson(createObjectMapper(),
                                                                                                                                     IdentifierColumnType.UUID,
                                                                                                                                     JSONColumnType.JSONB));
        persistenceStrategy.addAggregateEventStreamConfiguration(PRODUCT_PRICE,
                                                                 ProductId.class);

        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                persistenceStrategy);

        productPriceRepository = StatefulAggregateRepository.from(eventStore,
                                                                  PRODUCT_PRICE,
                                                                  reflectionBasedAggregateRootFactory(),
                                                                  ProductId.class,
                                                                  ProductPrice.class);
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();
    }

    // ----------------------------- Test methods --------------------------------

    @Test
    void test_setting_an_initial_price() throws Exception {
        var forProduct = ProductId.random();
        var validFrom  = Instant.now().minusSeconds(100);
        var price      = Money.of("100", CurrencyCode.DKK);
        var productPrice = new ProductPrice(forProduct,
                                            price,
                                            validFrom);

        CheckedConsumer<ProductPrice> productPriceValidator = (ProductPrice productPriceToBeValidated) -> {
            assertThat(productPriceToBeValidated.getPriceAt(validFrom))
                    .isPresent()
                    .hasValue(price);
            assertThat(productPriceToBeValidated.getPriceAt(validFrom.minusNanos(1)))
                    .isEmpty();
            assertThat(productPriceToBeValidated.getPriceAt(validFrom.plusNanos(1)))
                    .isPresent()
                    .hasValue(price);
            assertThat(productPriceToBeValidated.getPriceAt(PRESENT))
                    .isPresent()
                    .hasValue(price);
        };

        // Check the non persisted aggregate answers correctly
        productPriceValidator.accept(productPrice);

        // Persist
        persistProductPrice(productPrice);

        // Load again and Check the persisted aggregate also answers correctly
        var loadedProductPrice = unitOfWorkFactory.withUnitOfWork(() -> productPriceRepository.load(forProduct));
        productPriceValidator.accept(loadedProductPrice);
    }

    @Test
    void set_a_price_prior_to_the_initial_price() throws Exception {
        var forProduct   = ProductId.random();
        var validFrom    = Instant.now();
        var initialPrice = Money.of("100", CurrencyCode.DKK);
        var productPrice = new ProductPrice(forProduct,
                                            initialPrice,
                                            validFrom);

        var adjustedInitialPrice          = Money.of("120", CurrencyCode.DKK);
        var adjustedInitialPriceValidFrom = validFrom.minus(14, ChronoUnit.DAYS);
        productPrice.adjustPrice(adjustedInitialPrice,
                                 adjustedInitialPriceValidFrom);


        CheckedConsumer<ProductPrice> productPriceValidator = (ProductPrice productPriceToBeValidated) -> {
            assertThat(productPriceToBeValidated.getPriceAt(validFrom))
                    .isPresent()
                    .hasValue(initialPrice);
            assertThat(productPriceToBeValidated.getPriceAt(validFrom.plusNanos(1)))
                    .isPresent()
                    .hasValue(initialPrice);
            assertThat(productPriceToBeValidated.getPriceAt(PRESENT))
                    .isPresent()
                    .hasValue(initialPrice);
            // Check adjusted initialPrice is included
            assertThat(productPriceToBeValidated.getPriceAt(validFrom.minusNanos(1)))
                    .isPresent()
                    .hasValue(adjustedInitialPrice);
            assertThat(productPriceToBeValidated.getPriceAt(adjustedInitialPriceValidFrom))
                    .isPresent()
                    .hasValue(adjustedInitialPrice);
            // But there's no price prior to the adjusted initial price
            assertThat(productPriceToBeValidated.getPriceAt(adjustedInitialPriceValidFrom.minusNanos(1)))
                    .isEmpty();
        };

        // Check the non persisted aggregate answers correctly
        productPriceValidator.accept(productPrice);

        // Persist
        persistProductPrice(productPrice);

        // And load again and Check the persisted aggregate also answers correctly
        var loadedProductPrice = unitOfWorkFactory.withUnitOfWork(() -> productPriceRepository.load(forProduct));
        productPriceValidator.accept(loadedProductPrice);

    }


    @Test
    void set_a_future_product_price() throws Exception {
        var forProduct            = ProductId.random();
        var initialPriceValidFrom = Instant.now().minus(14, ChronoUnit.DAYS);
        var initialPrice          = Money.of("100", CurrencyCode.DKK);
        var productPrice = new ProductPrice(forProduct,
                                            initialPrice,
                                            initialPriceValidFrom);

        var futurePrice          = Money.of("120", CurrencyCode.DKK);
        var futurePriceValidFrom = Instant.now().plus(14, ChronoUnit.DAYS);
        productPrice.adjustPrice(futurePrice,
                                 futurePriceValidFrom);

        CheckedConsumer<ProductPrice> productPriceValidator = (ProductPrice productPriceToBeValidated) -> {
            assertThat(productPriceToBeValidated.getPriceAt(initialPriceValidFrom))
                    .isPresent()
                    .hasValue(initialPrice);
            // and there's no initialPrice prior to the initial initialPrice
            assertThat(productPriceToBeValidated.getPriceAt(initialPriceValidFrom.minusNanos(1)))
                    .isEmpty();
            assertThat(productPriceToBeValidated.getPriceAt(initialPriceValidFrom.plusNanos(1)))
                    .isPresent()
                    .hasValue(initialPrice);

            // Right before the futurePriceValidFrom, the initial price is still valid
            assertThat(productPriceToBeValidated.getPriceAt(futurePriceValidFrom.minusNanos(1)))
                    .isPresent()
                    .hasValue(initialPrice);

            // Check future price
            assertThat(productPriceToBeValidated.getPriceAt(futurePriceValidFrom))
                    .isPresent()
                    .hasValue(futurePrice);
            assertThat(productPriceToBeValidated.getPriceAt(futurePriceValidFrom.plusNanos(1)))
                    .isPresent()
                    .hasValue(futurePrice);
            assertThat(productPriceToBeValidated.getPriceAt(PRESENT))
                    .isPresent()
                    .hasValue(futurePrice);
        };

        // Check the non persisted aggregate answers correctly
        productPriceValidator.accept(productPrice);

        // Persist
        persistProductPrice(productPrice);

        // And load again and Check the persisted aggregate also answers correctly
        var loadedProductPrice = unitOfWorkFactory.withUnitOfWork(() -> productPriceRepository.load(forProduct));
        productPriceValidator.accept(loadedProductPrice);
    }

    @Test
    void add_a_price_in_between_two_other_prices() throws Exception {
        var forProduct            = ProductId.random();
        var initialPriceValidFrom = Instant.now().minus(14, ChronoUnit.DAYS);
        var initialPrice          = Money.of("100", CurrencyCode.DKK);
        var productPrice = new ProductPrice(forProduct,
                                            initialPrice,
                                            initialPriceValidFrom);

        var futurePrice          = Money.of("120", CurrencyCode.DKK);
        var futurePriceValidFrom = Instant.now().plus(14, ChronoUnit.DAYS);
        productPrice.adjustPrice(futurePrice,
                                 futurePriceValidFrom);

        var midPrice          = Money.of("110", CurrencyCode.DKK);
        var midPriceValidFrom = Instant.now();
        productPrice.adjustPrice(midPrice,
                                 midPriceValidFrom);


        CheckedConsumer<ProductPrice> productPriceValidator = (ProductPrice productPriceToBeValidated) -> {
            assertThat(productPriceToBeValidated.getPriceAt(initialPriceValidFrom))
                    .isPresent()
                    .hasValue(initialPrice);
            // and there's no initialPrice prior to the initial initialPrice
            assertThat(productPriceToBeValidated.getPriceAt(initialPriceValidFrom.minusNanos(1)))
                    .isEmpty();
            assertThat(productPriceToBeValidated.getPriceAt(initialPriceValidFrom.plusNanos(1)))
                    .isPresent()
                    .hasValue(initialPrice);

            // Right before the midPriceValidFrom, the initial price is still valid
            assertThat(productPriceToBeValidated.getPriceAt(midPriceValidFrom.minusNanos(1)))
                    .isPresent()
                    .hasValue(initialPrice);

            // ---> Check MID PRICE
            assertThat(productPriceToBeValidated.getPriceAt(midPriceValidFrom))
                    .isPresent()
                    .hasValue(midPrice);
            assertThat(productPriceToBeValidated.getPriceAt(futurePriceValidFrom.minusNanos(1)))
                    .isPresent()
                    .hasValue(midPrice);

            // Check future price
            assertThat(productPriceToBeValidated.getPriceAt(futurePriceValidFrom))
                    .isPresent()
                    .hasValue(futurePrice);
            assertThat(productPriceToBeValidated.getPriceAt(futurePriceValidFrom.plusNanos(1)))
                    .isPresent()
                    .hasValue(futurePrice);
            assertThat(productPriceToBeValidated.getPriceAt(PRESENT))
                    .isPresent()
                    .hasValue(futurePrice);
        };

        // Check the non persisted aggregate answers correctly
        productPriceValidator.accept(productPrice);

        // Persist
        persistProductPrice(productPrice);

        // And load again and Check the persisted aggregate also answers correctly
        var loadedProductPrice = unitOfWorkFactory.withUnitOfWork(() -> productPriceRepository.load(forProduct));
        productPriceValidator.accept(loadedProductPrice);
    }


    // ----------------------------- Supporting methods --------------------------------

    private void persistProductPrice(ProductPrice productPrice) {
        logEventsToBePersisted(productPrice);
        unitOfWorkFactory.usingUnitOfWork(() -> productPriceRepository.save(productPrice));
        logEventsPersisted(productPrice.aggregateId());
    }

    private void logEventsToBePersisted(ProductPrice productPrice) {
        log.debug("---------------------------- Events to be persisted -----------------------------");
        toIndexedStream(productPrice.getUncommittedChanges().events)
                .forEach(indexAndEvent -> {
                    log.debug("Event: {}", indexAndEvent._1);
                    log.debug("    {}", indexAndEvent._2.toString());
                    log.debug("--------------------------------------------------------------------------------------------------");
                });
    }

    private void logEventsPersisted(ProductId productId) {
        log.debug("============================ Events persisted =============================");
        unitOfWorkFactory.usingUnitOfWork(() -> {
            eventStore.fetchStream(PRODUCT_PRICE,
                                   productId)
                      .get()
                      .events()
                      .forEach(persistedEvent -> {
                          log.debug("Event: {}", persistedEvent.eventOrder());
                          log.debug("     {}", persistedEvent);
                          log.debug("==================================================================================================");
                      });
        });
    }

    private PersistableEventMapper persistableEventMapper() {
        return (aggregateId, aggregateTypeConfiguration, event, eventOrder) ->
                PersistableEvent.builder()
                                .setEvent(event)
                                .setAggregateType(aggregateTypeConfiguration.aggregateType)
                                .setAggregateId(aggregateId)
                                .setEventTypeOrName(EventTypeOrName.with(event.getClass()))
                                .setEventOrder(eventOrder)
                                .build();
    }

    private ObjectMapper createObjectMapper() {
        var objectMapper = JsonMapper.builder()
                                     .disable(MapperFeature.AUTO_DETECT_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_SETTERS)
                                     .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                                     .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                     .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                     .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                     .enable(MapperFeature.AUTO_DETECT_CREATORS)
                                     .enable(MapperFeature.AUTO_DETECT_FIELDS)
                                     .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                                     .addModule(new Jdk8Module())
                                     .addModule(new JavaTimeModule())
                                     .addModule(new EssentialTypesJacksonModule())
                                     .addModule(new EssentialsImmutableJacksonModule())
                                     .build();

        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return objectMapper;
    }

}
