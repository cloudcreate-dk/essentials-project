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

package dk.cloudcreate.essentials.components.foundation.messaging;

import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;

import java.lang.annotation.*;

/**
 * Methods annotated with this Annotation will automatically be called when a {@link PatternMatchingQueuedMessageHandler} and {@link PatternMatchingMessageHandler}
 * receives respectively a {@link QueuedMessage} or a {@link Message} where the {@link Message#getPayload()} matches the type of the first argument/parameter on a method annotated with {@literal @MessageHandler}
 * <p>
 * If the class extends {@link PatternMatchingQueuedMessageHandler} (when used with a {@link DurableQueues} instance) then it allows a second optional argument of type {@link QueuedMessage}<br>
 * If the class extends {@link PatternMatchingMessageHandler} (when used with a {@link Inboxes}/{@link Inbox} or {@link Outboxes}/{@link Outbox}) then it allows a second optional argument of type {@link Message}/{@link OrderedMessage}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MessageHandler {
}