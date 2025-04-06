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

import dk.cloudcreate.essentials.components.foundation.messaging.queue.QueuedMessage;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.reactive.command.interceptor.*;
import dk.cloudcreate.essentials.shared.measurement.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Measure the time {@link CommandBus#send(Object)}/{@link CommandBus#sendAsync(Object)} and {@link CommandBus#sendAndDontWait(Object)}
 * take to process the command (this includes the time it takes
 * to handle the command by the selected {@link CommandHandler} measured using the {@link MeasurementTaker} API.
 * <p>
 * The metric name always begins with {@value #METRIC_PREFIX} and any dynamic parameters (e.g. command_type) are added as tags.
 */
public class RecordExecutionTimeCommandBusInterceptor implements CommandBusInterceptor {
    private final       MeasurementTaker measurementTaker;
    public static final String           MODULE_TAG_NAME = "Module";
    public static final String           METRIC_PREFIX   = "essentials.reactive.commandbus";
    private final       boolean          recordExecutionTimeEnabled;
    private final       String           moduleTag;

    /**
     * Constructs a new interceptor.
     *
     * @param meterRegistryOptional      an Optional MeterRegistry to enable Micrometer metrics
     * @param recordExecutionTimeEnabled whether to record execution times or not
     * @param thresholds                 the logging thresholds configuration
     * @param moduleTag                  Optional {@value #MODULE_TAG_NAME} Tag value
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public RecordExecutionTimeCommandBusInterceptor(Optional<MeterRegistry> meterRegistryOptional,
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
    public Object interceptSend(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + ".send")
                                   .description("Time taken to handle a command sent using send")
                                   .tag("command_type", command.getClass().getName())
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(commandBusInterceptorChain::proceed);
        } else {
            return commandBusInterceptorChain.proceed();
        }
    }

    @Override
    public Object interceptSendAsync(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
        if (recordExecutionTimeEnabled) {
            return measurementTaker.context(METRIC_PREFIX + ".sendAsync")
                                   .description("Time taken to handle a command sent using sendAsync")
                                   .tag("command_type", command.getClass().getName())
                                   .optionalTag(MODULE_TAG_NAME, moduleTag)
                                   .record(commandBusInterceptorChain::proceed);
        } else {
            return commandBusInterceptorChain.proceed();
        }
    }

    @Override
    public void interceptSendAndDontWait(Object commandMessage, CommandBusInterceptorChain commandBusInterceptorChain) {
        if (recordExecutionTimeEnabled) {
            var commandType = commandMessage.getClass().getName();
            if (commandMessage instanceof QueuedMessage queuedMessage && queuedMessage.getMessage().getPayload() != null) {
                commandType = queuedMessage.getMessage().getPayload().getClass().getName();
            }
            measurementTaker.context(METRIC_PREFIX + ".sendAndDontWait")
                            .description("Time taken to handle a command sent using sendAndDontWait")
                            .tag("command_type", commandType)
                            .optionalTag(MODULE_TAG_NAME, moduleTag)
                            .record(commandBusInterceptorChain::proceed);
        } else {
            commandBusInterceptorChain.proceed();
        }
    }
}
