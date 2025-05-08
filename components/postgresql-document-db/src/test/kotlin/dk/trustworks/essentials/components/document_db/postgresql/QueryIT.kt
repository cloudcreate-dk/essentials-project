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

package dk.trustworks.essentials.components.document_db.postgresql

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.trustworks.essentials.components.document_db.DocumentDbRepository
import dk.trustworks.essentials.components.document_db.DocumentDbRepositoryFactory
import dk.trustworks.essentials.components.document_db.Index
import dk.trustworks.essentials.components.foundation.json.JacksonJSONSerializer
import dk.trustworks.essentials.components.foundation.transaction.jdbi.JdbiUnitOfWorkFactory
import dk.trustworks.essentials.jackson.immutable.EssentialsImmutableJacksonModule
import dk.trustworks.essentials.kotlin.types.Amount
import dk.trustworks.essentials.kotlin.types.jdbi.AmountArgumentFactory
import dk.trustworks.essentials.kotlin.types.jdbi.AmountColumnMapper
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime


@Testcontainers
class QueryIT {
    private lateinit var jdbi: Jdbi
    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: DocumentDbRepository<Product, ProductId>
    private lateinit var visitRepository: DocumentDbRepository<Visit, VisitId>
    private lateinit var shippingOrderRepository: DocumentDbRepository<ShippingOrder, ShippingOrderId>

