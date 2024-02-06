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

package dk.cloudcreate.essentials.types.springdata.jpa;

import dk.cloudcreate.essentials.types.*;
import dk.cloudcreate.essentials.types.springdata.jpa.model.Order;
import dk.cloudcreate.essentials.types.springdata.jpa.model.*;
import org.assertj.core.api.SoftAssertions;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@DirtiesContext
class OrderRepositoryIT {

    @Container
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:latest")
            .withDatabaseName("test")
            .withPassword("test")
            .withUsername("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        this.orderRepository.deleteAll();
    }

    @Test
    void test_we_can_store_an_order_and_load_it_again() {
        var currencyCode = CurrencyCode.of("DKK");
        var amount       = Amount.of("123.456");
        var percentage   = Percentage.from("40.5%");

        var storedOrder = transactionTemplate.execute(status -> orderRepository.save(new Order(OrderId.random(),
                                                                                               CustomerId.random(),
                                                                                               AccountId.random(),
//                                                                                               Map.of(ProductId.random(), Quantity.of(10),
//                                                                                                      ProductId.random(), Quantity.of(5),
//                                                                                                      ProductId.random(), Quantity.of(1)),
                                                                                               amount,
                                                                                               percentage,
                                                                                               currencyCode,
                                                                                               CountryCode.of("DK"),
                                                                                               EmailAddress.of("john@nonexistingdomain.com"),
                                                                                               new Money(amount.add(percentage.of(amount)), currencyCode),
                                                                                               Created.now(),
                                                                                               DueDate.now(),
                                                                                               LastUpdated.now(),
                                                                                               TimeOfDay.now(),
                                                                                               TransactionTime.now(),
                                                                                               TransferTime.now())));

        assertThat(storedOrder.getId()).isNotNull();

        var jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(), postgreSQLContainer.getUsername(), postgreSQLContainer.getPassword());
        var rawResults = jdbi.withHandle(handle -> handle.createQuery("select * from orders")
                                                         .mapToMap()
                                                         .list());
        assertThat(rawResults).hasSize(1);
        var softAssertions = new SoftAssertions();
        rawResults.get(0).entrySet().forEach(entry -> {
            Class<?> valueType = entry.getValue().getClass();
            System.out.println(entry.getKey() + ": " + valueType.getName() + " with value '" + entry.getValue() + "'");
            softAssertions.assertThat(valueType.isArray())
                          .describedAs("key '%s' shouldn't be a an array", entry.getKey())
                          .isFalse();
            if (valueType.isArray()) {
                softAssertions.assertThat(valueType.getComponentType().equals(byte.class))
                              .describedAs("key '%s' shouldn't be a byte array", entry.getKey())
                              .isFalse();
            }
        });
        softAssertions.assertAll();

        transactionTemplate.execute(status -> {
            var loadedOrder = orderRepository.findById(storedOrder.getId());
            assertThat(loadedOrder).isPresent();
            var actualOrder = loadedOrder.get();
            assertThat(actualOrder.getId()).isEqualTo(storedOrder.getId());
            assertThat(actualOrder.getId().value()).isEqualTo(storedOrder.getId().value());
            assertThat((CharSequence) actualOrder.getCustomerId()).isEqualTo(storedOrder.getCustomerId());
            assertThat(actualOrder.getAccountId()).isEqualTo(storedOrder.getAccountId());
//            assertThat(actualOrder.orderLines.size()).isEqualTo(3);
//            assertThat(actualOrder.orderLines.keySet()).doesNotContainNull();
//            assertThat(actualOrder.getOrderLines()).isEqualTo(storedOrder.getOrderLines());
            assertThat(actualOrder.getAmount()).isEqualTo(storedOrder.getAmount());
            assertThat(actualOrder.getPercentage()).isEqualTo(storedOrder.getPercentage());
            assertThat((CharSequence) actualOrder.getCountry()).isEqualTo(storedOrder.getCountry());
            assertThat((CharSequence) actualOrder.getCurrency()).isEqualTo(storedOrder.getCurrency());
            assertThat((CharSequence) actualOrder.getEmail()).isEqualTo(storedOrder.getEmail());
            assertThat(actualOrder.getTotalPrice()).isEqualTo(storedOrder.getTotalPrice());
            assertThat(actualOrder.getCreated().value()).isCloseTo(storedOrder.getCreated().value(), within(100, ChronoUnit.MICROS));
            assertThat(actualOrder.getDueDate()).isEqualTo(storedOrder.getDueDate());
            assertThat(actualOrder.getLastUpdated().value()).isCloseTo(storedOrder.getLastUpdated().value(), within(100, ChronoUnit.MICROS));
            assertThat(actualOrder.getTimeOfDay()).isEqualTo(storedOrder.getTimeOfDay());
            assertThat(actualOrder.getTransactionTime().value()).isCloseTo(storedOrder.getTransactionTime().value(), within(100, ChronoUnit.MICROS));;
            assertThat(actualOrder.getTransferTime().value()).isCloseTo(storedOrder.getTransferTime().value(), within(100, ChronoUnit.MICROS));
            return null;
        });
    }

}