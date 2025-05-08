package dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring;

import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.trustworks.essentials.components.foundation.types.SubscriberId;

/**
 * Implements monitoring operations on event store subscriptions
 */
public interface EventStoreSubscriptionMonitor {
    void monitor(SubscriberId subscriberId, AggregateType aggregateType);
}
