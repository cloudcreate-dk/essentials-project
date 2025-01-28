package dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DurableQueuesMicrometerMonitor implements DurableQueuesMonitor {
    private static final Logger log = LoggerFactory.getLogger(DurableQueuesMicrometerMonitor.class);
    private final boolean enabled;
    private final DurableQueueSizeMicrometerReporter reporter;

    public DurableQueuesMicrometerMonitor(final MeterRegistry meterRegistry,
                                          final DurableQueues durableQueues,
                                          String moduleTag,
                                          boolean enabled) {
        this.reporter = new DurableQueueSizeMicrometerReporter(meterRegistry, durableQueues, moduleTag);
        this.enabled = enabled;
        log.info("Queue message size reporting from {} is {}", this.getClass().getSimpleName(), enabled ? "enabled" : "disabled");
    }

    @Override
    public void monitor(QueueName queueName) {
        if (enabled()) {
            reporter.report(queueName);
        }
    }

    @Override
    public boolean enabled() {
        return enabled;
    }
}
