# Essentials Java building blocks

Essentials is a set of Java version 17 (and later) building blocks built from the ground up to have no dependencies on other libraries, unless explicitly mentioned.

The Essentials philosophy is to provide high level building blocks and coding constructs that allows for concise and strongly typed code, which doesn't depend on other libraries or frameworks, but
instead allows easy integrations with many of the most popular libraries and frameworks such as Jackson, Spring Boot, Spring Data, JPA, etc.

> **NOTE:**  
> **The library is WORK-IN-PROGRESS**

## Shared

This library contains the smallest set of supporting building blocks needed for other Essentials libraries.

To use `Shared` just add the following Maven dependency:
```
<dependency>
    <groupId>dk.cloudcreate.essentials</groupId>
    <artifactId>shared</artifactId>
    <version>0.30.7</version>
</dependency>
```

`Shared` usually needs additional third party dependencies to work, such as:
```
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>
```

### Tuples

Base Java is missing a simple Tuple library and while there are some excellent Functional libraries for Java, such as VAVR, adding a dependency on these goes against the Essentials philosophy, so
instead we provide the minimum in terms of Tuples support.

We offer two different flavors of Tuples:

- The normal `dk.cloudcreate.essentials.shared.functional.tuple.Tuple` that allows elements of any types
- The `dk.cloudcreate.essentials.shared.functional.tuple.comparable.ComparableTuple` that only allows elements that implement the Comparable interface

Example of using Tuples:

```
Triple<String, Long, BigDecimal> tuple = Tuple.of("Hello", 100L, new BigDecimal("125.95"));
var element1 = tuple._1;
var element2 = tuple._2;
var element3 = tuple._3;

var elements = tuple.toList();

Triple<String, String, String> stringTuple  = tuple.map((_1, _2, _3) -> Tuple.of(_1.toString(), _2.toString(), _3.toString));
Triple<String, String, String> stringTuple2 = tuple.map(Object::toString, Object::toString, Object::toString);

```

### Collections

Different utility functions for working with Collections, such as

`Stream<Pair<Integer, String>> indexedStream = Lists.toIndexedStream(List.of("A", "B", "C"));`

### Functional interfaces

Apart from Tuples, then the `dk.cloudcreate.essentials.shared.functional` package also contain reusable functional interfaces, such as the `TripleFunction`, which is used in the definition of
the `Triple` tuple's map function:

```
public <R1, R2, R3> Triple<R1, R2, R3> map(TripleFunction<? super T1, ? super T2, ? super T3, Triple<R1, R2, R3>> mappingFunction) {
   return mappingFunction.apply(_1, _2, _3);
}
```

or the Checked variant of the classic Functional-Interfaces (`Runnable`, `Consumer`, `Supplier`, `Function`, `BiFunction` and `TripleFunction`)
that behaves like the normal Functional-Interface, but which allows checked `Exception`'s to be thrown from their method:

- `CheckedRunnable`
- `CheckedConsumer`
- `CheckedSupplier`
- `CheckedFunction`
- `CheckedBiFunction`
- `CheckedTripleFunction`

#### Checked `CheckedRunnable` usage example:

Let's say we have a method called `someOperation` that cannot change, but which accepts a `Runnable` with the purpose of the calling `Runnable.run()`.

```
public void someOperation(Runnable operation) {        
    // ... Logic ...           
    operation.run();           
    // ... More logic ---   
}
```

The problem with `Runnable.run()` occurs when a `Runnable` lambda/instance calls an API that throws a checked `Exception`, e.g. the `java.io.File` API.  
Since `Runnable.run()` doesn't define that it throws any `Exception`'s we're forced to add a `try/catch` to handle the `java.io.IOException`
for the code to compile:

```
someOperation(() -> {                       
    try {                           
        // Logic that uses the File API                       
    } catch (IOException e) {                           
        throw new RuntimeException(e);                       
    }                 
}));
```

