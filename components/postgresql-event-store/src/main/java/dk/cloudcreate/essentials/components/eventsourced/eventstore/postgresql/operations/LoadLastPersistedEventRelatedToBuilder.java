package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;

/**
 * Builder for {@link LoadLastPersistedEventRelatedTo}
 *
 * @param <ID> the id type for the aggregate
 */
public class LoadLastPersistedEventRelatedToBuilder<ID> {
    private AggregateType aggregateType;
    private ID            aggregateId;

    /**
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @return this builder instance
     */
    public LoadLastPersistedEventRelatedToBuilder<ID> setAggregateType(AggregateType aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    /**
     * @param aggregateId the identifier of the aggregate we want to find the last {@link PersistedEvent}
     * @return this builder instance
     */
    public LoadLastPersistedEventRelatedToBuilder<ID> setAggregateId(ID aggregateId) {
        this.aggregateId = aggregateId;
        return this;
    }

    /**
     * Builder an {@link LoadLastPersistedEventRelatedTo} instance from the builder properties
     * @return the {@link LoadLastPersistedEventRelatedTo} instance
     */
    public LoadLastPersistedEventRelatedTo<ID> build() {
        return new LoadLastPersistedEventRelatedTo<>(aggregateType, aggregateId);
    }
}