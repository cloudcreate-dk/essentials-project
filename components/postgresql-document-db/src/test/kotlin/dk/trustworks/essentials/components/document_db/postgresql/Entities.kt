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

import dk.trustworks.essentials.components.document_db.Version
import dk.trustworks.essentials.components.document_db.VersionedEntity
import dk.trustworks.essentials.components.document_db.annotations.DocumentEntity
import dk.trustworks.essentials.components.document_db.annotations.Id
import dk.trustworks.essentials.components.document_db.annotations.Indexed
import dk.trustworks.essentials.components.foundation.types.RandomIdGenerator
import dk.trustworks.essentials.kotlin.types.Amount
import dk.trustworks.essentials.kotlin.types.StringValueType
import dk.trustworks.essentials.kotlin.types.jdbi.StringValueTypeArgumentFactory
import dk.trustworks.essentials.kotlin.types.jdbi.StringValueTypeColumnMapper
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC

@DocumentEntity("orders")
data class Order(
    @Id
    val orderId: OrderId,
    var description: String,
    var amount: Amount,
    var additionalProperty: Int,
    var orderDate: LocalDateTime,
    @Indexed
    var personName: String,
    var invoiceAddress: Address,
    var contactDetails: ContactDetails,
    override var version: Version = Version.NOT_SAVED_YET,
    override var lastUpdated: OffsetDateTime = OffsetDateTime.now(UTC)
) : VersionedEntity<OrderId, Order>

data class ContactDetails(val name: String, val address: Address, val phoneNumbers: List<String>)
data class Address(val street: String, val zipCode: Int, val city: String)

@JvmInline
value class OrderId(override val value: String) : StringValueType<OrderId> {
    companion object {
        fun random(): OrderId = OrderId(RandomIdGenerator.generate())
    }
}

@JvmInline
value class ProductId(override val value: String) : StringValueType<ProductId>

@JvmInline
value class VisitId(override val value: String) : StringValueType<VisitId>

@JvmInline
value class ShippingOrderId(override val value: String) : StringValueType<ShippingOrderId> {
    companion object {
        fun random(): ShippingOrderId = ShippingOrderId(RandomIdGenerator.generate())
    }
}

class OrderIdArgumentFactory : StringValueTypeArgumentFactory<OrderId>()
class OrderIdColumnMapper : StringValueTypeColumnMapper<OrderId>()
class ProductIdArgumentFactory : StringValueTypeArgumentFactory<ProductId>()
class ProductIdColumnMapper : StringValueTypeColumnMapper<ProductId>()
class VisitIdArgumentFactory : StringValueTypeArgumentFactory<VisitId>()
class VisitIdColumnMapper : StringValueTypeColumnMapper<VisitId>()
class ShippingOrderIdArgumentFactory : StringValueTypeArgumentFactory<ShippingOrderId>()
class ShippingOrderIdColumnMapper : StringValueTypeColumnMapper<ShippingOrderId>()


@DocumentEntity("products")
data class Product(
    @Id val productId: ProductId,
    var name: String,
    var price: Double,
    var category: String,
    var stock: Int,
    override var version: Version = Version.NOT_SAVED_YET,
    override var lastUpdated: OffsetDateTime = OffsetDateTime.now(UTC)
) : VersionedEntity<ProductId, Product>

@DocumentEntity("visits")
data class Visit(
    @Id val visitId: VisitId,
    var visitorName: String,
    var visitDate: LocalDateTime,
    var location: String,
    override var version: Version = Version.NOT_SAVED_YET,
    override var lastUpdated: OffsetDateTime = OffsetDateTime.now(UTC)
) : VersionedEntity<VisitId, Visit>

@DocumentEntity("shipping_orders")
data class ShippingOrder(
    @Id val shippingOrderId: ShippingOrderId,
    var orderReference: String,
    var shippingDate: LocalDateTime,
    var destination: Address,
    var status: String,
    override var version: Version = Version.NOT_SAVED_YET,
    override var lastUpdated: OffsetDateTime = OffsetDateTime.now(UTC)
) : VersionedEntity<ShippingOrderId, ShippingOrder>