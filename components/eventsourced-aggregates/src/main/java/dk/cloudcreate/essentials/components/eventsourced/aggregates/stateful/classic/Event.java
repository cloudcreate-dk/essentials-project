/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

/**
 * Based Event type that's built to work in combination with {@link AggregateRoot}<br>
 * All you need to do is to inherit from this class when building your own {@link Event} types.<p>
 * <b>Note:</b> You only have to supply the Aggregate ID, through {@link Event#aggregateId()}, for the FIRST/initial {@link Event}
 * that's being applied to the {@link AggregateRoot} using the {@link AggregateRoot#apply(Event)} method.<br>
 * Every consecutive {@link Event} applied will have its {@link Event#aggregateId(Object)} method called IF it doesn't already have a value. I.e. you can be lazy and skip setting the aggregate id
 * on the Event if you don't want to.<br>
 * The {@link AggregateRoot} also automatically keeps track of the {@link Event#eventOrder()} value and will set it for you and ensure that it's growing consecutively.
 *
 * @param <ID> the aggregate id type
 */
public abstract class Event<ID> {
    private ID         aggregateId;
    private EventOrder eventOrder;

    /**
     * Get the id of the aggregate this event relates to. As a developer you only need to supply this value yourself for the very FIRST {@link Event} that is being applied<br>
     * Every consecutive {@link Event} applied will have its {@link Event#aggregateId(Object)} method called IF it doesn't already have a value. I.e. you can be lazy and skip setting the aggregate id
     * on the Event if you don't want to.
     */
    public ID aggregateId() {
        return aggregateId;
    }

    /**
     * Set the id of the aggregate this event relates to. As a developer you only need to set this value yourself for the very FIRST {@link Event} that is being applied<br>
     * Every consecutive {@link Event} applied will have its {@link Event#aggregateId(Object)} method called IF it doesn't already have a value. I.e. you can be lazy and skip setting the aggregate id
     * on the Event if you don't want to.
     *
     * @param aggregateId the id of the aggregate this even relates to
     */
    public void aggregateId(ID aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * Contains the order of the event relative to the aggregate instance (the {@link #aggregateId}) it relates to<br>
     * This is also commonly called the sequenceNumber and it's a sequential ever growing number that tracks the order in which events have been stored in the {@link EventStore}
     * related to a <b>specific</b> aggregate instance (as opposed to the <b>globalOrder</b> that contains the order of ALL events related to a given {@link AggregateType})<p>
     * {@link #eventOrder()} is zero based, i.e. the first event has order value ZERO (0)<p>
     * The {@link AggregateRoot} automatically keeps track of the {@link Event#eventOrder()} value and will set it for you and ensure that it's growing consecutively
     */
    public EventOrder eventOrder() {
        return eventOrder;
    }

    /**
     * Set the event order - called by {@link AggregateRoot}
     *
     * @param eventOrder the order of the event for the aggregate instance the event relates to
     */
    public void eventOrder(EventOrder eventOrder) {
        this.eventOrder = eventOrder;
    }
}
