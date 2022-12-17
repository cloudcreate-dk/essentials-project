package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.operations;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.foundation.types.EventId;

/**
 * Builder for {@link LoadEvent}
 */
public class LoadEventBuilder {
    private AggregateType aggregateType;
    private EventId eventId;

    /**
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream}, which should contain the {@link PersistedEvent} with the given <code>eventId</code>, is associated with
     * @return this builder instance
     */
    public LoadEventBuilder setAggregateType(AggregateType aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    /**
     *
     * @param eventId the identifier of the {@link PersistedEvent}
     * @return this builder instance
     */
    public LoadEventBuilder setEventId(EventId eventId) {
        this.eventId = eventId;
        return this;
    }

    /**
     * Builder an {@link LoadEvent} instance from the builder properties
     * @return the {@link LoadEvent} instance
     */
    public LoadEvent build() {
        return new LoadEvent(aggregateType, eventId);
    }
}