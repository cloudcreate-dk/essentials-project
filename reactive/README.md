# Essentials Java building blocks

Essentials is a set of Java version 17 (and later) building blocks built from the ground up to have no dependencies on other libraries, unless explicitly mentioned.

The Essentials philosophy is to provide high level building blocks and coding constructs that allows for concise and strongly typed code, which doesn't depend on other libraries or frameworks, but
instead allows easy integrations with many of the most popular libraries and frameworks such as Jackson, Spring Boot, Spring Data, JPA, etc.

## Reactive

This library contains the smallest set of supporting reactive building blocks needed for other Essentials libraries.

To use `Reactive` just add the following Maven dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>reactive</artifactId>
    <version>0.40.1</version>
</dependency>
```

`Reactive` usually needs additional third party dependencies to work, such as:
```
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
</dependency>
```

And for Spring support dependencies such as:
```
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-beans</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
```

### LocalEventBus
Simple event bus that supports both synchronous and asynchronous subscribers that are registered and listening for events published within the local the JVM  
You can have multiple instances of the `LocalEventBus` deployed with the local JVM, but usually one event bus is sufficient.

```
LocalEventBus localEventBus    = new LocalEventBus("TestBus", 3, (failingSubscriber, event, exception) -> log.error("...."));
                  
localEventBus.addAsyncSubscriber(orderEvent -> {
           ...
       });

localEventBus.addSyncSubscriber(orderEvent -> {
             ...
       });
                  
localEventBus.publish(new OrderCreatedEvent());
```

If you wish to colocate multiple related Event handling methods inside the same class and use it together with the 
`LocalEventBus` then you can extend the `AnnotatedEventHandler` class and annotate each event handler method with the
`@Handler` annotation.

```
public class OrderEventsHandler extends AnnotatedEventHandler {

    @Handler
    void handle(OrderCreated event) {
    }

    @Handler
    void handle(OrderCancelled event) {
    }
}
```

### CommandBus/LocalCommandBus
The `LocalCommandBus` provides the default implementation for the `CommandBus` concepts, which provides an indirection between a command and the `CommandHandler` 
that's capable of handling the command.  
The `CommandBus` pattern provides location transparency, the sender of the command doesn't need to know which `CommandHandler` supports
the given command.   

There MUST always be one and only one `CommandHandler` capable of handling a given Command.  
All other cases will result in either a `NoCommandHandlerFoundException` or `MultipleCommandHandlersFoundException` being thrown.

The handling of a command usually doesn't return any value (according to the principles of CQRS), however the `LocalCommandBus` API allows
a `CommandHandler` to return a value if needed (e.g. such as a server generated id)  

Commands can be:
- Sent synchronously using `#send(Object)`  
- Sent asynchronously using `#sendAsync(Object)` which returns a `Mono` that will contain the result of the command handling
- Send asynchronously without waiting for the result of processing the Command using `#sendAndDontWait(Object)`/`#sendAndDontWait(Object, Duration)`
  - If you need durability for the Commands sent using `sendAndDontWait` please use `DurableLocalCommandBus` from `essentials-components`'s `foundation` instead.

```
var commandBus = new LocalCommandBus();
commandBus.addCommandHandler(new CreateOrderCommandHandler(...));
commandBus.addCommandHandler(new ImburseOrderCommandHandler(...));
 
var optionalResult = commandBus.send(new CreateOrder(...));
// or
var monoWithOptionalResult = commandBus.sendAsync(new ImbuseOrder(...))
                                       .block(Duration.ofMillis(1000));
```

In case you need to colocate multiple related command handling methods inside a single class then you 
should have your command handling class extend `AnnotatedCommandHandler` and annotate each command handler method with either 
the `@Handler` or `@CmdHandler` annotation.

Example:  
```
public class OrdersCommandHandler extends AnnotatedCommandHandler {

     @Handler
     private OrderId handle(CreateOrder cmd) {
        ...
     }

     @CmdHandler
     private void someMethod(ReimburseOrder cmd) {
        ...
     }
}
```

## Command Interceptors
You can register `CommandBusInterceptor`'s (such as the `dk.cloudcreate.essentials.components.foundation.reactive.command.UnitOfWorkControllingCommandBusInterceptor`
from the PostgresqlEventStore module from [Essentials Components](https://github.com/cloudcreate-dk/essentials-components)), which allows you to 
intercept Commands before and after they're being handled by the `LocalCommandBus`

Example:
```
var commandBus = new LocalCommandBus(new UnitOfWorkControllingCommandBusInterceptor(unitOfWorkFactory));
```

You can also add interceptors to the `LocalCommandBus` later:
```
var commandBus = new LocalCommandBus();
commandBus.addInterceptor(new UnitOfWorkControllingCommandBusInterceptor(unitOfWorkFactory));
```

## Spring support
When using Spring or Spring Boot it will be easier to register the `LocalEventBus`, `LocalCommandBus`, `CommandHandler` and `EventHandler` instances as `@Bean`'s or `@Component`'s
and automatically have the `CommandHandler` beans registered as with the single `LocalCommandBus` bean and the `EventHandler` beans registered as subscribers with the single `LocalEventBus` bean.

All you need to do is to add a `@Bean` of type `ReactiveHandlersBeanPostProcessor`:

```
@Configuration
public class ReactiveHandlersConfiguration {
    @Bean ReactiveHandlersBeanPostProcessor reactiveHandlersBeanPostProcessor() {
        return new ReactiveHandlersBeanPostProcessor();
    }
    
    @Bean
    public LocalEventBus localEventBus() {
        return new LocalEventBus("Test", 3, (failingSubscriber, event, exception) -> log.error(msg("Error for '{}' handling {}", failingSubscriber, event), exception));
    }
    
    @Bean
    public LocalCommandBus localCommandBus() {
        return new LocalCommandBus();
    }
}
```

`EventHandler`'s and `CommandHandler`'s can be registered in Spring as `@Beans` or `@Components`:
```
@Component
public static class MyCommandHandler implements CommandHandler {

    @Override
    public boolean canHandle(Class<?> commandType) {
        return ...;
    }

    @Override
    public Object handle(Object command) {
        ...
    }
}

@Component
public static class MyEventHandler implements EventHandler {
    @Override
    public void handle(Object e) {
        ...
    }
}
```
Per default the `EventHandler`'s are registered as **synchronous** event subscribers, unless you add the `@AsyncEventHandler` to the `EventHandler` class, in which case the `EventHandler` is 
registered as an asynchronous subscriber:
```
@Component
@AsyncEventHandler
public static class MyEventHandler implements EventHandler {
    @Override
    public void handle(Object e) {
        ...
    }
}
```