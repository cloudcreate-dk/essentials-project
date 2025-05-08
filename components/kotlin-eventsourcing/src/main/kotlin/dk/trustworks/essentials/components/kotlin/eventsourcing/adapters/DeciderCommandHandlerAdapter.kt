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

package dk.trustworks.essentials.components.kotlin.eventsourcing.adapters

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.EventStore
import dk.trustworks.essentials.components.kotlin.eventsourcing.AggregateTypeConfiguration
import dk.trustworks.essentials.components.kotlin.eventsourcing.Decider
import dk.trustworks.essentials.reactive.command.CommandHandler
import dk.trustworks.essentials.shared.types.GenericType
import org.slf4j.LoggerFactory

/**
 * Adapter that allows a [Decider] to be registered with a [dk.trustworks.essentials.reactive.command.CommandBus] as a [CommandHandler]
 * @param decider the decider instance
 * @param aggregateTypeConfiguration The [AggregateTypeConfiguration] that matches the [dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType]
 * that the [decider] can work with
 * @param eventStore The [EventStore] that can fetch and persist Events
 * @see DeciderAndAggregateTypeConfigurator
 */
class DeciderCommandHandlerAdapter<CMD, EVENT>(
    private val decider: Decider<CMD, EVENT>,
    private val aggregateTypeConfiguration: AggregateTypeConfiguration,
    private val eventStore: EventStore
) : CommandHandler {

    private val deciderHandlesCommandOfType: Class<*>? = GenericType.resolveGenericTypeForInterface(
        decider.javaClass,
        Decider::class.java,
        0
    )
    private val deciderClassName = decider::class.qualifiedName
    private val deciderSimpleClassName = decider::class.simpleName
    private val log = LoggerFactory.getLogger(javaClass.name + ".${deciderSimpleClassName}")

    init {
        if (deciderHandlesCommandOfType == null) {
            throw IllegalArgumentException("Couldn't resolve the command type from ${deciderClassName}")
        }
        log.info(
            "Creating {} for {} '{}'",
            CommandHandler::class.simpleName,
            Decider::class.simpleName,
            deciderClassName
        )
    }

    override fun canHandle(commandType: Class<*>?): Boolean {
        return commandType == deciderHandlesCommandOfType
    }

    override fun handle(command: Any): Any? {
        log.trace("Received command '{}'", deciderHandlesCommandOfType)

        var aggregateId = aggregateTypeConfiguration.commandAggregateIdResolver.invoke(command)
        val aggregateEventStream: List<EVENT> =
            if (aggregateId != null) loadAggregateEventStream(aggregateId) else listOf()

        val possibleEventResult = decider.handle(
            command as CMD,
            aggregateEventStream
        )

        if (possibleEventResult == null) {
            log.debug(
                "[{}:{}] Handling handling command '{}' resulted in no events",
                aggregateTypeConfiguration.aggregateType,
                aggregateId ?: "?",
                deciderHandlesCommandOfType
            )
            return null
        }

        if (aggregateId == null) {
            aggregateId = aggregateTypeConfiguration.eventAggregateIdResolver.invoke(possibleEventResult)
            log.debug(
                "[{}:{}] Resolved aggregate id from event '{}' resulting from handling command '{}'",
                aggregateTypeConfiguration.aggregateType,
                aggregateId,
                possibleEventResult!!::class.simpleName,
                deciderHandlesCommandOfType
            )
        }

        log.debug(
            "[{}:{}] Handling handling command '{}' resulted in event '{}' that will be persisted in the {}",
            aggregateTypeConfiguration.aggregateType,
            aggregateId,
            deciderHandlesCommandOfType,
            possibleEventResult!!::class.simpleName,
            EventStore::class.simpleName
        )


        eventStore.appendToStream(
            aggregateTypeConfiguration.aggregateType,
            aggregateId,
            possibleEventResult
        )
        return possibleEventResult
    }

    private fun loadAggregateEventStream(aggregateId: Any): List<EVENT> {
        val potentialAggregateEventStream = eventStore.fetchStream(
            aggregateTypeConfiguration.aggregateType,
            aggregateId
        )
        if (potentialAggregateEventStream.isPresent) {
            val deserializedEvents = potentialAggregateEventStream.get()
                .events()
                .map { it.event().deserialize<EVENT>() }
                .toList()
            log.debug(
                "[{}:{}] Resolved EventStream with {} event(s)",
                aggregateTypeConfiguration.aggregateType,
                aggregateId,
                deserializedEvents.size
            )
            return deserializedEvents
        } else {
            log.debug(
                "[{}:{}] Didn't find an existing EventStream",
                aggregateTypeConfiguration.aggregateType,
                aggregateId
            )
            return listOf()
        }
    }


    override fun toString(): String {
        return "'$deciderSimpleClassName' handling command '${deciderHandlesCommandOfType!!.simpleName}'"
    }
}