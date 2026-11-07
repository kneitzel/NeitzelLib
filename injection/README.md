# Injection

## Introduction

`injection` is a small container. You mark concrete classes, it scans a package, keeps one shared instance of each scanned class, and fills dependencies by type. [`fx`](../fx/README.md) uses it for controllers, components, and converters. You can use it without JavaFX.

The worked example is the person desk in `fx-example`, package `de.neitzel.fx.example`. This guide uses that desk for every feature the desk exercises, then covers the container rules the desk does not hit.

## Quick start

Depend on the module. It brings Reflections and `jakarta.inject-api`. The parent artifact is `de.neitzel.lib:neitzellib:1.0-SNAPSHOT`. Java 21.

```xml
<dependency>
    <groupId>de.neitzel.lib</groupId>
    <artifactId>injection</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Mark a class with `jakarta.inject.Singleton` or `jakarta.inject.Named`, and take dependencies with `jakarta.inject.Inject`. Open a context on the package that contains those classes.

The desk does it this way. `DeskApp` names the scan root and opens the context. `ViewLoader` then asks that context for the controller, the forms, and the converters. `AgeSuffix` is a `@Named` service. `AgeConverter` takes that service in its constructor.

```java
ApplicationContext context = new ApplicationContext(DeskApp.class);
LoadedView loaded = ViewLoader.load(
        DeskController.class.getResource("Desk.fxml"), DeskApp.sample(), context);
