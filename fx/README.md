# Using fx

`fx` binds an FXML View to an ordinary Java Model. The Model stays a JavaBean. The library builds one ViewModel for that instance, loads the View, and applies the binding elements declared in the FXML.

The worked example is the `fx-example` module, package `de.neitzel.fx.example`. From the NeitzelLib root:

```bash
mvn -pl fx-example javafx:run
```

The desk edits one person: name, age, greeting, home address, visits, and tags.

## What you write

| Piece | Role |
| --- | --- |
| Model | A JavaBean. Read-write getters and setters are the attributes the View can bind. |
| FXML | The View. Binding elements live in an `fx:define` list, so they are not scene-graph children. |
| Controller | Optional. A `@Singleton` the container creates. Computed bindings call its methods. |
| Component | A reusable fragment: one class, one `ClassName.fxml` in the same package, one model property. |
| Converter | Optional. Translates one Direct binding between the attribute value and the control value. |
| `ApplicationContext` | The existing injection container. It creates the Controller, each Component, and each Converter. |

The library creates the ViewModel. The container does not.

## A Model

A read-write attribute is a getter and a setter, and it is not declared on `Object`. Strings, numbers, booleans, enums, and date/time values stay simple values. A type that itself has a read-write attribute is a complex attribute: the ViewModel holds the ViewModel of that nested instance. A `List` of either kind is a list, not a nested ViewModel.

`Person` and `Address` in the example are Models. They mention no JavaFX type.

Start lists as a mutable `ArrayList`. After the View loads, the Model's list attribute is the `ObservableList` the View is watching. Add and remove on that list. A later call to the setter leaves the View on the list it already holds.

## Load a View

```java
ApplicationContext context = new ApplicationContext(DeskApp.class);
Person person = DeskApp.sample();
LoadedView loaded = ViewLoader.load(
        DeskController.class.getResource("Desk.fxml"), person, context);
Parent root = loaded.getRoot();
ViewModel viewModel = loaded.getViewModel();
```

`ApplicationContext` scans the package of the class you pass, and its subpackages, for `@Singleton` and `@Named`. `ViewLoader.load` reuses the ViewModel already built for that instance, or builds one, then loads the FXML and applies the binding elements.

One Model instance has one ViewModel. A second View of the same instance reuses it, so the two Views stay aligned. The first View fixes the ViewModel class. A later View that names a different class fails to load.

Put the JavaFX launch in a class that does not extend `Application`, and call `Application.launch` from there. `Main` in the example does that. The scene is built on the JavaFX thread, which is where `start` runs.

## Direct binding

A Direct binding names a control by `fx:id`, a JavaFX property of that control, a Path, and a direction.

```xml
<fx:define>
    <BindingList>
        <DirectBinding control="nameField" property="text" path="name" direction="both"/>
    </BindingList>
</fx:define>
<TextField fx:id="nameField"/>
```

The Path `name` is the attribute of this View's ViewModel. `home.street` steps through the home ViewModel, and the direction applies to `street`. When a complex attribute along the Path is null, that binding is left unapplied and both sides keep their current values.

| Direction | What it does |
| --- | --- |
| `both` | Shows the attribute and writes it when the control changes. |
| `view` | Shows the attribute and follows later changes of the JavaFX property. Edits to the control stay on the control. |
| `model` | Shows the current value, writes the attribute when the control changes, and does not push later property changes back to the control. |

The write happens when the JavaFX property changes, including each change of a text property. There is no separate save for that write. After the ViewModel exists, it is the writer: a later call to the entity setter leaves the control on the value the ViewModel holds.

A Path is resolved when the binding is applied. A binding whose last segment is a complex attribute follows later changes of that attribute and passes the Model instance to the control. A longer Path stays with the child ViewModel that was in place when the binding was applied. The desk's "Home street" label is `home.street` toward the View. The home address itself is not swapped out from under that label.

A list is not indexed in a Path. Repeat a Component, or hand the list to a control.

## Converter

A text field edits an `int` through a Converter. The container creates the class and injects its services. `toControlValue` runs toward the View. `toAttributeValue` runs only when the direction is `model` or `both`. A toward-view binding does not need the reverse method.

```xml
<DirectBinding control="ageField" property="text" path="age" direction="both"
               converter="de.neitzel.fx.example.view.AgeConverter"/>
```

When a conversion throws, the other side keeps its previous value. The ViewModel records one error Message whose source is the attribute name (`age` on the desk). Further failures of that binding update the Message in place. A later success removes it.

## Computed binding

A Computed binding names a public no-argument method on the Controller. The method returns the value written to the control. It may read the Controller's fields. It does not write the Model.

```xml
<ComputedBinding control="greetingLabel" property="text" method="#greeting"/>
```

```java
public String greeting() {
    String name = nameField.getText();
    if (name == null || name.isBlank()) {
        return "Hello";
    }
    return "Hello, " + name.trim();
}
```

The library calls the method when the binding is applied, and again when a JavaFX property changes on a control in this View or on this View's ViewModel, other than the target. Writing the greeting label does not call `greeting` again.

## Controller

Name the class on the root with `fx:controller`. Leave it off and the library supplies a Controller, which is enough for a View that has only Direct bindings.

