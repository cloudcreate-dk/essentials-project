/*
 * Copyright 2021-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.observability.micrometer;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreSubscription;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.observability.EventStoreSubscriptionObserver;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.foundation.types.*;
import dk.cloudcreate.essentials.shared.measurement.*;
import dk.cloudcreate.essentials.shared.reflection.FunctionalInterfaceLoggingNameResolver;
import dk.cloudcreate.essentials.types.LongRange;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * A Micrometer-enabled observer that tracks {@link EventStoreSubscription} performance using a {@link MeasurementTaker}.
 * <p>
 * The metric name always begins with
 * "essentials.eventstore.subscription" and any dynamic parameters (e.g. subscriber_id, aggregate_type) are added as tags.
 */
public class MeasurementEventStoreSubscriptionObserver implements EventStoreSubscriptionObserver {

    private static final Logger           log             = LoggerFactory.getLogger(MeasurementEventStoreSubscriptionObserver.class);
    public static final  String           MODULE_TAG_NAME = "Module";
    private final        MeasurementTaker measurementTaker;
    private final        boolean          recordExecutionTimeEnabled;
    private final        String           moduleTag;

    /**
     * Constructs a new observer with the provided MeasurementTaker.
     *
     * @param meterRegistryOptional      an Optional MeterRegistry to enable Micrometer metrics
     * @param recordExecutionTimeEnabled whether to record execution times or not
     * @param thresholds                 the logging thresholds configuration
     * @param moduleTag                  Optional {@value #MODULE_TAG_NAME} Tag value
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public MeasurementEventStoreSubscriptionObserver(Optional<MeterRegistry> meterRegistryOptional,
                                                     boolean recordExecutionTimeEnabled,
                                                     LogThresholds thresholds,
                                                     String moduleTag) {
        this.recordExecutionTimeEnabled = recordExecutionTimeEnabled;
        this.measurementTaker = MeasurementTaker.builder()
                                                .addRecorder(
                                                        new LoggingMeasurementRecorder(
                                                                log,
                                                                thresholds))
                                                .withOptionalMicrometerMeasurementRecorder(meterRegistryOptional)
                                                .build();
        this.moduleTag = moduleTag;
    }


    @Override
    public void startingSubscriber(EventStoreSubscription eventStoreSubscription) {

    }

    @Override
    public void startedSubscriber(EventStoreSubscription eventStoreSubscription, Duration startDuration) {

    }

    @Override
    public void stoppingSubscriber(EventStoreSubscription eventStoreSubscription) {

    }

    @Override
    public void stoppedSubscriber(EventStoreSubscription eventStoreSubscription, Duration stopDuration) {

    }

    @Override
    public void resolvedBatchSizeForEventStorePoll(SubscriberId subscriberId,
                                                   AggregateType aggregateType,
                                                   long defaultBatchFetchSize,
                                                   long remainingDemandForEvents,
                                                   long lastBatchSizeForEventStorePoll,
                                                   int consecutiveNoPersistedEventsReturned,
                                                   long nextFromInclusiveGlobalOrder,
                                                   long batchSizeForThisEventStorePoll,
                                                   Duration resolveBatchSizeDuration) {
        if (recordExecutionTimeEnabled) {
            var description = msg("DefaultBatchFetchSize: {}, RemainingDemandForEvents: {}, LastBatchSizeForEventStorePoll: {}, " +
                                          "ConsecutiveNoPersistedEventsReturned: {}, NextFromInclusiveGlobalOrder: {}, BatchSizeForThisEventStorePoll: {}",
                                  defaultBatchFetchSize,
                                  remainingDemandForEvents,
                                  lastBatchSizeForEventStorePoll,
                                  consecutiveNoPersistedEventsReturned,
                                  nextFromInclusiveGlobalOrder,
                                  batchSizeForThisEventStorePoll);
            var context = MeasurementContext.builder("essentials.eventstore.subscription.resolved_batch_size_for_event_store_poll")
                                            .description(description)
                                            .tag("subscriber_id", subscriberId)
                                            .tag("aggregate_type", aggregateType)
                                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                                            .build();
            measurementTaker.recordTime(context, resolveBatchSizeDuration);
        }
    }


    @Override
    public void skippingPollingDueToNoNewEventsPersisted(SubscriberId subscriberId,
                                                         AggregateType aggregateType,
                                                         long defaultBatchFetchSize,
                                                         long remainingDemandForEvents,
                                                         long lastBatchSizeForEventStorePoll,
                                                         int consecutiveNoPersistedEventsReturned,
                                                         long nextFromInclusiveGlobalOrder,
                                                         long batchSizeForThisEventStorePoll) {
        if (recordExecutionTimeEnabled) {
            var message = msg("skippingPollingDueToNoNewEventsPersisted({}:{}) DefaultBatchFetchSize: {}," +
                                      " RemainingDemandForEvents: {}," +
                                      " LastBatchSizeForEventStorePoll: {}," +
                                      " ConsecutiveNoPersistedEventsReturned: {}," +
                                      " NextFromInclusiveGlobalOrder: {}," +
                                      " BatchSizeForThisEventStorePoll: {}",
                              subscriberId, aggregateType,
                              defaultBatchFetchSize,
                              remainingDemandForEvents,
                              lastBatchSizeForEventStorePoll,
                              consecutiveNoPersistedEventsReturned,
                              nextFromInclusiveGlobalOrder,
                              batchSizeForThisEventStorePoll);
            log.debug(message);
        }
    }

    @Override
    public void eventStorePolled(SubscriberId subscriberId,
                                 AggregateType aggregateType,
                                 LongRange globalOrderRange,
                                 List<GlobalEventOrder> transientGapsToInclude,
                                 Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                 List<PersistedEvent> persistedEventsReturnedFromPoll,
                                 Duration pollDuration) {
        if (recordExecutionTimeEnabled) {
            var description = msg("GlobalOrderRange: {}, TransientGapsToInclude: {}, NumberOfPersistedEvents: {}",
                                  globalOrderRange,
                                  transientGapsToInclude.stream().map(GlobalEventOrder::toString)
                                                        .collect(Collectors.joining(",")),
                                  persistedEventsReturnedFromPoll.size());
            var context = MeasurementContext.builder("essentials.eventstore.subscription.event_store_polled")
                                            .description(description)
                                            .tag("subscriber_id", subscriberId)
                                            .tag("aggregate_type", aggregateType)
                                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                                            .build();
            measurementTaker.recordTime(context, pollDuration);
        }
    }

    @Override
    public void reconciledGaps(SubscriberId subscriberId,
                               AggregateType aggregateType,
                               LongRange globalOrderRange,
                               List<GlobalEventOrder> transientGapsToInclude,
                               List<PersistedEvent> persistedEvents,
                               Duration reconcileGapsDuration) {
        if (recordExecutionTimeEnabled) {
            var description = msg("GlobalOrderRange: {}, TransientGapsToInclude: {}, NumberOfPersistedEvents: {}",
                                  globalOrderRange,
                                  transientGapsToInclude.stream().map(GlobalEventOrder::toString)
                                                        .collect(Collectors.joining(",")),
                                  persistedEvents.size());
            var context = MeasurementContext.builder("essentials.eventstore.subscription.reconciled_gaps")
                                            .description(description)
                                            .tag("subscriber_id", subscriberId)
                                            .tag("aggregate_type", aggregateType)
                                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                                            .build();
            measurementTaker.recordTime(context, reconcileGapsDuration);
        }
    }

    @Override
    public void publishEvent(SubscriberId subscriberId,
                             AggregateType aggregateType,
                             PersistedEvent persistedEvent,
                             Duration publishEventDuration) {
        if (recordExecutionTimeEnabled) {
            var context = MeasurementContext.builder("essentials.eventstore.subscription.publish_event")
                                            .description("Time it takes to publish a PersistedEvent to the Flux Sink")
                                            .tag("subscriber_id", subscriberId)
                                            .tag("aggregate_type", aggregateType)
                                            .tag("event_type", persistedEvent.event().getEventTypeOrNamePersistenceValue())
                                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                                            .build();
            measurementTaker.recordTime(context, publishEventDuration);
        }
    }

    @Override
    public void requestingEvents(long numberOfEventsRequested,
                                 EventStoreSubscription eventStoreSubscription) {
        if (recordExecutionTimeEnabled) {
            if (numberOfEventsRequested > 1) {
                var message = msg("[{}:{}] requestingEvents: {}",
                                  eventStoreSubscription.aggregateType(),
                                  eventStoreSubscription.subscriberId(),
                                  numberOfEventsRequested);
                log.debug(message);
            }
        }
    }

    @Override
    public void lockAcquired(FencedLock lock, EventStoreSubscription eventStoreSubscription) {
    }

    @Override
    public void lockReleased(FencedLock lock, EventStoreSubscription eventStoreSubscription) {
    }

    @Override
    public void handleEvent(PersistedEvent event,
                            TransactionalPersistedEventHandler eventHandler,
                            EventStoreSubscription eventStoreSubscription,
                            Duration handleEventDuration) {
        if (recordExecutionTimeEnabled) {
            var context = MeasurementContext.builder("essentials.eventstore.subscription.handle_event_transactional")
                                            .description("Time it takes to handle a PersistedEvent in a Transaction")
                                            .tag("subscriber_id", eventStoreSubscription.subscriberId().toString())
                                            .tag("aggregate_type", eventStoreSubscription.aggregateType().toString())
                                            .tag("event_handler", FunctionalInterfaceLoggingNameResolver.resolveLoggingName(eventHandler))
                                            .tag("event_type", event.event().getEventTypeOrNamePersistenceValue())
                                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                                            .build();
            measurementTaker.recordTime(context, handleEventDuration);
        }
    }

    @Override
    public void handleEvent(PersistedEvent event,
                            PersistedEventHandler eventHandler,
                            EventStoreSubscription eventStoreSubscription,
                            Duration handleEventDuration) {
        if (recordExecutionTimeEnabled) {
            var context = MeasurementContext.builder("essentials.eventstore.subscription.handle_event")
                                            .description("Time it takes to handle a PersistedEvent")
                                            .tag("subscriber_id", eventStoreSubscription.subscriberId().toString())
                                            .tag("aggregate_type", eventStoreSubscription.aggregateType().toString())
                                            .tag("event_handler", FunctionalInterfaceLoggingNameResolver.resolveLoggingName(eventHandler))
                                            .tag("event_type", event.event().getEventTypeOrNamePersistenceValue())
                                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                                            .build();
            measurementTaker.recordTime(context, handleEventDuration);
        }
    }

    @Override
    public void handleEventFailed(PersistedEvent event, TransactionalPersistedEventHandler eventHandler,
                                  Throwable cause, EventStoreSubscription eventStoreSubscription) {
    }

    @Override
    public void handleEventFailed(PersistedEvent event, PersistedEventHandler eventHandler,
                                  Throwable cause, EventStoreSubscription eventStoreSubscription) {
    }

    @Override
    public void resolveResumePoint(SubscriptionResumePoint resumePoint,
                                   GlobalEventOrder onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder,
                                   EventStoreSubscription eventStoreSubscription,
                                   Duration resolveResumePointDuration) {
        if (recordExecutionTimeEnabled) {
            var description = msg("SubscribeFromAndIncludingGlobalOrder: {}", onFirstSubscriptionSubscribeFromAndIncludingGlobalOrder);
            var context = MeasurementContext.builder("essentials.eventstore.subscription.resolve_resume_point")
                                            .description(description)
                                            .tag("subscriber_id", eventStoreSubscription.subscriberId().toString())
                                            .tag("aggregate_type", eventStoreSubscription.aggregateType().toString())
                                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                                            .build();
            measurementTaker.recordTime(context, resolveResumePointDuration);
        }
    }

    @Override
    public void unsubscribing(EventStoreSubscription eventStoreSubscription) {
    }

    @Override
    public void resettingFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder,
                              EventStoreSubscription eventStoreSubscription) {
    }
}
