# Generated ViewModel runtime contract

A generator may extend `ViewModel` with typed constructors and accessors. Accessors call the protected final `storedProperty(name)` method, which returns the existing Attribute property and never invokes a reflective accessor. Unknown stored Attributes fail explicitly. Property storage, write-back, lazy nested Models, collection contents and Messages remain owned by `ViewModel`.

Modules contribute public `ViewModelRegistry` marker services annotated with repeatable `ViewModelRegistration` declarations. Each declaration names the configured Model, generated base and selected default class. Registries are inspected through `ServiceLoader.Provider.type()` without constructing them; their annotations are also available to non-initializing designer discovery. Classpath applications package `META-INF/services/de.neitzel.fx.view.ViewModelRegistry`; named modules declare `provides` and export/open generated ViewModel packages as appropriate.

`ViewModelDefaults.select` checks duplicate/conflicting defaults, finds the most-specific applicable Model declaration, and requires an explicit override to extend the corresponding generated base. No declaration preserves generic/default replacement behavior. A compatible explicit request takes precedence before creation. Existing instances are reused for a requested superclass/interface that they satisfy; incompatible requests fail. Nested lazy loading uses the same registry and identity table as direct loading. Failed subclass construction removes its partially registered instance.

Generated Java source and compiler integration live in NeitzelFXDesigner; its [generation guide](https://gitea.neitzel.de/konrad/NeitzelFXDesigner/src/branch/main/docs/user-guide/generation.md) explains processor setup, generated defaults and reviewed FXML creation. This library API is a development snapshot, not a published release. Runtime regressions cover stored identity/write-back, cycles, null children, Messages, compatible/incompatible requests, most-specific defaults, provider non-execution, conflicting contributions and constructor failure cleanup. Existing ungenerated Model and Component tests remain required.

## What a generated class represents

For a Model with a `String name` Attribute, a generated base can expose the existing property without creating a second storage location:

```java
package example.generated;

import de.neitzel.fx.view.ViewModel;
import javafx.beans.property.StringProperty;

public class PersonViewModel extends ViewModel {
    public PersonViewModel(example.Person model) { super(model); }
    public StringProperty nameProperty() {
        return (StringProperty) storedProperty("name");
    }
}
```

The generator owns this base; put handwritten derived properties in a subclass. The generic runtime already supports the Model without generated code. Generation adds typed access and discoverable contracts, not a separate binding engine or an automatically generated event controller.

A registry contribution for the generated base itself as default looks like this:

```java
package example.generated;

import de.neitzel.fx.view.ViewModelRegistry;
import de.neitzel.fx.view.ViewModelRegistration;

@ViewModelRegistration(model = example.Person.class,
        generated = PersonViewModel.class,
        defaultType = PersonViewModel.class)
public final class Registry implements ViewModelRegistry { }
```

On the classpath, put this line in `src/main/resources/META-INF/services/de.neitzel.fx.view.ViewModelRegistry`:

```text
example.generated.Registry
```

For JPMS, declare the service in the application module instead:

```java
provides de.neitzel.fx.view.ViewModelRegistry with example.generated.Registry;
```

The selected default may be a handwritten subclass of the generated base. `@ViewModelRegistration` is repeatable; its container is `@ViewModelRegistrations`. Conflicting defaults, ambiguous equally applicable Model declarations, or a default/explicit override outside the required generated base fail rather than silently choosing one. `ViewModelDefaults.select(modelClass, explicitClass, classLoader)` is the public selection operation used by runtime integration; normal applications let `ViewLoader` perform it.

Keep the generated registry and classes on the same application runtime path as the Model. If discovery succeeds during compilation but the registry service is absent at runtime, the runtime cannot select that default. Do not change the selected class after an instance's ViewModel has already been created: only requests compatible with the existing instance can reuse it.
