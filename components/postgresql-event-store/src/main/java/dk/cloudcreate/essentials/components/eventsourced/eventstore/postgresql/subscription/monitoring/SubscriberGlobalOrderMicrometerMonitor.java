package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.jetbrains.annotations.NotNull;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static java.lang.Long.max;

/**
 * Maintaining gauge to measure the difference between the current global event order and the event order
 * of the subscriber. This to detect subscribers that are falling behind the global event order
 */
public class SubscriberGlobalOrderMicrometerMonitor implements EventStoreSubscriptionMonitor {
    private static final String SUBSCRIPTION_EVENT_ORDER_DIFF_METRIC = "DurableSubscriptions_EventOrder_Diff";
    private static final String SUBSCRIBER_ID_TAG = "SubscriberId";
    private static final String AGGREGATE_TYPE_TAG = "AggregateType";

    private final EventStoreSubscriptionManager eventStoreSubscriptionManager;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<Pair<SubscriberId, AggregateType>, Gauge> subscriberGauges = new ConcurrentHashMap<>();

    public SubscriberGlobalOrderMicrometerMonitor(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                                  MeterRegistry meterRegistry) {
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "EventStoreSubscriptionManager must be provided");
        this.meterRegistry = requireNonNull(meterRegistry, "MeterRegistry must be provided");
    }

    @Override
    public void monitor(SubscriberId subscriberId, AggregateType aggregateType) {
        var key = Pair.of(subscriberId, aggregateType);
        subscriberGauges.computeIfAbsent(key, this::buildGauge);
    }

    @NotNull
    private Gauge buildGauge(Pair<SubscriberId, AggregateType> pair) {
        SubscriberId subscriberId = pair._1;
        AggregateType aggregateType = pair._2;
        return Gauge.builder(SUBSCRIPTION_EVENT_ORDER_DIFF_METRIC, () -> calculateEventOrderDiff(subscriberId, aggregateType))
            .tags(List.of(Tag.of(SUBSCRIBER_ID_TAG, subscriberId.toString()), Tag.of(AGGREGATE_TYPE_TAG, aggregateType.toString())))
            .register(meterRegistry);
    }

    private long calculateEventOrderDiff(SubscriberId subscriberId, AggregateType aggregateType) {
        var globalEventOrder = getGlobalEventOrder(aggregateType);
        var currentEventOrder = eventStoreSubscriptionManager.getCurrentEventOrder(subscriberId, aggregateType);
        return max(0, globalEventOrder.longValue() - currentEventOrder.longValue());
    }

    @NotNull
    private GlobalEventOrder getGlobalEventOrder(AggregateType aggregateType) {
        return eventStoreSubscriptionManager.getEventStore().findHighestGlobalEventOrderPersisted(aggregateType).orElse(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER);
    }
}
