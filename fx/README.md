# Using fx

## Documentation map

- [Build a complete application from scratch](docs/quick-start.md).
- [Architecture, MVVM, identity, threading and lifetime](docs/architecture.md).
- [Walk through the person desk](../fx-example/README.md).
- This page is the detailed FXML and runtime reference.
- [Generated ViewModel classes and registries](docs/generated-viewmodels.md).
- [Bounded integer spinners](docs/integer-spinners.md) and [system tray support](docs/tray-support.md).
- [Standalone toolbar and status bar](docs/bars.md), usable without MVVM.
- [Docking: opening, moving and activating live contents](docs/docking.md), with ordinary Java/FXML startup examples.
- [Optional MVVM docking integration](../fx-example/docs/docking-mvvm.md), with local and global bars.
- [Dragging between windows](docs/docking-drag.md), with native pointer and keyboard qualification.
- [Layout snapshots and empty-workspace restore](docs/docking-snapshots.md), with application-owned storage and loading.
- [Live reset](../fx-example/docs/docking-reset.md), retaining existing contents and unsaved edits.
- [Asynchronous tab close](docs/docking-close.md), including consent and resource release.
- [Quit, save and replace a workspace](docs/docking-shutdown.md), with global consent, retry and main-window ownership.
- [Complete docking workspace](docs/docking-workspace.md), combining Java/FXML startup, application storage and shutdown.
- [Keyboard access, translations and themes](docs/docking-accessibility.md).
- [Dependency injection](../injection/README.md).

- [Local DataGrid and asynchronous coordination](docs/data-grid.md).
- [Transactional FXML filters and popup lifecycle](docs/filter-editors.md).
- [Versioned saved setups and atomic restoration](docs/saved-setups.md).
- [Opt-in shared themes](docs/themes.md) and the [theme gallery](../fx-example/docs/theme-gallery.md).
- [Opt-in queried Table results](docs/table-results.md), the DataGrid rendering foundation.

## Introduction

`fx` includes docking and standalone bars for ordinary JavaFX applications; these controls do not require MVVM initialization. Its optional MVVM functionality binds an FXML view to an ordinary Java model. The model stays a JavaBean. The library builds one view model for that instance, loads the view, and applies the binding elements declared in the FXML.

You write the model, the FXML, and, where a screen needs them, a controller, reusable components, and converters. The library creates the view model. The [injection](../injection/README.md) container creates the controller, each component, and each converter.

The worked example is the `fx-example` module, package `de.neitzel.fx.example`. From the NeitzelLib root, with Java 25:

```bash
./mvnw -pl fx-example -am install
./mvnw -f fx-example/pom.xml javafx:run
```

The launch class is `de.neitzel.fx.example.Main`. `Main` does not extend `Application`. It calls `Application.launch(DeskApp.class, args)`, and `DeskApp.start` builds the scene on the JavaFX thread.

Depend on `de.neitzel.lib:fx`. That module depends on `core`, `injection` and on JavaFX base, controls, graphics, fxml, and web. Application types the container constructs use `jakarta.inject.Singleton` or `jakarta.inject.Named`.

## Dependencies

These are the versions configured by this source snapshot, not a promise of compatibility with every newer release:

| Part | Baseline / relationship | Consumer responsibility |
| --- | --- | --- |
| JDK | Java 25 | Select a matching JDK for compilation and launch |
| Maven | Minimum 3.9.11; wrapper 3.9.11 | Use the wrapper or an installed Maven |
| Library | `de.neitzel.lib:fx:1.0` | Use the Gitea Maven registry or install the local reactor |
| Internal modules | `fx` directly depends on `core`, `injection` and toolkit-independent `data-grid` | Resolved transitively through Maven |
| JavaFX | 27.0.1: base, graphics, controls, FXML and web | Resolved transitively; deployment still needs platform-native artifacts |
| Injection scanning | Reflections 0.10.2 | Brought by `injection` |
| Injection annotations | `jakarta.inject-api` 2.0.1 | Use Jakarta annotations, not `javax.inject` |
| Logging | SLF4J API 2.0.20 | Choose one logging backend; the example uses `slf4j-simple` 2.0.20 at runtime |
| Library build | Lombok 1.18.48 and test/analysis tooling | Not required in ordinary consumer source; use your own annotations only if wanted |
| Designer | Optional preview API / Model processor | Needed only for the selected Designer workflow, not normal `ViewLoader` use |

The quick-start POM adds only `fx` and a logging backend. Do not copy library build-time dependencies into an application just to obtain runtime binding. A project using injection alone needs the `injection` artifact and does not need JavaFX.

## Quick start

`DeskApp` scans its package, loads one person into `Desk.fxml`, and asks for a dialog when a new message is added:

```java
ApplicationContext context = new ApplicationContext(DeskApp.class);
Person person = DeskApp.sample();
LoadedView loaded = ViewLoader.load(
        DeskController.class.getResource("Desk.fxml"), person, context);
MessagePopup.showNewMessages(loaded.getViewModel());
Parent root = loaded.getRoot();
```

