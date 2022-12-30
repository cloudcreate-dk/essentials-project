# Essentials Components - EventSourced Aggregates

This library focuses on providing different flavours of Event Source Aggregates that are built to work with
the `EventStore` concept.  
The `EventStore` is very flexible and doesn't specify any specific design requirements for an Aggregate or its Events,
except that that have to be associated with an `AggregateType` (see the
`AggregateType` sub section or the `EventStore` section for more information).

This library supports multiple flavours of Aggregate design such as:

- The **modern** `dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.modern.AggregateRoot`
- The *classic* `dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.AggregateRoot`
- The *classic* with separate state
  object `dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.state.AggregateRootWithState`
- The **functional** `dk.cloudcreate.essentials.components.eventsourced.aggregates.flex.FlexAggregate`

The **modern** `AggregateRoot`, *classic* `AggregateRoot` and *classic* `AggregateRootWithState` are all examples of a
mutable `StatefulAggregate` design.

What makes an `Aggregate` design **stateful** is the fact that any changes, i.e. Events applied as the result of calling
command methods on the aggregate instance, are stored within
the `StatefulAggregate` and can be queried using `getUncommittedChanges()` and reset (e.g. after a
transaction/UnitOfWork has completed) using `markChangesAsCommitted()`

Each aggregate loaded or being saved gets associated with the currently active `UnitOfWork`.  
When the `UnitOfWork` is in the commit phase, then the `UnitOfWork` is queries for all changed entities, and the events
stored within the `StatefulAggregate`'s
will be persisted to the `EventStore`.

The `FlexAggregate` follows a functional immutable Aggregate design where each command method returns
the `EventsToPersist` and applying events doesn't alter the state of the aggregate (only rehydration modifies the
aggregate state).

Check the `Order` and `FlexAggregateRepositoryIT` examples
in `essentials-components/eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/flex`

### Modern stateful Order aggregate with a separate state object

See `eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/modern/OrderAggregateRootRepositoryTest.java`
for more details.

```
public class Order extends AggregateRoot<OrderId, OrderEvent, Order> implements WithState<OrderId, OrderEvent, Order, OrderState> {
    /**
     * Used for rehydration
     */
    public Order(OrderId orderId) {
        super(orderId);
    }

    public Order(OrderId orderId,
                 CustomerId orderingCustomerId,
                 int orderNumber) {
        super(orderId);
        requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");

        apply(new OrderEvent.OrderAdded(orderId,
                                        orderingCustomerId,
                                        orderNumber));
    }

    public void addProduct(ProductId productId, int quantity) {
        requireNonNull(productId, "You must provide a productId");
        if (state().accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        apply(new OrderEvent.ProductAddedToOrder(aggregateId(),
                                                 productId,
                                                 quantity));
    }

    public void adjustProductQuantity(ProductId productId, int newQuantity) {
        requireNonNull(productId, "You must provide a productId");
        if (state().accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (state().productAndQuantity.containsKey(productId)) {
            apply(new OrderEvent.ProductOrderQuantityAdjusted(aggregateId(),
                                                              productId,
                                                              newQuantity));
        }
    }

    public void removeProduct(ProductId productId) {
        requireNonNull(productId, "You must provide a productId");
        if (state().accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (state().productAndQuantity.containsKey(productId)) {
            apply(new OrderEvent.ProductRemovedFromOrder(aggregateId(),
                                                         productId));
        }
    }

    public void accept() {
        if (state().accepted) {
            return;
        }
        // Apply the event together with its event order (in case this is needed)
        apply(eventOrder -> new OrderEvent.OrderAccepted(aggregateId(),
                                                         eventOrder));
    }

    /**
     * Covariant return type overriding.<br>
     * This will allow the {@link AggregateRoot#state()} method to return
     * the specific state type, which means we don't need to use e.g. <code>state(OrderState.class).accepted</code><br>
     */
    @SuppressWarnings("unchecked")
    protected OrderState state() {
        return super.state();
    }
}
```

##### Order Events

```
public class OrderEvent {
    public final OrderId orderId;

    public OrderEvent(OrderId orderId) {
        this.orderId = requireNonNull(orderId);
    }

    public static class OrderAdded extends OrderEvent {
        public final CustomerId orderingCustomerId;
        public final long       orderNumber;

        public OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
            super(orderId);
            this.orderingCustomerId = orderingCustomerId;
            this.orderNumber = orderNumber;
        }
    }

    public static class OrderAccepted extends OrderEvent {
        public final EventOrder eventOrder;

        public OrderAccepted(OrderId orderId, EventOrder eventOrder) {
            super(orderId);
            this.eventOrder = eventOrder;
        }
    }

    public static class ProductAddedToOrder extends OrderEvent {
        public final ProductId productId;
        public final int       quantity;

        public ProductAddedToOrder(OrderId orderId, ProductId productId, int quantity) {
            super(orderId);
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    public static class ProductOrderQuantityAdjusted extends OrderEvent {
        public final ProductId productId;
        public final int       newQuantity;

        public ProductOrderQuantityAdjusted(OrderId orderId, ProductId productId, int newQuantity) {
            super(orderId);
            this.productId = productId;
            this.newQuantity = newQuantity;
        }
    }

    public static class ProductRemovedFromOrder extends OrderEvent {
        public final ProductId productId;

        public ProductRemovedFromOrder(OrderId orderId, ProductId productId) {
            super(orderId);
            this.productId = productId;
        }
    }
}
```

