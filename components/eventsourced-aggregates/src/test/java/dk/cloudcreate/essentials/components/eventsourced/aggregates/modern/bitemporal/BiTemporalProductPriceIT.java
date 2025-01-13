/*
 * Copyright 2021-2025 the original author or authors.
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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JacksonJSONEventSerializer;
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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.bitemporal.ProductPrice.PRESENT;
import static dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static dk.cloudcreate.essentials.shared.collections.Lists.toIndexedStream;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class BiTemporalProductPriceIT {
    private static final Logger log = LoggerFactory.getLogger(BiTemporalProductPriceIT.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

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
                                                                                       SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfiguration(new JacksonJSONEventSerializer(createObjectMapper()),
                                                                                                                                                                                      IdentifierColumnType.UUID,
                                                                                                                                                                                      JSONColumnType.JSONB));

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

    /**
     * Will produce a price timeline similar to: <code>[2023-02-28 ----- 100 DKK ----- ∞[</code><br>
     * and will persist 1 event:
     * <pre>{@code
     * -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 0
     *     InitialPriceSet{price=100 DKK, forProduct=fe1835c0-3332-4ed7-b8db-eff2f8565f54, priceId=bc529063-2cd4-4bcd-95d4-22d297f38f70, priceValidityPeriod=TimeWindow{fromInclusive=2023-02-28T20:24:55.498397Z, toExclusive=null}}
     * -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * }</pre>
     * @throws Exception
     */
    @Test
    void test_setting_an_initial_price() throws Exception {
        var validFrom  = Instant.now().minus(1, ChronoUnit.DAYS);
        var price      = Money.of("100", CurrencyCode.DKK);

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

        // Create a price that was valid since yesterday
        var forProduct = ProductId.random();
        var productPrice = new ProductPrice(forProduct,
                                            price,
                                            validFrom);


        // Check the non persisted aggregate answers correctly
        productPriceValidator.accept(productPrice);

        // Persist
        persistProductPrice(productPrice);

        // Load again and Check the persisted aggregate also answers correctly
        var loadedProductPrice = unitOfWorkFactory.withUnitOfWork(() -> productPriceRepository.load(forProduct));
        productPriceValidator.accept(loadedProductPrice);
    }

    /**
     * Will produce a price timeline similar to: <code>[2023-02-15 ----- 120 DKK ----- 2023-03-01[ → [2023-03-01 ----- 100 DKK ----- ∞[</code><br>
     * and will persist 2 events:
     * <pre>{@code
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 0
     *     InitialPriceSet{price=100 DKK, forProduct=a6726e53-1383-4031-b7b7-59d61d451387, priceId=947dfa76-3d31-4f80-a6f9-3feaf5cb0a08, priceValidityPeriod=TimeWindow{fromInclusive=2023-03-01T20:24:56.875559Z, toExclusive=null}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 1
     *     PriceAdjusted{price=120 DKK, forProduct=a6726e53-1383-4031-b7b7-59d61d451387, priceId=a827c4b5-94b5-44d9-98da-fd7266412887, priceValidityPeriod=TimeWindow{fromInclusive=2023-02-15T20:24:56.875559Z, toExclusive=2023-03-01T20:24:56.875559Z}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * }</pre>
     * @throws Exception
     */
    @Test
    void set_a_price_prior_to_the_initial_price() throws Exception {
        var forProduct   = ProductId.random();

        // Setup initial price that is valid from now
        var validFrom    = Instant.now();
        var initialPrice = Money.of("100", CurrencyCode.DKK);
        var productPrice = new ProductPrice(forProduct,
                                            initialPrice,
                                            validFrom);

        // Adjust the price for this product and set it with start date of 14 days ago
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


    /**
     * Will produce a price timeline similar to: <code>[2023-02-15 ----- 100 DKK ----- 2023-03-15[ → [2023-03-15 ----- 120 DKK ----- ∞[</code><br>
     * and will persist 3 events:
     * <pre>{@code
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 0
     *     InitialPriceSet{price=100 DKK, forProduct=9c034dd1-913b-4d81-9503-2e9afe7fee24, priceId=0524e3b9-5239-4768-8bbc-43c6d55f3958, priceValidityPeriod=TimeWindow{fromInclusive=2023-02-15T20:24:53.582570Z, toExclusive=null}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 1
     *     PriceValidityAdjusted{forProduct=9c034dd1-913b-4d81-9503-2e9afe7fee24, priceId=0524e3b9-5239-4768-8bbc-43c6d55f3958, priceValidityPeriod=TimeWindow{fromInclusive=2023-02-15T20:24:53.582570Z, toExclusive=2023-03-15T20:24:53.654086Z}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 2
     *     PriceAdjusted{price=120 DKK, forProduct=9c034dd1-913b-4d81-9503-2e9afe7fee24, priceId=46c70769-e411-4129-ac9f-2ba70fbc82b0, priceValidityPeriod=TimeWindow{fromInclusive=2023-03-15T20:24:53.654086Z, toExclusive=null}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * }</pre>
     * @throws Exception
     */
    @Test
    void set_a_future_product_price() throws Exception {
        var forProduct            = ProductId.random();

        // Set the initial which is valid as of 14 days ago
        var initialPriceValidFrom = Instant.now().minus(14, ChronoUnit.DAYS);
        var initialPrice          = Money.of("100", CurrencyCode.DKK);
        var productPrice = new ProductPrice(forProduct,
                                            initialPrice,
                                            initialPriceValidFrom);

        // Set a future price, that will be valid in 14 days
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

    /**
     * Will produce a price timeline similar to: <code>[2023-02-15 ----- 100 DKK ----- 2023-03-01[ → [2023-03-01 ----- 110 DKK ----- 2023-03-15[ → [2023-03-15 ----- 120 DKK ----- ∞[</code><br>
     * and will persist 5 events:
     * <pre>{@code
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 0
     *     InitialPriceSet{price=100 DKK, forProduct=1fa246b1-8c8f-43fa-a9fd-6b002ffcfd6c, priceId=e9f7eefd-fa5c-4c12-ae39-4b47f001309f, priceValidityPeriod=TimeWindow{fromInclusive=2023-02-15T20:24:58.305118Z, toExclusive=null}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 1
     *     PriceValidityAdjusted{forProduct=1fa246b1-8c8f-43fa-a9fd-6b002ffcfd6c, priceId=e9f7eefd-fa5c-4c12-ae39-4b47f001309f, priceValidityPeriod=TimeWindow{fromInclusive=2023-02-15T20:24:58.305118Z, toExclusive=2023-03-15T20:24:58.307168Z}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 2
     *     PriceAdjusted{price=120 DKK, forProduct=1fa246b1-8c8f-43fa-a9fd-6b002ffcfd6c, priceId=4a69d73f-3451-42cf-a30b-5cc94922e69f, priceValidityPeriod=TimeWindow{fromInclusive=2023-03-15T20:24:58.307168Z, toExclusive=null}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 3
     *     PriceValidityAdjusted{forProduct=1fa246b1-8c8f-43fa-a9fd-6b002ffcfd6c, priceId=e9f7eefd-fa5c-4c12-ae39-4b47f001309f, priceValidityPeriod=TimeWindow{fromInclusive=2023-02-15T20:24:58.305118Z, toExclusive=2023-03-01T20:24:58.310391Z}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * Event: 4
     *     PriceAdjusted{price=110 DKK, forProduct=1fa246b1-8c8f-43fa-a9fd-6b002ffcfd6c, priceId=3990eedd-d8ed-44eb-bd3b-d753538b4a2b, priceValidityPeriod=TimeWindow{fromInclusive=2023-03-01T20:24:58.310391Z, toExclusive=2023-03-15T20:24:58.307168Z}}
     * ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * }</pre>
     * @throws Exception
     */
    @Test
    void add_a_price_in_between_two_other_prices() throws Exception {
        var forProduct            = ProductId.random();

        // Set the initial which is valid as of 14 days ago
        var initialPriceValidFrom = Instant.now().minus(14, ChronoUnit.DAYS);
        var initialPrice          = Money.of("100", CurrencyCode.DKK);
        var productPrice = new ProductPrice(forProduct,
                                            initialPrice,
                                            initialPriceValidFrom);

        // Set a future price, that will be valid in 14 days
        var futurePrice          = Money.of("120", CurrencyCode.DKK);
        var futurePriceValidFrom = Instant.now().plus(14, ChronoUnit.DAYS);
        productPrice.adjustPrice(futurePrice,
                                 futurePriceValidFrom);

        // Add a new price that is valid from now and the next 14 days
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

        // Print out timeline
        var businessTimeline = productPrice.getPriceOverTime()
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(o -> o.getKey().fromInclusive))
                .map(entry -> {
                    var priceValidityPeriod = entry.getKey();
                    var price = entry.getValue().price();
                    return msg("[{} ----- {} ----- {}[",
                               DATE_FORMATTER.format(priceValidityPeriod.fromInclusive),
                               price,
                               priceValidityPeriod.toExclusive != null ? DATE_FORMATTER.format(priceValidityPeriod.toExclusive) : "∞");
                })
                .reduce((s1, s2) -> s1 + " → " + s2)
                .get();
        log.debug("Business timeline: {}", businessTimeline);
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