`Person` mentions no JavaFX type. `Desk.fxml` names `DeskController` and lists the bindings in `fx:define`, so they are not scene-graph children. A name field is a direct binding:

```xml
<fx:define>
    <BindingList>
        <DirectBinding control="nameField" property="text" path="name" direction="both"/>
    </BindingList>
</fx:define>
<TextField fx:id="nameField"/>
```

`ApplicationContext` scans the package of the class you pass, and its subpackages. `DeskApp` carries `@Config(basePackage = "de.neitzel.fx.example")`, which is that package. `ViewLoader.load` reuses the view model already built for that person, or builds one, then loads the FXML and applies the binding elements.

One model instance has one view model. The first view fixes the view model class. The desk's controller names `PersonViewModel`, so that is the class for this person.

## The person desk

The desk edits one person: name, age, greeting, title, role, active flag, home address, visits, and tags. The visits appear again in a table, with one row per visit, and again as repeated forms.

| What you see | Feature | Where it is written |
| --- | --- | --- |
| Name | Direct binding, `both` | `Desk.fxml`, `nameField` |
| Shown name | Direct binding, `view`, on an editable field | `nameEcho` |
| Name draft | Direct binding, `model` | `nameDraft` |
| Age, shown as `years 36` | Converter, constructor injection, `@Named` service | `AgeConverter`, `AgeSuffix` |
| Greeting | Computed binding that reads the name field and the age | `DeskController.greeting` |
| Title `titled-Ada Lovelace` | View model subclass | `PersonViewModel`, `@ViewModelClass` on the controller |
| Active | Boolean attribute on a check box | `Person.active` |
| Role | Enum attribute on a choice box | `Person.role`, `Role` |
| Home address | Component, setter injection, `HasViewModel` | `AddressForm` |
| Note under the home address | Nested component | `NoteForm` inside `AddressForm.fxml` |
| Home street label | Path `home.street`, `view` | `homeStreetLabel` |
| Empty street | Converter failure carried as `home.street` | `StreetConverter`, field injection of `StreetPolicy` |
| Visit forms | Repeat | `VisitForm` in `visitPane` |
| Visit combo and table selection | One `selectedVisit` attribute | `visitChoice`, `visitTable` |
| Visit table | Columns, a date, a Remove button after the fields | `Table`, `VisitRow`, `VisitedOnConverter` |
| Tags | List of simple values | `tagList` |
| Notice, and the three buttons | Messages, including info, warning, and fatal | `noticeLabel`, `DeskController` |

`DeskLoadsTest` loads this same desk and walks those bindings.

## Model

A read-write attribute is a getter and a setter, and it is not declared on `Object`. `Person`, `Address`, `Note`, and `Role` are models.

| Declared type | What the view model stores |
| --- | --- |
| `String` | `StringProperty` |
| `int`, `Integer` | `IntegerProperty`. A null `Integer` becomes 0 |
| `long`, `Long` | `LongProperty`. A null `Long` becomes 0 |
| `float`, `Float`, `double`, `Double` | The matching primitive property. A null value becomes 0 |
| `boolean`, `Boolean` | `BooleanProperty`. A null `Boolean` becomes false |
| anything else | `ObjectProperty` holding the value, including null |

Primitives, enums, arrays, `String`, `Boolean`, `Number`, `Collection`, `Map`, `Date`, `Calendar`, and any type in a `java.time` package stay simple values, even when they have setters. The desk uses that for `name`, `age`, `active`, `role`, `tags`, and `visitedOn`.

A type that itself has a read-write attribute is complex. The view model holds the view model of that nested instance, or null. `Person.home` is an `Address`. `Address.note` is a `Note`. A `List` of either kind is a list, not a nested view model. `visits` is a list of addresses. `tags` is a list of strings.

Instances are compared by identity. One person has one view model. The same address object on two attributes would be one view model, so an edit through either attribute would show on the other. Two equal addresses that are different objects keep two view models. A cycle, such as a person who points at themselves, reuses the view model already under construction.

Start lists as a mutable `ArrayList`, as `Person` does for `visits` and `tags`. After the view loads, the model's list attribute is the `ObservableList` the view is watching. Add and remove on that list. The view model writes that observable list back through the setter when the setter's parameter type can accept it. A `List` setter does. A setter typed as `ArrayList` or `Set` does not, and the property then diverges from the field.

After the view model exists, it is the writer. A property change calls the setter. A later call to the entity setter leaves the control on the value the view model holds. `DeskLoadsTest.entitySetterLeavesTheControls` sets the person's name to `Ignored` and the name field stays `Ada Lovelace`. The next edit of the field writes through the view model and replaces that name.

## Load a view

```java
public static LoadedView load(URL fxml, Object model, ApplicationContext context)
```

