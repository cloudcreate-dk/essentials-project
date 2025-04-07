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

import io.micrometer.core.instrument.*;

import java.time.Duration;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * A {@code MeasurementRecorder} implementation that uses Micrometer's {@code Timer} to record execution times.
 */
public class MicrometerMeasurementRecorder implements MeasurementRecorder {
    private final MeterRegistry meterRegistry;

    /**
     * Constructs a new MicrometerMeasurementRecorder.
     *
     * @param meterRegistry the MeterRegistry instance to use for recording metrics
     */
    public MicrometerMeasurementRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry, "No meterRegistry provided");
    }

    @Override
    public void record(MeasurementContext context, Duration duration) {
        requireNonNull(context, "No context provided");
        requireNonNull(duration, "No duration provided");

        var tagArray = context.getTags().entrySet().stream()
                              .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                              .toArray(String[]::new);

        Timer.builder(context.getMetricName())
             .description(context.getDescription())
             .tags(tagArray)
             .publishPercentileHistogram()
             .publishPercentiles(0.95, 0.99)
             .register(meterRegistry)
             .record(duration);
    }
}
