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

package dk.trustworks.essentials.components.boot.autoconfigure.postgresql.eventstore;

import dk.trustworks.essentials.components.boot.autoconfigure.postgresql.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.gap.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Properties for the Postgresql EventStore<br>
 * <br>
 * <br>
 * <u><b>Security:</b></u><br>
 * If you in your own Spring Boot application choose to override the Beans defined by this starter,
 * then you need to check the component document to learn about the Security implications of each configuration.
 * <br>
 * Also see {@link EssentialsComponentsConfiguration} for security information related to common Essentials components.
 *
 * @see PostgresqlDurableSubscriptionRepository
 * @see SeparateTablePerAggregateTypePersistenceStrategy
 * @see SeparateTablePerAggregateTypeEventStreamConfigurationFactory
 * @see SeparateTablePerAggregateEventStreamConfiguration
 * @see EventStreamTableColumnNames
 * @see dk.trustworks.essentials.components.queue.postgresql.PostgresqlDurableQueues
 * @see dk.trustworks.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager
 * @see dk.trustworks.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockStorage
 * @see dk.trustworks.essentials.components.foundation.postgresql.MultiTableChangeListener
 */
@Configuration
@ConfigurationProperties(prefix = "essentials.eventstore")
public class EssentialsEventStoreProperties {
    private IdentifierColumnType                             identifierColumnType     = IdentifierColumnType.TEXT;
    private JSONColumnType                                   jsonColumnType           = JSONColumnType.JSONB;
    private boolean                                          useEventStreamGapHandler = true;
    private boolean                                          verboseTracing           = false;
    private EssentialsComponentsProperties.MetricsProperties metrics                  = new EssentialsComponentsProperties.MetricsProperties();

    private final EventStoreSubscriptionManagerProperties subscriptionManager = new EventStoreSubscriptionManagerProperties();

    private final EventStoreSubscriptionMonitorProperties subscriptionMonitor = new EventStoreSubscriptionMonitorProperties();

    /**
     * Should the Tracing produces only include all operations or only top level operations (default false)
     *
     * @return Should the Tracing produces only include all operations or only top level operations
     */
    public boolean isVerboseTracing() {
        return verboseTracing;
    }

    /**
     * Should the Tracing produces only include all operations or only top level operations (default false)
     *
     * @param verboseTracing Should the Tracing produces only include all operations or only top level operations
     */
    public void setVerboseTracing(boolean verboseTracing) {
        this.verboseTracing = verboseTracing;
    }

    /**
     * The {@link IdentifierColumnType} used for all Aggregate-Ids
     *
     * @return The {@link IdentifierColumnType} used for all Aggregate-Ids
     */
    public IdentifierColumnType getIdentifierColumnType() {
        return identifierColumnType;
    }

    /**
     * The {@link IdentifierColumnType} used for all Aggregate-Ids
     *
     * @param identifierColumnType The {@link IdentifierColumnType} used for all Aggregate-Ids
     */
    public void setIdentifierColumnType(IdentifierColumnType identifierColumnType) {
        this.identifierColumnType = identifierColumnType;
    }

    /**
     * The {@link JSONColumnType} used for all JSON columns
     *
     * @return The {@link JSONColumnType} used for all JSON columns
     */
    public JSONColumnType getJsonColumnType() {
        return jsonColumnType;
    }

    /**
     * The {@link JSONColumnType} used for all JSON columns
     *
     * @param jsonColumnType The {@link JSONColumnType} used for all JSON columns
     */
    public void setJsonColumnType(JSONColumnType jsonColumnType) {
        this.jsonColumnType = jsonColumnType;
    }

    /**
     * Get the {@link EventStoreSubscriptionManager} properties
     *
     * @return the {@link EventStoreSubscriptionManager} properties
     */
    public EventStoreSubscriptionManagerProperties getSubscriptionManager() {
        return subscriptionManager;
    }

    /**
     * Should the {@link PostgresqlEventStore} use {@link PostgresqlEventStreamGapHandler} or the {@link NoEventStreamGapHandler}?
     *
     * @return Should the {@link PostgresqlEventStore} use {@link PostgresqlEventStreamGapHandler} or the {@link NoEventStreamGapHandler}?
     */
    public boolean isUseEventStreamGapHandler() {
        return useEventStreamGapHandler;
    }

    /**
     * Should the {@link PostgresqlEventStore} use {@link PostgresqlEventStreamGapHandler} or the {@link NoEventStreamGapHandler}?
     *
     * @param useEventStreamGapHandler Should the {@link PostgresqlEventStore} use {@link PostgresqlEventStreamGapHandler} or the {@link NoEventStreamGapHandler}?
     */
    public void setUseEventStreamGapHandler(boolean useEventStreamGapHandler) {
        this.useEventStreamGapHandler = useEventStreamGapHandler;
    }

    /**
     * Get the {@link dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring.EventStoreSubscriptionMonitorManager} properties
     *
     * @return the {@link dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring.EventStoreSubscriptionMonitorManager} properties
     */
    public EventStoreSubscriptionMonitorProperties getSubscriptionMonitor() {
        return this.subscriptionMonitor;
    }