The load reads `fx:controller` from the document first, so it can see `@ViewModelClass`, and builds or reuses the view model. It then loads the FXML. The container supplies the controller. Components come from the container as well. The root must be a `Parent`. Bindings run after `@FXML` fields are filled. Tables under the root open last, including a table inside a scroll pane.

`LoadedView.getRoot()` is the scene graph. `getViewModel()` returns the view model when it is a `ViewModel`, which includes a subclass such as `PersonViewModel`. A replacement that does not extend `ViewModel` makes `getViewModel()` throw `IllegalStateException` with the message `View is bound to` and the class name. Use `property(String)` in that case. The desk's view model is a subclass, so `getViewModel()` returns it.

`LoadedView.property(String)` returns the JavaFX property for an attribute name, for `latestMessage`, or for a `nameProperty()` method on the view model. A missing name returns null. A binding whose path does not resolve fails the load.

FXML that cannot be read becomes `IllegalStateException` `Could not load view` and the URL. A missing controller class is `IllegalArgumentException` `No controller` and the name.

`DeskLoadsTest.secondViewReusesTheViewModel` loads a second document, `NameLine.fxml`, for the same person. The second document names no controller and no view model class. It reuses `PersonViewModel`. Typing in the desk's name field updates the second field, and the title becomes `titled-Grace`. Loading `Desk.fxml` a second time while the first desk is showing does not work: the home `AddressForm` is the cached singleton and already has children. See [Component](#component).

## Direct binding

A direct binding names a control by `fx:id`, a JavaFX property of that control, a path, and a direction. The property is resolved as `propertyName + "Property"`, so `text` means `textProperty()`. An optional `converter` is a class name.

```xml
<DirectBinding control="nameField" property="text" path="name" direction="both"/>
```

For `both` and `model`, the write happens when the JavaFX property changes, including each change of a text property. There is no separate save. `view` does not write the attribute.

`BindingList` is the element that holds direct bindings, computed bindings, and repeats. Its default property is `elements`, so the children in FXML are the list. An unknown child fails the load with `Not a binding element:`. The children that apply are a direct binding, a computed binding, and a repeat.

