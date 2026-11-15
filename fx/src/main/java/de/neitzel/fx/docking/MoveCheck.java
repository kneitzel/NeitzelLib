package de.neitzel.fx.docking;

/** Side-effect-free result of checking a proposed move. */
public sealed interface MoveCheck {
    record Allowed(ResolvedDockTarget destination) implements MoveCheck { }
    record NoChange() implements MoveCheck { }
    record Rejected(DockFailure failure) implements MoveCheck { }
}