##### Modern Order State

```
public class OrderState extends AggregateState<OrderId, OrderEvent, Order> {
     Map<ProductId, Integer> productAndQuantity;
     boolean                 accepted;

    @EventHandler
    private void on(OrderEvent.OrderAdded e) {
        productAndQuantity = new HashMap<>();
    }

    @EventHandler
    private void on(OrderEvent.ProductAddedToOrder e) {
        var existingQuantity = productAndQuantity.get(e.productId);
        productAndQuantity.put(e.productId, e.quantity + (existingQuantity != null ? existingQuantity : 0));
    }

    @EventHandler
    private void on(OrderEvent.ProductOrderQuantityAdjusted e) {
        productAndQuantity.put(e.productId, e.newQuantity);
    }

    @EventHandler
    private void on(OrderEvent.ProductRemovedFromOrder e) {
        productAndQuantity.remove(e.productId);
    }

    @EventHandler
    private void on(OrderEvent.OrderAccepted e) {
        accepted = true;
    }
}
```

#### Modern stateful Order Aggregate without separate state object

```
public class Order extends AggregateRoot<OrderId, OrderEvent, Order> {
    private Map<ProductId, Integer> productAndQuantity;
    private boolean                 accepted;

    /**
     * Used for Aggregate Snapshot deserialization
     */
    public Order() {}

    /**
     * Used for rehydration
     */
    public Order(OrderId orderId) {
        super(orderId);
    }

    public Order(OrderId orderId,
                 CustomerId orderingCustomerId,
                 int orderNumber) {
        this(orderId);
        requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");

        apply(new OrderEvent.OrderAdded(orderId,
                                        orderingCustomerId,
                                        orderNumber));
    }

    public void addProduct(ProductId productId, int quantity) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        apply(new OrderEvent.ProductAddedToOrder(aggregateId(),
                                                 productId,
                                                 quantity));
    }

    public void adjustProductQuantity(ProductId productId, int newQuantity) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (productAndQuantity.containsKey(productId)) {
            apply(new OrderEvent.ProductOrderQuantityAdjusted(aggregateId(),
                                                              productId,
                                                              newQuantity));
        }
    }

    public void removeProduct(ProductId productId) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (productAndQuantity.containsKey(productId)) {
            apply(new OrderEvent.ProductRemovedFromOrder(aggregateId(),
                                                         productId));
        }
    }

    public void accept() {
        if (accepted) {
            return;
        }
        apply(eventOrder -> new OrderEvent.OrderAccepted(aggregateId(),
                                                         eventOrder));
    }

    @EventHandler
    private void on(OrderEvent.OrderAdded e) {
        productAndQuantity = new HashMap<>();
    }

    @EventHandler
    private void on(OrderEvent.ProductAddedToOrder e) {
        var existingQuantity = productAndQuantity.get(e.productId);
        productAndQuantity.put(e.productId, e.quantity + (existingQuantity != null ? existingQuantity : 0));
    }

    @EventHandler
    private void on(OrderEvent.ProductOrderQuantityAdjusted e) {
        productAndQuantity.put(e.productId, e.newQuantity);
    }

    @EventHandler
    private void on(OrderEvent.ProductRemovedFromOrder e) {
        productAndQuantity.remove(e.productId);
    }

    @EventHandler
    private void on(OrderEvent.OrderAccepted e) {
        accepted = true;
    }
}
```

For other examples see:

#### Modern `AggregateRoot`

- With separate `WithState` object using `ReflectionBasedAggregateInstanceFactory`:
    - `eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/modern/with_state/OrderAggregateRootWithStateRepositoryIT.java`
- **Without** separate State object using `ReflectionBasedAggregateInstanceFactory`:
    - `eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/modern/OrderAggregateRootRepositoryIT.java`

#### Functional `FlexAggregate`

- `eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/flex/FlexAggregateRepositoryIT.java`

#### Classic `AggregateRoot`

- Using `ObjenesisAggregateInstanceFactory`:
    - `eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/classic/objenesis/OrderAggregateRootRepositoryIT.java`
