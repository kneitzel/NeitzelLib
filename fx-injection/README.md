# Neitzel DI adapter for FX

The adapter is available from NeitzelLib 1.0.7. See the
[Maven repository setup](../README.md#use-a-release-from-maven). For a
local source build, run `./mvnw -pl fx-injection -am install` from the
NeitzelLib root with JDK 25 and the application’s Maven repository.

```xml
<dependency>
    <groupId>de.neitzel.lib</groupId>
    <artifactId>fx-injection</artifactId>
    <version>1.0.7</version>
</dependency>
```

Use the same version for all NeitzelLib modules. The adapter resolves `fx`,
`injection` and its scanner dependencies. Applications with their own
`ViewDependencies` can depend on `fx` directly.

Import the adapter from `de.neitzel.fx.injection`, the container from
`de.neitzel.injection` and the loader and Model context from
`de.neitzel.fx.view`.

```java
var services = new ApplicationContext(MyApplication.class);
var dependencies = new NeitzelViewDependencies(services, Set.of(AddressForm.class, VisitRow.class));
var models = new ViewModelContext();
var view = ViewLoader.loadWithDependencies(fxml, model, dependencies, models);
stage.setScene(new Scene(view.getRoot()));
stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> models.close());
```

The registration set contains the exact FXML Component classes, including
those used by Repeat and Table. The adapter asks the context for a fresh
controller or Component and gives FX the corresponding release action.
The release action closes UI instances that implement `AutoCloseable`.
Converters and Localizer come from the context's ordinary lookup. The adapter
reports a missing service as an empty result and propagates lookup or
creation errors. The application owns the context and its shared services.

The JPMS module name is `de.neitzel.fx.injection`. Named applications
keep the package access needed by FXML and Neitzel's package scanner.
Declare `requires de.neitzel.fx.injection` for the adapter and `requires fx` and
`requires injection` for their directly imported types. The Neitzel package
scanner still needs access to its scanned packages in named applications.

## Migrate an existing FX application

Use `ViewLoader.loadWithDependencies(..., dependencies, models)`,
`ViewLoader.loadComponentWithDependencies(..., dependencies, models)`,
and `FxFixture.loadWithDependencies(..., dependencies)` for the new
FX artifacts. Replace the previous public `ApplicationContext` loader
calls and recompile. Retain the Model context with its window or
dockable and close it when that owner ends. Each View load gets its own
controller and Components. If a row previously called a cached controller,
inject the shared action it needs. The [Person Desk](../fx-example/README.md)
does this with `VisitActions`.

A Designer provider on the new artifacts returns the configured adapter
from `PreviewSession.context()` and declares `ViewDependencies` as its
context type. Existing providers returning `ApplicationContext` continue to
run with compatible older FX artifacts such as 1.0.6. Consumers of the standalone
[Neitzel injection container](../injection/README.md) can keep using its
existing API.
