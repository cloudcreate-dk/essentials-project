package dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer;

import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class DurableQueuesMonitorManager implements Lifecycle {
    private static final Logger log = LoggerFactory.getLogger(DurableQueuesMonitorManager.class);
    private boolean started;
    private final boolean enabled;
    private final Duration interval;
    private final DurableQueues durableQueues;
    private final List<DurableQueuesMonitor> enabledMonitors;
    private ScheduledFuture<?> scheduledFuture;

    public DurableQueuesMonitorManager(final boolean enabled,
                                       final Duration interval,
                                       DurableQueues durableQueues,
                                       List<DurableQueuesMonitor> monitors) {
        this.enabled = enabled;
        this.interval = requireNonNull(interval, "Interval must be provided");
        this.durableQueues = requireNonNull(durableQueues, "DurableQueues must be provided");
        this.enabledMonitors = requireNonNull(monitors, "Monitors must be provided").stream()
                .filter(DurableQueuesMonitor::enabled).toList();
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("[{}] is disabled", this.getClass().getSimpleName());
            return;
        }
        if (this.enabledMonitors.isEmpty()) {
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

    private void executeMonitoring() {
        var queueNames = durableQueues.getQueueNames();
        log.debug("[{}] executing queue monitoring for {} queues and {} monitors", this.getClass().getSimpleName(), queueNames.size(), enabledMonitors.size());
        queueNames.forEach(this::execute);
    }

    private void execute(QueueName queueName) {
        enabledMonitors.forEach(monitor -> monitor.monitor(queueName));
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

    @Override
    public boolean isStarted() {
        return started;
    }
}
