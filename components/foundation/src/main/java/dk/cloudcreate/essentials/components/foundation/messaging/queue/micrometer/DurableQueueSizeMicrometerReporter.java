package dk.cloudcreate.essentials.components.foundation.messaging.queue.micrometer;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueueName;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class DurableQueueSizeMicrometerReporter {
    private static final Logger log = LoggerFactory.getLogger(DurableQueueSizeMicrometerReporter.class);
    private static final String QUEUED_MESSAGES_GAUGE_NAME                     = "DurableQueues_QueuedMessages_Size";
    private static final String DEAD_LETTER_MESSAGES_GAUGE_NAME                = "DurableQueues_DeadLetterMessages_Size";
    public static final  String QUEUE_NAME_TAG_NAME                            = "QueueName";
    public static final  String MODULE_TAG_NAME                                = "Module";
    private final List<Tag> commonTags = new ArrayList<>();
    private final ConcurrentHashMap<QueueName, GaugeWrapper> queuedMessagesGauges     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<QueueName, GaugeWrapper> deadLetterMessagesGauges = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final DurableQueues durableQueues;

    public DurableQueueSizeMicrometerReporter(MeterRegistry meterRegistry,
                                              DurableQueues durableQueues,
                                              String moduleTag) {
        this.meterRegistry = requireNonNull(meterRegistry, "No meterRegistry instance provided");
        this.durableQueues = durableQueues;
        Optional.ofNullable(moduleTag).map(t -> Tag.of(MODULE_TAG_NAME, t)).ifPresent(commonTags::add);
    }

    public void report(QueueName queueName) {
        var messageCounts = durableQueues.getQueuedMessageCountsFor(queueName);
        this.queuedMessagesGauges.computeIfAbsent(queueName, this::buildQueuedMessagesGauge)
                .setMessageCount(messageCounts.numberOfQueuedMessages());
        this.deadLetterMessagesGauges.computeIfAbsent(queueName, this::buildDeadLetterMessagesGauge)
                .setMessageCount(messageCounts.numberOfQueuedDeadLetterMessages());
        log.debug("Reporting queued messages for {}: Queued messages: {}, Dead letter messages: {}", queueName,
                messageCounts.numberOfQueuedMessages(), messageCounts.numberOfQueuedDeadLetterMessages());
    }

    private GaugeWrapper buildQueuedMessagesGauge(QueueName queueName) {
        var queuedMessagesQueuedCount = new AtomicLong();
        var gauge = Gauge
                .builder(QUEUED_MESSAGES_GAUGE_NAME, queuedMessagesQueuedCount::get)
                .tags(buildTagList(QUEUE_NAME_TAG_NAME, queueName.toString()))
                .register(meterRegistry);
        return new GaugeWrapper(gauge, queuedMessagesQueuedCount);
    }

    private GaugeWrapper buildDeadLetterMessagesGauge(QueueName queueName) {
        var deadLetterMessagesQueuedCount = new AtomicLong();
        var gauge = Gauge
                .builder(DEAD_LETTER_MESSAGES_GAUGE_NAME, deadLetterMessagesQueuedCount::get)
                .tags(buildTagList(QUEUE_NAME_TAG_NAME, queueName.toString()))
                .register(meterRegistry);
        return new GaugeWrapper(gauge, deadLetterMessagesQueuedCount);
    }

    private List<Tag> buildTagList(String key, String value) {
        ArrayList<Tag> tagList = new ArrayList<>(this.commonTags);
        tagList.add(Tag.of(key, value));
        return tagList;
    }

    private static class GaugeWrapper extends Pair<Gauge, AtomicLong> {

        private GaugeWrapper(Gauge gauge, AtomicLong messageCount) {
            super(gauge, messageCount);
        }

        private void setMessageCount(long messageCount) {
            this._2.set(messageCount);
        }
    }

}
