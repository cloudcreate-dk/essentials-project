package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.collections.Lists.nullSafeList;

public class EventStoreSubscriptionMonitorManager implements Lifecycle {
    private static final Logger log = LoggerFactory.getLogger(EventStoreSubscriptionMonitorManager.class);
    private boolean started;
    private final boolean enabled;
    private final Duration interval;
    private final List<EventStoreSubscriptionMonitor> monitors = new ArrayList<>();
    private final EventStoreSubscriptionManager eventStoreSubscriptionManager;
    private ScheduledFuture<?> scheduledFuture;

    public EventStoreSubscriptionMonitorManager(boolean enabled,
                                                Duration interval,
                                                EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                                List<EventStoreSubscriptionMonitor> monitors) {
        this.enabled = enabled;
        this.interval = requireNonNull(interval, "Interval must be provided");
        this.monitors.addAll(nullSafeList(monitors));
        this.eventStoreSubscriptionManager = requireNonNull(eventStoreSubscriptionManager, "SubscriptionManager must be provided");
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("[{}] is disabled", this.getClass().getSimpleName());
            return;
        }
        if (this.monitors.isEmpty()) {
            log.info("[{}] No monitors configured", this.getClass().getSimpleName());
            return;
        }
        if (!started) {
            log.info("Starting [{}]", this.getClass().getSimpleName());
            scheduledFuture = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder.builder()
                .nameFormat("EventStoreSubscriptionMonitoring")
                .daemon(true)
                .build())
                .scheduleAtFixedRate(this::executeMonitoring,
                    interval.toMillis(),
                    interval.toMillis(),
                    TimeUnit.MILLISECONDS);
            started = true;
        } else {
            log.debug("[{}] was already started", this.getClass().getSimpleName());
        }
    }

    @Override
    public void stop() {
        if (started) {
            log.info("Stopping [{}]", this.getClass().getSimpleName());
            scheduledFuture.cancel(true);
            started = false;
        } else {
            log.debug("[{}] was already stopped", this.getClass().getSimpleName());
        }
    }

    private void executeMonitoring() {
        Set<Pair<SubscriberId, AggregateType>> subscriptions = eventStoreSubscriptionManager.getActiveSubscriptions();
        log.debug("[{}] executing monitoring for {} subscriptions and {} monitors", this.getClass().getSimpleName(), subscriptions.size(), monitors.size());
        subscriptions.forEach(subscription -> executeMonitoring(subscription._1, subscription._2));
    }

    private void executeMonitoring(SubscriberId subscriberId, AggregateType aggregateType) {
        monitors.forEach(monitor -> monitor.monitor(subscriberId, aggregateType));
    }

    @Override
    public boolean isStarted() {
        return started;
    }
}
