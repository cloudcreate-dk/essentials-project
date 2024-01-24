package dk.cloudcreate.essentials.components.kotlin.eventsourcing.test

import dk.cloudcreate.essentials.components.foundation.types.RandomIdGenerator
import dk.cloudcreate.essentials.components.kotlin.eventsourcing.Decider
import dk.cloudcreate.essentials.types.CharSequenceType
import org.junit.jupiter.api.Test

class GivenWhenThenScenarioTest {
    @Test
    fun `Create an Order`() {
        val scenario = GivenWhenThenScenario(CreateOrderDecider())

        var orderId = OrderId.random()
        scenario
            .given()
            .when_(CreateOrder(orderId))
            .then_(OrderCreated(orderId))
    }

    @Test
    fun `Create an Order twice`() {
        val scenario = GivenWhenThenScenario(CreateOrderDecider())

        var orderId = OrderId.random()
        scenario
            .given(OrderCreated(orderId))
            .when_(CreateOrder(orderId))
            .thenExpectNoEvent()
    }

    @Test
    fun `Accept an Order`() {
        val scenario = GivenWhenThenScenario(AcceptOrderDecider())

        var orderId = OrderId.random()
        scenario
            .given(OrderCreated(orderId))
            .when_(AcceptOrder(orderId))
            .then_(OrderAccepted(orderId))
    }

    @Test
    fun `Accept an Order twice`() {
        val scenario = GivenWhenThenScenario(AcceptOrderDecider())

        var orderId = OrderId.random()
        scenario
            .given(
                OrderCreated(orderId),
                OrderAccepted(orderId)
            )
            .when_(AcceptOrder(orderId))
            .thenExpectNoEvent()
    }

    @Test
    fun `Accept an Order that has not been created`() {
        val scenario = GivenWhenThenScenario(AcceptOrderDecider())

        var orderId = OrderId.random()
        scenario
            .given()
            .when_(AcceptOrder(orderId))
            .thenFailsWithExceptionType(RuntimeException::class)
    }

    @Test
    fun `Ship an Order`() {
        val scenario = GivenWhenThenScenario(ShipOrderDecider())

        var orderId = OrderId.random()
        scenario
            .given(
                OrderCreated(orderId),
                OrderAccepted(orderId)
            )
            .when_(ShipOrder(orderId))
            .then_(OrderShipped(orderId))
    }

    @Test
    fun `try to Ship an Order that hasn't been Accepted`() {
        val scenario = GivenWhenThenScenario(ShipOrderDecider())

        var orderId = OrderId.random()
        scenario
            .given(
                OrderCreated(orderId),
            )
            .when_(ShipOrder(orderId))
            .thenFailsWithException(RuntimeException("Cannot ship an order that hasn't been accepted"))
    }
}

class CreateOrderDecider : Decider<CreateOrder, OrderEvent> {
    override fun handle(cmd: CreateOrder, events: List<OrderEvent>): OrderEvent? {
        if (events.isEmpty()) {
            return OrderCreated(cmd.id)
        }
        return null
    }
}

class AcceptOrderDecider : Decider<AcceptOrder, OrderEvent> {
    override fun handle(cmd: AcceptOrder, events: List<OrderEvent>): OrderEvent? {
        if (events.isEmpty()) {
            throw RuntimeException("Cannot accept an order that hasn't been created")
        }
        if (events.any { it is OrderAccepted }) {
            // Already accepted - idempotent handling
            return null
        }
        return OrderAccepted(cmd.id)
    }
}

class ShipOrderDecider : Decider<ShipOrder, OrderEvent> {
    override fun handle(cmd: ShipOrder, events: List<OrderEvent>): OrderEvent? {
        if (events.isEmpty()) {
            throw RuntimeException("Cannot accept an order that hasn't been created")
        }
        if (events.any { it is OrderShipped}) {
            // Already shipped - idempotent handling
            return null
        }
        if (!events.any { it is OrderAccepted }) {
            throw RuntimeException("Cannot ship an order that hasn't been accepted")
        }
        return OrderShipped(cmd.id)
    }
}

interface OrderCommand {
    val id: OrderId
}

interface OrderEvent {
    val id: OrderId
}

data class CreateOrder(override val id: OrderId) : OrderCommand
data class AcceptOrder(override val id: OrderId) : OrderCommand
data class ShipOrder(override val id: OrderId) : OrderCommand

data class OrderCreated(override val id: OrderId) : OrderEvent
data class OrderAccepted(override val id: OrderId) : OrderEvent
data class OrderShipped(override val id: OrderId) : OrderEvent

class OrderId constructor(value: CharSequence) : CharSequenceType<OrderId>(value) {
    companion object {
        fun random(): OrderId {
            return OrderId(RandomIdGenerator.generate())
        }

        fun of(id: CharSequence): OrderId {
            return OrderId(id)
        }
    }
}