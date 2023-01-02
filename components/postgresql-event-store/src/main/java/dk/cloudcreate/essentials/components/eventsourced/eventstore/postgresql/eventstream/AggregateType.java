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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypePersistenceStrategy;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.EventStoreSubscriptionManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.types.*;

/**
 * The primary concept of the {@link EventStore} are <b>Event Streams</b><br>
 * Definition: <b>An Event Stream is a collection of related Events.</b><br>
 * The most common denominator for Events in an Event Stream is the <b>Type of Aggregate</b> they're associated with.<br>
 * Classical examples of Aggregate Types and their associated events are:
 * <ul>
 *     <li><b>Order</b> aggregate<br>
 *     <i>Examples of Order Events:</i>
 *          <ul>
 *              <li>OrderCreated</li>
 *              <li>ProductAddedToOrder</li>
 *              <li>ProductRemoveFromOrder</li>
 *              <li>OrderAccepted</li>
 *          </ul>
 *      </li>
 *     <li><b>Account</b> aggregate<br>
 *     <i>Examples of Account Events:</i>
 *      <ul>
 *         <li>AccountRegistered</li>
 *         <li>AccountCredited</li>
 *         <li>AccountDebited</li>
 *      </ul>
 *     </li>
 *     <li><b>Customer</b> aggregate<br>
 *     <i>Examples of Customer Events:</i>
 *     <ul>
 *         <li>CustomerRegistered</li>
 *         <li>CustomerMoved</li>
 *         <li>CustomersAddressCorrected</li>
 *         <li>CustomerStatusChanged</li>
 *     </ul>
 *     </li>
 * </ul>
 * <br>
 * We could put all Events from all Aggregate Types into one Event Stream, but this is often <b>not very useful</b>:
 * <ul>
 *     <li>From a usage and use case perspective it makes more sense to subscribe and handle events related to the same type of Aggregates separate from
 *     the handling of other Events related to other types of Aggregates.<br>
 *     E.g. it makes more sense to handle Order related Events separate from Account related Events
 *     </li>
 *     <li>Using the {@link SeparateTablePerAggregateTypePersistenceStrategy} we can store all Events related to a specific Aggregate Type in a separate table
 *     from other Aggregate types, which is more efficient and allows us to store many more Events related to this given {@link AggregateType}.<br>
 *     This allows use to use the {@link PersistedEvent#globalEventOrder()} to track the order in which Events, related to the same type of Aggregate, were persisted.<br>
 *     This also allows us to use the {@link GlobalEventOrder} as a natual Resume-Point for the {@link EventStore} subscriptions (see {@link EventStoreSubscriptionManager}
 *     </li>
 * </ul
 * <p>
 * This aligns with the concept of the {@link AggregateEventStream}, which contains Events related to a specific {@link AggregateType} with a distinct <b>AggregateId</b><br>
 * When loading/fetching and persisting/appending Events we always work at the Aggregate instance level, i.e. with {@link AggregateEventStream}'s.<br>
 * <br>
 * The {@link AggregateType} is used for grouping/categorizing multiple {@link AggregateEventStream} instances related to
 * similar types of aggregates.<br>
 * Unless you're using a fully functional style aggregate where you only perform a Left-Fold of all Events in an {@link AggregateEventStream}, then there will typically be a
 * 1-1 relationship between an {@link AggregateType} and the class that implements the Aggregate.<br>
 * <br>
 * What's important here is that the {@link AggregateType} is only a <b>name</b> and shouldn't be confused with the Fully Qualified Class Name of the Aggregate implementation class.<br>
 * This is the classical split between the logical concept and the physical implementation. It's important to not link the Aggregate Implementation Class (the Fully Qualified Class Name)
 * with the {@link AggregateType} name as that would make refactoring of your code base much harder, as the Fully Qualified Class Name then would be captured in the stored Events.<br>
 * Had the {@link AggregateType} and the Aggregate Implementation Class been one and the same, then moving the Aggregate class to another package or renaming it would break many things.<br>
 * To avoid the temptation to use the same name for both the {@link AggregateType} and the Aggregate Implementation Class, we prefer using the <b>plural name</b> of the Aggregate as the {@link AggregateType} name.<br>
 * Example:
 * <table>
 *     <tr><td>Aggregate-Type</td><td>Aggregate Implementation Class (Fully Qualified Class Name)</td><td>Top-level Event Type (Fully Qualified Class Name)</td></tr>
 *     <tr><td>Orders</td><td>com.mycompany.project.persistence.Order</td><td>com.mycompany.project.persistence.OrderEvent</td></tr>
 *     <tr><td>Accounts</td><td>com.mycompany.project.persistence.Account</td><td>com.mycompany.project.persistence.AccountEvent</td></tr>
 *     <tr><td>Customer</td><td>com.mycompany.project.persistence.Customer</td><td>com.mycompany.project.persistence.CustomerEvent</td></tr>
 * </table>
 */
public class AggregateType extends CharSequenceType<AggregateType> implements Identifier {
    public AggregateType(CharSequence value) {
        super(value);
    }

    public static AggregateType of(CharSequence value) {
        return new AggregateType(value);
    }
}
