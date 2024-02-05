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

package dk.cloudcreate.essentials.components.kotlin.eventsourcing

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType
import kotlin.reflect.KClass

/**
 * Exception that can be thrown in case an Event is received out of order for a
 * View/[dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.EventProcessor]/etc.
 */
class EventOutOfOrderException(
    aggregateType: AggregateType,
    aggregateId: String,
    eventType: KClass<*>,
    expectedEventOrder: Long,
    actualEventOrder: Long
) : RuntimeException("$aggregateType event ${eventType.simpleName}, associated with aggregate-id $aggregateId, was received out of order." +
        "Expected eventOrder $expectedEventOrder but actual eventOrder was $actualEventOrder") {
}