This is where the `CheckedRunnable` comes to the aid as its `run()` method defines that it throws a Checked `Exception` and its `safe(CheckedRunnable)` method will return a new `Runnable` instance
with a `Runnable.run()` method that ensures that the `run()` method is called and any checked `Exception`'s thrown will be caught and rethrown as a `CheckedExceptionRethrownException`:

```
someOperation(CheckedRunnable.safe(() -> {                       
     // Logic that uses the File API that throws IOException                 
}));
```

### FailFast argument validation (replacements for Objects.requireNonNull)

The `Objects.requireNonNull()` function is nice to have, but it's only limited to checking for null arguments, and it throws a `NullPointerException` which can be misleading.

This is where the `FailFast` class comes in as it supports many more assertion methods which all throw a `IllegalArgumentException` if the argument doesn't pass the assertion:

- `requireMustBeInstanceOf`
- `requireNonBlank`
- `requireTrue`
- `requireFalse`
- `requireNonEmpty`

``` 
import static dk.cloudcreate.essentials.shared.FailFast.*;

public static Optional<Field> findField(Set<Field> fields,
                                        String fieldName,
                                        Class<?> fieldType) {
    requireNonBlank(fieldName, "You must supply a fieldName");
    requireNonNull(fieldType, "You must supply a fieldType");
    
    return requireNonNull(fields, "You must supply a fields set")
                   .stream()
                   .filter(field -> field.getName().equals(fieldName))
                   .filter(field -> field.getType().equals(fieldType))
                   .findFirst();
}
```

### Message formatter that aligns with SLF4J logging messages

Java already provides the `String.format()` method, but switching between it and SLF4J log messages, such as `log.debug("Found {} customers", customers.size());`, doesn't create as coherent code as
some want.

For these cases the `MessageFormatter` provides the simple static `msg()` method which supports the positional SLF4J placeholders `{}`.

`msg` is often used when constructing messages for Exceptions:

```
throw new ReflectionException(msg("Failed to find static method '{}' on type '{}' taking arguments of {}", methodName, type.getName(), Arrays.toString(argumentTypes)));
```

For situations, such as translation, where the arguments are known, but the order of them depends on the actual language text, `MessageFormatter` provides the static `bind()` method, which allows you
to use named placeholders:

Example:

```
var danishText  = "Kære {:firstName} {:lastName}";
var mergedDanishText = MessageFormatter.bind(danishText,
                                             arg("firstName", "John"),
                                             arg("lastName", "Doe"));

assertThat(mergedDanishText).isEqualTo("Kære John Doe");
```

Example 2:

```
var englishText = "Dear {:lastName}, {:firstName}";
var mergedEnglishText = MessageFormatter.bind(englishText,
                                              Map.of("firstName", "John",
                                                     "lastName", "Doe"));
                                                     
assertThat(mergedEnglishText).isEqualTo("Dear Doe, John");
```

### If expression
An **if expression** is an if and else combination, with multiple optional intermediate elseIf's, 
which **returns a value of the evaluation of the if expression**, unlike the normal Java if statement.  
In this way the **if expression** is similar to the Java ternary operation, except that the
**if expression** supports multiple **elseIf**'s.

With the **if expression** you no longer need to write code like this:
```
import static dk.cloudcreate.essentials.shared.logic.IfExpression.If;

int value = getValue();
String description = If(value < 0, "Negative number")
                    .ElseIf(value == 0, "Zero")
                    .Else("Positive number");
```
instead of this
```
int value = getValue();
String description;
if (value < 0) {
   description = "Negative number";
} else if (value == 0) {
   description = "Zero";
} else {
   description = "Positive number";
} 
```

The **if expression** supports both simple boolean predicate/condition and fixed value return values 
as well as lambda predicates and return value suppliers:
```
import static dk.cloudcreate.essentials.shared.logic.IfExpression.If;

OrderId orderId = ...;
Amount orderAmount = ...;

var orderProcessingResult = If(() -> orderAmountExceedsAccountThreshold(orderAmount),
                               () -> cancelOrder(orderId)).
                            Else(() -> acceptOrder(orderId));
```

