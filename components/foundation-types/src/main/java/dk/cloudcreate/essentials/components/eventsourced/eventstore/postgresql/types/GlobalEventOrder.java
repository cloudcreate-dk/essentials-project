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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.types.*;

import java.util.List;
import java.util.stream.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The Global Order is a sequential ever-growing number, which that tracks the order in which events have been stored in the EventStore table
 * across all AggregateEventStreams with the same {@link AggregateType}.<br>
 * The first global-event-order has value 1, since this is the initial value for a Postgresql BIGINT IDENTITY column.
 */
public final class GlobalEventOrder extends LongType<GlobalEventOrder> {
    /**
     * Special value that contains the {@link GlobalEventOrder} of the FIRST Event persisted in context of a given {@link AggregateType}
     */
    public static final GlobalEventOrder FIRST_GLOBAL_EVENT_ORDER = GlobalEventOrder.of(1);
    /**
     * Special value that contains the maximum allowed {@link GlobalEventOrder} value
     */
    public static final GlobalEventOrder MAX_GLOBAL_EVENT_ORDER = GlobalEventOrder.of(Long.MAX_VALUE);

    public GlobalEventOrder(Long value) {
        super(value);
    }

    public static GlobalEventOrder of(long value) {
        return new GlobalEventOrder(value);
    }


    public static GlobalEventOrder from(String value) {
        requireNonNull(value, "No value provided");
        return new GlobalEventOrder(Long.parseLong(value));
    }

    /**
     * Convert a list of global events orders (as long) to a List of {@link GlobalEventOrder}'s
     *
     * @param globalEventOrders the global event orders
     * @return the <code>globalEventOrders</code> converted to a List of {@link GlobalEventOrder}'s
     */
    public static List<GlobalEventOrder> of(long... globalEventOrders) {
        return LongStream.of(globalEventOrders)
                         .mapToObj(GlobalEventOrder::of)
                         .collect(Collectors.toList());
    }

    public static LongRange rangeBetween(GlobalEventOrder fromInclusive, GlobalEventOrder toInclusive) {
        return LongRange.between(fromInclusive.longValue(), toInclusive.longValue());
    }

    public static LongRange rangeFrom(GlobalEventOrder fromInclusive) {
        return LongRange.from(fromInclusive.longValue());
    }

    public static LongRange rangeOnly(GlobalEventOrder fromAndToInclusive) {
        return LongRange.only(fromAndToInclusive.longValue());
    }

    public static LongRange rangeFrom(GlobalEventOrder fromInclusive, long rangeLength) {
        return LongRange.from(fromInclusive.longValue(), rangeLength);
    }

    public GlobalEventOrder increment() {
        return new GlobalEventOrder(value + 1);
    }

    public GlobalEventOrder decrement() {
        return new GlobalEventOrder(value - 1);
    }

    public boolean isFirstGlobalEventOrder() {
        return this.equals(FIRST_GLOBAL_EVENT_ORDER);
    }
}