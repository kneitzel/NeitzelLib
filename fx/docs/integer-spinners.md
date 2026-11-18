# Bounded integer inputs

`de.neitzel.fx.control.IntegerSpinners.nonNegative(min, max, value)` creates an
editable JavaFX spinner with inclusive nonnegative bounds. Construct and use it
on the JavaFX application thread.

Every valid typed number immediately updates the value property, so a form can
save its model without waiting for Enter or a focus change. Non-digit input and
values above the maximum are rejected. Empty input and intermediate numbers
below the minimum retain the last committed value and revert when focus leaves
the editor. Increment/decrement use the normal JavaFX spinner behavior.

The factory rejects negative/inverted bounds and out-of-range initial values.
Applications own their field labels, units, and domain-specific bounds.


Create and add a spinner in a controller's `initialize` method or in `Application.start`:

```java
Spinner<Integer> quantity = IntegerSpinners.nonNegative(0, 100, 1);
quantity.valueProperty().addListener((observable, oldValue, newValue) ->
        System.out.println("Quantity: " + newValue));
container.getChildren().add(quantity);
```

Import `javafx.scene.control.Spinner` and `de.neitzel.fx.control.IntegerSpinners`; `container` is your Pane. Read `quantity.getValue()` to obtain the committed number. Set a programmatic value through `quantity.getValueFactory().setValue(...)`. Use the factory's bounds as UI constraints and keep independent business validation where the value is saved. The helper does not add an FXML binding declaration or create a Model Attribute automatically.

## FXML

Declare `NonNegativeIntegerSpinner` with `minimum`, `maximum`, and `initialValue`
constructor attributes. Bind its writable `number` property with `DirectBinding`;
it shares the value factory property and supports both directions. See the
[FXML controls guide](fxml-controls.md#bounded-integer-spinner).
