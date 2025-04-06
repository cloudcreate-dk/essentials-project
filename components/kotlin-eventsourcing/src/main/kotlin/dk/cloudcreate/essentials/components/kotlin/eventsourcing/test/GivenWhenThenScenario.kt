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

package dk.cloudcreate.essentials.components.kotlin.eventsourcing.test

import dk.cloudcreate.essentials.components.kotlin.eventsourcing.Decider
import java.util.function.Consumer
import kotlin.reflect.KClass

/**
 * Single use Given/When/Then scenario runner and asserter
 *
 * Assertions exceptions thrown from [then_], [thenExpectNoEvent], [thenFailsWithException], [thenFailsWithExceptionType]
 * are all subclasses of [AssertionException]
 *
 * Example:
 * ```
 * val scenario = GivenWhenThenScenario(ShipOrderDecider())
 *
 * var orderId = OrderId.random()
 * scenario
 *     .given(
 *         OrderCreated(orderId),
 *         OrderAccepted(orderId)
 *     )
 *     .when_(ShipOrder(orderId))
 *     .then_(OrderShipped(orderId))
 * ```
 * And [Decider] example
 * ```
 * class ShipOrderDecider : Decider<ShipOrder, OrderEvent> {
 *     override fun handle(cmd: ShipOrder, events: List<OrderEvent>): OrderEvent? {
 *         if (events.isEmpty()) {
 *             throw RuntimeException("Cannot accept an order that hasn't been created")
 *         }
 *         if (events.any { it is OrderShipped}) {
 *             // Already shipped - idempotent handling
 *             return null
 *         }
 *         if (!events.any { it is OrderAccepted }) {
 *             throw RuntimeException("Cannot ship an order that hasn't been accepted")
 *         }
 *         return OrderShipped(cmd.id)
 *     }
 * }
 * ```
 */
class GivenWhenThenScenario<CMD, EVENT>(val decider: Decider<CMD, EVENT>) {
    var _givenEvents: List<EVENT> = listOf()
    var _whenCommand: CMD? = null
    var _actualEvent: EVENT? = null
    lateinit var _expectException: Exception
    lateinit var _expectedExceptionType: KClass<out Exception>

    /**
     * Set up the test scenario by providing past events that will be provided to
     * the [Decider.handle] method as an event stream.
     *
     * This step is also known as the **Arrange** step in Arrange, Act, Assert
     *
     * In case this method isn't called, then the default value for [_givenEvents] is an empty list
     * @param events the past events related to the aggregate instance (CAN be empty)
     * @return this [GivenWhenThenScenario] instance
     */
    fun given(vararg events: EVENT): GivenWhenThenScenario<CMD, EVENT> {
        _givenEvents = events.toList()
        return this
    }

    /**
     * Define the command that will be supplied to the [Decider.handle] as the command when one of the **then** methods
     * are called
     *
     * This step is also known as the **Act** step in Arrange, Act, Assert
     * @param cmd the command
     * @return this [GivenWhenThenScenario] instance
     */
    fun when_(cmd: CMD): GivenWhenThenScenario<CMD, EVENT> {
        _whenCommand = cmd
        return this
    }

    /**
     * Define the expected event outcome when [GivenWhenThenScenario] is calling the [Decider.handle] with the command
     * provided in [when_] and past events provided in [given]
     *
     * This step is also known as the **Assert** step in Arrange, Act, Assert
     * @param expectedEvent The event we expect to be returned from the [Decider.handle] method as a result of
     * handling the [_whenCommand] using the [_givenEvents] past events
     * @return this [GivenWhenThenScenario] instance
     * @throws DidNotExpectAnEventException
     * @throws ExpectedAnEventButDidGetAnyEventException
     * @throws ActualAndExpectedEventsAreNotEqualExcepted
     * @throws FailedWithUnexpectException
     */
    fun then_(expectedEvent: EVENT?): GivenWhenThenScenario<CMD, EVENT> {
        if (_whenCommand == null) throw NoCommandProvidedException()
        try {
            _actualEvent = decider.handle(_whenCommand!!, _givenEvents)
            if (_actualEvent != expectedEvent) {
                if (expectedEvent == null) {
                    throw DidNotExpectAnEventException(_actualEvent!!)
                }
                if (_actualEvent == null) {
                    throw ExpectedAnEventButDidGetAnyEventException(expectedEvent)
                }
                throw ActualAndExpectedEventsAreNotEqualExcepted(expectedEvent, _actualEvent!!)
            }
            return this
        } catch (e: Exception) {
            throw FailedWithUnexpectException(e)
        }
    }