    /**
     * Configuration properties for essentials metrics collection and logging.
     * <p>
     * This configuration is used to enable and fine-tune metrics gathering and logging for the event store.
     * If the <code>enabled</code> property is
     * set to <code>false</code>, then no performance metrics will be collected or logged for that component.
     * <p>
     * <b>YAML example:</b>
     * <pre>{@code
     * essentials:
     *   event-store:
     *     metrics:
     *       enabled: true
     *       thresholds:
     *         debug: 25ms
     *         info: 200ms
     *         warn: 500ms
     *         error: 5000ms
     * }</pre>
     * <b>Properties example:</b>
     * <pre>{@code
     * essentials.event-store.metrics.enabled=true
     * essentials.event-store.metrics.thresholds.debug=25ms
     * essentials.event-store.metrics.thresholds.info=200ms
     * essentials.event-store.metrics.thresholds.warn=500ms
     * essentials.event-store.metrics.thresholds.error=5000ms
     * }</pre>
     * <p>
     * You can further control the log levels by adjusting the minimum log level for the respective loggers:
     * <table border="1">
     *     <tr><th>Metric</th><th>Logger Class</th></tr>
     *     <tr><td>essentials.event-store.metrics</td>
     *        <td>
     *            dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.interceptor.micrometer.RecordExecutionTimeEventStoreInterceptor<br>
     *            dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.observability.micrometer.MeasurementEventStoreSubscriptionObserver
     *        </td>
     *     </tr>
     * </table>
     *
     * @return essentials metrics properties
     */
    public EssentialsComponentsProperties.MetricsProperties getMetrics() {
        return metrics;
    }

    /**
     * {@link EventStoreSubscriptionManager} properties
     */
    public static class EventStoreSubscriptionManagerProperties {
        private int                                              eventStorePollingBatchSize = 10;
        private Duration                                         eventStorePollingInterval  = Duration.ofMillis(100);
        private Duration                                         snapshotResumePointsEvery  = Duration.ofSeconds(10);
        private EssentialsComponentsProperties.MetricsProperties metrics                    = new EssentialsComponentsProperties.MetricsProperties();

        /**
         * How many events should The {@link EventStore} maximum return when polling for events
         *
         * @return how many events should The {@link EventStore} maximum return when polling for events
         */
        public int getEventStorePollingBatchSize() {
            return eventStorePollingBatchSize;
        }

        /**
         * How many events should The {@link EventStore} maximum return when polling for events
         *
         * @param eventStorePollingBatchSize how many events should The {@link EventStore} maximum return when polling for events
         */
        public void setEventStorePollingBatchSize(int eventStorePollingBatchSize) {
            this.eventStorePollingBatchSize = eventStorePollingBatchSize;
        }

        /**
         * How often should the {@link EventStore} be polled for new events
         *
         * @return how often should the {@link EventStore} be polled for new events
         */
        public Duration getEventStorePollingInterval() {
            return eventStorePollingInterval;
        }

        /**
         * How often should the {@link EventStore} be polled for new events
         *
         * @param eventStorePollingInterval how often should the {@link EventStore} be polled for new events
         */
        public void setEventStorePollingInterval(Duration eventStorePollingInterval) {
            this.eventStorePollingInterval = eventStorePollingInterval;
        }

        /**
         * How often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
         *
         * @return how often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
         */
        public Duration getSnapshotResumePointsEvery() {
            return snapshotResumePointsEvery;
        }

        /**
         * How often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
         *
         * @param snapshotResumePointsEvery How often should active (for exclusive subscribers this means subscribers that have acquired a distributed lock) subscribers have their {@link SubscriptionResumePoint} saved
         */
        public void setSnapshotResumePointsEvery(Duration snapshotResumePointsEvery) {
            this.snapshotResumePointsEvery = snapshotResumePointsEvery;
        }

        /**
         * Configure essentials event store subscription manager metrics
         * <p>
         * YAML example:
         * <pre>{@code
         * essentials:
         *   event-store:
         *     subscription-manager:
         *       metrics:
         *         enabled: true
         *         thresholds:
         *           debug: 25ms
         *           info: 200ms
         *           warn: 500ms
         *           error: 5000ms
         * }</pre>
         * <p>
         * Properties example:
         * <pre>{@code
         * essentials.event-store.subscription-manager.metrics.enabled=true
         * essentials.event-store.subscription-manager.metrics.thresholds.debug=25ms
         * essentials.event-store.subscription-manager.metrics.thresholds.info=200ms
         * essentials.event-store.subscription-manager.metrics.thresholds.warn=500ms
         * essentials.event-store.subscription-manager.metrics.thresholds.error=5000ms
         * }</pre>
         *
         * @return essentials event store subscription manager metrics
         */
        public EssentialsComponentsProperties.MetricsProperties getMetrics() {
            return metrics;
        }
    }

    public static class EventStoreSubscriptionMonitorProperties {
        private boolean  enabled  = true;
        private Duration interval = Duration.ofMinutes(1);

        /**
         * Is monitoring of event store subscribers enabled
         *
         * @return Is monitoring of event store subscribers enabled
         */
        public boolean isEnabled() {
            return this.enabled;
        }

        /**
         * Monitoring of event store subscribers using implementations of
         * {@link dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring.EventStoreSubscriptionMonitor}
         *
         * @param enabled Monitoring of event store subscribers
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Monitoring interval
         *
         * @param interval Monitoring interval
         */
        public void setInterval(Duration interval) {
            this.interval = interval;
        }

        /**
         * The interval to execute the {@link dk.trustworks.essentials.components.eventsourced.eventstore.postgresql.subscription.monitoring.EventStoreSubscriptionMonitor}s
         *
         * @return monitoring interval
         */
        public Duration getInterval() {
            return this.interval;
        }
    }
}
