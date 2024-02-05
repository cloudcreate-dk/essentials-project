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

package dk.cloudcreate.essentials.components.kotlin.eventsourcing.adapters

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType
import dk.cloudcreate.essentials.components.kotlin.eventsourcing.AggregateTypeConfiguration
import dk.cloudcreate.essentials.components.kotlin.eventsourcing.Decider
import dk.cloudcreate.essentials.reactive.command.CommandBus
import dk.cloudcreate.essentials.reactive.command.CommandHandler
import dk.cloudcreate.essentials.shared.types.GenericType
import org.slf4j.LoggerFactory

/**
 * Configurator that registers all [aggregateTypeConfigurations] with the [eventStore]
 * and all [deciders] with the [CommandBus] wrapped in a [DeciderCommandHandlerAdapter]
 *
 * If you register this class as a **Bean** in Spring then it will automatically handle the event sourcing
 * and [Decider] configuration:
 *
 * ```
 * @Bean
 * fun deciderAndAggregateTypeConfigurator(eventStore: ConfigurableEventStore<*>,
 *                                         commandBus: CommandBus,
 *                                         aggregateTypeConfigurations: List<AggregateTypeConfiguration>,
 *                                         deciders: List<Decider<*, *>>) : DeciderAndAggregateTypeConfigurator {
 *     return DeciderAndAggregateTypeConfigurator(eventStore, commandBus, aggregateTypeConfigurations, deciders)
 * }
 * ```
 * @param eventStore The [EventStore] that can fetch and persist events
 * @param commandBus The [CommandBus] where all [Decider]'s in the [deciders] list will be registered as [CommandHandler]
 * @param aggregateTypeConfigurations The [AggregateTypeConfiguration]'s - these are registered with the [EventStore]
 * to ensure that the [EventStore] is configured to handle event streams related to the [AggregateType]'s
 * The [AggregateTypeConfiguration.deciderSupportsAggregateTypeChecker] is used to determine which [AggregateType]
 * each [Decider] works with
 * @param deciders All the [Decider]'s that should be as [CommandHandler]'s on the [commandBus]
 */
class DeciderAndAggregateTypeConfigurator(
    private val eventStore: ConfigurableEventStore<*>,
    private val commandBus: CommandBus,
    private val aggregateTypeConfigurations: List<AggregateTypeConfiguration>,
    private val deciders: List<Decider<*, *>>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        addAggregateTypeConfigurationsToTheEventStore();
        registerDecidersAsCommandHandlersOnTheCommandBus();
    }

    private fun addAggregateTypeConfigurationsToTheEventStore() {
        log.info(
            "Registering {} {}(s) with the EventStore",
            aggregateTypeConfigurations.size,
            AggregateTypeConfiguration::class.simpleName
        )

        aggregateTypeConfigurations.forEach {
            log.info(
                """
                    
                ============================================================================================================================================================================================
                Registering {} for '{}' with aggregateType '{}' to the EventStore
                ============================================================================================================================================================================================
                """.trimIndent(),
                AggregateTypeConfiguration::class.simpleName,
                it.aggregateType,
                it.aggregateIdType.simpleName
            )
            eventStore.addAggregateEventStreamConfiguration(
                it.aggregateType,
                it.aggregateIdSerializer
            )
            log.info("" +
                    "============================================================================================================================================================================================")
        }
    }

    private fun registerDecidersAsCommandHandlersOnTheCommandBus() {
        log.info(
            """
                
            ============================================================================================================================================================================================    
            Registering {} {}(s) as {}(s) on the {}
            {}
            ============================================================================================================================================================================================
            """.trimIndent(),
            deciders.size,
            Decider::class.simpleName,
            CommandHandler::class.simpleName,
            commandBus::class.simpleName,
            deciders.map { it::class.simpleName }
        )

        deciders.forEach { decider ->
            var matchingAggregateTypes = aggregateTypeConfigurations.filter {
                it.deciderSupportsAggregateTypeChecker.doesDeciderWorkWithThisAggregateType(decider)
            }
            if (matchingAggregateTypes.isEmpty()) {
                throw IllegalStateException(
                    "Couldn't resolve which ${AggregateType::class.simpleName} that ${Decider::class.simpleName}" +
                            " '${decider::class.qualifiedName}' works with"
                )
            }
            if (matchingAggregateTypes.size > 1) {
                throw IllegalStateException(
                    "Resolved ${matchingAggregateTypes.size} ${AggregateType::class.simpleName}(s) " +
                            "that ${Decider::class.simpleName} '${decider::class.qualifiedName}' works with: ${matchingAggregateTypes.map { it.aggregateType }}"
                )
            }

            log.info(
                "Registering {} '{}' as {}, wrapped in a {}, handling command '{}' (CommandBus: {})",
                Decider::class.simpleName,
                decider.javaClass.name,
                CommandHandler::class.simpleName,
                DeciderCommandHandlerAdapter::class.simpleName,
                GenericType.resolveGenericTypeForInterface(decider::class.java, Decider::class.java, 0),
                commandBus::class.simpleName,
            )
            commandBus.addCommandHandler(
                DeciderCommandHandlerAdapter(
                    decider,
                    matchingAggregateTypes.first(),
                    eventStore
                )
            )
            log.info("" +
                    "============================================================================================================================================================================================")
        }


    }

}