    /**
     * Define the expected event outcome when [GivenWhenThenScenario] is calling the [Decider.handle] with the command
     * provided in [when_] and past events provided in [given]
     *
     * Example:
     * ```
     * scenario
     *    .given()
     *    .when_(
     *        RegisterDocument(
     *            id = id,
     *            attributes = attributes,
     *            payload = payload
     *        )
     *    )
     *    .thenAssert { actualEvent ->
     *        assertThat(actualEvent).isNotNull
     *        assertThat(actualEvent).isInstanceOf(DocumentRegistered::class.java)
     *
     *        val documentRegistered = actualEvent as DocumentRegistered
     *        assertThat(documentRegistered.id).isEqualTo(id)
     *        assertThat(documentRegistered.version).isEqualTo(DocumentVersion.FIRST)
     *        assertThat(documentRegistered.attributes).isEqualTo(attributes)
     *        assertThat(documentRegistered.registeredAt)
     *            .isAfter(now)
     *            .isBefore(now.plus(Duration.ofSeconds(1)))
     *    }
     * ```
     *
     * This step is also known as the **Assert** step in Arrange, Act, Assert
     * @param actualEventAsserter A [Consumer] that will receive the **actual** event that resulted from handling
     * handling the [_whenCommand] using the [_givenEvents] past events. This [Consumer] is expected to manually assert
     * the content of the actual event
     * @return this [GivenWhenThenScenario] instance
     */
    fun thenAssert(actualEventAsserter: Consumer<EVENT?>): GivenWhenThenScenario<CMD, EVENT> {
        if (_whenCommand == null) throw NoCommandProvidedException()
        try {
            _actualEvent = decider.handle(_whenCommand!!, _givenEvents)
            actualEventAsserter.accept(_actualEvent)
            return this
        } catch (e: Exception) {
            throw FailedWithUnexpectException(e)
        }
    }

    /**
     * Define that we don't expect any events outcome when [GivenWhenThenScenario]  is calling the [Decider.handle] with the command
     * provided in [when_] and past events provided in [given]
     *
     * Example:
     * ```
     * scenario
     *    .given(
     *        DocumentRegistered(
     *            id = id,
     *            version = DocumentVersion.FIRST,
     *            attributes = attributes,
     *            registeredAt = now
     *        )
     *    )
     *    .when_(
     *        RegisterDocument(
     *            id = id,
     *            attributes = attributes,
     *            payload = payload
     *        )
     *    )
     *    .thenExpectNoEvent()
     * ```
     *
     * This step is also known as the **Assert** step in Arrange, Act, Assert
     * @param expectedEvent The event we expect to be returned from the [Decider.handle] method as a result of
     * handling the [_whenCommand] using the [_givenEvents] past events
     * @return this [GivenWhenThenScenario] instance
     * @throws DidNotExpectAnEventException
     * @throws ExpectedAnEventButDidGetAnyEventException
     * @throws ActualAndExpectedEventsAreNotEqualExcepted
     * @throws FailedWithUnexpectException
     */
    fun thenExpectNoEvent(): GivenWhenThenScenario<CMD, EVENT> {
        then_(null)
        return this
    }