When the attribute value is already an `ObservableList`, the control property is set to that same list. Direction and converter are not consulted. The tag binding names `direction="view"` and still shares the list. See [A list of simple values](#a-list-of-simple-values).

A binding that names no such control fails with `No control "` and the id. A missing `xxxProperty()` method fails with `No property` and the property name. A method that returns something other than a `Property` fails with `did not return a property`.

## Directions

| Direction | What it does |
| --- | --- |
| `both` | Shows the attribute and writes it when the control changes. |
| `view` | Shows the attribute and follows later changes of the JavaFX property. Edits to the control stay on the control. |
| `model` | Shows the current value, writes the attribute when the control changes, and leaves the control where it is when the attribute changes later. |

The desk puts all three on `name`. The name field is `both`. Shown name (`nameEcho`) is `view`: typing there leaves `Person.name` and the name field unchanged, and typing in the name field updates the echo. Name draft (`nameDraft`) is `model`: it opens as `Ada Lovelace`, stays there while the name field changes, and typing in the draft writes `Person.name` and updates the name field and the echo.

Always specify `direction`: it has no default. A non-null value other than `view`, `model`, or `both` fails the load with `Not a direction:`. Omitting it can currently fail with a `NullPointerException` during binding application. The ObservableList shortcut described above bypasses direction handling; do not rely on that shortcut to omit the declaration.

For a complex last segment the control receives the model instance behind the view model, and the same three directions apply to later changes of that attribute. The home form's `model` property is `home` in both directions, so the form edits that address. A later change of the ViewModel's `home` property updates the Component's Model property, but its internal bindings do not reattach; create a new Component to edit another Model. The desk sets the home address once.

## Paths

A path is attribute names separated by `.`. One name is an attribute of this view's view model, or `latestMessage`, or a `nameProperty()` on the view model. Earlier names must be complex attributes. The direction applies to the last name.

`home.street` on the home street label steps through the home view model, and `view` applies to `street`. When a complex attribute along the path is null, that binding is left unapplied and both sides keep their current values. The load still succeeds.

The path is resolved when the binding is applied. A binding whose last segment is complex follows later changes of that attribute. A longer path stays with the child view model that was in place when the binding was applied. The home street label is `home.street`. Replacing the ViewModel's `home` property later would leave that label on the street it was bound to. Calling the bean setter directly would not notify either binding.

A list is not indexed in a path. Repeat a component, place a table, or hand a list of simple values to a control.

A path that is not a read-write attribute fails the load with `No read-write attribute for path "` and the path. `Object.getClass` is not an attribute, and a getter without a setter is not one either.

## Converter

A converter translates one direct binding between the attribute value and the control value. The container creates the class and injects its services. The class must implement `de.neitzel.fx.view.Converter`. Otherwise the load fails with `Not a converter:` or `No converter`.

`toControlValue` runs toward the view, including the value shown at load. `toAttributeValue` runs only when the direction is `model` or `both`. A toward-view binding does not call it, and the default method throws `UnsupportedOperationException`.

The age field is the desk's `both` converter. `AgeConverter` takes `AgeSuffix` in its constructor. `AgeSuffix` is `@Named` and `prefix()` returns `years `. Age 36 shows as `years 36`. Typing `years 41` sets the age to 41.

```xml
<DirectBinding control="ageField" property="text" path="age" direction="both"
               converter="de.neitzel.fx.example.view.AgeConverter"/>
```

`StreetConverter` is the other shape: a no-arg constructor and a private `@Inject StreetPolicy` field. `toAttributeValue` trims the text and throws `IllegalArgumentException` with `Street is required` when the result is empty. The home form and each visit form use it on `street`.

`VisitedOnConverter` turns a `LocalDate` into ISO text in the table, and `LocalDate.parse` back. An empty field writes null.

A converter is not applied to a complex attribute or to an `ObservableList`. Those bindings assign the model instance or the list.

When a conversion throws, the other side keeps its previous value. The control keeps the text that was typed, because the failed write does not replace it. The view model that owns the last path segment records one error message. The source is that segment's name. The text is the exception message, or `Conversion failed` when the exception message is null or empty. A later failure for that same Attribute updates the message in place. A later success removes it.

On the desk, `nope` in the age field leaves the age at 41, leaves the field text `nope`, and sets `latestMessage` to an error whose source is `age`. Typing `years 41` again clears it. A blank home street leaves the street as it was and records source `home.street` on the person, because the failure is owned by the address and carried onto the parent. The notice label shows `Street is required`. A later real street clears that notice. See [Messages](#messages).

## Computed binding

A computed binding names a public no-argument method on the controller. The method value in FXML is `#name`. A leading `#` is stripped. A value without `#` is used as the method name. The method returns the value written to the control. It may read the controller's fields and the view model. It does not write the model.

```xml
<ComputedBinding control="greetingLabel" property="text" method="#greeting"/>
```

```java
public String greeting() {
    String name = nameField.getText();
    Object age = viewModel.property("age").getValue();
    if (name == null || name.isBlank()) {
        return "Hello (" + age + ")";
    }
    return "Hello, " + name.trim() + " (" + age + ")";
}
```

The library calls `greeting` when the binding is applied, and again when a JavaFX property changes on a `Control` in this view, other than the target property, or when an attribute property on this view's `ViewModel` changes. Typing the name updates the greeting because the name field changed. Typing the age updates it because the age attribute changed. Writing the greeting label does not call `greeting` again. Nodes that are not controls are not watched, and controls added after binding setup are not automatically subscribed. Computed methods should be fast and free of side effects; this is broad notification-based refresh, not dependency analysis. A replacement view model that does not extend `ViewModel` is not watched.

A missing method fails the load with `No computed method "` and the method value. No controller fails with `No controller for`. A method that throws fails with `Could not call`.

An `onAction="#method"` handler is ordinary FXML. `addVisit`, `addTag`, `leaveNote`, `leaveWarning`, and `leaveFatal` are handlers. They are separate from the computed binding.

## Controller

Name the class on the root with `fx:controller`. The container creates it with `getComponent`, so it is the cached singleton. `@FXML` fields are filled during load, before the bindings run.

Leave `fx:controller` off and the library supplies a controller, which is enough for a view that has only direct bindings. `NameLine.fxml` in the test is that shape. You cannot obtain the supplied controller from `LoadedView`.

Implement `HasViewModel` when your code needs the view model. The library calls `setViewModel` after construction and before the bindings run, and only when the selected object is a `ViewModel`. The desk stores it and reads `visits`, `tags`, `name`, and `age` from it.

A second view whose document names the same controller class receives that same singleton. FXML then injects the second view's fields into it. The desk is one window, so its controller fields stay on that window. Give a second screen its own controller class when each screen needs its own `@FXML` fields.

## Choosing the ViewModel class

With no annotation, the library uses a registered generated default when one applies, otherwise it builds `ViewModel`. See the [generated runtime contract](docs/generated-viewmodels.md). Put `@ViewModelClass` on the controller or the component to name a subclass, or another object that exposes JavaFX properties by name. The class needs a constructor that takes the model instance. If several one-argument constructors match, the most specific parameter type wins. The library calls that constructor. The container does not create the view model.

```java
@Singleton
@ViewModelClass(PersonViewModel.class)
public class DeskController implements HasViewModel {
}
```

```java
public class PersonViewModel extends ViewModel {
    private final StringProperty title = new SimpleStringProperty(this, "title");

    public PersonViewModel(Object model) {
        super(model);
        title.set("titled-" + ((Person) model).getName());
        property("name").addListener((observable, oldValue, newValue) ->
                title.set("titled-" + newValue));
    }

    public StringProperty titleProperty() {
        return title;
    }
}
```

The desk binds `titleLabel` to path `title`, direction `view`. The label shows `titled-Ada Lovelace`, and it follows the name because the subclass listens to the name property. A binding addresses `titleProperty` by the name `title`.

The first view of an instance fixes the class. A later view requesting a class that the existing ViewModel does not satisfy fails the load with `ViewModel is ` the existing class ` but the View names ` the requested class, and the first view stays as it was. A compatible superclass/interface request reuses the existing instance. A later view with no annotation also reuses the view model. That is what `NameLine.fxml` does.

No matching constructor fails with `No constructor taking the Model on` and the class name.

A component's `@ViewModelClass` applies to that component's model, not to the parent. The library builds that class before the path is read, so a generic view model is not fixed first. An earlier binding in the same list that already resolved a path into that instance would have fixed the class, and an incompatible annotation would then fail the load. The desk does not do that. The person's class is fixed from the controller before the home form's model is set, and `AddressForm` names no view model class, so the address uses `ViewModel`.

## Component

Component elements accept writable FXML properties, including custom attributes and inherited
layout properties such as `prefWidth`. The container constructs the Component first; its own
FXML and `initialize()` run before the containing document's property values are applied.
JavaFX performs the normal property conversion and rejects unknown properties or invalid values.
Object references such as `model="$address"` retain their identity and are assigned after the
Component's model listener is installed. Static layout constraints such as `GridPane.rowIndex`
remain handled by the containing FXMLLoader.

A component is a class the container can create. It extends `Parent` (usually a Pane subclass such as `VBox`), loads `ClassName.fxml` from its own package through `fx:root`, and is the controller of that file. It has no `fx:controller`. It exposes one JavaFX property named `model`.

`AddressForm` edits the home address. `VisitForm` edits one visit in the repeated list. `NoteForm` edits a note. `VisitRow` edits one table row. Each is `@Singleton`, extends `VBox` or `HBox`, and has `modelProperty()`.

The home document binds the form and sets `model` from the path. When the path's last segment is complex, the value assigned is the model instance behind that view model.

```xml
<DirectBinding control="homeForm" property="model" path="home" direction="both"/>
<AddressForm fx:id="homeForm"/>
```

The component's own bindings run once, the first time `model` becomes non-null. A later change of `model` does not apply them again. Set the model once. To show another instance, load another component.

`initialize` runs during the component's FXML load, before `model` is set. `AddressForm` uses that moment to copy the injected heading onto its label, which does not need the address. For a Component implementing `HasViewModel`, `setViewModel` runs after `model` is set to a non-null Model and before its bindings, but only when the selected presentation object extends `ViewModel`. An arbitrary replacement does not receive this callback. `AddressForm` implements `HasViewModel` and writes `Opened as ` plus the city at that moment. Editing the city afterward leaves the label on the city the form opened with. The sample home opens as `Opened as Sample City`.

The first component of a class in one load is `context.getComponent`, the cached singleton. Each further one in that same load is `context.createComponent`. Repeat and table rows always use `createComponent`, so they are new instances and the cached singleton of that class stays unused by the rows. `getComponent(VisitForm.class)` after the desk loads is still the singleton, and the three forms in the visit pane are other instances.

A later load starts over. Its first component of a class is the cached singleton again. The home `AddressForm` is that singleton and is already in the scene, so loading `Desk.fxml` a second time fails while building the home form: a node cannot be added to a second parent. The test's second view is a document with no component.

A component class needs `ClassName.fxml` beside it. A missing document fails with `No component document`. A missing `modelProperty` method fails with `No model property on`. A method that does not return a `Property` fails with `modelProperty did not return a property`.

## Nested component

A nested component is the same mechanism one level down. The child document names the next class as an element and binds its `model`.

`AddressForm.fxml` binds `noteForm` to path `note` and contains `<NoteForm fx:id="noteForm"/>`. `NoteForm.fxml` binds its text field to path `text`. The sample home note is `Kept at home`. Editing the note field writes `text` on the existing `Note` held by `Address.note`; it does not replace that Note object.

`VisitForm` does not contain `NoteForm`. A visit added after the desk has loaded is built in a new load. The first component of a class in that load is the cached singleton again, so a `NoteForm` nested in the repeated form would be the instance already showing under the home address. Keeping the note on the home form, which is loaded once, leaves that singleton where it was placed.

A null `note` leaves the nested binding unapplied. `NoteForm` stays empty and the load succeeds. The sample home has a note, so its bindings run.

## Repeat

A `Repeat` element in the same binding list names a pane, a component class, and a collection attribute. The library places one new component in that pane for each model instance, in list order. Adding or removing an element updates the pane. A permutation or an extractor update notification does not. Replacing an element with `set(index, newValue)` produces remove/add notifications and is handled.

```xml
<Repeat pane="visitPane" component="de.neitzel.fx.example.view.VisitForm" collection="visits"/>
<VBox fx:id="visitPane"/>
```

Each child is a new injected instance. Its `model` is the element itself. The desk's Add visit button appends to the list on the view model, which is the list stored on the person:

```java
@SuppressWarnings("unchecked")
private List<Address> visits() {
    return (List<Address>) viewModel.property("visits").getValue();
}
```

A missing pane, or a node that is not a `Pane`, fails with `No pane "`. A collection that is missing or is not an `ObservableList` fails with `No collection "`. A null list attribute is not an observable list. The desk starts `visits` as an `ArrayList`, which the view model wraps. An unknown component class fails with `No component`.

## Selection

Selection is a separate attribute of the parent model. Null means nothing is selected. The repeated component and the table row do not store it.

The desk uses one `selectedVisit` for the combo box and the table:

```xml
<DirectBinding control="visitChoice" property="items" path="visits" direction="view"/>
<DirectBinding control="visitChoice" property="value" path="selectedVisit" direction="both"/>
<DirectBinding control="visitTable" property="selection" path="selectedVisit" direction="both"/>
```

`Address.toString` is the combo box text, `street, city`. The items binding hits the list shortcut, so the combo box holds the same visit list.

A primary click on a row, including a click in a field, sets the selection to that element. A second click on the row leaves it selected. A click on the blank body clears it. Removing the selected element clears it. The selected row has style class `selected`. The sample desk opens with the first visit selected.

## A list of simple values

A list of strings, numbers, or other simple values is one shared `ObservableList`. A direct binding sets the control property to that list, so an add or a remove is visible on both sides.

```xml
<DirectBinding control="tagList" property="items" path="tags" direction="view"/>
<ListView fx:id="tagList"/>
```

The sample tags are `dns` and `math`. Add tag appends the field's text to the list on the view model and clears the field. Adding to `person.getTags()` after the load grows the list view, because that list is the one the view is watching.

A list of model instances is the same kind of observable list of instances. The visit combo box is that list. It is not a table.

## Table

A table shows a collection of model instances with column headers. Each element keeps one row, a component, for as long as it stays in the collection. The row binds its own fields. Filter and sort use the column attribute. They do not reorder the collection.

```xml
<Table fx:id="visitTable" collection="visits"
       row="de.neitzel.fx.example.view.VisitRow" prefWidth="496" prefHeight="160">
    <Column header="Street" attribute="street" width="300"/>
    <Column header="City" attribute="city"/>
    <Column header="Visited" attribute="visitedOn" width="120"/>
</Table>
```

`Table` is a `VBox`. Its default property is `columns`. `VisitRow` is an `fx:root` whose first children are the fields, in column order, and whose last child is Remove. The table sets the width of the first children, one per column. It does not check that child i is bound to column i. Controls after them keep their own size, so Remove is not a column and keeps `USE_COMPUTED_SIZE`. Spacing on the row is repeated between the header cells.

```xml
<fx:root type="javafx.scene.layout.HBox" spacing="8">
    <TextField fx:id="streetField"/>
    <TextField fx:id="cityField"/>
    <TextField fx:id="visitedField"/>
    <Button text="Remove" onAction="#remove"/>
</fx:root>
```

`VisitRow.remove` asks the desk controller to remove that address from the visit list. The row and the matching visit form disappear, and a removed selection becomes null.

An empty collection shows the headers and a blank body. Rows scroll vertically inside the table. The header stays put and follows the body's horizontal scroll. Hidden rows are kept and reused when a filter is cleared.

Add and remove update the body. A permutation or an extractor update notification is ignored; `set(index, replacement)` is handled as remove/add. The table refreshes filter and sort when a text control in a row changes. Other controls in the row do not retrigger it. The field's own direct binding still writes on any property change.

A column attribute must be a simple value: string, boolean, enum, a primitive other than `char` and `void`, a `Number`, a `Date`, a `Calendar`, or a `java.time` temporal. `visitedOn` is a `LocalDate`, so it is a column. A complex attribute fails the load with `Attribute "` name `" is not a simple value`.

Other load failures, all `IllegalArgumentException`:

| Condition | Message |
| --- | --- |
| No column | `A table needs a column` |
| Missing header, blank header, or missing attribute | `A column needs a header and an attribute` |
| The same attribute twice | `Attribute "` name `" is used twice` |
| `width` present and not a positive finite number | `Column "` header `" needs a positive width` |
| Collection missing or not an `ObservableList` | `No collection "` |
| Row class missing | `No row ` |
| Row is not a `Node` | `Row is not a node: ` |

These style classes are on the nodes: `table`, `column-header-row`, `table-scroll`, `table-header-scroll`, `column-header`, `column-cell`, `column-resize`, `column-opener`, `sort-mark`, `selected`, `column-popup`, `column-search`, `column-select-all`, `column-comparison-operator`, `column-comparison-value`, and `filtered` on an opener whose column has an active filter.

### Column width

A column with no `width` shares the table width with the other open columns. A `width` is the starting width, and not below the header text. The text minimum is the header text's layout width, rounded up, plus 8. The desk's Street column opens at 300 and Visited at 120. City names no width, so it receives what remains of the table's 496, which is 76.

A drag on the resize handle changes that column only. A later change of the table's width does not redistribute the columns. A click on the handle does not sort. A width narrower than the header text opens at the header text. If the table's width and preferred width are both 0, the fallback used for equal shares is 80 times the number of columns.

### Sort

The table opens in collection order, with no sort mark.

A primary click on the header cell, aside from the opener button and the resize handle, toggles that column: the first click sorts ascending, the second descending. Sorting a different column drops the other mark and starts at ascending. The mark text is the word `ascending` or `descending`. Null values stay last in both directions. Strings compare ignoring case, and ties keep collection order. Numbers use `doubleValue`. Booleans and enums use `Comparable` when both sides have the same class, so enum order is declaration order. `LocalDate` sorts chronologically.

The collection order is unchanged. Another table on the same list would stay in collection order.

The opener button (style class `column-opener`) opens a popup: `Ascending`, `Descending`, `Clear sort`, `Clear filter`, a search box, `Select all`, the distinct values, a comparison when the attribute allows one, and `Confirm`. `Ascending` sorts and closes the popup. A click outside closes the popup and leaves the sort. `Clear sort` restores collection order and hides the mark.

### Filter

The popup lists the distinct values of that column among rows that pass every other column's filter. This column's own filter is ignored, so unchecked values stay listed. Null is an empty label and sorts first. Enums use `name()`.

All boxes start checked, and confirming with every box checked leaves the filter inactive. A row added later is shown. Unchecking every box and confirming hides every row, including rows added later. Confirming a subset hides the other rows, adds style `filtered` on that column's opener, and does not change the collection. `Clear filter` shows the same row instances again and removes `filtered`.

An edit that makes the row fail the filter hides it and keeps the new value on the entity. A click outside the popup leaves the previous filter. Two active filters both apply.

The role and the active flag are not table columns. A boolean or enum column would offer the checklist and would not offer a comparison.

### Comparison

The comparison is offered for a string, a number, or a date or time. It is not offered for a boolean or an enum.

| Attribute | Operators |
| --- | --- |
| `String` | `Contains`, `Starts with`, `Ends with` |
| number | `Greater than`, `Less than` |
| date or time | `Before`, `After` |

Street and city on the desk are strings. Visited is a `LocalDate`, so its popup offers `Before` and `After`. The desk has no numeric column. A numeric attribute would offer `Greater than` and `Less than`.

If the operator or the value is non-blank, Confirm treats this as a comparison and ignores the checkboxes. The comparison must be valid, or the popup stays open and the previous filter stays. Operator and value are both required. Integral numbers must parse with `Long.parseLong`. Other numbers use `Double.valueOf`. `LocalDate`, `LocalDateTime`, and `Instant` use their `parse`. `Date` and `Calendar` use `Instant.parse`. A value such as `02.01.2020` is rejected for a `LocalDate`. `2020-01-02` is accepted.

String match uses the checklist text, lowercased with `Locale.ROOT`. `Contains` `ER` keeps `Berlin` and `berlin`. The boundary is excluded for numbers and for dates: `Greater than` `5` keeps 6 and drops 5, and `Before` `2020-01-02` keeps the 1st and drops the 2nd. Null does not match a comparison.

Reopening the popup shows the operator and the text that were confirmed. Clearing them and confirming a checklist replaces the comparison.

### Search

The search box hides checklist entries whose text does not contain the query, ignoring case, with `Locale.ROOT`. A `*` is an ordinary character. Checks are unchanged, and rows are unchanged until Confirm. Each opening of the popup clears the search.

`Select all` looks at every box, including hidden ones. Mixed checks show an indeterminate mark. A click selects every box when any is clear, and clears every box when all are selected.

## Messages

Every runtime `ViewModel` has a list of messages and a latest message. A replacement object that does not extend `ViewModel` does not automatically provide this message machinery. A message has a severity, a text, and a source.

| Severity | What happens |
| --- | --- |
| `INFO` | Stored on the view model. The desk's Leave a note button adds one whose source is `desk` |
| `WARN` | Stored. Leave a warning adds one. It is not written to the log |
| `ERROR` | Stored and written to the SLF4J logger `de.neitzel.fx.view.ViewModel`. Conversion failures use this severity |
| `FATAL` | Stored and written to that same logger. Leave a fatal notice adds one |

```java
viewModel.addMessage(new Message(
        Message.Severity.INFO,
        "The model already has the name " + name + ".",
        "desk"));
```

`getMessages()` is an unmodifiable live list, oldest first. There is no public remove/clear operation for application-added messages; converter failures have their own internal recovery removal. `getLatestMessage()` is the most recently added notice. An in-place update of a binding failure does not change which notice is latest, and it is logged again when the severity is error or fatal.

`latestMessage` is also a string property, the text of the latest message, or null when there is none. It is not a model attribute. The notice label binds it toward the view:

```xml
<DirectBinding control="noticeLabel" property="text" path="latestMessage" direction="view"/>
```

A `FATAL` notice does not terminate the application. The library does not open a popup or fill a status bar on its own. `MessagePopup.showNewMessages(viewModel)` shows each message added after that call. The desk calls it once, after load. The dialog has no header, its content is a label of the message text, and its button is OK. `Dialog.show()` does not block the caller. Messages already on the view model are not shown. An in-place update does not open another dialog.

A child message is carried onto the parent. The copy is a different `Message`, with the source extended by the attribute name, so `street` on an address becomes `home.street` on the person. The copy is not written to the log again. When the child drops the message, the parent drops that copy. An in-place text change updates the copy. Setting the complex attribute to null drops the parent's copies and leaves the child's own message. Setting a new child copies the messages that child already has. The same address object on two attributes carries the notice under both attribute names. A cycle does not copy a message back onto a view model that already holds it.

The desk calls `showNewMessages` on the person. A carried copy counts as a message added on the parent, so an empty home street opens a dialog as well as filling the notice label.

A list element is not a nested view model. A failure recorded on a visit is carried onto the person when that same address instance is also a complex attribute, such as `selectedVisit`. The visit list itself does not carry it.

## What the container creates

`ViewLoader.load` takes an `ApplicationContext`. The view model is never created by the container.

| Object | How it is created |
| --- | --- |
| Controller named by `fx:controller` | `getComponent`, once |
| Converter named on a direct binding | `getComponent` |
| First component of a class in a load | `getComponent`, when that instance is free to place |
| Another element of that class in the same load | `createComponent` |
| Repeat children and table rows | `createComponent` |

Details of scanning, `@Singleton`, `@Named`, constructor, field, and setter injection, and `@Config` are in the [injection guide](../injection/README.md). The desk's scan root is `DeskApp`.

## When a load fails

These are the `IllegalArgumentException` messages a document can produce. The view is not shown.

| Message | Cause |
| --- | --- |
| `No controller ` | `fx:controller` names a missing class |
| `No control "` | `fx:id` is missing |
| `No property ` | the control has no such JavaFX property |
| `Not a direction:` | an explicitly supplied direction is not `view`, `model`, or `both`; an omitted direction can instead cause `NullPointerException` |
| `No read-write attribute for path "` | the path is not an attribute |
| `Not a binding element:` | a child of `BindingList` is not a direct binding, a computed binding, or a repeat |
| `No converter ` / `Not a converter:` | the converter class is missing or does not implement `Converter` |
| `No controller for ` / `No computed method "` | a computed binding has no controller or no such method |
| `ViewModel is ` … ` but the View names ` | a later view requests an incompatible view model class |
| `No constructor taking the Model on ` | the named view model class has no matching constructor |
| `No component document ` | `ClassName.fxml` is missing |
| `No model property on ` | the component has no `modelProperty` |
| `No pane "` / `No collection "` / `No component ` | a repeat is incomplete |
| `A table needs a column` and the other table messages | see [Table](#table) |
| `View root must be a parent` | the FXML root is not a `Parent` |
| `No component found for type:` | the container was asked for a controller, component, or converter it did not scan |

A conversion failure does not fail the load. It records a message, as [Converter](#converter) describes.

Generated Java ViewModels can share the existing runtime storage and identity registry; see
[the generated ViewModel runtime contract](docs/generated-viewmodels.md) for stored-property
access, declarative service defaults, compatible overrides and module-path registration.


## Public API checklist

The reference above covers the FXML declarations and their behavior. These are the public entry points by responsibility:

| Responsibility | Types and key members |
| --- | --- |
| Load | `ViewLoader.load`, `LoadedView.getRoot`, `getViewModel`, `property` |
| Presentation state | `ViewModel.property`, protected `storedProperty`, `HasViewModel.setViewModel`, `@ViewModelClass` |
| Declarations | `BindingList`, `DirectBinding`, `ComputedBinding`, `Repeat`; JavaBean setters correspond to the FXML attributes described above |
| Convert values | `Converter.toControlValue`, `toAttributeValue` |
| Table | `Table` collection/row/columns/selection, `Column` header/attribute/width |
| Messages | `Message` severity/text/source, `Message.Severity`, `ViewModel.addMessage`, `getMessages`, `getLatestMessage`, `MessagePopup.showNewMessages` |
| Generated defaults | `ViewModelRegistry`, `ViewModelRegistration`, `ViewModelRegistrations`, `ViewModelDefaults.select` |
| Independent helpers | `IntegerSpinners.nonNegative`, `TraySupport` and its `Backend`/`Registration` |

`property(name)` returns `Property<?>`: cast to the known property type when writing from Java, for example `((StringProperty) loaded.property("name")).set("Grace")`. Generated accessors avoid these casts. `storedProperty` is for subclasses and reads only stored Model Attributes; it does not invoke custom reflective accessors. Internal loaders, appliers and identity-cache helpers are implementation details, not extension APIs.

For threading, strong identity retention, context reuse, the absence of a general disposal API, and JPMS reflection, see [architecture and lifetime](docs/architecture.md). For an independent application POM and runnable files, use the [complete quick start](docs/quick-start.md).