### `GenericType` for capturing a generic/parameterized argument type

Using this class makes it possible to capture a generic/parameterized argument type ,such as `List<Money>`, instead of having to rely on the classical `.class` construct.   
When you specify a type reference using `.class` you loose any Generic/Parameterized information, as you cannot write `List<Money>.class`, only `List.class`.

With `GenericType` you can specify and capture parameterized type information:  
`var genericType = new GenericType<List<Money>>(){};` 

where genericType.getType() will return `List.class`  
and genericType#getGenericType() will return `ParameterizedType`, which can be introspected further

### `StopWatch` for timing different methods/operations

```
Duration duration = StopWatch.time(CheckedRunnable.safe(() -> someMethodCall()));
```

or operations/method that return a value

```
TimingResult<String> result = StopWatch.time(CheckedSupplier.safe(() -> return someMethodCall()));
Duration duration = result.getDuration();
String result = result.getResult();
```

### `Exceptions`

That support `sneakyThrow` (use with caution) as well as getting a stacktrace as a String.

```
try {
    var duration = time(CheckedRunnable.safe(() -> methodPatternMatcher.invokeMethod(methodToInvoke, argument, invokeMethodsOn, resolvedInvokeMethodWithArgumentOfType)));
} catch (CheckedExceptionRethrownException e) {
    // Unwrap the real cause and rethrow this exception
    sneakyThrow(e.getCause());
}
```

### High level Reflection package

Writing reflection can be cumbersome and there are many checked exception to handle. The `Reflector` class, and it's supporting classes
(`Accessibles`, `BoxedTypes`, `Classes`, `Constructors`, `Fields`, `Interfaces`, `Methods`), makes working with Reflection easier.

Example:

```
Class<?> concreteType = ...;
Object[] arguments = new Object[] { "Test", TestEnum.A };

var reflector = Reflector.reflectOn(concreteType);
if (reflector.hasMatchingConstructorBasedOnArguments(arguments)) {
    return reflector.newInstance(arguments);
} else {
    return reflector.invokeStatic("of", arguments);
}
```

### Reflective `PatternMatchingMethodInvoker`

Which supports creating your own reflective pattern matching method invokers.

Example using `PatternMatchingMethodInvoker` together with the provided `SingleArgumentAnnotatedMethodPatternMatcher`:

```
public class OrderEventHandler {
    private final PatternMatchingMethodInvoker patternMatchingInvoker;
    
    public OrderEventHandler() {
      patternMatchingInvoker = new PatternMatchingMethodInvoker<>(testSubject,
                                                                  new SingleArgumentAnnotatedMethodPatternMatcher<>(EventHandler.class,
                                                                                                                    OrderEvent.class),
                                                                  InvocationStrategy.InvokeMostSpecificTypeMatched);
    }
    
    public void handle(OrderEvent orderEvent) {
      // Find the single best matching method annotated with @EventHandler and invoke it based on the orderEvent argument
      patternMatchingInvoker.invoke(orderCreated);
    }

    @EventHandler
    private void orderEvent(OrderEvent orderEvent) {
      // Fallback event handler that will be called for e.g. OrderAccepted event as there isn't a method that explicitly handle this event
    }

    @EventHandler
    private void orderCreated(OrderCreated orderCreated) {
    }

    @EventHandler
    private void orderCancelled(OrderCancelled orderCancelled) {
    }
}
```

### MessageTemplate, MessageTemplates and Message

#### MessageTemplate

The `MessageTemplate` concept supports structured messages with typed parameters.
Each `MessageTemplate` instance has a unique `key` that clearly identifies the `MessageTemplate`.  
`MessageTemplate` keys can be nested, to support message hierarchies:
```
// Has key: "ESSENTIALS"
MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");

// Has key: "ESSENTIALS.VALIDATION"
MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");

// Has key: "ESSENTIALS.VALIDATION.AMOUNT_TOO_HIGH"
MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = VALIDATION.key2("AMOUNT_TOO_HIGH",
                                                                         "Amount {0} is higher than {1}");

// Has key: "ESSENTIALS.VALIDATION.AMOUNT_TOO_LOW"
MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_LOW = VALIDATION.key2("AMOUNT_TOO_LOW",
                                                                        "Amount {0} is lower than {1}");
```

