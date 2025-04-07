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

import org.slf4j.Logger;

import java.time.Duration;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * A {@code MeasurementRecorder} implementation that logs the measurement using configurable thresholds.
 */
public class LoggingMeasurementRecorder implements MeasurementRecorder {
    private final Logger logger;
    private final LogThresholds thresholds;

    /**
     * Constructs a new LoggingMeasurementRecorder.
     *
     * @param logger     the logger to log the measurement messages
     * @param thresholds the thresholds for logging at different levels
     */
    public LoggingMeasurementRecorder(Logger logger, LogThresholds thresholds) {
        this.logger = requireNonNull(logger, "No logger provided");
        this.thresholds = requireNonNull(thresholds, "No thresholds provided");
    }

    @Override
    public void record(MeasurementContext context, Duration duration) {
        requireNonNull(context, "No context provided");
        requireNonNull(duration, "No duration provided");

        var millis = duration.toMillis();
        var logMessage = String.format("Measurement [%s]: %d ms. Tags: %s: %s",
                                          context.getMetricName(),
                                          millis,
                                          context.getTags(),
                                          context.getDescription());
        if (millis >= thresholds.getError()) {
            logger.error(logMessage);
        } else if (millis >= thresholds.getWarn()) {
            logger.warn(logMessage);
        } else if (millis >= thresholds.getInfo()) {
            logger.info(logMessage);
        } else if (millis >= thresholds.getDebug()) {
            logger.debug(logMessage);
        }  else {
            logger.trace(logMessage);
        }
    }
}
