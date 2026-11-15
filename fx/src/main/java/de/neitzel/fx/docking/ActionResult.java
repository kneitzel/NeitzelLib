package de.neitzel.fx.docking;

/** Outcome of a synchronous docking operation. */
public sealed interface ActionResult {
    record Applied() implements ActionResult { }
    record NoChange() implements ActionResult { }
    record Rejected(DockFailure failure) implements ActionResult { }
}
