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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.Tenant;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.*;

/**
 * Builder for {@link LoadEventsByGlobalOrder}
 */
public class LoadEventsByGlobalOrderBuilder {
    private AggregateType          aggregateType;
    private LongRange              globalEventOrderRange;
    private List<GlobalEventOrder> includeAdditionalGlobalOrders       = List.of();
    private Optional<Tenant>       onlyIncludeEventIfItBelongsToTenant = Optional.empty();

    /**
     * @param aggregateType the aggregate type that the underlying events are associated with
     * @return this builder instance
     */
    public LoadEventsByGlobalOrderBuilder setAggregateType(AggregateType aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    /**
     * @param globalEventOrderRange the range of {@link GlobalEventOrder}'s to include in the stream
     * @return this builder instance
     */
    public LoadEventsByGlobalOrderBuilder setGlobalEventOrderRange(LongRange globalEventOrderRange) {
        this.globalEventOrderRange = globalEventOrderRange;
        return this;
    }

    /**
     * @param includeAdditionalGlobalOrders a list of additional global orders (typically outside the <code>globalOrderRange</code>) that you want to include additionally<br>
     *                                      May be null or empty if no additional events should be loaded outside the <code>globalOrderRange</code>
     * @return this builder instance
     */
    public LoadEventsByGlobalOrderBuilder setIncludeAdditionalGlobalOrders(List<GlobalEventOrder> includeAdditionalGlobalOrders) {
        this.includeAdditionalGlobalOrders = includeAdditionalGlobalOrders;
        return this;
    }

    /**
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @return this builder instance
     */
    public LoadEventsByGlobalOrderBuilder setOnlyIncludeEventIfItBelongsToTenant(Optional<Tenant> onlyIncludeEventIfItBelongsToTenant) {
        this.onlyIncludeEventIfItBelongsToTenant = onlyIncludeEventIfItBelongsToTenant;
        return this;
    }

    /**
     * @param onlyIncludeEventIfItBelongsToTenant if non-null then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @return this builder instance
     */
    public LoadEventsByGlobalOrderBuilder setOnlyIncludeEventIfItBelongsToTenant(Tenant onlyIncludeEventIfItBelongsToTenant) {
        this.onlyIncludeEventIfItBelongsToTenant = Optional.ofNullable(onlyIncludeEventIfItBelongsToTenant);
        return this;
    }

    /**
     * Builder an {@link LoadEventsByGlobalOrder} instance from the builder properties
     * @return the {@link LoadEventsByGlobalOrder} instance
     */
    public LoadEventsByGlobalOrder build() {
        return new LoadEventsByGlobalOrder(aggregateType, globalEventOrderRange, includeAdditionalGlobalOrders, onlyIncludeEventIfItBelongsToTenant);
    }
}