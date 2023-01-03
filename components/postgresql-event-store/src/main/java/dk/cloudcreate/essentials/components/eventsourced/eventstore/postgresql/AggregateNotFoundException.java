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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;


import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class AggregateNotFoundException extends EventStoreException {
    public final Object        aggregateId;
    public final Class<?>      aggregateRootImplementationType;
    public final AggregateType aggregateType;

    public AggregateNotFoundException(Object aggregateId, Class<?> aggregateRootImplementationType, AggregateType aggregateType) {
        super(generateMessage(aggregateId, aggregateRootImplementationType, aggregateType));
        this.aggregateId = requireNonNull(aggregateId, "You must supply an aggregateId");
        this.aggregateRootImplementationType = requireNonNull(aggregateRootImplementationType, "You must supply an aggregateRootImplementationType");
        this.aggregateType = requireNonNull(aggregateType, "You must supply an aggregateType");
    }

    private static String generateMessage(Object aggregateId, Class<?> aggregateRootImplementationType, AggregateType aggregateType) {
        return msg("Couldn't find a '{}' aggregate root with Id '{}' belonging to the aggregateType '{}'",
                   aggregateRootImplementationType.getName(),
                   aggregateId,
                   aggregateType);
    }

    public AggregateNotFoundException(Object aggregateId, Class<?> aggregateRootImplementationType, AggregateType aggregateType, Exception cause) {
        super(generateMessage(aggregateId, aggregateRootImplementationType, aggregateType), cause);
        this.aggregateId = requireNonNull(aggregateId, "You must supply an aggregateId");
        this.aggregateRootImplementationType = requireNonNull(aggregateRootImplementationType, "You must supply an aggregateRootImplementationType");
        this.aggregateType = requireNonNull(aggregateType, "You must supply an aggregateType");
    }
}
