/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward;

/**
 * Defines how messages can be consumed by the provided message consumer
 */
public enum MessageConsumptionMode {
    /**
     * Only a single consumer instance in a cluster will be allowed to consume messages at a time (supports failover if the given consumer is shutdown or crashes)
     */
    SingleGlobalConsumer,
    /**
     * Multiple consumers in a cluster can compete to handle messages, but a message will only be handled a single consumer
     */
    GlobalCompetingConsumers
}
