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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer
import dk.cloudcreate.essentials.components.kotlin.eventsourcing.DeciderSupportsAggregateTypeChecker.HandlesCommandsThatInheritsFromCommandType
import dk.cloudcreate.essentials.shared.types.GenericType
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * [AggregateType] configuration which defines how the [dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore]
 * should be configured in order to support event-streams for the specified [aggregateType]
 *
 * If you register your [AggregateTypeConfiguration] in Spring as a **Bean** and combine it with [DeciderSupportsAggregateTypeChecker] then
 * [AggregateType]'s are automatically registered with the [dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore]
 *
 * Example:
 * ```
 * @Configuration
 * class OrdersConfiguration {
 *     companion object {
 *         @JvmStatic
 *         val AGGREGATE_TYPE = AggregateType.of("Orders")
 *     }
 *
 *     @Bean
 *     fun orderAggregateTypeConfiguration(): AggregateTypeConfiguration {
 *         return AggregateTypeConfiguration(AGGREGATE_TYPE,
 *             OrderId::class.java,
 *             AggregateIdSerializer.serializerFor(OrderId::class.java),
 *             DeciderSupportsAggregateTypeChecker.HandlesCommandsThatInheritsFromCommandType(OrderCommand::class),
 *             commandAggregateIdResolver = { cmd -> (cmd as OrderCommand).id },
 *             eventAggregateIdResolver = { e -> (e as OrderEvent).id })
 *     }
 * }
 * ```
 *
 * @param aggregateType The [AggregateType] that is being configured
 * @param aggregateIdType The **Aggregate-Id** type that is used in commands and events relating to the [AggregateType]
 * @param aggregateIdSerializer The [AggregateIdSerializer] capable of serializing/deserializing the **AggregateType-Id**
 * @param deciderSupportsAggregateTypeChecker  A [DeciderSupportsAggregateTypeChecker] instance which given a concrete [Decider] instance is capable of determining
 * if the given [Decider] instance is working with the [AggregateType] configured in this [AggregateTypeConfiguration] - see [DeciderSupportsAggregateTypeChecker.HandlesCommandsThatInheritsFromCommandType]
 * @param commandAggregateIdResolver - A [CommandAggregateIdResolver] which can resolve the optional Aggregate-Id value from an instance of a Command compatible with this [AggregateType]
 * @param eventAggregateIdResolver - A [EventAggregateIdResolver] which can resolve the required Aggregate-Id value from an instance of an Event compatible with this [AggregateType]
 */
data class AggregateTypeConfiguration(
    val aggregateType: AggregateType,
    val aggregateIdType: Class<*>,
    val aggregateIdSerializer: AggregateIdSerializer,
    val deciderSupportsAggregateTypeChecker: DeciderSupportsAggregateTypeChecker,
    val commandAggregateIdResolver: CommandAggregateIdResolver,
    val eventAggregateIdResolver: EventAggregateIdResolver
)

/**
 * Checker strategy that is configured on the [AggregateTypeConfiguration] for a specific [AggregateTypeConfiguration] and [AggregateType] combination.
 * If the [doesDeciderWorkWithThisAggregateType] method returns true for a given [Decider] instance then [Decider] is
 * determined to work with the [AggregateType] that the [AggregateTypeConfiguration] that uses this
 * [HandlesCommandsThatInheritsFromCommandType] object instance is configured with
 * @see dk.cloudcreate.essentials.components.kotlin.eventsourcing.adapters.DeciderAndAggregateTypeConfigurator
 */
fun interface DeciderSupportsAggregateTypeChecker {
    /**
     * Does the provided [decider] work with the [AggregateType] configured in the
     * [AggregateTypeConfiguration] where this [DeciderSupportsAggregateTypeChecker] instance
     * is configured as [AggregateTypeConfiguration.deciderSupportsAggregateTypeChecker]
     */
    fun doesDeciderWorkWithThisAggregateType(decider: Decider<*, *>): Boolean

    /**
     * Checks the first type argument to the concrete [Decider] and check if it inherits
     * from the [baseCommandType] - if it does then the [Decider] is determined to work
     * with the [AggregateType] that the [AggregateTypeConfiguration] that uses this
     * [HandlesCommandsThatInheritsFromCommandType] object instance is configured with
     */
    class HandlesCommandsThatInheritsFromCommandType(private val baseCommandType: KClass<*>) :
        DeciderSupportsAggregateTypeChecker {
        override fun doesDeciderWorkWithThisAggregateType(decider: Decider<*, *>): Boolean {
            var commandTypeTheDeciderHandles = GenericType.resolveGenericTypeForInterface(
                decider.javaClass,
                Decider::class.java,
                0
            )
            if (commandTypeTheDeciderHandles == null) {
                throw IllegalArgumentException("Couldn't resolve command type from '${decider.javaClass}'")
            }
            log.debug(
                "Resolved command type '{}' from '{}' - checking if it inherits from '{}'",
                commandTypeTheDeciderHandles.name,
                decider.javaClass.name,
                baseCommandType.qualifiedName
            )

            return baseCommandType.java.isAssignableFrom(commandTypeTheDeciderHandles)
        }

        companion object {
            @JvmStatic
            private val log = LoggerFactory.getLogger(javaClass.enclosingClass)
        }
    }
}

/**
 * [CommandAggregateIdResolver] which can resolve the optional Aggregate-Id value from an instance of a Command
 */
typealias CommandAggregateIdResolver = (cmd: Any) -> Any?

/**
 * An [EventAggregateIdResolver] can resolve the required Aggregate-Id value from an instance of an Event
 */
typealias EventAggregateIdResolver = (event: Any) -> Any



