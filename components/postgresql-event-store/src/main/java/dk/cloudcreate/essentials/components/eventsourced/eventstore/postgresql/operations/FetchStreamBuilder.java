package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.components.foundation.types.Tenant;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.Optional;

/**
 * Builder for {@link FetchStream}
 *
 * @param <ID> the id type for the aggregate
 */
public class FetchStreamBuilder<ID> {
    private AggregateType    aggregateType;
    private ID               aggregateId;
    private LongRange        eventOrderRange;
    private Optional<Tenant> tenant;

    /**
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @return this builder instance
     */
    public FetchStreamBuilder<ID> setAggregateType(AggregateType aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    /**
     * @param aggregateId the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @return this builder instance
     */
    public FetchStreamBuilder<ID> setAggregateId(ID aggregateId) {
        this.aggregateId = aggregateId;
        return this;
    }

    /**
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     * @return this builder instance
     */
    public FetchStreamBuilder<ID> setEventOrderRange(LongRange eventOrderRange) {
        this.eventOrderRange = eventOrderRange;
        return this;
    }

    /**
     * @param tenant only return events belonging to the specified tenant (if {@link Optional#isPresent()})
     * @return this builder instance
     */
    public FetchStreamBuilder<ID> setTenant(Optional<Tenant> tenant) {
        this.tenant = tenant;
        return this;
    }

    /**
     * @param tenant only return events belonging to the specified tenant (if not null)
     * @return this builder instance
     */
    public FetchStreamBuilder<ID> setTenant(Tenant tenant) {
        this.tenant = Optional.ofNullable(tenant);
        return this;
    }

    /**
     * Builder an {@link FetchStream} instance from the builder properties
     *
     * @return the {@link FetchStream} instance
     */
    public FetchStream<ID> build() {
        return new FetchStream<>(aggregateType, aggregateId, eventOrderRange, tenant);
    }
}