```

`DeskApp.start` is that load, then the scene. The class carries `@Config(basePackage = "de.neitzel.fx.example")`.

```java
@Named
public class AgeSuffix {
    public String prefix() {
        return "years ";
    }
}
```

`AgeConverter` in `fx-example` is the full class. It keeps the suffix and implements `Converter`. `getComponent` returns the same instance on every call. `AgeConverter` and `AgeSuffix` are each created once, and every use of the converter shares that one suffix.

## Mark a component

The sections from here on are the container's rules. The desk is the example where a rule shows up on the screen. Rules the desk does not hit are stated on their own.

The scanner looks for `jakarta.inject.Singleton` and `jakarta.inject.Named` on the class. Both annotations mean the same thing here: the class is a component, and it is stored as a singleton. `@Named`'s value is never read. There is no lookup by name.

`AgeSuffix` carries `@Named`. `AgeConverter`, `StreetConverter`, `AddressForm`, `VisitForm`, `NoteForm`, `VisitRow`, `DeskController`, `FormHeading`, `StreetPolicy`, and `VisitedOnConverter` carry `@Singleton`. The age field still shows `years 36`, which is how the desk shows that `@Named` was found.

Put the annotation on each concrete class you want created. The scan uses Reflections' "annotated types" query, which also returns subtypes of an annotated type that sit inside the scan root. An unannotated subclass of a `@Singleton` class can therefore be picked up. Mark the classes you intend to create.

The scanner does not read `@Qualifier`, `Provider`, `Instance`, producer methods, `@PostConstruct`, or `@PreDestroy`. `@Named` on a parameter or a field is not a qualifier. Injection matches the raw class after generics are erased, so a `List<Address>` is looked up as `List`.

There is no `@Component` stereotype. Comments on `Scope` and `@Config` still mention one. Discovery is `@Singleton` and `@Named`.

## Where the container injects

For each component the scanner chooses one constructor, then the `@Inject` fields, then the `@Inject` setters. Members are taken from that class only. Inherited fields and methods are left alone. The chosen constructor and the injected members are made accessible, so they may be non-public.

Constructor choice:

1. More than one constructor annotated with `@Inject` makes the component invalid. It is left out of the context.
2. Exactly one `@Inject` constructor is used, even when other constructors exist.
3. Otherwise the single declared constructor is used. A compiler-generated public no-arg constructor counts. Parameters are allowed, and `@Inject` is not required on that lone constructor.
4. Otherwise a zero-argument constructor is used when one exists.
5. Otherwise the component is invalid.

After `new`, each `@Inject` field is set, then each valid `@Inject` setter is called. A setter is an `@Inject` method that is `void`, has one parameter, and whose name starts with `set`. An `@Inject` method of another shape is skipped and recorded as an error. The component can still be created.

The desk uses all three places:

| Place | Desk class | What is injected |
| --- | --- | --- |
| Constructor | `AgeConverter` | `AgeSuffix` |
| Constructor | `VisitRow` | `DeskController` |
| Field | `StreetConverter` | `StreetPolicy`, on a private field, after the no-arg constructor |
| Setter | `AddressForm.setHeading` | `FormHeading` |

`AddressForm.initialize` runs later, when FXML loads the form, and copies `heading.text()` onto the label. The setter has already run by then. The home form shows the word `Address`.

`VisitRow` is created again for every table row. Each row receives the same `DeskController`, because the controller is a singleton and dependencies are loaded with `getComponent`.

## Ask for an instance

```java
public <T> T getComponent(Class<? extends T> type)
public <T> T createComponent(Class<? extends T> type)
public <T> void addSingleton(Class<? extends T> type, T instance)
```

`getComponent` returns the cached instance. The first call creates it and stores it. Later calls return that object.

`createComponent` always builds a new instance and does not store it. Dependencies of that instance still come from `getComponent`, so they are the shared singletons. `fx` uses this for a second component of the same class in one load, and for every repeated row and table row. The home `AddressForm` is the cached instance. Each `VisitForm` and each `VisitRow` is a new instance, and each of them still sees the one `DeskController` and the one `StreetConverter`.

A type the context does not know produces `IllegalArgumentException` with the message `No component found for type:` and the class name. A failure while calling the constructor or writing a member is wrapped as `RuntimeException` whose message starts with `Fehler beim Erstellen der Instanz für`, with the original failure as the cause.

The context is a plain map. It is not safe to share across threads without external locking. There is no shutdown and no list-all method.

## Choose the scan root

```java
public ApplicationContext(String basePackage)
public ApplicationContext(Class<?> configClass)
```

The string constructor scans that package and its subpackages. The desk uses the class constructor:

```java
ApplicationContext context = new ApplicationContext(DeskApp.class);
```

`@Config` on the class supplies the package when `basePackage` is not empty. `DeskApp` sets it to `de.neitzel.fx.example`, which is also the class's own package. Subpackages are included, so `de.neitzel.fx.example.view` is scanned. Without the annotation, or with `basePackage` left as `""`, the scan root is `configClass.getPackageName()`. The annotation is not inherited, so a superclass `@Config` is ignored.

`@Config` only chooses the scan root. It does not bind properties.

Scan failures are not thrown from either constructor. A package full of broken components still produces a context. Those types are simply absent, and `getComponent` fails later with `No component found for type:`. To see why, scan with `ComponentScanner` and read `getErrors()`.

## Types you can inject

`getComponent` accepts the concrete class. It also accepts a superclass or an interface when exactly one scanned component has that super type. The context stores one `ComponentData` under the concrete class and under each such unique super type, and both keys return the same instance.

A super type implemented by two or more components is ambiguous. It is not a key. `getComponent` on it throws `No component found for type:`. The concrete classes stay available. The desk does not use this: every injection point names a concrete class.

Super types considered for a component are its superclass chain, stopping before `Object`, and the interfaces declared on that class. Interfaces declared only on a superclass are not included. The class itself is not registered twice under this rule.

`ComponentScanner` is the way to see the result before you build a context:

```java
ComponentScanner scanner = new ComponentScanner("de.neitzel.fx.example");
scanner.getInstantiableComponents(); // concrete classes and unique super types
scanner.getNotUniqueTypes();         // super types with two or more components
scanner.getErrors();                 // messages, empty when the scan is clean
```

The lists and the map are the scanner's own collections.

A cycle never enters the map. Each side is reported as a missing dependency.

## When creation fails

`new ApplicationContext(...)` stays quiet. Inspect `ComponentScanner.getErrors()` when a type you expected is missing. The messages are:

| Situation | Message |
| --- | --- |
| Two or more `@Inject` constructors | `Multiple @Inject constructors found for ` plus the class name |
| Several constructors, none of them `@Inject`, and none of them zero-argument | `No @Inject constructor and no no-arg constructor found for ` plus the class name |
| `@Inject` method that is not a one-argument `void` method named `set...` | `Invalid @Inject method detected in ` plus the class name, `: `, and the method name. The method is skipped |
| A dependency never becomes a known type | `Component cannot be instantiated: ` plus the class name, then ` \| Missing constructor dependencies: `, ` \| Missing field dependencies: `, or ` \| Missing setter dependencies: `, each followed by the list of classes |
| Every dependency is either known or an ambiguous super type | the same prefix plus ` \| Conflicting super types detected` |

A class with a single `String` constructor and no `@Inject` takes that constructor (rule 3 above). `String` is not a component, so the class is omitted and the error names `class java.lang.String` as the missing constructor dependency.

## Register an instance yourself

`addSingleton` stores an object you already built.

- The type is absent: the instance is stored and `getComponent` returns it. The context does not construct it, and it does not scan again. Components already dropped because a dependency was missing stay dropped.
- The concrete class was found by the scan: `IllegalStateException` with `Type cannot be replaced:` and the class name.
- The key is a unique super type, whose stored component class is the implementation: the super-type entry is replaced. The concrete-class entry is left as it was, so the interface and the class can then return different objects.

`createComponent` on a manually added type has no injection constructor and fails.

The desk does not call `addSingleton`. Its services are the scanned classes.

## Scope

`Scope` has two constants, `SINGLETON` and `PROTOTYPE`. Every class the scanner accepts is stored as `SINGLETON`. Nothing in the scanner assigns `PROTOTYPE`. `getComponent` on a singleton returns the cached instance. `createComponent` is how you get another object of a singleton class, which is what the desk's repeated forms and table rows do.

## What this container does not do

It does not match qualifiers, read `@Named` values, inject by name, bind configuration properties, or call lifecycle methods. It does not create a new instance per injection. `fx` view models are built by `fx`, not by this container.