Implement `HasViewModel` when your code needs the ViewModel. The library calls `setViewModel` after construction and before the bindings run. The desk uses that hand-off for the tag list, the visit list, and Messages.

```java
@Singleton
public class DeskController implements HasViewModel {
    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
    }
}
```

`@FXML` fields are filled during load. A method used as `onAction` is ordinary FXML and is separate from a Computed binding.

## Component

A Component is a class you reuse for one Model instance. It extends a pane, loads `ClassName.fxml` from its own package through `fx:root`, and is the Controller of that file. It exposes one JavaFX property named `model`.

```java
@Singleton
public class AddressForm extends VBox {
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    public ObjectProperty<Object> modelProperty() {
        return model;
    }
}
```

The component document binds its own controls and has no `fx:controller`:

```xml
<fx:root type="javafx.scene.layout.VBox" xmlns="http://javafx.com/javafx"
         xmlns:fx="http://javafx.com/fxml">
    <fx:define>
        <BindingList>
            <DirectBinding control="streetField" property="text" path="street" direction="both"/>
        </BindingList>
    </fx:define>
    <TextField fx:id="streetField"/>
</fx:root>
```

A parent document names the class as an element and sets `model` with a Direct binding. When the Path's last segment is complex, the value assigned is the Model instance behind that ViewModel.

```xml
<DirectBinding control="homeForm" property="model" path="home" direction="both"/>
<AddressForm fx:id="homeForm"/>
```

The container creates the Component and injects its services. The component's own bindings run once, when `model` first becomes non-null, and they see that ViewModel. Set the model once. To show another instance, load another Component.

A nested Component is the same mechanism: the child document names the next class as an element.

## Repeat

A `Repeat` element in the same list names a pane, a Component class, and a collection attribute. The library places one new Component in that pane for each Model instance, in list order. Adding or removing an element updates the pane. Those instances are new injected objects, so the cached singleton of the class stays the one used by a single element in the parent document.

```xml
<Repeat pane="visitPane" component="de.neitzel.fx.example.view.AddressForm" collection="visits"/>
<VBox fx:id="visitPane"/>
```

Selection is a separate attribute of the parent Model. Null means nothing is selected. A Direct binding can tie a control's selection to it. The repeated Component does not store the selection.

```xml
<DirectBinding control="visitChoice" property="items" path="visits" direction="view"/>
<DirectBinding control="visitChoice" property="value" path="selectedVisit" direction="both"/>
<ComboBox fx:id="visitChoice"/>
```

The desk's "Add visit" button appends to the list on the ViewModel. That is the list stored back onto the person.

```java
@SuppressWarnings("unchecked")
private List<Address> visits() {
    return (List<Address>) viewModel.property("visits").getValue();
}
```

## A list of simple values

A list of strings, numbers, or other simple values is one shared `ObservableList`. A Direct binding sets the control property to that list, so an add or remove is visible on both sides.

```xml
<DirectBinding control="tagList" property="items" path="tags" direction="view"/>
<ListView fx:id="tagList"/>
```

The desk's "Add tag" button appends to `tags` the same way "Add visit" appends to `visits`.

## Messages

Every ViewModel has a list of Messages and a latest Message. A Message has a severity (`INFO`, `WARN`, `ERROR`, `FATAL`), a text, and a source. Add one of any severity yourself:

```java
viewModel.addMessage(new Message(
        Message.Severity.INFO,
        "The model already has the name " + name + ".",
        "desk"));
```

Error and fatal Messages are written to the log. Info and warn stay on the ViewModel. A child Message is carried onto the parent with the source extended, so `street` on an address becomes `home.street` on the person. When the child drops the Message, the parent drops that copy.

The library does not open a popup or fill a status bar. Bind `latestMessage` when you want the text on a control:

```xml
<DirectBinding control="noticeLabel" property="text" path="latestMessage" direction="view"/>
```

`latestMessage` is the text of the latest Message. An in-place update of a binding failure refreshes that text and does not count as a new Message.

Call `MessagePopup.showNewMessages(viewModel)` when a new Message should also appear in a dialog. The example does that after load. An in-place update does not open another dialog.

`LoadedView.property(String)` returns the JavaFX property for an attribute name, for `latestMessage`, or for a JavaFX property declared on a ViewModel subclass.

## Choosing the ViewModel class

With no annotation, the library builds the generic ViewModel. Put `@ViewModelClass` on the Controller or the Component to name a subclass, or another object that exposes JavaFX properties by name. The class needs a constructor that takes the Model instance. The library calls that constructor. The container still does not create the ViewModel.

```java
@Singleton
@ViewModelClass(TitledViewModel.class)
public class DeskController implements HasViewModel {
}
```

The first View of an instance fixes that class. A later View that names a different class fails to load, and the earlier View stays as it was. A later View with no annotation reuses the ViewModel.

A subclass may add JavaFX properties under new names. Binding elements address those names the same way they address an attribute. `property` on the subclass is a method such as `titleProperty`.

## Packages

Every package in the `fx` module is public. Depend on `de.neitzel.lib:fx`. The module depends on `injection`. Application types that the container constructs (`Controller`, `Component`, `Converter`) use `jakarta.inject.Singleton` or `jakarta.inject.Named`, and constructor injection when they need a service.
