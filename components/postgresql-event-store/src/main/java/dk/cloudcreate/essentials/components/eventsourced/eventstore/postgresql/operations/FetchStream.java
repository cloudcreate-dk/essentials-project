package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.components.foundation.types.Tenant;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Operation matching the {@link EventStore#fetchStream(AggregateType, Object, LongRange, Optional)} method call<br>
 * Operation also matches {@link EventStoreInterceptor#intercept(FetchStream, EventStoreInterceptorChain)}
 *
 * @param <ID> the id type for the aggregate
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public
class FetchStream<ID> {
    /**
     * the aggregate type that the underlying {@link AggregateEventStream} is associated with
     */
    public final AggregateType    aggregateType;
    /**
     * the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     */
    public final ID               aggregateId;
    private      LongRange        eventOrderRange;
    private      Optional<Tenant> tenant;

    /**
     * Create a new builder that produces a new {@link FetchStream} instance
     *
     * @param <ID> the id type for the aggregate
     * @return a new {@link FetchStreamBuilder} instance
     */
    public static <ID> FetchStreamBuilder<ID> builder() {
        return new FetchStreamBuilder<>();
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from the specified <code>eventOrderRange</code><br>
     * If the <code>tenant</code> arguments is {@link Optional#isPresent()}, then only {@link PersistedEvent}'s belonging to the specified <code>tenant</code> and matching the specified <code>eventOrderRange</code> will be returned<br>
     * Otherwise all {@link PersistedEvent}'s matching the specified <code>eventOrderRange</code> will be returned
     *
     * @param aggregateType   the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId     the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     * @param tenant          only return events belonging to the specified tenant (if {@link Optional#isPresent()})
     */
    public FetchStream(AggregateType aggregateType, ID aggregateId, LongRange eventOrderRange, Optional<Tenant> tenant) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.aggregateId = requireNonNull(aggregateId, "No aggregateId provided");
        this.eventOrderRange = requireNonNull(eventOrderRange, "No eventOrderRange provided");
        this.tenant = requireNonNull(tenant, "No tenant provided");
    }

    /**
     * @return the aggregate type that the underlying {@link AggregateEventStream} is associated with
     */
    public AggregateType getAggregateType() {
        return aggregateType;
    }

    /**
     * @return the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     */
    public ID getAggregateId() {
        return aggregateId;
    }

    /**
     * @return the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     */
    public LongRange getEventOrderRange() {
        return eventOrderRange;
    }

    /**
     * @return only return events belonging to the specified tenant (if {@link Optional#isPresent()})
     */
    public Optional<Tenant> getTenant() {
        return tenant;
    }

    /**
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     */
    public void setEventOrderRange(LongRange eventOrderRange) {
        this.eventOrderRange = requireNonNull(eventOrderRange, "No eventOrderRange provided");
    }

    /**
     * @param tenant only return events belonging to the specified tenant (if {@link Optional#isPresent()})
     */
    public void setTenant(Optional<Tenant> tenant) {
        this.tenant = requireNonNull(tenant, "No tenant provided");
    }

    /**
     * @param tenant only return events belonging to the specified tenant (if {@link Optional#isPresent()})
     */
    public void setTenant(Tenant tenant) {
        this.tenant = Optional.ofNullable(tenant);
    }

    @Override
    public String toString() {
        return "FetchStream{" +
                "aggregateType=" + aggregateType +
                ", aggregateId=" + aggregateId +
                ", eventOrderRange=" + eventOrderRange +
                ", tenant=" + tenant +
                '}';
    }
}