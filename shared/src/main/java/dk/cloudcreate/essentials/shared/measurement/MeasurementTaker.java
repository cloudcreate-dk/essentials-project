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

package dk.cloudcreate.essentials.shared.measurement;

import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * A facade to record the execution time of a given code block using one or more {@link MeasurementRecorder} instances.
 * <p>
 * Example of fluent usage:
 * <pre>{@code
 * return measurementTaker.context("essentials.eventstore.append_to_stream")
 *                         .description("Time taken to append events to the event store")
 *                         .tag("aggregateType", operation.getAggregateType())
 *                         .record(chain::proceed);
 * }
 * </pre>
 * or
 * <pre>{@code
 * measurementTaker.recordTime(MeasurementContext.builder("essentials.invocation")
 *                                               .description("Time it takes to invoke a method")
 *                                               .tag("class", FunctionalInterfaceLoggingNameResolver.resolveLoggingName(invokeMethodsOn))
 *                                               .tag("method", methodLoggingName)
 *                                               .build(),
 *                             duration);
 * }
 * </pre>
 */
public class MeasurementTaker {
    private final List<MeasurementRecorder> recorders;

    private MeasurementTaker(List<MeasurementRecorder> recorders) {
        requireNonNull(recorders, "No recorders provided");
        this.recorders = Collections.unmodifiableList(recorders);
    }

    /**
     * Creates a new Builder for constructing a MeasurementTaker.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes the supplied block of code, measures its execution time, and notifies all configured recorders.
     *
     * @param context the measurement context containing metric information
     * @param block   the code block whose execution time is to be measured
     * @param <T>     the type of result returned by the code block
     * @return the result of executing the code block
     */
    public <T> T record(MeasurementContext context, Supplier<T> block) {
        requireNonNull(context, "No context provided");
        requireNonNull(block, "No block provided");
        long start = System.nanoTime();
        try {
            return block.get();
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            recorders.forEach(recorder -> recorder.record(context, elapsed));
        }
    }

    /**
     * Records an already measured duration.
     *
     * @param context the measurement context containing metric name, description and tags
     * @param elapsed the elapsed time to record
     */
    public void recordTime(MeasurementContext context, Duration elapsed) {
        requireNonNull(context, "No context provided");
        requireNonNull(elapsed, "No elapsed provided");
        recorders.forEach(recorder -> recorder.record(context, elapsed));
    }


    /**
     * Starts a fluent measurement configuration for the specified metric.
     *
     * @param metricName the name of the metric
     * @return a fluent context builder for further configuration
     */
    public FluentMeasurementContext context(String metricName) {
        return new FluentMeasurementContext(this, metricName);
    }

    /**
     * Fluent builder for constructing a MeasurementTaker.
     */
    public static class Builder {
        private final List<MeasurementRecorder> recorders = new ArrayList<>();

        /**
         * Adds a {@link MeasurementRecorder} to the configuration.
         *
         * @param recorder the recorder to add
         * @return this builder instance for fluent chaining
         */
        public Builder addRecorder(MeasurementRecorder recorder) {
            recorders.add(
                    requireNonNull(recorder, "No recorder provided")
                         );
            return this;
        }

        /**
         * Optionally configures a MeterRegistry.
         * If the provided {@code Optional<MeterRegistry>} is non-empty,
         * a {@link MicrometerMeasurementRecorder} is added.
         *
         * @param meterRegistryOptional an Optional MeterRegistry instance
         * @return this builder instance for fluent chaining
         */
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public Builder withOptionalMicrometerMeasurementRecorder(Optional<MeterRegistry> meterRegistryOptional) {
            requireNonNull(meterRegistryOptional, "No meterRegistryOptional provided");
            meterRegistryOptional.ifPresent(registry ->
                                                    addRecorder(new MicrometerMeasurementRecorder(registry)));
            return this;
        }

        /**
         * Builds the MeasurementTaker instance.
         *
         * @return a new MeasurementTaker with the configured recorders
         */
        public MeasurementTaker build() {
            return new MeasurementTaker(recorders);
        }
    }

    /**
     * A fluent builder for constructing a measurement context and recording its execution.
     * <p>
     * Example usage:
     * <pre>
     *     return measurementTaker.context("essentials.eventstore.append_to_stream")
     *                             .description("Time taken to append events to the event store")
     *                             .tag("aggregateType", operation.getAggregateType())
     *                             .record(chain::proceed);
     * </pre>
     * </p>
     */
    public static class FluentMeasurementContext {
        private final MeasurementTaker           measurementTaker;
        private final MeasurementContext.Builder contextBuilder;

        private FluentMeasurementContext(MeasurementTaker measurementTaker, String metricName) {
            this.measurementTaker = requireNonNull(measurementTaker, "No measurementTaker provided");
            this.contextBuilder = MeasurementContext.builder(metricName);
        }

        /**
         * Sets the description for the measurement.
         *
         * @param description the description text
         * @return this FluentMeasurementContext instance for fluent chaining
         */
        public FluentMeasurementContext description(String description) {
            contextBuilder.description(description);
            return this;
        }

        /**
         * Adds a tag to the measurement.
         *
         * @param key   the tag key
         * @param value the tag value
         * @return this FluentMeasurementContext instance for fluent chaining
         */
        public FluentMeasurementContext tag(String key, CharSequence value) {
            contextBuilder.tag(key, value);
            return this;
        }

        /**
         * Adds a tag to the measurement.
         *
         * @param key   the tag key
         * @param value the tag value
         * @return this FluentMeasurementContext instance for fluent chaining
         */
        public FluentMeasurementContext tag(String key, String value) {
            contextBuilder.tag(key, value);
            return this;
        }

        /**
         * Adds a tag to the measurement.
         *
         * @param key   the tag key
         * @param value the tag value
         * @return this FluentMeasurementContext instance for fluent chaining
         */
        public FluentMeasurementContext tag(String key, int value) {
            contextBuilder.tag(key, value);
            return this;
        }

        /**
         * Executes the supplied code block, measuring its execution time using the built measurement context.
         *
         * @param block the code block to execute and measure
         * @param <T>   the type of result returned by the code block
         * @return the result of executing the code block
         */
        public <T> T record(Supplier<T> block) {
            requireNonNull(block, "No block provided");
            return measurementTaker.record(contextBuilder.build(), block);
        }

        /**
         * Adds an optional tag to the measurement. If the value is null then the tag isn't added
         *
         * @param key   the tag key
         * @param value the tag value
         * @return this FluentMeasurementContext instance for fluent chaining
         */
        public FluentMeasurementContext optionalTag(String key, String value) {
            contextBuilder.optionalTag(key, value);
            return this;
        }
    }
}



