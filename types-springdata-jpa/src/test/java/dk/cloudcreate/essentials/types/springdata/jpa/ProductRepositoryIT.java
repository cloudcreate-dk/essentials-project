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

package dk.cloudcreate.essentials.types.springdata.jpa;

import dk.cloudcreate.essentials.types.*;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DirtiesContext
class ProductRepositoryIT {
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
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        this.productRepository.deleteAll();
    }

    @Test
    void test_we_can_store_a_product_and_load_it_again() {
        var storedProduct = transactionTemplate.execute(status -> productRepository.save(new Product(ProductId.random(),
                                                                                                     "Test Product",
                                                                                                     new Price(Amount.of("100.5"), CurrencyCode.of("DKK")),
                                                                                                     OrderId.random())));

        assertThat(productRepository.findById(storedProduct.getId()).get()).isEqualTo(storedProduct);

        var jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(), postgreSQLContainer.getUsername(), postgreSQLContainer.getPassword());
        var rawResults = jdbi.withHandle(handle -> handle.createQuery("select * from products")
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
            var loadedProduct = productRepository.findById(storedProduct.getId());
            assertThat(loadedProduct).isPresent();
            assertThat((CharSequence) loadedProduct.get().getId()).isEqualTo(storedProduct.getId());
            assertThat(loadedProduct.get().getId().value()).isEqualTo(storedProduct.getId().value());
            assertThat(loadedProduct.get()).isEqualTo(storedProduct);
            return null;
        });
    }
}