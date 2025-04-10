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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.cloudcreate.essentials.types.*;

/**
 * Encapsulates the name of an Event as opposed to the {@link EventType}<br>
 * The Event Name is used if only a JSON String payload representing a "CustomerRegistered" event is persisted (in the PersistedEvent)
 * as opposed as an actual typed Event (i.e., a custom class such as a com.company.product.events.CustomerRegistered class)
 * @see EventTypeOrName
 */
public final class EventName extends CharSequenceType<EventName> implements Identifier {
    public EventName(CharSequence value) {
        super(value);
    }

    public EventName(String value) {
        super(value);
    }

    public static EventName of(CharSequence value) {
        return new EventName(value);
    }
}
