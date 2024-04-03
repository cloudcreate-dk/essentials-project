package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;

/**
 * Implements monitoring operations on event store subscriptions
 */
public interface EventStoreSubscriptionMonitor {
    void monitor(SubscriberId subscriberId, AggregateType aggregateType);
}
