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