    /**
     * Define that we expect the scenario to fail with an [expectedException] of a given [Exception]
     * instance when the [GivenWhenThenScenario]  is calling the [Decider.handle] with the command
     * provided in [when_] and past events provided in [given]
     *
     * Example:
     * ```
     * scenario
     *    .given(
     *    )
     *    .when_(
     *        ChangeProductPrice(
     *            productId,
     *            price
     *        )
     *    )
     *    .thenFailsWithException(ProductHasNotBeenAddedException(productId))
     * ```
     *ﬁ
     * This step is also known as the **Assert** step in Arrange, Act, Assert
     * @param expectedException The exception that we expect the [Decider.handle] to throw
     * when handling the [_whenCommand] using the [_givenEvents] past events
     * @return this [GivenWhenThenScenario] instance
     * @throws ExpectToFailWithAnExceptionButNoneWasThrown
     * @throws ActualExceptionIsNotEqualToExpectedException
     */
    fun thenFailsWithException(expectedException: Exception): GivenWhenThenScenario<CMD, EVENT> {
        this._expectException = expectedException
        try {
            decider.handle(_whenCommand!!, _givenEvents)
            throw ExpectToFailWithAnExceptionButNoneWasThrown(expectedException)
        } catch (actualException: Exception) {
            if (actualException::class != expectedException::class) {
                throw ActualExceptionIsNotEqualToExpectedException(expectedException, actualException)
            }
            // TODO: Allow the call to specify how exception instances should be compared
            if (actualException.message != expectedException.message) {
                throw ActualExceptionIsNotEqualToExpectedException(expectedException, actualException)
            }
            return this
        }
    }

    /**
     * Define that we expect the scenario to fail with an [expectedExceptionType]  of a specific [Exception] type
     * when the [GivenWhenThenScenario]  is calling the [Decider.handle] with the command
     * provided in [when_] and past events provided in [given]
     *
     * Example:
     * ```
     * scenario
     *     .given(
     *         ProductAdded(
     *             productId,
     *             productName,
     *             price
     *         )
     *     )
     *     .when_(
     *         AddProduct(
     *             ProductId.random(), // Different ProductId
     *             productName,
     *             price
     *         )
     *     )
     *     .thenFailsWithExceptionType(RuntimeException::class)
     * ```
     *
     * This step is also known as the **Assert** step in Arrange, Act, Assert
     * @param expectedException The exception that we expect the [Decider.handle] to throw
     * when handling the [_whenCommand] using the [_givenEvents] past events
     * @return this [GivenWhenThenScenario] instance
     * @throws ExpectToFailWithAnExceptionButNoneWasThrown
     * @throws ActualExceptionIsNotEqualToExpectedException
     */
    fun thenFailsWithExceptionType(expectedExceptionType: KClass<out Exception>): GivenWhenThenScenario<CMD, EVENT> {
        this._expectedExceptionType = expectedExceptionType
        try {
            decider.handle(_whenCommand!!, _givenEvents)
            throw ExpectToFailWithAnExceptionTypeButNoneWasThrown(expectedExceptionType)
        } catch (actualException: Exception) {
            val actualExceptionType = actualException::class
            if (actualExceptionType != expectedExceptionType) {
                throw ActualExceptionTypeIsNotEqualToExpectedException(expectedExceptionType, actualException)
            }
            return this
        }
    }
}

abstract class AssertionException(msg: String?, e: Exception?) : RuntimeException(msg, e) {
    constructor(exception: Exception) : this(null, exception)
    constructor(msg: String) : this(msg, null)
    constructor() : this(null, null)
}

class NoCommandProvidedException : AssertionException()
class FailedWithUnexpectException(val unexpectedException: Exception) : AssertionException(unexpectedException)
class ExpectToFailWithAnExceptionButNoneWasThrown(val expectedException: Exception) :
    AssertionException(expectedException)

class ExpectToFailWithAnExceptionTypeButNoneWasThrown(val expectedExceptionType: KClass<out Exception>) :
    AssertionException("No exception thrown. Expected exception of type $expectedExceptionType")

class ActualExceptionTypeIsNotEqualToExpectedException(
    val expectedExceptionType: KClass<out Exception>,
    val actualException: Exception
) : AssertionException("Expected exception of type $expectedExceptionType, but got", actualException)

class ActualExceptionIsNotEqualToExpectedException(val expectedException: Exception, val actualException: Exception) :
    AssertionException("Actual exception ${actualException::class.simpleName} is not equal to expected exception ${expectedException::class.simpleName} ")

class DidNotExpectAnEventException(val actualEvent: Any) : AssertionException("Did not expect event: $actualEvent")
class ExpectedAnEventButDidGetAnyEventException(val expectedEvent: Any) :
    AssertionException("Did not get an event. Expected event: $expectedEvent")

class ActualAndExpectedEventsAreNotEqualExcepted(val expectedEvent: Any, val actualEvent: Any) :
    AssertionException("Got actual event: $actualEvent, but expected event: $expectedEvent")