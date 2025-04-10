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

package dk.cloudcreate.essentials.components.foundation.interceptor.micrometer;

import dk.cloudcreate.essentials.components.foundation.messaging.MessageHandler;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.MessageHandlerInterceptor;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.operation.InvokeMessageHandlerMethod;
import dk.cloudcreate.essentials.shared.interceptor.InterceptorChain;
import dk.cloudcreate.essentials.shared.measurement.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Measure {@link MessageHandler} annotated methods processing time using the {@link MeasurementTaker} API.
 * <p>
 * The metric name is {@value #METRIC} and any dynamic parameters (e.g. message_type, message_handler_class, message_handler_method)
 * are added as tags.
 */
public class RecordExecutionTimeMessageHandlerInterceptor implements MessageHandlerInterceptor {
    public static final  String                            MODULE_TAG_NAME  = "Module";
    public static final  String                            METRIC           = "essentials.messaging.message_handler";
    private final        MeasurementTaker                  measurementTaker;
    /**
     * Key: Metod<br>
     * Value: logging-friendly name
     */
    private static final ConcurrentHashMap<Method, String> loggingNameCache = new ConcurrentHashMap<>();


    private final boolean recordExecutionTimeEnabled;
    private final String  moduleTag;

    /**
     * Constructs a new interceptor.
     *
     * @param meterRegistryOptional      an Optional MeterRegistry to enable Micrometer metrics
     * @param recordExecutionTimeEnabled whether to record execution times or not
     * @param thresholds                 the logging thresholds configuration
     * @param moduleTag                  Optional {@value #MODULE_TAG_NAME} Tag value
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public RecordExecutionTimeMessageHandlerInterceptor(Optional<MeterRegistry> meterRegistryOptional,
                                                        boolean recordExecutionTimeEnabled,
                                                        LogThresholds thresholds,
                                                        String moduleTag) {
        this.recordExecutionTimeEnabled = recordExecutionTimeEnabled;
        this.moduleTag = moduleTag;
        this.measurementTaker = MeasurementTaker.builder()
                                                .addRecorder(
                                                        new LoggingMeasurementRecorder(
                                                                LoggerFactory.getLogger(this.getClass()),
                                                                thresholds))
                                                .withOptionalMicrometerMeasurementRecorder(meterRegistryOptional)
                                                .build();
    }

    @Override
    public void intercept(InvokeMessageHandlerMethod operation, InterceptorChain<InvokeMessageHandlerMethod, Void, MessageHandlerInterceptor> interceptorChain) {
        if (recordExecutionTimeEnabled) {
            var methodLoggingName = loggingNameCache.computeIfAbsent(operation.methodToInvoke, method -> getMethodDescription(operation));
            measurementTaker.context(METRIC)
                            .description("Time taken to handle a message")
                            .tag("message_handler_class", operation.methodToInvoke.getDeclaringClass().getSimpleName())
                            .tag("message_handler_method", methodLoggingName)
                            .tag("message_type", operation.resolvedInvokeMethodWithArgumentOfType.getName())
                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                            .record(interceptorChain::proceed);
        } else {
            interceptorChain.proceed();
        }
    }

    private static String getMethodDescription(InvokeMessageHandlerMethod operation) {
        return operation.methodToInvoke.getName() + "(" + Arrays.stream(operation.methodToInvoke.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", ")) + ")";
    }
}
