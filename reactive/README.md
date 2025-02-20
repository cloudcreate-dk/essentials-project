# Essentials Java building blocks

Essentials is a set of Java version 17 (and later) building blocks built from the ground up to have no dependencies on other libraries, unless explicitly mentioned.

The Essentials philosophy is to provide high level building blocks and coding constructs that allows for concise and strongly typed code, which doesn't depend on other libraries or frameworks, but
instead allows easy integrations with many of the most popular libraries and frameworks such as Jackson, Spring Boot, Spring Data, JPA, etc.

> **NOTE:**  
> **The library is WORK-IN-PROGRESS**

## Reactive

This library contains the smallest set of supporting reactive building blocks needed for other Essentials libraries.

To use `Reactive` just add the following Maven dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>reactive</artifactId>
    <version>0.40.22</version>
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

# CommandBus / LocalCommandBus / DurableLocalCommandBus

The `CommandBus` pattern decouples the sending of commands from their handling by introducing an indirection between the command and its corresponding `CommandHandler`.  
This design provides **location transparency**, meaning that the sender does not need to know which handler will process the command.  
The `LocalCommandBus` is the default implementation of the CommandBus pattern.

## Single CommandHandler Requirement

- **Exactly One Handler:** Every command must have one, and only one, `CommandHandler`.
  - If **no handler is found**, a `NoCommandHandlerFoundException` is thrown.
  - If **more than one handler is found**, a `MultipleCommandHandlersFoundException` is thrown.

## Command Processing and Return Values

- **No Expected Return Value:** According to CQRS principles, command handling typically does not return a value.
- **Optional Return Value:** The API allows a `CommandHandler` to return a value if necessary (for example, a server-generated ID).

## Sending Commands

Commands can be dispatched in different ways depending on your needs:

1. **Synchronous processing**
  - **Method:** `CommandBus.send(Object)`
  - **Usage:** Use this when you need to process a command immediately and receive instant feedback on success or failure.

2. **Asynchronous processing with Feedback**
  - **Method:** `CommandBus.sendAsync(Object)`
  - **Usage:** Returns a `Mono` that will eventually contain the result of the command processing. This is useful when you want asynchronous processing but still need to know the outcome.

3. **Fire-and-Forget Asynchronous Processing**
  - **Method:** `CommandBus.sendAndDontWait(Object)` or `CommandBus.sendAndDontWait(Object, Duration)`
  - **Usage:** Sends a command without waiting for a result. This is true asynchronous fire-and-forget processing.
  - **Note:** If durability is required (ensuring the command is reliably processed), consider using `DurableLocalCommandBus` from the `essentials-components`'s `foundation` module instead.

## Handling Command Processing Failures

### Using `send` / `sendAsync`

- **Immediate Error Feedback:** These methods provide quick feedback if command processing fails (e.g., due to business validation errors), making error handling straightforward.

### Using `sendAndDontWait`

- **Delayed Asynchronous processing:** Commands sent via this method are processed in a true asynchronous fire-and-forget fashion, with an optional delay.
- **Spring Boot Starters Default:** When using the Essentials Spring Boot starters, the default `CommandBus` implementation is `DurableLocalCommandBus`, which places `sendAndDontWait` commands on a `DurableQueue` for reliable processing.
- **Exception Handling:** Because processing is asynchronous fire-and-forget (and because commands may be processed on a different node or after a JVM restart), exceptions can not be captured by the caller.
  - **Error Management:** The `SendAndDontWaitErrorHandler` takes care of handling exceptions that occur during asynchronous command processing.
  - **Retries:** The `DurableLocalCommandBus` is configured with a `RedeliveryPolicy` to automatically retry commands that fail.

## Summary

- **For Immediate Feedback:**  
  Use `CommandBus.send` or `CommandBus.sendAsync` to simplify error handling with instant feedback on command processing.

- **For True Asynchronous, Fire-and-Forget Processing:**  
  Use `CommandBus.sendAndDontWait` when you require asynchronous processing with retry capabilities.
  - Be aware that error handling is deferred to the `SendAndDontWaitErrorHandler` and managed by any configured `RedeliveryPolicy`.

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
    @Bean static ReactiveHandlersBeanPostProcessor reactiveHandlersBeanPostProcessor() {
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