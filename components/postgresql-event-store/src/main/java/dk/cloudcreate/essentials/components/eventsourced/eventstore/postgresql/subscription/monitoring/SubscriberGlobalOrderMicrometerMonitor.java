package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.NotNull;

import static dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer.DurableQueuesMicrometerInterceptor.MODULE_TAG_NAME;
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
    private final ConcurrentHashMap<Pair<SubscriberId, AggregateType>, ImmutablePair<Gauge, AtomicLong>> subscriberGauges = new ConcurrentHashMap<>();
    private final List<Tag> commonTags = new ArrayList<>();

    public SubscriberGlobalOrderMicrometerMonitor(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                                  MeterRegistry meterRegistry,
                                                  String moduleTag) {
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "EventStoreSubscriptionManager must be provided");
        this.meterRegistry = requireNonNull(meterRegistry, "MeterRegistry must be provided");
        Optional.ofNullable(moduleTag).map(t -> Tag.of(MODULE_TAG_NAME, t)).ifPresent(commonTags::add);
    }

    @Override
    public void monitor(SubscriberId subscriberId, AggregateType aggregateType) {
        var key = Pair.of(subscriberId, aggregateType);
        ImmutablePair<Gauge, AtomicLong> subscriberGauge = subscriberGauges.computeIfAbsent(key, key1 -> {
            AtomicLong atomicLong = new AtomicLong();
            return new ImmutablePair<>(buildGauge(key1, atomicLong), atomicLong);
        });
        subscriberGauge.right.set(calculateEventOrderDiff(subscriberId, aggregateType));
    }

    @NotNull
    private Gauge buildGauge(Pair<SubscriberId, AggregateType> pair, AtomicLong atomicLong) {
        SubscriberId subscriberId = pair._1;
        AggregateType aggregateType = pair._2;

        List<Tag> tags = new ArrayList<>(commonTags);
        tags.add(Tag.of(SUBSCRIBER_ID_TAG, subscriberId.toString()));
        tags.add(Tag.of(AGGREGATE_TYPE_TAG, aggregateType.toString()));
        return Gauge.builder(SUBSCRIPTION_EVENT_ORDER_DIFF_METRIC, atomicLong::get)
            .tags(tags)
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
