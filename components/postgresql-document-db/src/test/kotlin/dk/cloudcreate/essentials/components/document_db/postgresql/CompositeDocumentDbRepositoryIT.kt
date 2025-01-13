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

package dk.cloudcreate.essentials.components.document_db.postgresql

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.cloudcreate.essentials.components.document_db.*
import dk.cloudcreate.essentials.components.document_db.annotations.DocumentEntity
import dk.cloudcreate.essentials.components.document_db.annotations.Id
import dk.cloudcreate.essentials.components.document_db.annotations.Indexed
import dk.cloudcreate.essentials.components.foundation.json.JacksonJSONSerializer
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.JdbiUnitOfWorkFactory
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC

@Testcontainers
class CompositeDocumentDbRepositoryIT {
    private lateinit var jdbi: Jdbi
    private lateinit var repository: DocumentDbRepository<CompositeOrder, CompositeOrderId>

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
            this.registerArgument(ShippingOrderIdArgumentFactory())
            this.registerColumnMapper(ShippingOrderIdColumnMapper())
        }

        val repositoryFactory = DocumentDbRepositoryFactory(
            jdbi,
            JdbiUnitOfWorkFactory(jdbi),
            JacksonJSONSerializer(
                EssentialTypesJacksonModule.createObjectMapper(
                    Jdk8Module(),
                    JavaTimeModule()
                ).registerKotlinModule()
            )
        )

        repository = repositoryFactory.createForCompositeId(CompositeOrder::class) { "${it.orderId.value}:${it.shippingOrderId.value}" }
        repository.addIndex(Index(name = "city", listOf(CompositeOrder::shippingAddress then ShippingAddress::city)))
    }

    @Test
    fun `test save`() {
        val order = createRandomCompositeOrder()
        assertThat(order.version).isEqualTo(Version.NOT_SAVED_YET)
        val savedOrder = repository.save(order)
        assertThat(savedOrder.version).isEqualTo(Version(0))
        assertThat(repository.findById(order.id)).isEqualTo(savedOrder)
    }

    @Test
    fun `test update`() {
        val order = repository.save(createRandomCompositeOrder())
        val updatedAddress = ShippingAddress(
            id = order.shippingAddress.id,
            street = "New Street",
            zipCode = 99999,
            city = "New City"
        )
        val updatedOrder = order.copy(shippingAddress = updatedAddress)

        repository.update(updatedOrder)

        val foundOrder = repository.findById(order.id)
        assertThat(foundOrder).isNotNull
        assertThat(foundOrder!!.shippingAddress.street).isEqualTo("New Street")
        assertThat(foundOrder.shippingAddress.zipCode).isEqualTo(99999)
        assertThat(foundOrder.shippingAddress.city).isEqualTo("New City")
    }

    @Test
    fun `test findById`() {
        val order = repository.save(createRandomCompositeOrder())

        val foundOrder = repository.findById(order.id)
        assertThat(foundOrder).isEqualTo(order)
    }

    @Test
    fun `test findAll`() {
        val orders = listOf(
            createRandomCompositeOrder(),
            createRandomCompositeOrder()
        )
        orders.forEach { repository.save(it) }

        val foundOrders = repository.findAll()
        assertThat(foundOrders).containsExactlyInAnyOrderElementsOf(orders)
    }

    @Test
    fun `test delete`() {
        val order = repository.save(createRandomCompositeOrder())

        repository.delete(order)

        assertThat(repository.findById(order.id)).isNull()
    }

    @Test
    fun `test deleteAll`() {
        repository.save(createRandomCompositeOrder())
        repository.save(createRandomCompositeOrder())

        repository.deleteAll()

        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `test deleteById`() {
        val order = repository.save(createRandomCompositeOrder())

        repository.deleteById(order.id)

        assertThat(repository.findById(order.id)).isNull()
    }

    private fun createRandomCompositeOrder(): CompositeOrder {
        val orderId = OrderId.random()
        val shippingOrderId = ShippingOrderId.random()
        val compositeOrderId = CompositeOrderId(orderId, shippingOrderId)
        val addressId = CompositeOrderShippingAddressId(orderId, shippingOrderId)
        val shippingAddress = ShippingAddress(id = addressId, street = "123 Some St", zipCode = 12345, city = "Some City")
        return CompositeOrder(
            id = compositeOrderId,
            shippingAddress = shippingAddress
        )
    }
}

@DocumentEntity("composite_orders")
data class CompositeOrder(
    @Id
    val id: CompositeOrderId,
    @Indexed
    var shippingAddress: ShippingAddress,
    override var version: Version = Version.NOT_SAVED_YET,
    override var lastUpdated: OffsetDateTime = OffsetDateTime.now(UTC)
) : VersionedEntity<CompositeOrderId, CompositeOrder>

data class CompositeOrderId(val orderId: OrderId, val shippingOrderId: ShippingOrderId)
data class CompositeOrderShippingAddressId(val orderId: OrderId, val shippingOrderId: ShippingOrderId)

data class ShippingAddress(val id: CompositeOrderShippingAddressId, val street: String, val zipCode: Int, val city: String)