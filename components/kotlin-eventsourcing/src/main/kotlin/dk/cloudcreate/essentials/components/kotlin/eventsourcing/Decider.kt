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

package dk.cloudcreate.essentials.components.kotlin.eventsourcing

/**
 * A Decider (aka a command or use-case handler) is a class with a single [handle] method,
 * that can handle a specific concrete [COMMAND] and based on its logic it will decide
 * if **zero** or **one** [EVENT] is returned;
 * or in case it's unable to handle the Command (e.g. due to invariant rules) an Exception is thrown.
 *
 * Command handling SHOULD be **idempotent**, meaning that if a Command has already been handled then
 * the [handle] method MUST return **zero** Events as opposed to throwing an Exception.
 *
 * Example:
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
 *
 *     override fun canHandle(cmd: Any): Boolean {
 *        return cmd is ShipOrder
 *     }
 * }
 * ```
 */
interface Decider<COMMAND, EVENT> {
    /**
     * Handle the [COMMAND] and decide if **zero** or **one** [EVENT] is returned as a result of handling the command
     *
     * The [handle] method MAY also throw Exceptions if it's unable to handle the Command (e.g. due to invariant rules).
     *
     * Command handling SHOULD be **idempotent**, meaning that if a Command has already been handled then
     * the [handle] method MUST return **zero** Events as opposed to throwing an Exception.
     *
     * @param cmd the command to handle
     * @param events the event stream of past events associated with the Aggregate instance that is associated with the command (CAN be empty)
     * @return **zero** or **one** [EVENT] as a result of handling the command
     */
    fun handle(cmd: COMMAND, events: List<EVENT>): EVENT?

    /**
     * Guard method that can check if the specific command is supported by this [Decider] instance
     * @param cmd The command instance that want to check whether this [Decider] instance supports
     * @return true if this [Decider] instance supports the given [cmd] instance
     */
    fun canHandle(cmd: Any): Boolean
}