    @Container
    val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:latest")
        .apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }

    @BeforeEach
    fun setup() {
        jdbi = Jdbi.create(
            postgresContainer.getJdbcUrl(),
            postgresContainer.getUsername(),
            postgresContainer.getPassword()
        ).apply {
            this.registerArgument(OrderIdArgumentFactory())
            this.registerColumnMapper(OrderIdColumnMapper())
            this.registerArgument(ProductIdArgumentFactory())
            this.registerColumnMapper(ProductIdColumnMapper())
            this.registerArgument(VisitIdArgumentFactory())
            this.registerColumnMapper(VisitIdColumnMapper())
            this.registerArgument(ShippingOrderIdArgumentFactory())
            this.registerColumnMapper(ShippingOrderIdColumnMapper())
            this.registerArgument(AmountArgumentFactory())
            this.registerColumnMapper(AmountColumnMapper())
        }

        val repositoryFactory = DocumentDbRepositoryFactory(
            jdbi,
            JdbiUnitOfWorkFactory(jdbi),
            JacksonJSONSerializer(
                EssentialsImmutableJacksonModule.createObjectMapper(
                    Jdk8Module(),
                    JavaTimeModule()
                ).registerKotlinModule()
            )
        )

        orderRepository =  OrderRepository(repositoryFactory.create(Order::class))
        productRepository = repositoryFactory.create(Product::class)
        visitRepository = repositoryFactory.create(Visit::class)
        shippingOrderRepository = repositoryFactory.create(ShippingOrder::class)

        orderRepository.addIndex(Index(name = "city", listOf(Order::contactDetails then ContactDetails::address then Address::city)))
        orderRepository.addIndex(Index(name = "orderdate_amount", listOf(Order::orderDate.asProperty(), Order::amount.asProperty())))

        populateTestData()
    }

    private fun populateTestData() {
        // Populate the database with test data
        orderRepository.save(
            Order(
                orderId = OrderId("order1"),
                description = "Test Order 1",
                amount = Amount(100.0),
                orderDate = LocalDateTime.now(),
                personName = "John Doe",
                invoiceAddress = Address("123 Some Street", 3000, "Springfield"),
                contactDetails = ContactDetails("John Doe", Address("123 Some Street", 3000, "Springfield"), listOf("Some Phone Number")),
                additionalProperty = 10
            )
        )

        orderRepository.save(
            Order(
                orderId = OrderId("order2"),
                description = "Test Order 2",
                amount = Amount(200.0),
                orderDate = LocalDateTime.now(),
                personName = "Jane Smith",
                invoiceAddress = Address("456 Some Other Street", 4000, "Shelbyville"),
                contactDetails = ContactDetails("Jane Smith", Address("456 Some Other Street", 4000, "Shelbyville"), listOf("Some Other Phone Number")),
                additionalProperty = 20
            )
        )

        productRepository.save(
            Product(
                productId = ProductId("product1"),
                name = "Product 1",
                price = 10.0,
                category = "Electronics",
                stock = 100
            )
        )

        visitRepository.save(
            Visit(
                visitId = VisitId("visit1"),
                visitorName = "Visitor 1",
                visitDate = LocalDateTime.now(),
                location = "Lobby"
            )
        )

        shippingOrderRepository.save(
            ShippingOrder(
                shippingOrderId = ShippingOrderId("shippingOrder1"),
                orderReference = "order1",
                shippingDate = LocalDateTime.now(),
                destination = Address("123 Main St", 3000, "Springfield"),
                status = "Pending"
            )
        )
    }

    @Test
    fun `Find Orders using eq`() {
        orderRepository.deleteAll()

        val numberOfOrders = storeOrders(100)

        // Simple query
        val query = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::additionalProperty eq 50
                    // Continue chaining other conditions as needed
                })
            .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
            .limit(200)
            .offset(0)

        val result = orderRepository.find(query)
        assertThat(result).hasSize(1)
        assertThat(result[0].additionalProperty).isEqualTo(50)
    }

    @Test
    fun `Test using the findOrdersWithAmountGreaterThan query method defined on the OrderRepository class`() {
        orderRepository.deleteAll()

        storeOrders(100)

        // Simple query
        val amount = Amount("150.00")
        val result = orderRepository.findOrdersWithAmountGreaterThan(amount)

        assertThat(result).hasSize(50)
        assertThat(result.map { it.amount }).allMatch { it > amount }
    }

    @Test
    fun `Find Orders using eq on nested property`() {
        orderRepository.deleteAll()

        val numberOfOrders = storeOrders(100)

        // Simple query
        val query = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::contactDetails then ContactDetails::address then Address::zipCode eq 1000
                    // Continue chaining other conditions as needed
                })
            .limit(200)
            .offset(0)

        val result = orderRepository.find(query)
        assertThat(result).hasSize(numberOfOrders/2)
        assertThat(result.map { it.contactDetails.address.zipCode }).allMatch { it == 1000 }
    }

    @Test
    fun `Find Orders using lt and gt`() {
        orderRepository.deleteAll()

        val numberOfOrders = storeOrders(100)

        // Simple query
        val query = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::additionalProperty lt 50
                    // Continue chaining other conditions as needed
                })
            .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
            .limit(200)
            .offset(0)

        var result = orderRepository.find(query)
        assertThat(result).hasSize(numberOfOrders / 2)
        assertThat(result.map { it.additionalProperty }).isEqualTo((0 until 50).toList())

        // Comparison query
        result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::amount gt 200
                    // Continue chaining other conditions as needed
                })
            .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
            .limit(200)
            .offset(0)
            .find()
        assertThat(result).hasSize(numberOfOrders / 2)
        assertThat(result.count { it.amount == Amount("201.00") }).isEqualTo(50)

        // Text query
        result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    (Order::personName like "%John%").or(Order::personName like "%Jane%")
                        .and(Order::description like "%unique%")
                })
            .find()
        assertThat(result).hasSize(numberOfOrders)
    }

    @Test
    fun `Find Orders using lt and gt on nested properties`() {
        orderRepository.deleteAll()

        val numberOfOrders = storeOrders(100)

        // Simple query
        val query = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::invoiceAddress then Address::zipCode lt 1001
                })
            .limit(200)
            .offset(0)

        var result = orderRepository.find(query)
        assertThat(result).hasSize(numberOfOrders / 2)
        assertThat(result.map { it.invoiceAddress.zipCode }).allMatch { it == 1000 }

        // Comparison query
        result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::invoiceAddress then Address::zipCode gt 1999
                })
            .limit(200)
            .offset(0)
            .find()
        assertThat(result).hasSize(numberOfOrders / 2)
        assertThat(result.map { it.invoiceAddress.zipCode }).allMatch { it == 2000 }
    }

    @Test
    fun `Find Orders using lte and gte`() {
        orderRepository.deleteAll()

        val numberOfOrders = storeOrders(100)

        // Simple query
        val query = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::additionalProperty lte 49
                    // Continue chaining other conditions as needed
                })
            .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
            .limit(200)
            .offset(0)

        var result = orderRepository.find(query)
        assertThat(result).hasSize(numberOfOrders / 2)
        assertThat(result.map { it.additionalProperty }).isEqualTo((0 until 50).toList())

        // Comparison query
        result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::amount gte 200
                    // Continue chaining other conditions as needed
                })
            .orderBy(Order::additionalProperty, QueryBuilder.Order.ASC)
            .limit(200)
            .offset(0)
            .find()
        assertThat(result).hasSize(numberOfOrders / 2)
        assertThat(result.count { it.amount == Amount("201.00") }).isEqualTo(50)

        // Text query
        result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    (Order::personName like "%John%").or(Order::personName like "%Jane%")
                        .and(Order::description like "%unique%")
                })
            .find()
        assertThat(result).hasSize(numberOfOrders)
    }

    @Test
    fun `Find Orders using lte and gte on nested properties`() {
        orderRepository.deleteAll()

        val numberOfOrders = storeOrders(100)

        // Simple query
        val query = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::invoiceAddress then Address::zipCode lte 1000
                })
            .limit(200)
            .offset(0)

        var result = orderRepository.find(query)
        assertThat(result).hasSize(numberOfOrders / 2)
        assertThat(result.map { it.invoiceAddress.zipCode }).allMatch { it == 1000 }

        // Comparison query
        result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::invoiceAddress then Address::zipCode gte 2000
                })
            .limit(200)
            .offset(0)
            .find()
        assertThat(result).hasSize(numberOfOrders / 2)
        assertThat(result.map { it.invoiceAddress.zipCode }).allMatch { it == 2000 }
    }

    private fun storeOrders(numberOfOrders: Int): Int {
        for (i in 0 until numberOfOrders) {
            orderRepository.save(
                Order(
                    OrderId.random(),
                    "Some description that's not unique",
                    Amount.of(if (i % 2 == 0) "100.50" else "201.00"),
                    i,
                    LocalDateTime.now().minusDays(i.toLong()),
                    if (i % 2 == 0) "John Doe" else "Jane Doe",
                    Address("Some Street", if (i % 2 == 0) 1000 else 2000, "Some City"),
                    ContactDetails(
                        if (i % 2 == 0) "John Doe" else "Jane Doe",
                        Address("Some Other Street", if (i % 2 == 0) 1000 else 2000, "Some Other City"),
                        listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
                    )
                )
            )
        }
        return numberOfOrders
    }

    @Test
    fun `find by city`() {
        orderRepository.save(
            Order(
                OrderId.random(),
                "Some description that's not unique",
                Amount.of("201.00"),
                10,
                LocalDateTime.now(),
                "John Doe",
                Address("Some Street", 1000, "Some City"),
                ContactDetails(
                    "John Doe",
                    Address("Some Other Street", 1000, "Some Other City"),
                    listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
                )
            )
        )
        orderRepository.save(
            Order(
                OrderId.random(),
                "Some description that's not unique",
                Amount.of("105.00"),
                20,
                LocalDateTime.now(),
                "Jane Doe",
                Address("Some Road", 2000, "Some Town"),
                ContactDetails(
                    "Jane Doe",
                    Address("Some Other Road", 2000, "Some Other Town"),
                    listOf("PhoneNumber11", "PhoneNumber12", "PhoneNumber13")
                )
            )
        )

        val result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::contactDetails then ContactDetails::address then Address::city like "Some Other%"
                })
            .orderBy(Order::contactDetails then ContactDetails::address then Address::city, QueryBuilder.Order.ASC)
            .limit(200)
            .find()

        assertThat(result).hasSize(2)
    }

    @Test
    fun `test where clause with like`() {
        val result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::description like "%Order%"
                })
            .find()

        assertThat(result).hasSize(2)
        assertThat(result).extracting("description").contains("Test Order 1", "Test Order 2")
    }

    @Test
    fun `test where clause with nested properties`() {
        val result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::contactDetails then ContactDetails::address then Address::city eq "Springfield"
                })
            .find()

        assertThat(result).hasSize(1)
        assertThat(result[0].invoiceAddress.city).isEqualTo("Springfield")
    }

    @Test
    fun `test orderBy clause`() {
        val result = orderRepository.queryBuilder()
            .orderBy(Order::amount, QueryBuilder.Order.ASC)
            .find()

        assertThat(result).hasSize(2)
        assertThat(result[0].amount).isLessThan(result[1].amount)
    }

    @Test
    fun `test limit clause`() {
        val result = orderRepository.queryBuilder()
            .limit(1)
            .find()

        assertThat(result).hasSize(1)
    }

    @Test
    fun `test offset clause`() {
        val result = orderRepository.queryBuilder()
            .offset(1)
            .find()

        assertThat(result).hasSize(1)
    }

    @Test
    fun `test combination of where, orderBy, limit, and offset`() {
        val result = orderRepository.queryBuilder()
            .where(orderRepository.condition()
                .matching {
                    Order::description like "%Order%"
                })
            .orderBy(Order::amount, QueryBuilder.Order.DESC)
            .limit(1)
            .offset(1)
            .find()

        assertThat(result).hasSize(1)
        assertThat(result[0].description).isEqualTo("Test Order 1")
    }
}