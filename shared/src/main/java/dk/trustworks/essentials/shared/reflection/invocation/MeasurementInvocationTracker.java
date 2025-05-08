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

package dk.trustworks.essentials.shared.reflection.invocation;

import dk.trustworks.essentials.shared.measurement.*;
import dk.trustworks.essentials.shared.reflection.FunctionalInterfaceLoggingNameResolver;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simple {@link LoggerAwareInvocationTracker} that uses {@link MeasurementTaker} to log
 * invocation performance to the log using default thresholds
 */
public class MeasurementInvocationTracker implements LoggerAwareInvocationTracker {
    private              Logger                            logger;
    private              MeasurementTaker                  measurementTaker;
    /**
     * Key: Metod<br>
     * Value: logging-friendly name
     */
    private static final ConcurrentHashMap<Method, String> loggingNameCache = new ConcurrentHashMap<>();

    @Override
    public void trackMethodInvoked(Method method, Object invokeMethodsOn, Duration duration, Object argument) {
        if (logger != null) {
            measurementTaker = MeasurementTaker.builder()
                                               .addRecorder(new LoggingMeasurementRecorder(logger, getLogThresholds()))
                                               .build();
        }
        if (measurementTaker != null) {
            var methodLoggingName = loggingNameCache.computeIfAbsent(method, MeasurementInvocationTracker::getMethodDescription);

            measurementTaker.recordTime(MeasurementContext.builder("essentials.invocation")
                                                          .description("Time it takes to invoke a method")
                                                          .tag("class", FunctionalInterfaceLoggingNameResolver.resolveLoggingName(invokeMethodsOn))
                                                          .tag("method", methodLoggingName)
                                                          .build(),
                                        duration);
        }
    }

    /**
     * Override this method to provide a custom {@link LogThresholds}
     *
     * @return The {@link LogThresholds} to use for logging invocation metrics
     */
    protected LogThresholds getLogThresholds() {
        return LogThresholds.defaultThresholds();
    }

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    private static String getMethodDescription(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", ")) + ")";
    }
}
