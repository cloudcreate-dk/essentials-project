package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;
import io.micrometer.core.instrument.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer.DurableQueuesMicrometerInterceptor.MODULE_TAG_NAME;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static java.lang.Long.max;

/**
 * Maintaining gauge to measure the difference between the current global event order and the event order
 * of the subscriber. This to detect subscribers that are falling behind the global event order
 */
public class SubscriberGlobalOrderMicrometerMonitor implements EventStoreSubscriptionMonitor {
    private static final Logger log = LoggerFactory.getLogger(SubscriberGlobalOrderMicrometerMonitor.class);
    private static final String SUBSCRIPTION_EVENT_ORDER_DIFF_METRIC = "DurableSubscriptions_EventOrder_Diff";
    private static final String SUBSCRIBER_ID_TAG = "SubscriberId";
    private static final String AGGREGATE_TYPE_TAG = "AggregateType";

    private final EventStoreSubscriptionManager eventStoreSubscriptionManager;
    private final MeterRegistry meterRegistry;
    private final EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory;
    private final ConcurrentHashMap<Pair<SubscriberId, AggregateType>, Pair<Gauge, AtomicLong>> subscriberGauges = new ConcurrentHashMap<>();
    private final List<Tag> commonTags = new ArrayList<>();

    public SubscriberGlobalOrderMicrometerMonitor(EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                                  MeterRegistry meterRegistry,
                                                  EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory,
                                                  String moduleTag) {
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "EventStoreSubscriptionManager must be provided");
        this.meterRegistry = requireNonNull(meterRegistry, "MeterRegistry must be provided");
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "Unit of work factory is required");
        Optional.ofNullable(moduleTag).map(t -> Tag.of(MODULE_TAG_NAME, t)).ifPresent(commonTags::add);
    }

    @Override
    public void monitor(SubscriberId subscriberId, AggregateType aggregateType) {
        unitOfWorkFactory.usingUnitOfWork(() -> doExecuteMonitoring(subscriberId, aggregateType));
    }

    private void doExecuteMonitoring(SubscriberId subscriberId, AggregateType aggregateType) {
        var key = Pair.of(subscriberId, aggregateType);
        calculateSubscriberGlobalEventOrderDiff(subscriberId, aggregateType)
            .ifPresent(currentLag -> {
                if (currentLag > 0) {
                    log.info("Subscriber lag found for subscriber '{}' on aggregateType '{}'. Lag is {}", subscriberId, aggregateType, currentLag);
                } else {
                    log.debug("No Subscriber lag found for subscriber '{}' on aggregateType '{}'.", subscriberId, aggregateType);
                }
                subscriberGauges.computeIfAbsent(key, this::initializeEventOrderDiffCountGauge)
                    ._2.set(currentLag);
            });
    }

    private Pair<Gauge, AtomicLong> initializeEventOrderDiffCountGauge(Pair<SubscriberId, AggregateType> key) {
        var eventOrderDifferenceCount = new AtomicLong();
        return Pair.of(buildGauge(key, eventOrderDifferenceCount), eventOrderDifferenceCount);
    }

    @NotNull
    private Gauge buildGauge(Pair<SubscriberId, AggregateType> pair, AtomicLong eventOrderDifferenceCount) {
        var subscriberId = pair._1;
        var aggregateType = pair._2;

        var tags = new ArrayList<>(commonTags);
        tags.add(Tag.of(SUBSCRIBER_ID_TAG, subscriberId.toString()));
        tags.add(Tag.of(AGGREGATE_TYPE_TAG, aggregateType.toString()));
        return Gauge.builder(SUBSCRIPTION_EVENT_ORDER_DIFF_METRIC, eventOrderDifferenceCount::get)
            .tags(tags)
            .register(meterRegistry);
    }

    private Optional<Long> calculateSubscriberGlobalEventOrderDiff(SubscriberId subscriberId, AggregateType aggregateType) {
        return eventStoreSubscriptionManager.getCurrentEventOrder(subscriberId, aggregateType)
            .map(currentSubscriberGlobalEventOrder -> {
                var highestGlobalEventOrderPersisted = findHighestGlobalEventOrderPersisted(aggregateType);
                return max(0, highestGlobalEventOrderPersisted.longValue() - currentSubscriberGlobalEventOrder.longValue());
            });
    }

    @NotNull
    private GlobalEventOrder findHighestGlobalEventOrderPersisted(AggregateType aggregateType) {
        return eventStoreSubscriptionManager.getEventStore().findHighestGlobalEventOrderPersisted(aggregateType).orElse(GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER);
    }
}
