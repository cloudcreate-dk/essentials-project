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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.Tenant;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Operation matching the {@link EventStore#loadEventsByGlobalOrder(AggregateType, LongRange, List, Optional)}  method call<br>
 * Operation also matches {@link EventStoreInterceptor#intercept(LoadEventsByGlobalOrder, EventStoreInterceptorChain)}
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class LoadEventsByGlobalOrder {
    /**
     * the aggregate type that the underlying events are associated with
     */
    public final AggregateType          aggregateType;
    private      LongRange              globalEventOrderRange;
    private      List<GlobalEventOrder> includeAdditionalGlobalOrders;
    private      Optional<Tenant>       onlyIncludeEventIfItBelongsToTenant;

    /**
     * Create a new builder that produces a new {@link LoadEventsByGlobalOrder} instance
     *
     * @return a new {@link LoadEventsByGlobalOrderBuilder} instance
     */
    public static LoadEventsByGlobalOrderBuilder builder() {
        return new LoadEventsByGlobalOrderBuilder();
    }

    /**
     * Load all events, belonging to the specified <code>onlyIncludeEventIfItBelongsToTenant</code> option, and which are relating to the provided <code>aggregateType</code> by their {@link PersistedEvent#globalEventOrder()}
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param globalEventOrderRange               the range of {@link GlobalEventOrder}'s to include in the stream
     * @param includeAdditionalGlobalOrders       a list of additional global orders (typically outside the <code>globalOrderRange</code>) that you want to include additionally<br>
     *                                            May be null or empty if no additional events should be loaded outside the <code>globalOrderRange</code>
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     */
    public LoadEventsByGlobalOrder(AggregateType aggregateType,
                                   LongRange globalEventOrderRange,
                                   List<GlobalEventOrder> includeAdditionalGlobalOrders,
                                   Optional<Tenant> onlyIncludeEventIfItBelongsToTenant) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.globalEventOrderRange = requireNonNull(globalEventOrderRange, "No globalOrderRange provided");
        this.includeAdditionalGlobalOrders = includeAdditionalGlobalOrders;
        this.onlyIncludeEventIfItBelongsToTenant = requireNonNull(onlyIncludeEventIfItBelongsToTenant, "No onlyIncludeEventIfItBelongsToTenant option provided");
    }

    /**
     * @return the aggregate type that the underlying events are associated with
     */
    public AggregateType getAggregateType() {
        return aggregateType;
    }

    /**
     * @return the range of {@link GlobalEventOrder}'s to include in the stream
     */
    public LongRange getGlobalEventOrderRange() {
        return globalEventOrderRange;
    }

    /**
     * @return if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     */
    public Optional<Tenant> getOnlyIncludeEventIfItBelongsToTenant() {
        return onlyIncludeEventIfItBelongsToTenant;
    }

    /**
     * @param globalEventOrderRange the range of {@link GlobalEventOrder}'s to include in the stream
     */
    public void setGlobalEventOrderRange(LongRange globalEventOrderRange) {
        this.globalEventOrderRange = requireNonNull(globalEventOrderRange, "No globalOrderRange provided");
    }

    /**
     * @return a list of additional global orders (typically outside the <code>globalOrderRange</code>) that you want to include additionally<br>
     * May be null or empty if no additional events should be loaded outside the <code>globalOrderRange</code>
     */
    public List<GlobalEventOrder> getIncludeAdditionalGlobalOrders() {
        return includeAdditionalGlobalOrders;
    }

    /**
     * @param includeAdditionalGlobalOrders a list of additional global orders (typically outside the <code>globalOrderRange</code>) that you want to include additionally<br>
     *                                      May be null or empty if no additional events should be loaded outside the <code>globalOrderRange</code>
     */
    public LoadEventsByGlobalOrder setIncludeAdditionalGlobalOrders(List<GlobalEventOrder> includeAdditionalGlobalOrders) {
        this.includeAdditionalGlobalOrders = includeAdditionalGlobalOrders;
        return this;
    }

    /**
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     */
    public LoadEventsByGlobalOrder setOnlyIncludeEventIfItBelongsToTenant(Optional<Tenant> onlyIncludeEventIfItBelongsToTenant) {
        this.onlyIncludeEventIfItBelongsToTenant = requireNonNull(onlyIncludeEventIfItBelongsToTenant, "No onlyIncludeEventIfItBelongsToTenant option provided");
        return this;
    }

    /**
     * @param onlyIncludeEventIfItBelongsToTenant if non-null then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     */
    public LoadEventsByGlobalOrder setOnlyIncludeEventIfItBelongsToTenant(Tenant onlyIncludeEventIfItBelongsToTenant) {
        this.onlyIncludeEventIfItBelongsToTenant = Optional.ofNullable(onlyIncludeEventIfItBelongsToTenant);
        return this;
    }

    @Override
    public String toString() {
        return "LoadEventsByGlobalOrder{" +
                "aggregateType=" + aggregateType +
                ", globalEventOrderRange=" + globalEventOrderRange +
                ", includeAdditionalGlobalOrders=" + includeAdditionalGlobalOrders +
                ", onlyIncludeEventIfItBelongsToTenant=" + onlyIncludeEventIfItBelongsToTenant +
                '}';
    }
}