- Using `ReflectionBasedAggregateInstanceFactory`:
    - `eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/classic/OrderAggregateRootRepositoryIT.java`

#### Classic `AggregateRootWithState`

- Using `ObjenesisAggregateInstanceFactory`:
    - `eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/classic/objenesis/state/OrderWithStateAggregateRootRepositoryIT.java`
- Using `ReflectionBasedAggregateInstanceFactory`:
    - `eventsourced-aggregates/src/test/java/dk/cloudcreate/essentials/components/eventsourced/aggregates/classic/state/OrderWithStateAggregateRootRepositoryIT.java`

### AggregateType

Each Aggregate implementation class (such as the `Order` Aggregate above) needs to be associated with an `AggregateType`
.  
An `AggregateType` should not be confused with the Java implementation class for your Aggregate.

An `AggregateType` is used for grouping/categorizing multiple `AggregateEventStream` instances related to similar types
of aggregates.  
This allows us to easily retrieve or be notified of new Events related to the same type of Aggregates (such as when
using `EventStore#pollEvents(..)`)     
Using `SeparateTablePerAggregateTypePersistenceStrategy` means that each `AggregateType` will be persisted in a separate
event store table.

What's important here is that the AggregateType is only a name and shouldn't be confused with the Fully Qualified Class
Name of the Aggregate implementation class.  
This is the classical split between the logical concept and the physical implementation.  
It's important to not link the Aggregate Implementation Class (the Fully Qualified Class Name) with the AggregateType
name as that would make refactoring of your code base much harder, as the Fully
Qualified Class Name then would be captured in the stored Events.   
Had the AggregateType and the Aggregate Implementation Class been one and the same, then moving the Aggregate class to
another package or renaming it would break many things.

To avoid the temptation to use the same name for both the AggregateType and the Aggregate Implementation Class, we
prefer using the plural name of the Aggregate as the AggregateType name.  
Example:

| Aggregate-Type | Aggregate Root Implementation Class (Fully Qualified Class Name) | Top-level Event Type (Fully Qualified Class Name) |  
|----------------|------------------------------------------------------------------|---------------------------------------------------|
| Orders         | com.mycompany.project.persistence.Order                          | com.mycompany.project.persistence.OrderEvent      |
| Accounts       | com.mycompany.project.persistence.Account                        | com.mycompany.project.persistence.AccountEvent    |
| Customer       | com.mycompany.project.persistence.Customer                       | com.mycompany.project.persistence.CustomerEvent   |

You can add as many `AggregateType` configurations as needed, but they need to be added BEFORE you try to persist or
load events related to a given `AggregateType`.

### AggregateRoot Repository

To load and persist Aggregates you need an Aggregate Repository.

For the `FlexAggregate` you must acquire a `FlexAggregateRepository` instance using the static `from` method on the `FlexAggregateRepository` interface.

For `StatefulAggregate`'s you must acquire `StatefulAggregateRepository` instance for your Aggregate Root Implementation
Class,  using the static `from` method on the `StatefulAggregateRepository` interface.

Apart from providing an instance of the `EventStore` you also need to provide either an `AggregateTypeConfiguration`,
such as
the `SeparateTablePerAggregateTypeConfiguration` (which instructs the `EventStore`'s persistence strategy, such as
the `SeparateTablePerAggregateTypePersistenceStrategy`
how to map your Java Events into JSON in the Event Store) or use the default configuration provided with the
configured `AggregateEventStreamPersistenceStrategy`
(see the `PostgreSQL Event Store` section for details on configuring the `EventStore`)

```
var orders = AggregateType.of("Orders");
var ordersRepository = StatefulAggregateRepository.from(eventStore,
                                                    SeparateTablePerAggregateTypeConfiguration.standardSingleTenantConfigurationUsingJackson(
                                                        orders,
                                                        createObjectMapper(),
                                                        AggregateIdSerializer.serializerFor(OrderId.class),
                                                        IdentifierColumnType.UUID,
                                                        JSONColumnType.JSONB),
                                                    StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory(), // Alternative is StatefulAggregateInstanceFactory.objenesisAggregateRootFactory()
                                                    Order.class);
// or 
var ordersRepository = StatefulAggregateRepository.from(eventStore,
                                                        orders,
                                                        StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory(),
                                                        Order.class);
                                                    

var orderId = OrderId.random();
unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {                                                    
   var order = new Order(orderId, CustomerId.random(), 1234);
   order.addProduct(ProductId.random(), 2);
   ordersRepository.persist(order);
});

// Using Spring Transaction Template
var order = transactionTemplate.execute(status -> ordersRepository.load(orderId));
```

To use `EventSourced Aggregates` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components/groupId>
    <artifactId>eventsourced-aggregates</artifactId>
    <version>0.8.2</version>
</dependency>
```
