package dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;

public interface DurableQueuesMonitor {
    void monitor(QueueName queueName);
    boolean enabled();
}
