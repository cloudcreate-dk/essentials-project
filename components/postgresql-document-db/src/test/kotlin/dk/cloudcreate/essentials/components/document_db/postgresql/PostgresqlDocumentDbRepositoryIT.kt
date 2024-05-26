/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.components.document_db.postgresql

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.cloudcreate.essentials.components.document_db.*
import dk.cloudcreate.essentials.components.foundation.json.JacksonJSONSerializer
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.JdbiUnitOfWorkFactory
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule
import dk.cloudcreate.essentials.kotlin.types.Amount
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import kotlin.reflect.KMutableProperty1


@Testcontainers
class DocumentDbRepositoryImplIT {
    private lateinit var jdbi: Jdbi
    private lateinit var orderRepository: DocumentDbRepository<Order, OrderId>
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

        orderRepository = repositoryFactory.create(Order::class)
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
                invoiceAddress = Address("123 Some Street", 1000, "Springfield"),
                contactDetails = ContactDetails("John Doe", Address("123 Some Street", 1000, "Springfield"), listOf("Some Phone Number")),
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
                invoiceAddress = Address("456 Some Other Street", 1000, "Shelbyville"),
                contactDetails = ContactDetails("Jane Smith", Address("456 Some Other Street", 1000, "Shelbyville"), listOf("Some Other Phone Number")),
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
                destination = Address("123 Main St", 1000, "Springfield"),
                status = "Pending"
            )
        )
    }

    @Test
    fun `Configure Order entity`() {
        val config = EntityConfiguration.configureEntity(Order::class)
        assertThat(config.entityClass()).isEqualTo(Order::class)
        assertThat(config.tableName()).isEqualTo("orders")
        assertThat(config.idProperty().name).isEqualTo("orderId")
        assertThat(config.idProperty() as? KMutableProperty1).isNull()
        assertThat(config.versionProperty().name).isEqualTo("version")
    }

    @Test
    fun `Save, load and update an Order`() {
        val orderId = OrderId.random()
        val orderToSave = Order(
            orderId,
            "Some description",
            Amount.of("100.25"),
            50,
            LocalDateTime.now(),
            "John Doe",
            Address("Some Street", 1000, "Some City"),
            ContactDetails(
                "John Doe",
                Address("Some Other Street", 1000, "Some Other City"),
                listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
            )
        )

        val savedOrder = orderRepository.save(orderToSave)
        assertThat(savedOrder.version).isEqualTo(Version.ZERO)
        assertThat(savedOrder)
            .usingRecursiveAssertion()
            .isEqualTo(orderToSave)
        assertIdAndVersionColumns(orderId, Version.ZERO, orderRepository.entityConfiguration())

        var loadedOrder = orderRepository.findById(orderId)
        assertThat(loadedOrder).isNotNull
        assertThat(loadedOrder)
            .usingRecursiveAssertion()
            .isEqualTo(savedOrder)

        loadedOrder!!.amount = Amount.Companion.of("200.00")
        loadedOrder.description = "Updated description"
        loadedOrder.additionalProperty = 75
        val updatedOrder = orderRepository.update(loadedOrder)
        assertThat(updatedOrder.version).isEqualTo(Version(1))
        assertThat(updatedOrder)
            .usingRecursiveAssertion()
            .isEqualTo(loadedOrder)
        assertIdAndVersionColumns(orderId, Version(1), orderRepository.entityConfiguration())

        // Load the Order again
        loadedOrder = orderRepository.findById(orderId)
        assertThat(loadedOrder).isNotNull
        assertThat(loadedOrder)
            .usingRecursiveAssertion()
            .isEqualTo(updatedOrder)
    }

    private fun assertIdAndVersionColumns(
        entityId: Any,
        expectedVersion: Version,
        entityConfiguration: EntityConfiguration<*, *>
    ) {
        jdbi.useHandle<Exception> {
            val match =
                it.createQuery("SELECT 1 FROM ${entityConfiguration.tableName()} WHERE id = ? AND version = ?")
                    .bind(0, entityId)
                    .bind(1, expectedVersion)
                    .mapTo(Long::class.java)
                    .findOne()
            assertThat(match)
                .describedAs("[${entityConfiguration.tableName()}] Expected to find a row with id '${entityId} and version '${expectedVersion}' column values, but found none")
                .isPresent
        }
    }

    @Test
    fun `Test saving the same entity twice results in Optimistic Locking Exception`() {
        val orderId = OrderId.random()
        val orderToSave = Order(
            orderId,
            "Some description",
            Amount.of("100.25"),
            50,
            LocalDateTime.now(),
            "John Doe",
            Address("Some Street", 1000, "Some City"),
            ContactDetails(
                "John Doe",
                Address("Some Other Street", 1000, "Some Other City"),
                listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
            )
        )

        val savedOrder = orderRepository.save(orderToSave)
        assertThat(savedOrder.version).isEqualTo(Version.ZERO)

        assertThatThrownBy {
            orderRepository.save(
                Order(
                    orderId,
                    "Some other description",
                    Amount.of("110.25"),
                    80,
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
        }.isInstanceOf(OptimisticLockingException::class.java)
        assertIdAndVersionColumns(orderId, Version.ZERO, orderRepository.entityConfiguration())
    }

    @Test
    fun `Two overlapping updates to the same Order results in Optimistic Locking Exception`() {
        val orderId = OrderId.random()
        val orderToSave = Order(
            orderId,
            "Some description",
            Amount.of("100.25"),
            50,
            LocalDateTime.now(),
            "John Doe",
            Address("Some Street", 1000, "Some City"),
            ContactDetails(
                "John Doe",
                Address("Some Other Street", 1000, "Some Other City"),
                listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
            )
        )

        val savedOrder = orderRepository.save(orderToSave)
        assertThat(savedOrder.version).isEqualTo(Version.ZERO)

        // load the entity twice
        val orderLoaded = orderRepository.findById(orderId)!!
        orderLoaded.additionalProperty = 75
        val orderLoadedAgain = orderRepository.findById(orderId)!!
        orderLoadedAgain.additionalProperty = 75

        // Update of the orders first
        val updatedOrder = orderRepository.update(orderLoaded)
        assertThat(updatedOrder.version).isEqualTo(Version(1))

        // Update the other order right after assuming that the version in the DB should be 0 (but it is 1 due to the previous update)
        assertThatThrownBy {
            orderRepository.update(orderLoadedAgain)
        }.isInstanceOf(OptimisticLockingException::class.java)
        assertIdAndVersionColumns(orderId, Version(1), orderRepository.entityConfiguration())
    }

    @Test
    fun findById_ShouldReturnEntityForExistingEntity() {
        val orderId = OrderId.random()
        val orderToSave = Order(
            orderId,
            "Some description",
            Amount.of("100.25"),
            50,
            LocalDateTime.now(),
            "John Doe",
            Address("Some Street", 1000, "Some City"),
            ContactDetails(
                "John Doe",
                Address("Some Other Street", 1000, "Some Other City"),
                listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
            )
        )
        orderRepository.save(orderToSave)

        val found = orderRepository.findById(orderId)
        assertThat(found).isNotNull()
        assertThat(found)
            .usingRecursiveAssertion()
            .isEqualTo(orderToSave)
    }

    @Test
    fun findById_ShouldReturnNothingForNonExistingEntity() {
        val orderId = OrderId.random()

        val found = orderRepository.findById(orderId)
        assertThat(found).isNull()
    }

    @Test
    fun existsById_ShouldReturnTrueForExistingEntity() {
        val orderId = OrderId.random()
        val orderToSave = Order(
            orderId,
            "Some description",
            Amount.of("100.25"),
            50,
            LocalDateTime.now(),
            "John Doe",
            Address("Some Street", 1000, "Some City"),
            ContactDetails(
                "John Doe",
                Address("Some Other Street", 1000, "Some Other City"),
                listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
            )
        )
        orderRepository.save(orderToSave)
        val exists = orderRepository.existsById(orderId)
        assertThat(exists).isTrue()
    }

    @Test
    fun existsById_ShouldReturnFalseForNonExistingEntity() {
        val orderId = OrderId.random()
        val exists = orderRepository.existsById(orderId)
        assertThat(exists).isFalse()
    }

    @Test
    fun saveAll_ShouldSaveEntities() {
        val order1Id = OrderId.random()
        val order1ToSave = Order(
            order1Id,
            "Some description",
            Amount.of("100.25"),
            50,
            LocalDateTime.now(),
            "John Doe",
            Address("Some Street", 1000, "Some City"),
            ContactDetails(
                "John Doe",
                Address("Some Other Street", 1000, "Some Other City"),
                listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
            )
        )
        val order2Id = OrderId.random()
        val order2ToSave = Order(
            order2Id,
            "Some other description",
            Amount.of("300.00"),
            100,
            LocalDateTime.now(),
            "Jane Doe",
            Address("Some Street", 2000, "Some City"),
            ContactDetails(
                "John Doe",
                Address("Some Other Street", 2000, "Some Other City"),
                listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
            )
        )
        val savedEntities = orderRepository.saveAll(order1ToSave, order2ToSave)
        assertThat(savedEntities).hasSize(2)

        assertThat(savedEntities[0].version).isEqualTo(Version.ZERO)
        assertThat(savedEntities[0])
            .usingRecursiveAssertion()
            .isEqualTo(order1ToSave)
        assertIdAndVersionColumns(order1Id, Version.ZERO, orderRepository.entityConfiguration())

        assertThat(savedEntities[1].version).isEqualTo(Version.ZERO)
        assertThat(savedEntities[1])
            .usingRecursiveAssertion()
            .isEqualTo(order2ToSave)
        assertIdAndVersionColumns(order2Id, Version.ZERO, orderRepository.entityConfiguration())

        assertThat(orderRepository.existsById(savedEntities[0].orderId)).isTrue()
        assertThat(orderRepository.existsById(savedEntities[1].orderId)).isTrue()
    }

    @Test
    fun updateAll_ShouldUpdateEntities() {
        val order1Id = OrderId.random()
        val order2Id = OrderId.random()
        orderRepository.saveAll(
            Order(
                order1Id,
                "Some description",
                Amount.of("100.25"),
                50,
                LocalDateTime.now(),
                "John Doe",
                Address("Some Street", 1000, "Some City"),
                ContactDetails(
                    "John Doe",
                    Address("Some Other Street", 1000, "Some Other City"),
                    listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
                )
            ), Order(
                order2Id,
                "Some other description",
                Amount.of("300.00"),
                100,
                LocalDateTime.now(),
                "Jane Doe",
                Address("Another Street", 2000, "Another City"),
                ContactDetails(
                    "Jane Doe",
                    Address("Some Another Street", 2000, "Some Another City"),
                    listOf("PhoneNumber4", "PhoneNumber5", "PhoneNumber6")
                )
            )
        )

        val order1 = orderRepository.getById(order1Id)
        order1.additionalProperty = 123
        val order2 = orderRepository.getById(order2Id)
        order2.additionalProperty = 321
        var updatedEntities = orderRepository.updateAll(order1, order2)
        assertThat(updatedEntities).hasSize(2)
        assertThat(updatedEntities[0].version).isEqualTo(Version(1))
        assertThat(updatedEntities[0])
            .usingRecursiveAssertion()
            .isEqualTo(order1)
        assertIdAndVersionColumns(order1Id, Version(1), orderRepository.entityConfiguration())
        assertThat(orderRepository.getById(order1Id)).isEqualTo(order1)

        assertThat(updatedEntities[1].version).isEqualTo(Version(1))
        assertThat(updatedEntities[1])
            .usingRecursiveAssertion()
            .isEqualTo(order2)
        assertIdAndVersionColumns(order2Id, Version(1), orderRepository.entityConfiguration())
        assertThat(orderRepository.getById(order2Id)).isEqualTo(order2)
    }

    @Test
    fun deleteById_ShouldRemoveEntity() {
        val order1Id = OrderId.random()
        val order2Id = OrderId.random()
        orderRepository.saveAll(
            Order(
                order1Id,
                "Some description",
                Amount.of("100.25"),
                50,
                LocalDateTime.now(),
                "John Doe",
                Address("Some Street", 1000, "Some City"),
                ContactDetails(
                    "John Doe",
                    Address("Some Other Street", 1000, "Some Other City"),
                    listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
                )
            ), Order(
                order2Id,
                "Some other description",
                Amount.of("300.00"),
                100,
                LocalDateTime.now(),
                "Jane Doe",
                Address("Another Street", 1000, "Another City"),
                ContactDetails(
                    "Jane Doe",
                    Address("Some Another Street", 1000, "Some Another City"),
                    listOf("PhoneNumber4", "PhoneNumber5", "PhoneNumber6")
                )
            )
        )
        assertThat(orderRepository.existsById(order1Id)).isTrue()
        assertThat(orderRepository.existsById(order2Id)).isTrue()

        // When
        orderRepository.deleteById(order1Id)

        // Then
        assertThat(orderRepository.existsById(order1Id)).isFalse()
        assertThat(orderRepository.existsById(order2Id)).isTrue()
    }

    @Test
    fun delete_ShouldRemoveEntity() {
        val order1Id = OrderId.random()
        val order2Id = OrderId.random()
        orderRepository.saveAll(
            Order(
                order1Id,
                "Some description",
                Amount.of("100.25"),
                50,
                LocalDateTime.now(),
                "John Doe",
                Address("Some Street", 1000, "Some City"),
                ContactDetails(
                    "John Doe",
                    Address("Some Other Street", 1000, "Some Other City"),
                    listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
                )
            ), Order(
                order2Id,
                "Some other description",
                Amount.of("300.00"),
                100,
                LocalDateTime.now(),
                "Jane Doe",
                Address("Another Street", 1000, "Another City"),
                ContactDetails(
                    "Jane Doe",
                    Address("Some Another Street", 1000, "Some Another City"),
                    listOf("PhoneNumber4", "PhoneNumber5", "PhoneNumber6")
                )
            )
        )
        assertThat(orderRepository.existsById(order1Id)).isTrue()
        assertThat(orderRepository.existsById(order2Id)).isTrue()

        // When
        orderRepository.delete(orderRepository.getById(order1Id))

        // Then
        assertThat(orderRepository.existsById(order1Id)).isFalse()
        assertThat(orderRepository.existsById(order2Id)).isTrue()
    }

    @Test
    fun deleteAll_ShouldRemoveProvidedEntities() {
        val order1Id = OrderId.random()
        val order2Id = OrderId.random()
        val order1 = Order(
            order1Id,
            "Some description",
            Amount.of("100.25"),
            50,
            LocalDateTime.now(),
            "John Doe",
            Address("Some Street", 1000, "Some City"),
            ContactDetails(
                "John Doe",
                Address("Some Other Street", 1000, "Some Other City"),
                listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
            )
        )
        val order2 = Order(
            order2Id,
            "Some other description",
            Amount.of("300.00"),
            100,
            LocalDateTime.now(),
            "Jane Doe",
            Address("Another Street", 1000, "Another City"),
            ContactDetails(
                "Jane Doe",
                Address("Some Another Street", 1000, "Some Another City"),
                listOf("PhoneNumber4", "PhoneNumber5", "PhoneNumber6")
            )
        )

        orderRepository.saveAll(
            order1, order2
        )
        assertThat(orderRepository.existsById(order1Id)).isTrue()
        assertThat(orderRepository.existsById(order2Id)).isTrue()

        // When
        orderRepository.deleteAll(order1, order2)

        // Then
        assertThat(orderRepository.existsById(order1Id)).isFalse()
        assertThat(orderRepository.existsById(order2Id)).isFalse()
    }


    @Test
    fun deleteAllById_ShouldRemoveEntities() {
        val order1Id = OrderId.random()
        val order2Id = OrderId.random()
        orderRepository.saveAll(
            Order(
                order1Id,
                "Some description",
                Amount.of("100.25"),
                50,
                LocalDateTime.now(),
                "John Doe",
                Address("Some Street", 1000, "Some City"),
                ContactDetails(
                    "John Doe",
                    Address("Some Other Street", 1000, "Some Other City"),
                    listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
                )
            ), Order(
                order2Id,
                "Some other description",
                Amount.of("300.00"),
                100,
                LocalDateTime.now(),
                "Jane Doe",
                Address("Another Street", 2000, "Another City"),
                ContactDetails(
                    "Jane Doe",
                    Address("Some Another Street", 2000, "Some Another City"),
                    listOf("PhoneNumber4", "PhoneNumber5", "PhoneNumber6")
                )
            )
        )
        assertThat(orderRepository.existsById(order1Id)).isTrue()
        assertThat(orderRepository.existsById(order2Id)).isTrue()

        // When
        orderRepository.deleteAllById(order1Id, order2Id)

        // Then
        assertThat(orderRepository.existsById(order1Id)).isFalse()
        assertThat(orderRepository.existsById(order2Id)).isFalse()
    }

    @Test
    fun deleteAll_ShouldRemoveAllEntities() {
        val order1Id = OrderId.random()
        val order2Id = OrderId.random()
        orderRepository.saveAll(
            Order(
                order1Id,
                "Some description",
                Amount.of("100.25"),
                50,
                LocalDateTime.now(),
                "John Doe",
                Address("Some Street", 1000, "Some City"),
                ContactDetails(
                    "John Doe",
                    Address("Some Other Street", 1000, "Some Other City"),
                    listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
                )
            ), Order(
                order2Id,
                "Some other description",
                Amount.of("300.00"),
                100,
                LocalDateTime.now(),
                "Jane Doe",
                Address("Another Street", 2000, "Another City"),
                ContactDetails(
                    "Jane Doe",
                    Address("Some Another Street", 2000, "Some Another City"),
                    listOf("PhoneNumber4", "PhoneNumber5", "PhoneNumber6")
                )
            )
        )
        assertThat(orderRepository.existsById(order1Id)).isTrue()
        assertThat(orderRepository.existsById(order2Id)).isTrue()

        // When
        orderRepository.deleteAll()

        // Then
        assertThat(orderRepository.existsById(order1Id)).isFalse()
        assertThat(orderRepository.existsById(order2Id)).isFalse()
    }

    @Test
    fun findAll_ShouldReturnAllEntities() {
        val orders = (0 until 50).toList()
            .map { i ->
                Order(
                    OrderId.random(),
                    "Some description that's not unique",
                    Amount.of(if (i % 2 == 0) "100.50" else "201.00"),
                    i,
                    LocalDateTime.now().minusDays(i.toLong()),
                    if (i % 2 == 0) "John Doe" else "Jane Doe",
                    Address("Another Street", if (i % 2 == 0) 1000 else 2000, "Another City"),
                    ContactDetails(
                        if (i % 2 == 0) "John Doe" else "Jane Doe",
                        Address("Some Another Street", if (i % 2 == 0) 1000 else 2000, "Some Another City"),
                        listOf("PhoneNumber4", "PhoneNumber5", "PhoneNumber6")
                    )
                )
            }

        orderRepository.saveAll(
            orders
        )

        var allOrdersFound = orderRepository.findAll()
        assertThat(allOrdersFound).containsAll(orders)
    }

    @Test
    fun count_ShouldReturnCorrectCount() {
        val orders = (0 until 50).toList()
            .map { i ->
                Order(
                    OrderId.random(),
                    "Some description that's not unique",
                    Amount.of(if (i % 2 == 0) "100.50" else "201.00"),
                    i,
                    LocalDateTime.now().minusDays(i.toLong()),
                    if (i % 2 == 0) "John Doe" else "Jane Doe",
                    Address("Some Street", 1000, "Some City"),
                    ContactDetails(
                        "John Doe",
                        Address("Some Other Street", 1000, "Some Other City"),
                        listOf("PhoneNumber1", "PhoneNumber2", "PhoneNumber3")
                    )
                )
            }

        orderRepository.saveAll(
            orders
        )

        var count = orderRepository.count()
        assertThat(count).isEqualTo(50 + 2) // +2 are for the orders added in populateTestData
    }

    @Test
    fun findAllById_ShouldReturnMatchingEntities() {
        val orders = (0 until 50).toList()
            .map { i ->
                Order(
                    OrderId.random(),
                    "Some description that's not unique",
                    Amount.of(if (i % 2 == 0) "100.50" else "201.00"),
                    i,
                    LocalDateTime.now().minusDays(i.toLong()),
                    if (i % 2 == 0) "John Doe" else "Jane Doe",
                    Address("Another Street", if (i % 2 == 0) 1000 else 2000, "Another City"),
                    ContactDetails(
                        if (i % 2 == 0) "John Doe" else "Jane Doe",
                        Address("Some Another Street", if (i % 2 == 0) 1000 else 2000, "Some Another City"),
                        listOf("PhoneNumber4", "PhoneNumber5", "PhoneNumber6")
                    )
                )
            }

        orderRepository.saveAll(
            orders
        )

        var allOrdersFound = orderRepository.findAllById(orders.map { it.orderId })
        assertThat(allOrdersFound).containsAll(orders)
    }
}