There are multiple `MessageTemplate` subclasses that support a different number of parameters:
- `MessageTemplate0` which supports 0 parameters (mostly used for root or nested keys)
- `MessageTemplate1` which supports 1 parameter
- `MessageTemplate2` which supports 2 parameters
- `MessageTemplate3` which supports 3 parameters
- `MessageTemplate4` which supports 4 parameters

Example defining a `MessageTemplate4`'s:
```java
// Has key: "ESSENTIALS"
MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");

// Has key: "ESSENTIALS.ACCOUNT_OVERDRAWN"
MessageTemplate4<String, BigDecimal, BigDecimal, LocalDate> ACCOUNT_OVERDRAWN = ROOT.key4("ACCOUNT_OVERDRAWN",
                                                                                          "Account {0} is overdrawn by ${1}. A fee of ${2} will be debited on the {3}");
```

#### Message
From a `MessageTemplate` we can create `Message` instances, which are useful for e.g. error/validation reporting.  
A `Message` is an instance of a `MessageTemplate` with parameters bound to it.

Example:
```java
// Has key: "ESSENTIALS"
MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");

// Has key: "ESSENTIALS.ACCOUNT_OVERDRAWN"
MessageTemplate4<String, BigDecimal, BigDecimal, LocalDate> ACCOUNT_OVERDRAWN = ROOT.key4("ACCOUNT_OVERDRAWN",
                                                                                          "Account {0} is overdrawn by ${1}. A fee of ${2} will be debited on the {3}");

String accountId = "Account1";
BigDecimal overdrawnAmount = new BigDecimal("125");
BigDecimal feeAmount = new BigDecimal("10");
LocalDate  feeDebitDate =  LocalDate.of(2023, 2, 25);
Message msg = ACCOUNT_OVERDRAWN.create(accountId,
                                       overdrawnAmount,
                                       feeAmount,
                                       feeDebitDate);

}
```

This will create a `Message` with `Message#getMessage()`:
`"Account Account1 is overdrawn by $125. A fee of $10 will be debited on the 2023-2-25"` (date formatting is dependent on the {@link java.util.Locale})

#### MessageTemplates
`MessageTemplates` is a marker interface for classes or interfaces to contain {@link MessageTemplate} fields, which can be queried using 
`MessageTemplates.getMessageTemplates(Class, boolean)`

Example:
```java
public interface MyMessageTemplates extends MessageTemplates {
    // Has key: "ESSENTIALS"
    MessageTemplate0 ROOT = MessageTemplates.root("ESSENTIALS");

    // Has key: "ESSENTIALS.VALIDATION"
    MessageTemplate0 VALIDATION = ROOT.subKey("VALIDATION");

    // Has key: "ESSENTIALS.VALIDATION.AMOUNT_TOO_HIGH"
    MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_HIGH = VALIDATION.key2("AMOUNT_TOO_HIGH",
                                                                             "Amount {0} is higher than {1}");

    // Has key: "ESSENTIALS.VALIDATION.AMOUNT_TOO_LOW"
    MessageTemplate2<BigDecimal, BigDecimal> AMOUNT_TOO_LOW = VALIDATION.key2("AMOUNT_TOO_LOW",
                                                                            "Amount {0} is lower than {1}");

    // Has key: "ESSENTIALS.BUSINESS_RULES"
    MessageTemplate0 BUSINESS_RULES = ROOT.subKey("BUSINESS_RULES");

    // Has key: "ESSENTIALS.BUSINESS_RULES.ACCOUNT_NOT_ACTIVATED"
    MessageTemplate1<String> ACCOUNT_NOT_ACTIVATED = BUSINESS_RULES.key1("ACCOUNT_NOT_ACTIVATED",
                                                                         "Account {0} is not activated");
}
``` 

